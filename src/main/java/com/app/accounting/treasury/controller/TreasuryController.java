package com.app.accounting.treasury.controller;

import com.app.accounting.treasury.dto.request.PaymentReceiptRequest;
import com.app.accounting.treasury.dto.response.PaymentReceiptResponse;
import com.app.accounting.treasury.entity.PaymentType;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/treasury")
@RequiredArgsConstructor
@Tag(name = "Tesorería (Ingresos y Egresos)", description = "Recibos de Caja (CASH_IN) y Comprobantes de Egreso (CASH_OUT) con amortización de saldos y asientos contables automáticos")
public class TreasuryController {

    private final TreasuryService treasuryService;

    @PostMapping("/receipts")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTADOR', 'AUXILIAR')")
    @Operation(summary = "Procesar transacción de tesorería: Recibo de Caja (CASH_IN) o Comprobante de Egreso (CASH_OUT)")
    public ResponseEntity<PaymentReceiptResponse> processReceipt(@Valid @RequestBody PaymentReceiptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(treasuryService.processPaymentReceipt(request));
    }

    @GetMapping("/receipts/{id}")
    @Operation(summary = "Obtener recibo de caja / egreso por ID")
    public ResponseEntity<PaymentReceiptResponse> getReceiptById(@PathVariable UUID id) {
        return ResponseEntity.ok(treasuryService.getReceiptById(id));
    }

    @GetMapping("/receipts/number/{receiptNumber}")
    @Operation(summary = "Obtener recibo de caja / egreso por número (ej: RC-8402)")
    public ResponseEntity<PaymentReceiptResponse> getReceiptByNumber(@PathVariable String receiptNumber) {
        return ResponseEntity.ok(treasuryService.getReceiptByNumber(receiptNumber));
    }

    @GetMapping("/receipts")
    @Operation(summary = "Listar recibos de caja y egresos con filtros por tipo y rango de fechas")
    public ResponseEntity<Page<PaymentReceiptResponse>> getReceipts(
            @RequestParam(required = false) PaymentType paymentType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @org.springdoc.core.annotations.ParameterObject @PageableDefault(size = 20, sort = "paymentDate") Pageable pageable
    ) {
        return ResponseEntity.ok(treasuryService.getReceipts(paymentType, startDate, endDate, pageable));
    }

    @GetMapping(value = {"/payments/{id}/pdf", "/receipts/{id}/pdf"}, produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Generar el comprobante de ingreso/egreso oficial del abono en formato PDF")
    public ResponseEntity<byte[]> getPaymentReceiptPdf(@PathVariable UUID id) {
        byte[] pdf = treasuryService.generatePaymentReceiptPdf(id);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        headers.setContentDisposition(org.springframework.http.ContentDisposition.inline()
                .filename("recibo-pago-" + id + ".pdf")
                .build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}
