package com.app.accounting.security.payload.controller;

import com.app.accounting.security.payload.model.PublicKeyResponse;
import com.app.accounting.security.payload.service.PayloadKeyManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
@Tag(name = "Protección de Datos", description = "Servicio de claves para protección de canal seguro")
public class PayloadSecurityController {

    private final PayloadKeyManager keyManager;

    @GetMapping("/public-key")
    @Operation(summary = "Obtener clave pública activa para protección de payloads")
    public ResponseEntity<PublicKeyResponse> getActivePublicKey() {
        PublicKeyResponse response = PublicKeyResponse.builder()
                .keyId(keyManager.getCurrentKeyId())
                .publicKey(keyManager.getCurrentPublicKeyPem())
                .build();
        return ResponseEntity.ok(response);
    }
}
