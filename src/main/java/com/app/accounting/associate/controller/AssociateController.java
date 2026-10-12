package com.app.accounting.associate.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.app.accounting.associate.dto.request.CreateAssociateReceiptRequest;
import com.app.accounting.associate.dto.response.AssociateReceiptResponse;
import com.app.accounting.associate.dto.response.AssociateStatementResponse;
import com.app.accounting.associate.dto.response.QuotaTemplateResponse;
import com.app.accounting.associate.service.AssociateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/associates")
@RequiredArgsConstructor
@Tag(name = "Asociados y Cuotas", description = "Gestión de plantillas de cuotas, recaudos discriminados y estados de cuenta para Asociaciones")
@PreAuthorize("hasAnyAuthority('COLLECTIONS_VIEW', 'ASSOCIATES_VIEW')")
public class AssociateController {

    private final AssociateService associateService;

    @GetMapping("/templates")
    @Operation(summary = "Listar plantillas de cuotas activas")
    public ResponseEntity<List<QuotaTemplateResponse>> getActiveTemplates() {
        return ResponseEntity.ok(associateService.getActiveTemplates());
    }

    @GetMapping("/templates/{id}")
    @Operation(summary = "Obtener detalle de plantilla de cuota por ID")
    public ResponseEntity<QuotaTemplateResponse> getTemplateById(@PathVariable UUID id) {
        return ResponseEntity.ok(associateService.getTemplateById(id));
    }

    @PreAuthorize("hasAuthority('COLLECTIONS_CREATE')")
    @PostMapping("/receipts")
    @Operation(summary = "Registrar recaudo de cuota con desglose contable automático y generación de comprobante")
    public ResponseEntity<AssociateReceiptResponse> registerReceipt(@Valid @RequestBody CreateAssociateReceiptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(associateService.registerReceipt(request));
    }

    @GetMapping("/receipts")
    @Operation(summary = "Listar historial de recaudos de asociados paginado")
    public ResponseEntity<Page<AssociateReceiptResponse>> getAllReceipts(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(associateService.getAllReceipts(pageable));
    }

    @GetMapping("/receipts/{id}")
    @Operation(summary = "Obtener detalle de recibo de caja de asociado")
    public ResponseEntity<AssociateReceiptResponse> getReceiptById(@PathVariable UUID id) {
        return ResponseEntity.ok(associateService.getReceiptById(id));
    }

    @GetMapping(value = "/receipts/{id}/pdf", produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Generar recibo de caja de asociado (RB) en formato PDF oficial")
    public ResponseEntity<byte[]> getReceiptPdf(@PathVariable UUID id) {
        byte[] pdf = associateService.generateAssociateReceiptPdf(id);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        headers.setContentDisposition(org.springframework.http.ContentDisposition.inline()
                .filename("recibo-asociado-" + id + ".pdf")
                .build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    @PostMapping("/receipts/{id}/send-email")
    @Operation(summary = "Enviar el recibo oficial con el PDF adjunto al correo del asociado")
    public ResponseEntity<Void> sendReceiptEmail(@PathVariable UUID id) {
        associateService.sendReceiptEmail(id);
        return ResponseEntity.ok().build();
    }


    @GetMapping("/{thirdPartyId}/receipts")
    @Operation(summary = "Obtener historial de recibos emitidos a un asociado específico")
    public ResponseEntity<List<AssociateReceiptResponse>> getReceiptsByAssociate(@PathVariable UUID thirdPartyId) {
        return ResponseEntity.ok(associateService.getReceiptsByAssociate(thirdPartyId));
    }

    @GetMapping("/{thirdPartyId}/statement")
    @Operation(summary = "Obtener estado de cuenta del asociado (aportes acumulados vs fondos pagados)")
    public ResponseEntity<AssociateStatementResponse> getAssociateStatement(@PathVariable UUID thirdPartyId) {
        return ResponseEntity.ok(associateService.getAssociateStatement(thirdPartyId));
    }

    @GetMapping(value = {"/{thirdPartyId}/statement/export", "/statements/{thirdPartyId}/export"})
    @Operation(summary = "Exportar estado de cuenta del asociado en formato PDF o Excel")
    public ResponseEntity<byte[]> exportAssociateStatement(
            @PathVariable UUID thirdPartyId,
            @RequestParam(defaultValue = "pdf") String format
    ) {
        byte[] fileBytes = associateService.exportAssociateStatement(thirdPartyId, format);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();

        if ("excel".equalsIgnoreCase(format) || "xlsx".equalsIgnoreCase(format)) {
            headers.setContentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDisposition(org.springframework.http.ContentDisposition.attachment()
                    .filename("estado-cuenta-asociado-" + thirdPartyId + ".xlsx")
                    .build());
        } else {
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
            headers.setContentDisposition(org.springframework.http.ContentDisposition.inline()
                    .filename("estado-cuenta-asociado-" + thirdPartyId + ".pdf")
                    .build());
        }

        return new ResponseEntity<>(fileBytes, headers, HttpStatus.OK);
    }
}
