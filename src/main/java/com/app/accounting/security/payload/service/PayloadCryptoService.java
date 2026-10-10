package com.app.accounting.security.payload.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

@Slf4j
@Service
public class PayloadCryptoService {

    private static final int AES_KEY_SIZE_BYTES = 32;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int CAESAR_SHIFT = 13;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Decrypts an ephemeral AES key that was encrypted with RSA-OAEP (SHA-256).
     */
    public byte[] decryptAesKeyWithRsa(String base64EncryptedKey, PrivateKey rsaPrivateKey) throws Exception {
        byte[] encryptedKeyBytes = Base64.getDecoder().decode(base64EncryptedKey);

        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                new MGF1ParameterSpec("SHA-256"),
                PSource.PSpecified.DEFAULT
        );
        cipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey, oaepParams);
        return cipher.doFinal(encryptedKeyBytes);
    }

    /**
     * Decrypts AES-GCM ciphertext + reverses Caesar obfuscation.
     */
    public String decryptPayload(String base64Ciphertext, byte[] aesKeyBytes, String base64Nonce) throws Exception {
        byte[] ciphertextWithTag = Base64.getDecoder().decode(base64Ciphertext);
        byte[] iv = Base64.getDecoder().decode(base64Nonce);

        SecretKeySpec keySpec = new SecretKeySpec(aesKeyBytes, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
        byte[] decryptedObfuscated = cipher.doFinal(ciphertextWithTag);

        // Revert complementary Caesar obfuscation layer
        byte[] originalBytes = reverseCaesar(decryptedObfuscated, CAESAR_SHIFT);
        return new String(originalBytes, StandardCharsets.UTF_8);
    }

    /**
     * Applies complementary Caesar obfuscation + encrypts with AES-GCM.
     * Returns array of [Base64Ciphertext, Base64Nonce].
     */
    public String[] encryptPayload(String plaintextJson, byte[] aesKeyBytes) throws Exception {
        byte[] utf8Bytes = plaintextJson.getBytes(StandardCharsets.UTF_8);
        byte[] obfuscatedBytes = applyCaesar(utf8Bytes, CAESAR_SHIFT);

        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        SecretKeySpec keySpec = new SecretKeySpec(aesKeyBytes, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
        byte[] ciphertextWithTag = cipher.doFinal(obfuscatedBytes);

        String base64Ciphertext = Base64.getEncoder().encodeToString(ciphertextWithTag);
        String base64Nonce = Base64.getEncoder().encodeToString(iv);

        return new String[]{base64Ciphertext, base64Nonce};
    }

    /**
     * Complementary byte-level Caesar transformation.
     */
    public static byte[] applyCaesar(byte[] input, int shift) {
        byte[] out = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            out[i] = (byte) ((input[i] + shift) & 0xFF);
        }
        return out;
    }

    /**
     * Reversal of complementary byte-level Caesar transformation.
     */
    public static byte[] reverseCaesar(byte[] input, int shift) {
        byte[] out = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            out[i] = (byte) ((input[i] - shift) & 0xFF);
        }
        return out;
    }
}
