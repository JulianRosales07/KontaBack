package com.app.accounting.closing.controller;

import com.app.accounting.closing.dto.request.ClosePeriodRequest;
import com.app.accounting.closing.dto.request.OpenPeriodRequest;
import com.app.accounting.closing.dto.response.AccountingPeriodResponse;
import com.app.accounting.closing.dto.response.AnnualClosingResponse;
import com.app.accounting.closing.service.AccountingPeriodService;
import com.app.accounting.closing.service.AnnualClosingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/closing")
@RequiredArgsConstructor
@Tag(name = "Cierre Contable y Periodos", description = "Gestión de periodos contables mensuales y motor de cierre anual de cuentas de resultado")
@PreAuthorize("hasAuthority('ACCOUNTING_VIEW')")
public class AccountingClosingController {

    private final AccountingPeriodService periodService;
    private final AnnualClosingService annualClosingService;

    @GetMapping("/periods")
    @Operation(summary = "Consultar el estado de los periodos contables de un año")
    public ResponseEntity<List<AccountingPeriodResponse>> getPeriods(@RequestParam Integer year) {
        return ResponseEntity.ok(periodService.getPeriodsByYear(year));
    }

    @PreAuthorize("hasAuthority('ACCOUNTING_CLOSE')")
    @PostMapping("/periods/open")
    @Operation(summary = "Abrir o desbloquear un periodo contable mensual")
    public ResponseEntity<AccountingPeriodResponse> openPeriod(@Valid @RequestBody OpenPeriodRequest request) {
        return ResponseEntity.ok(periodService.openPeriod(request.getYear(), request.getMonth()));
    }

    @PreAuthorize("hasAuthority('ACCOUNTING_CLOSE')")
    @PostMapping("/periods/close")
    @Operation(summary = "Cerrar o bloquear un periodo contable mensual")
    public ResponseEntity<AccountingPeriodResponse> closePeriod(@Valid @RequestBody ClosePeriodRequest request) {
        return ResponseEntity.ok(periodService.closePeriod(request.getYear(), request.getMonth(), request.getClosedBy()));
    }

    @PreAuthorize("hasAuthority('ACCOUNTING_CLOSE')")
    @PostMapping("/annual/{year}")
    @Operation(summary = "Ejecutar el cierre anual de cuentas de resultado (Clases 4, 5, 6, 7 contra 5905 y traslado a patrimonio)")
    public ResponseEntity<AnnualClosingResponse> executeAnnualClosing(@PathVariable int year) {
        return ResponseEntity.ok(annualClosingService.executeYearEndClosing(year));
    }
}
