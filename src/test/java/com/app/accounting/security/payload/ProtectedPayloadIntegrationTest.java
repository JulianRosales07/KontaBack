package com.app.accounting.security.payload;

import com.app.accounting.security.payload.model.ProtectedPayloadEnvelope;
import com.app.accounting.security.payload.service.PayloadCryptoService;
import com.app.accounting.security.payload.service.PayloadKeyManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ProtectedPayloadIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private PayloadKeyManager keyManager;

    @Autowired
    private PayloadCryptoService cryptoService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String encryptAesKey(byte[] aesKeyBytes, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                new MGF1ParameterSpec("SHA-256"),
                PSource.PSpecified.DEFAULT
        );
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepParams);
        return Base64.getEncoder().encodeToString(cipher.doFinal(aesKeyBytes));
    }

    @Test
    @DisplayName("Public key endpoint returns 200 with active keyId and PEM public key")
    void testPublicKeyEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/security/public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyId").value(keyManager.getCurrentKeyId()))
                .andExpect(jsonPath("$.publicKey").exists());
    }

    @Test
    @DisplayName("Tampered payload is rejected with 400 SECURE_PAYLOAD_INVALID")
    void testTamperedPayloadRejected() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        SecretKey aesKey = kg.generateKey();
        byte[] aesBytes = aesKey.getEncoded();

        PublicKey pubKey = keyManager.getCurrentPublicKey();
        String encryptedKey = encryptAesKey(aesBytes, pubKey);

        String[] cipherAndIv = cryptoService.encryptPayload("{\"email\":\"test@corp.com\",\"password\":\"12345\"}", aesBytes);

        // Tamper with data
        byte[] rawCipher = Base64.getDecoder().decode(cipherAndIv[0]);
        rawCipher[2] = (byte) (rawCipher[2] ^ 0xAA);
        String tamperedCipher = Base64.getEncoder().encodeToString(rawCipher);

        ProtectedPayloadEnvelope envelope = ProtectedPayloadEnvelope.builder()
                .keyId(keyManager.getCurrentKeyId())
                .requestId(UUID.randomUUID().toString())
                .timestamp(System.currentTimeMillis())
                .key(encryptedKey)
                .nonce(cipherAndIv[1])
                .data(tamperedCipher)
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(envelope)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SECURE_PAYLOAD_INVALID"));
    }

    @Test
    @DisplayName("Replay attack (same requestId) is rejected on second attempt")
    void testReplayAttackRejected() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        SecretKey aesKey = kg.generateKey();
        byte[] aesBytes = aesKey.getEncoded();

        PublicKey pubKey = keyManager.getCurrentPublicKey();
        String encryptedKey = encryptAesKey(aesBytes, pubKey);

        String[] cipherAndIv = cryptoService.encryptPayload("{\"username\":\"invalid_user\",\"password\":\"badpass\"}", aesBytes);

        String uniqueRequestId = "replay-test-" + UUID.randomUUID();
        long now = System.currentTimeMillis();

        ProtectedPayloadEnvelope envelope = ProtectedPayloadEnvelope.builder()
                .keyId(keyManager.getCurrentKeyId())
                .requestId(uniqueRequestId)
                .timestamp(now)
                .key(encryptedKey)
                .nonce(cipherAndIv[1])
                .data(cipherAndIv[0])
                .build();

        // First attempt (reaches authentication, fails auth 401, but passes payload validation)
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(envelope)))
                .andExpect(status().isUnauthorized());

        // Second attempt with exact same requestId (must be rejected by replay protection with 400)
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(envelope)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SECURE_PAYLOAD_INVALID"));
    }

    @Test
    @DisplayName("Expired timestamp (>5min) is rejected with 400 SECURE_PAYLOAD_INVALID")
    void testExpiredTimestampRejected() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        SecretKey aesKey = kg.generateKey();
        byte[] aesBytes = aesKey.getEncoded();

        PublicKey pubKey = keyManager.getCurrentPublicKey();
        String encryptedKey = encryptAesKey(aesBytes, pubKey);

        String[] cipherAndIv = cryptoService.encryptPayload("{\"email\":\"test@test.com\",\"password\":\"pwd\"}", aesBytes);

        // 10 minutes in the past
        long expiredTime = System.currentTimeMillis() - (10 * 60 * 1000);

        ProtectedPayloadEnvelope envelope = ProtectedPayloadEnvelope.builder()
                .keyId(keyManager.getCurrentKeyId())
                .requestId(UUID.randomUUID().toString())
                .timestamp(expiredTime)
                .key(encryptedKey)
                .nonce(cipherAndIv[1])
                .data(cipherAndIv[0])
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(envelope)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SECURE_PAYLOAD_INVALID"));
    }

    @Test
    @DisplayName("Unknown or rotated keyId is rejected gracefully with 400 SECURE_PAYLOAD_INVALID")
    void testUnknownKeyIdRejected() throws Exception {
        ProtectedPayloadEnvelope envelope = ProtectedPayloadEnvelope.builder()
                .keyId("unknown-key-version-99")
                .requestId(UUID.randomUUID().toString())
                .timestamp(System.currentTimeMillis())
                .key("dummy-key")
                .nonce("dummy-nonce")
                .data("dummy-data")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(envelope)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SECURE_PAYLOAD_INVALID"));
    }
}
