package com.app.accounting.common.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Datos de la organización para encabezados de documentos impresos (planes de pago, comprobantes, reportes). */
@RestController
@RequestMapping("/api/v1/organization")
@Tag(name = "Organización")
public class OrganizationController {

    @Value("${app.organization.name:Asociación}")
    private String name;

    @Value("${app.organization.nit:}")
    private String nit;

    @GetMapping
    @Operation(summary = "Nombre y NIT de la organización")
    public ResponseEntity<Map<String, String>> get() {
        return ResponseEntity.ok(Map.of("name", name, "nit", nit));
    }
}
