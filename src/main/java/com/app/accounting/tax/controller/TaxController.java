package com.app.accounting.tax.controller;

import com.app.accounting.tax.dto.request.TaxCalculationRequest;
import com.app.accounting.tax.dto.request.TaxTemplateRequest;
import com.app.accounting.tax.dto.response.TaxCalculationResult;
import com.app.accounting.tax.dto.response.TaxTemplateResponse;
import com.app.accounting.tax.service.TaxEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/taxes", "/api/v1/tax"})
@RequiredArgsConstructor
@Tag(name = "Impuestos y Retenciones", description = "Motores de liquidación (IVA, Retefuente, ReteICA, Impoconsumo) y plantillas contables")
public class TaxController {

    private final TaxEngineService taxEngineService;

    @GetMapping("/templates")
    @Operation(summary = "Listar todas las plantillas de impuestos activas")
    public ResponseEntity<List<TaxTemplateResponse>> getActiveTemplates() {
        return ResponseEntity.ok(taxEngineService.getAllActiveTemplates());
    }

    @GetMapping("/templates/{id}")
    @Operation(summary = "Obtener plantilla de impuesto por ID")
    public ResponseEntity<TaxTemplateResponse> getTemplateById(@PathVariable UUID id) {
        return ResponseEntity.ok(taxEngineService.getTemplateById(id));
    }

    @PostMapping("/templates")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTADOR')")
    @Operation(summary = "Crear nueva plantilla de impuesto")
    public ResponseEntity<TaxTemplateResponse> createTemplate(@Valid @RequestBody TaxTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taxEngineService.createTemplate(request));
    }

    @PostMapping("/calculate")
    @Operation(summary = "Simular liquidación de impuestos sobre una base gravable")
    public ResponseEntity<TaxCalculationResult> calculateTaxes(@Valid @RequestBody TaxCalculationRequest request) {
        return ResponseEntity.ok(taxEngineService.calculateTaxes(request));
    }

    @GetMapping(value = {"/withholding-certificate", "/withholding-certificate/export"}, produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Generar certificado oficial de retención en la fuente (Art. 381 E.T.) en formato PDF")
    public ResponseEntity<byte[]> getWithholdingCertificate(
            @RequestParam UUID partyId,
            @RequestParam int year
    ) {
        byte[] pdf = taxEngineService.generateWithholdingCertificatePdf(partyId, year);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        headers.setContentDisposition(org.springframework.http.ContentDisposition.inline()
                .filename("certificado-retencion-" + partyId + "-" + year + ".pdf")
                .build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}
