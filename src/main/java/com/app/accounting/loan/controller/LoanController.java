package com.app.accounting.loan.controller;

import com.app.accounting.loan.dto.request.CreateLoanRequest;
import com.app.accounting.loan.dto.request.LegacyLoanImportRequest;
import com.app.accounting.loan.dto.request.LoanPaymentRequest;
import com.app.accounting.loan.dto.request.LoanPreviewRequest;
import com.app.accounting.loan.dto.response.LegacyImportResultResponse;
import com.app.accounting.loan.dto.response.LoanPaymentResponse;
import com.app.accounting.loan.dto.response.LoanPreviewResponse;
import com.app.accounting.loan.dto.response.LoanResponse;
import com.app.accounting.loan.entity.LoanStatus;
import com.app.accounting.loan.service.LoanService;
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
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('LOANS_VIEW', 'COLLECTIONS_VIEW', 'RECEIVABLES_VIEW')")
@Tag(name = "Préstamos", description = "Préstamos a asociados bajo saldo de capital, plan de pagos, abonos y cartera")
public class LoanController {

    private final LoanService loanService;

    @PostMapping("/preview")
    @Operation(summary = "Simular plan de pagos (mismo cálculo que se persiste al crear el préstamo)")
    public ResponseEntity<LoanPreviewResponse> preview(@Valid @RequestBody LoanPreviewRequest request) {
        return ResponseEntity.ok(loanService.preview(request));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LOANS_CREATE')")
    @Operation(summary = "Crear y desembolsar un préstamo (genera plan de pagos y comprobante de egreso)")
    public ResponseEntity<LoanResponse> create(@Valid @RequestBody CreateLoanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(loanService.createLoan(request));
    }

    @GetMapping
    @Operation(summary = "Listar préstamos (filtros opcionales por estado y asociado)")
    public ResponseEntity<List<LoanResponse>> list(
            @RequestParam(required = false) LoanStatus status,
            @RequestParam(required = false) UUID thirdPartyId,
            @RequestParam(defaultValue = "false") boolean includeInstallments
    ) {
        return ResponseEntity.ok(loanService.getLoans(status, thirdPartyId, includeInstallments));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle del préstamo con su plan de pagos")
    public ResponseEntity<LoanResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(loanService.getLoan(id));
    }

    @GetMapping("/{id}/payments")
    @Operation(summary = "Historial de abonos del préstamo agrupado por recibo")
    public ResponseEntity<List<LoanPaymentResponse>> payments(@PathVariable UUID id) {
        return ResponseEntity.ok(loanService.getPayments(id));
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAuthority('COLLECTIONS_CREATE')")
    @Operation(summary = "Registrar abono a una o varias cuotas (genera comprobante de ingreso)")
    public ResponseEntity<LoanPaymentResponse> registerPayment(@PathVariable UUID id, @Valid @RequestBody LoanPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(loanService.registerPayment(id, request));
    }

    @GetMapping("/payments")
    @Operation(summary = "Abonos a préstamos registrados en un rango de fechas (agrupados por recibo)")
    public ResponseEntity<List<LoanPaymentResponse>> paymentsBetween(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to
    ) {
        return ResponseEntity.ok(loanService.getPaymentsBetween(from, to));
    }

    @GetMapping("/receipts/{receiptNumber}")
    @Operation(summary = "Consultar un recibo de abono")
    public ResponseEntity<LoanPaymentResponse> receipt(@PathVariable String receiptNumber) {
        return ResponseEntity.ok(loanService.getReceipt(receiptNumber));
    }

    @PostMapping("/import-legacy")
    @PreAuthorize("hasAuthority('LOANS_CREATE')")
    @Operation(summary = "Importar una única vez préstamos guardados en el navegador, sin recalcular su plan")
    public ResponseEntity<LegacyImportResultResponse> importLegacy(@Valid @RequestBody LegacyLoanImportRequest request) {
        return ResponseEntity.ok(loanService.importLegacy(request));
    }
}
