package com.app.accounting.billing.controller;

import com.app.accounting.billing.dto.request.SalesInvoiceRequest;
import com.app.accounting.billing.dto.response.DebtDocumentResponse;
import com.app.accounting.billing.dto.response.SalesInvoiceResponse;
import com.app.accounting.billing.entity.DocumentStatus;
import com.app.accounting.billing.dto.response.CustomerStatementResponse;
import com.app.accounting.billing.dto.response.ReceivableAgingReportResponse;
import com.app.accounting.billing.service.BillingService;
import com.app.accounting.treasury.dto.response.PaymentReceiptResponse;
import com.app.accounting.treasury.service.TreasuryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@Tag(name = "Facturación y Cartera (Cuentas por Cobrar)", description = "Emisión de facturas de venta a crédito y control de cartera de clientes")
@PreAuthorize("hasAuthority('ACCOUNTING_VIEW')")
public class BillingController {

    private final BillingService billingService;
    private final TreasuryService treasuryService;

    @PreAuthorize("hasAuthority('ACCOUNTING_CREATE')")
    @PostMapping("/invoices")
    @Operation(summary = "Emitir factura de venta a crédito con liquidación de impuestos y causación contable automática")
    public ResponseEntity<SalesInvoiceResponse> createSalesInvoice(@Valid @RequestBody SalesInvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(billingService.createSalesInvoice(request));
    }

    @GetMapping("/receivables/{id}")
    @Operation(summary = "Obtener documento de cartera por ID")
    public ResponseEntity<DebtDocumentResponse> getDocumentById(@PathVariable UUID id) {
        return ResponseEntity.ok(billingService.getDocumentById(id));
    }

    @GetMapping("/receivables/number/{documentNumber}")
    @Operation(summary = "Obtener documento de cartera por número de documento (ej: FAC-1001)")
    public ResponseEntity<DebtDocumentResponse> getDocumentByNumber(@PathVariable String documentNumber) {
        return ResponseEntity.ok(billingService.getDocumentByNumber(documentNumber));
    }

    @GetMapping("/receivables")
    @Operation(summary = "Listar cuentas por cobrar (cartera) filtradas por tercero y estado")
    public ResponseEntity<Page<DebtDocumentResponse>> getReceivables(
            @RequestParam(required = false) UUID thirdPartyId,
            @RequestParam(required = false) DocumentStatus status,
            @org.springdoc.core.annotations.ParameterObject @PageableDefault(size = 20, sort = "dueDate") Pageable pageable
    ) {
        return ResponseEntity.ok(billingService.getReceivables(thirdPartyId, status, pageable));
    }

    @GetMapping("/receivables/aging")
    @Operation(summary = "Reporte de Cartera por Edades / Análisis de Vencimiento (Corriente, 1-30, 31-60, 61-90, +90 días)")
    public ResponseEntity<ReceivableAgingReportResponse> getReceivablesAging(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) UUID thirdPartyId
    ) {
        return ResponseEntity.ok(billingService.getReceivablesAging(asOfDate, thirdPartyId));
    }

    @GetMapping("/receivables/customer/{thirdPartyId}/statement")
    @Operation(summary = "Obtener Estado de Cuenta de Cartera de un cliente específico")
    public ResponseEntity<CustomerStatementResponse> getCustomerStatement(@PathVariable UUID thirdPartyId) {
        return ResponseEntity.ok(billingService.getCustomerStatement(thirdPartyId));
    }

    @GetMapping("/receivables/{id}/payments")
    @Operation(summary = "Obtener el historial de recibos de caja (abonos) aplicados a una factura de cartera")
    public ResponseEntity<List<PaymentReceiptResponse>> getDocumentPayments(@PathVariable UUID id) {
        return ResponseEntity.ok(treasuryService.getReceiptsByDocumentId(id));
    }

    @GetMapping(value = {"/statements/{partyId}/export", "/receivables/customer/{partyId}/statement/export"})
    @Operation(summary = "Exportar Estado de Cuenta de Cartera consolidado en formato PDF o Excel")
    public ResponseEntity<byte[]> exportCustomerStatement(
            @PathVariable UUID partyId,
            @RequestParam(defaultValue = "pdf") String format
    ) {
        byte[] fileBytes = billingService.exportCustomerStatement(partyId, format);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();

        if ("excel".equalsIgnoreCase(format) || "xlsx".equalsIgnoreCase(format)) {
            headers.setContentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDisposition(org.springframework.http.ContentDisposition.attachment()
                    .filename("estado-cuenta-cartera-" + partyId + ".xlsx")
                    .build());
        } else {
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
            headers.setContentDisposition(org.springframework.http.ContentDisposition.inline()
                    .filename("estado-cuenta-cartera-" + partyId + ".pdf")
                    .build());
        }

        return new ResponseEntity<>(fileBytes, headers, HttpStatus.OK);
    }
}

