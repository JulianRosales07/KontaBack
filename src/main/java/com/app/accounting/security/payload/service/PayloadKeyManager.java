package com.app.accounting.security.payload.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class PayloadKeyManager {

    @Getter
    @Value("${app.security.payload.key-id:2026-v1}")
    private String currentKeyId;

    @Value("${app.security.payload.private-key:}")
    private String configuredPrivateKeyPem;

    @Value("${app.security.payload.public-key:}")
    private String configuredPublicKeyPem;

    private final Map<String, KeyPair> keyPairsById = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            if (configuredPrivateKeyPem != null && !configuredPrivateKeyPem.isBlank()
                    && configuredPublicKeyPem != null && !configuredPublicKeyPem.isBlank()) {
                PrivateKey privateKey = parsePrivateKey(configuredPrivateKeyPem);
                PublicKey publicKey = parsePublicKey(configuredPublicKeyPem);
                keyPairsById.put(currentKeyId, new KeyPair(publicKey, privateKey));
                log.info("Initialized secure payload keys from configuration. KeyId: {}", currentKeyId);
            } else {
                // Generate ephemeral 2048-bit RSA key pair for runtime / dev
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                kpg.initialize(2048, new SecureRandom());
                KeyPair kp = kpg.generateKeyPair();
                // Llave nueva en cada arranque: identificador propio para que los navegadores con la llave anterior
                // en caché la detecten (con el mismo identificador seguían cifrando con una llave que ya no existe)
                currentKeyId = currentKeyId + "-" + java.util.HexFormat.of().formatHex(SecureRandom.getSeed(4));
                keyPairsById.put(currentKeyId, kp);
                log.info("Initialized runtime ephemeral RSA keypair for secure payload. KeyId: {}", currentKeyId);
            }
        } catch (Exception e) {
            log.error("Failed to initialize payload key manager: {}", e.getMessage(), e);
            throw new IllegalStateException("Could not initialize secure payload keys", e);
        }
    }

    public PublicKey getPublicKey(String keyId) {
        KeyPair kp = keyPairsById.get(keyId);
        return kp != null ? kp.getPublic() : null;
    }

    public PrivateKey getPrivateKey(String keyId) {
        KeyPair kp = keyPairsById.get(keyId);
        return kp != null ? kp.getPrivate() : null;
    }

    public PublicKey getCurrentPublicKey() {
        return getPublicKey(currentKeyId);
    }

    public PrivateKey getCurrentPrivateKey() {
        return getPrivateKey(currentKeyId);
    }

    public String getCurrentPublicKeyPem() {
        PublicKey pk = getCurrentPublicKey();
        if (pk == null) return null;
        String base64 = Base64.getEncoder().encodeToString(pk.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + formatBase64Lines(base64) + "\n-----END PUBLIC KEY-----";
    }

    public void registerKey(String keyId, KeyPair keyPair) {
        keyPairsById.put(keyId, keyPair);
    }

    private String formatBase64Lines(String base64) {
        StringBuilder sb = new StringBuilder();
        int index = 0;
        while (index < base64.length()) {
            int next = Math.min(index + 64, base64.length());
            sb.append(base64, index, next);
            if (next < base64.length()) {
                sb.append("\n");
            }
            index = next;
        }
        return sb.toString();
    }

    private PrivateKey parsePrivateKey(String pem) throws Exception {
        String cleaned = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] encoded = Base64.getDecoder().decode(cleaned);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(keySpec);
    }

    private PublicKey parsePublicKey(String pem) throws Exception {
        String cleaned = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] encoded = Base64.getDecoder().decode(cleaned);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(keySpec);
    }
}
