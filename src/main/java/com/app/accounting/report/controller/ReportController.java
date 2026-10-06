package com.app.accounting.report.controller;

import com.app.accounting.report.dto.response.*;
import com.app.accounting.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reportes Contables y Financieros", description = "Balance de Prueba, Libro Mayor, Auxiliar por Tercero (Kárdex), Estado de Resultados (P&G) y Balance General")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/trial-balance")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTADOR')")
    @Operation(summary = "Generar reporte de Balance de Prueba (Trial Balance)")
    public ResponseEntity<TrialBalanceResponse> getTrialBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(reportService.generateTrialBalance(startDate, endDate));
    }

    @GetMapping("/general-ledger")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTADOR')")
    @Operation(summary = "Generar Libro Mayor / Auxiliar para una cuenta específica")
    public ResponseEntity<LedgerReportResponse> getGeneralLedger(
            @RequestParam String accountCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(reportService.generateGeneralLedger(accountCode, startDate, endDate));
    }

    @GetMapping("/party-auxiliary")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTADOR')")
    @Operation(summary = "Generar Auxiliar por Tercero y Cuenta (Kárdex de Cartera / Proveedores)")
    public ResponseEntity<PartyAuxiliaryReportResponse> getPartyAuxiliary(
            @RequestParam UUID accountId,
            @RequestParam UUID partyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(reportService.generatePartyAuxiliary(accountId, partyId, startDate, endDate));
    }

    @GetMapping("/income-statement")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTADOR')")
    @Operation(summary = "Generar Estado de Resultados (P&G) con cálculo de utilidad del periodo")
    public ResponseEntity<IncomeStatementResponse> getIncomeStatement(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(reportService.generateIncomeStatement(startDate, endDate));
    }

    @GetMapping("/balance-sheet")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTADOR')")
    @Operation(summary = "Generar Balance General (Estado de Situación Financiera) a una fecha de corte")
    public ResponseEntity<BalanceSheetResponse> getBalanceSheet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        return ResponseEntity.ok(reportService.generateBalanceSheet(asOfDate));
    }

    @GetMapping("/trial-balance-by-party")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTADOR')")
    @Operation(summary = "Generar Balance de Prueba a nivel de Terceros (discriminado por cuenta y tercero)")
    public ResponseEntity<PartyTrialBalanceResponse> getTrialBalanceByParty(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID partyId
    ) {
        return ResponseEntity.ok(reportService.generatePartyTrialBalance(startDate, endDate, partyId));
    }

    @GetMapping("/income-statement-by-party")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTADOR')")
    @Operation(summary = "Generar Estado de Resultados (P&G) a nivel de Terceros (ingresos, costos y gastos por tercero)")
    public ResponseEntity<PartyIncomeStatementResponse> getIncomeStatementByParty(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID partyId
    ) {
        return ResponseEntity.ok(reportService.generatePartyIncomeStatement(startDate, endDate, partyId));
    }

    @GetMapping("/balance-sheet-by-party")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTADOR')")
    @Operation(summary = "Generar Balance General (Situación Financiera) a nivel de Terceros a una fecha de corte")
    public ResponseEntity<PartyBalanceSheetResponse> getBalanceSheetByParty(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) UUID partyId
    ) {
        return ResponseEntity.ok(reportService.generatePartyBalanceSheet(asOfDate, partyId));
    }
}

