package com.app.accounting.security.payload.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProtectedPayloadEnvelope {

    private String keyId;
    private String requestId;
    private Long timestamp;
    private String key;       // RSA-OAEP encrypted ephemeral AES-256 key (Base64)
    private String nonce;     // AES-GCM IV 12 bytes (Base64)
    private String data;      // AES-GCM ciphertext + tag (Base64)

    public boolean isCompleteRequestEnvelope() {
        return data != null && !data.isBlank()
                && key != null && !key.isBlank()
                && nonce != null && !nonce.isBlank()
                && requestId != null && !requestId.isBlank()
                && timestamp != null;
    }

    public boolean isCompleteResponseEnvelope() {
        return data != null && !data.isBlank()
                && nonce != null && !nonce.isBlank();
    }
}
