package com.app.accounting.voucher.controller;

import com.app.accounting.voucher.dto.request.JournalVoucherRequest;
import com.app.accounting.voucher.dto.response.JournalVoucherResponse;
import com.app.accounting.voucher.entity.VoucherStatus;
import com.app.accounting.voucher.service.VoucherService;
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
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
@Tag(name = "Comprobantes Contables", description = "Gestión de comprobantes, asientos y motor de partida doble")
public class JournalVoucherController {

    private final VoucherService voucherService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTADOR', 'AUXILIAR')")
    @Operation(summary = "Crear y asentar comprobante contable validando partida doble")
    public ResponseEntity<JournalVoucherResponse> createAndPostVoucher(@Valid @RequestBody JournalVoucherRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(voucherService.createAndPostVoucher(request));
    }

    @PostMapping("/draft")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTADOR', 'AUXILIAR')")
    @Operation(summary = "Crear comprobante en borrador (DRAFT)")
    public ResponseEntity<JournalVoucherResponse> createDraftVoucher(@Valid @RequestBody JournalVoucherRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(voucherService.createDraftVoucher(request));
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTADOR')")
    @Operation(summary = "Asentar comprobante en borrador a definitivo (POSTED) con partida doble")
    public ResponseEntity<JournalVoucherResponse> postVoucher(@PathVariable UUID id) {
        return ResponseEntity.ok(voucherService.postVoucher(id));
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTADOR')")
    @Operation(summary = "Revertir comprobante asentado mediante comprobante de contrapartida")
    public ResponseEntity<JournalVoucherResponse> reverseVoucher(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason
    ) {
        return ResponseEntity.ok(voucherService.reverseVoucher(id, reason));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener comprobante por UUID")
    public ResponseEntity<JournalVoucherResponse> getVoucherById(@PathVariable UUID id) {
        return ResponseEntity.ok(voucherService.getVoucherById(id));
    }

    @GetMapping("/number/{voucherNumber}")
    @Operation(summary = "Obtener comprobante por número de comprobante (ej: RC-001)")
    public ResponseEntity<JournalVoucherResponse> getVoucherByNumber(@PathVariable String voucherNumber) {
        return ResponseEntity.ok(voucherService.getVoucherByNumber(voucherNumber));
    }

    @GetMapping
    @Operation(summary = "Listar comprobantes contables con filtros y paginación")
    public ResponseEntity<Page<JournalVoucherResponse>> getAllVouchers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) VoucherStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(voucherService.getAllVouchers(startDate, endDate, status, pageable));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTADOR')")
    @Operation(summary = "Eliminar comprobante en borrador (DRAFT)")
    public ResponseEntity<Void> deleteDraftVoucher(@PathVariable UUID id) {
        voucherService.deleteDraftVoucher(id);
        return ResponseEntity.noContent().build();
    }
}
