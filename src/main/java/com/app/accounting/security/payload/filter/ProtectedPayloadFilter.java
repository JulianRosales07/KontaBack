package com.app.accounting.security.payload.filter;

import com.app.accounting.security.payload.model.ProtectedPayloadEnvelope;
import com.app.accounting.security.payload.service.PayloadCryptoService;
import com.app.accounting.security.payload.service.PayloadKeyManager;
import com.app.accounting.security.payload.service.ReplayProtectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProtectedPayloadFilter extends OncePerRequestFilter {

    public static final String ATTR_AES_KEY = "SECURE_PAYLOAD_AES_KEY";
    public static final String ATTR_KEY_ID = "SECURE_PAYLOAD_KEY_ID";
    public static final String ATTR_REQUEST_ID = "SECURE_PAYLOAD_REQUEST_ID";

    private static final String HEADER_KEY = "X-Payload-Key";
    private static final String HEADER_NONCE = "X-Payload-Nonce";
    private static final String HEADER_KEY_ID = "X-Payload-Key-Id";
    private static final String HEADER_REQ_ID = "X-Payload-Request-Id";
    private static final String HEADER_TIMESTAMP = "X-Payload-Timestamp";

    private final PayloadCryptoService cryptoService;
    private final PayloadKeyManager keyManager;
    private final ReplayProtectionService replayProtectionService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        // Exclude specific paths that must remain raw / public
        return path.contains("/security/public-key")
                || path.contains("/v3/api-docs")
                || path.contains("/swagger-ui")
                || path.contains("/h2-console")
                || path.endsWith("/export")
                || path.contains("/export/")
                || path.contains("/print");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        HttpServletRequest requestToUse = request;
        byte[] ephemeralAesKey = null;
        String activeKeyId = null;
        String activeRequestId = null;

        String method = request.getMethod();
        String contentType = request.getContentType();

        // 1. Process Request Envelope for POST / PUT / PATCH
        if (("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method))
                && contentType != null && contentType.toLowerCase().contains("application/json")) {

            byte[] bodyBytes = StreamUtils.copyToByteArray(request.getInputStream());

            if (bodyBytes.length > 0) {
                try {
                    String rawJson = new String(bodyBytes, StandardCharsets.UTF_8).trim();
                    if (rawJson.startsWith("{") && rawJson.contains("\"data\"") && rawJson.contains("\"key\"")) {
                        ProtectedPayloadEnvelope envelope = objectMapper.readValue(rawJson, ProtectedPayloadEnvelope.class);

                        if (!envelope.isCompleteRequestEnvelope()) {
                            log.warn("Incomplete protected payload envelope received for URI: {}", request.getRequestURI());
                            writeGenericError(response, HttpServletResponse.SC_BAD_REQUEST, "SECURE_PAYLOAD_INVALID", "No fue posible procesar la solicitud.");
                            return;
                        }

                        // Replay & timestamp verification
                        if (!replayProtectionService.validateRequest(envelope.getRequestId(), envelope.getTimestamp())) {
                            writeGenericError(response, HttpServletResponse.SC_BAD_REQUEST, "SECURE_PAYLOAD_INVALID", "No fue posible procesar la solicitud.");
                            return;
                        }

                        activeKeyId = envelope.getKeyId() != null ? envelope.getKeyId() : keyManager.getCurrentKeyId();
                        PrivateKey privateKey = keyManager.getPrivateKey(activeKeyId);
                        if (privateKey == null) {
                            log.warn("Unknown or rotated keyId in payload: {}", activeKeyId);
                            writeGenericError(response, HttpServletResponse.SC_BAD_REQUEST, "SECURE_PAYLOAD_INVALID", "No fue posible procesar la solicitud.");
                            return;
                        }

                        // Decrypt AES key with RSA-OAEP
                        ephemeralAesKey = cryptoService.decryptAesKeyWithRsa(envelope.getKey(), privateKey);

                        // Decrypt ciphertext with AES-GCM + reverse Caesar
                        String decryptedJson = cryptoService.decryptPayload(envelope.getData(), ephemeralAesKey, envelope.getNonce());

                        activeRequestId = envelope.getRequestId();
                        request.setAttribute(ATTR_AES_KEY, ephemeralAesKey);
                        request.setAttribute(ATTR_KEY_ID, activeKeyId);
                        request.setAttribute(ATTR_REQUEST_ID, activeRequestId);

                        requestToUse = new DecryptedHttpServletRequestWrapper(request, decryptedJson.getBytes(StandardCharsets.UTF_8));
                    } else {
                        // Allow non-enveloped requests if needed, but restore body for controllers
                        requestToUse = new DecryptedHttpServletRequestWrapper(request, bodyBytes);
                    }
                } catch (Exception e) {
                    log.warn("Protected payload validation or decryption failed for URI: {}", request.getRequestURI());
                    writeGenericError(response, HttpServletResponse.SC_BAD_REQUEST, "SECURE_PAYLOAD_INVALID", "No fue posible procesar la solicitud.");
                    return;
                }
            }
        } else if ("GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            // Process secure channel headers for GET / DELETE
            String headerKey = request.getHeader(HEADER_KEY);
            if (headerKey != null && !headerKey.isBlank()) {
                try {
                    String headerNonce = request.getHeader(HEADER_NONCE);
                    String headerKeyId = request.getHeader(HEADER_KEY_ID);
                    String headerReqId = request.getHeader(HEADER_REQ_ID);
                    String headerTimestampStr = request.getHeader(HEADER_TIMESTAMP);

                    Long headerTimestamp = headerTimestampStr != null ? Long.parseLong(headerTimestampStr) : null;

                    if (!replayProtectionService.validateRequest(headerReqId, headerTimestamp)) {
                        writeGenericError(response, HttpServletResponse.SC_BAD_REQUEST, "SECURE_PAYLOAD_INVALID", "No fue posible procesar la solicitud.");
                        return;
                    }

                    activeKeyId = headerKeyId != null ? headerKeyId : keyManager.getCurrentKeyId();
                    PrivateKey privateKey = keyManager.getPrivateKey(activeKeyId);
                    if (privateKey == null) {
                        writeGenericError(response, HttpServletResponse.SC_BAD_REQUEST, "SECURE_PAYLOAD_INVALID", "No fue posible procesar la solicitud.");
                        return;
                    }

                    ephemeralAesKey = cryptoService.decryptAesKeyWithRsa(headerKey, privateKey);
                    activeRequestId = headerReqId;

                    request.setAttribute(ATTR_AES_KEY, ephemeralAesKey);
                    request.setAttribute(ATTR_KEY_ID, activeKeyId);
                    request.setAttribute(ATTR_REQUEST_ID, activeRequestId);
                } catch (Exception e) {
                    log.warn("Secure header processing failed for URI: {}", request.getRequestURI());
                    writeGenericError(response, HttpServletResponse.SC_BAD_REQUEST, "SECURE_PAYLOAD_INVALID", "No fue posible procesar la solicitud.");
                    return;
                }
            }
        }

        // 2. Wrap Response to protect outgoing payload
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(requestToUse, responseWrapper);
        } finally {
            byte[] aesKey = (byte[]) request.getAttribute(ATTR_AES_KEY);
            String responseContentType = responseWrapper.getContentType();

            boolean isJson = responseContentType != null
                    && (responseContentType.contains(MediaType.APPLICATION_JSON_VALUE)
                    || responseContentType.contains("application/problem+json"));

            if (aesKey != null && isJson && !response.isCommitted()) {
                try {
                    byte[] rawResponseBytes = responseWrapper.getContentAsByteArray();
                    if (rawResponseBytes.length > 0) {
                        String plaintextResponse = new String(rawResponseBytes, StandardCharsets.UTF_8);

                        String[] encryptedDataAndNonce = cryptoService.encryptPayload(plaintextResponse, aesKey);

                        String reqId = (String) request.getAttribute(ATTR_REQUEST_ID);
                        String kId = (String) request.getAttribute(ATTR_KEY_ID);

                        ProtectedPayloadEnvelope responseEnvelope = ProtectedPayloadEnvelope.builder()
                                .keyId(kId != null ? kId : keyManager.getCurrentKeyId())
                                .requestId(reqId != null ? reqId : UUID.randomUUID().toString())
                                .timestamp(System.currentTimeMillis())
                                .data(encryptedDataAndNonce[0])
                                .nonce(encryptedDataAndNonce[1])
                                .build();

                        byte[] envelopeBytes = objectMapper.writeValueAsBytes(responseEnvelope);

                        response.resetBuffer();
                        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
                        response.setHeader("Pragma", "no-cache");
                        response.setHeader("X-Content-Type-Options", "nosniff");
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.setContentLength(envelopeBytes.length);
                        response.getOutputStream().write(envelopeBytes);
                        response.getOutputStream().flush();
                        return;
                    }
                } catch (Exception ex) {
                    log.error("Failed to protect response payload: {}", ex.getMessage());
                }
            }

            responseWrapper.copyBodyToResponse();
        }
    }

    private void writeGenericError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        String errorJson = String.format("{\"code\":\"%s\",\"message\":\"%s\"}", code, message);
        response.getWriter().write(errorJson);
    }
}
