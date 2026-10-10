package com.app.accounting.security.payload;

import com.app.accounting.security.payload.service.PayloadCryptoService;
import com.app.accounting.security.payload.service.ReplayProtectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class PayloadCryptoServiceTest {

    private PayloadCryptoService cryptoService;
    private KeyPair rsaKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        cryptoService = new PayloadCryptoService();

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, new SecureRandom());
        rsaKeyPair = kpg.generateKeyPair();
    }

    @Test
    @DisplayName("Should successfully encrypt and decrypt payload with Caesar layer and AES-GCM")
    void testEncryptAndDecryptPayload() throws Exception {
        String sensitiveJson = "{\"documento\":\"1085123456\",\"saldo\":1500000,\"nombre\":\"Juan Pérez\"}";

        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        SecretKey aesKey = kg.generateKey();
        byte[] aesKeyBytes = aesKey.getEncoded();

        // Encrypt
        String[] encrypted = cryptoService.encryptPayload(sensitiveJson, aesKeyBytes);
        assertNotNull(encrypted[0]);
        assertNotNull(encrypted[1]);
        assertFalse(encrypted[0].contains("Juan Pérez"));
        assertFalse(encrypted[0].contains("1085123456"));

        // Decrypt
        String decryptedJson = cryptoService.decryptPayload(encrypted[0], aesKeyBytes, encrypted[1]);
        assertEquals(sensitiveJson, decryptedJson);
    }

    @Test
    @DisplayName("Tampered ciphertext should fail decryption and throw exception")
    void testTamperedPayloadFails() throws Exception {
        String sensitiveJson = "{\"saldo\":50000}";

        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        byte[] aesKeyBytes = kg.generateKey().getEncoded();

        String[] encrypted = cryptoService.encryptPayload(sensitiveJson, aesKeyBytes);

        // Tamper with ciphertext by modifying a character
        byte[] rawCipher = Base64.getDecoder().decode(encrypted[0]);
        rawCipher[0] = (byte) (rawCipher[0] ^ 0xFF);
        String tamperedCipher = Base64.getEncoder().encodeToString(rawCipher);

        assertThrows(Exception.class, () ->
                cryptoService.decryptPayload(tamperedCipher, aesKeyBytes, encrypted[1]));
    }

    @Test
    @DisplayName("ReplayProtectionService rejects duplicate requestId and expired timestamp")
    void testReplayProtection() {
        ReplayProtectionService replayService = new ReplayProtectionService();
        long now = System.currentTimeMillis();

        String reqId = "test-req-" + System.nanoTime();

        // First attempt succeeds
        assertTrue(replayService.validateRequest(reqId, now));

        // Replay attempt fails
        assertFalse(replayService.validateRequest(reqId, now));

        // Expired timestamp (10 minutes ago) fails
        long expiredTime = now - (10 * 60 * 1000);
        assertFalse(replayService.validateRequest("new-req-expired", expiredTime));
    }
}
