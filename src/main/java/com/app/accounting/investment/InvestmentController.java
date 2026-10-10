package com.app.accounting.investment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/investments")
@RequiredArgsConstructor
@Tag(name = "Inversiones", description = "Registro mínimo de CDT y otras inversiones (el saldo contable vive en la cuenta PUC)")
public class InvestmentController {

    private final InvestmentService service;

    @GetMapping
    @PreAuthorize("hasAuthority('REPORTS_VIEW')")
    @Operation(summary = "Listar inversiones")
    public ResponseEntity<List<InvestmentResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ACCOUNTING_CREATE')")
    @Operation(summary = "Registrar una inversión")
    public ResponseEntity<InvestmentResponse> create(@Valid @RequestBody InvestmentRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ACCOUNTING_CREATE')")
    @Operation(summary = "Actualizar una inversión")
    public ResponseEntity<InvestmentResponse> update(@PathVariable UUID id, @Valid @RequestBody InvestmentRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ACCOUNTING_CREATE')")
    @Operation(summary = "Eliminar el registro de una inversión (no afecta la contabilidad)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
