package com.app.accounting.report.controller;

import com.app.accounting.report.dto.response.FinancialReportResponse;
import com.app.accounting.report.service.FinancialReportService;
import com.app.accounting.report.service.ReportExportService;
import com.app.accounting.voucher.entity.VoucherType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports/financial")
@RequiredArgsConstructor
@Tag(name = "Reportes Financieros", description = "Reportes de caja, recaudos, cartera, préstamos, aportes, ahorros, CxC y CxP con exportación PDF/Excel")
public class FinancialReportController {

    private final FinancialReportService reportService;
    private final ReportExportService exportService;

    @GetMapping
    @PreAuthorize("hasAuthority('REPORTS_VIEW')")
    @Operation(summary = "Catálogo de reportes disponibles")
    public ResponseEntity<List<Map<String, String>>> catalog() {
        return ResponseEntity.ok(FinancialReportService.CATALOG.entrySet().stream()
                .map(e -> Map.of("code", e.getKey(), "title", e.getValue()))
                .toList());
    }

    @GetMapping("/{code}")
    @PreAuthorize("hasAuthority('REPORTS_VIEW')")
    @Operation(summary = "Generar un reporte financiero")
    public ResponseEntity<FinancialReportResponse> generate(
            @PathVariable String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String accountCode,
            @RequestParam(required = false) VoucherType voucherType,
            @RequestParam(required = false) UUID thirdPartyId,
            @RequestParam(required = false) String paymentMethod
    ) {
        return ResponseEntity.ok(reportService.generate(code,
                new FinancialReportService.Filters(from, to, accountCode, voucherType, thirdPartyId, paymentMethod)));
    }

    @GetMapping("/{code}/export")
    @PreAuthorize("hasAuthority('REPORTS_EXPORT')")
    @Operation(summary = "Exportar un reporte financiero a PDF o Excel")
    public ResponseEntity<byte[]> export(
            @PathVariable String code,
            @RequestParam(defaultValue = "pdf") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String accountCode,
            @RequestParam(required = false) VoucherType voucherType,
            @RequestParam(required = false) UUID thirdPartyId,
            @RequestParam(required = false) String paymentMethod
    ) {
        FinancialReportResponse report = reportService.generate(code,
                new FinancialReportService.Filters(from, to, accountCode, voucherType, thirdPartyId, paymentMethod));
        String base = "reporte-" + report.getCode().toLowerCase().replace('_', '-') + "-" + report.getPeriodFrom() + "_" + report.getPeriodTo();
        HttpHeaders headers = new HttpHeaders();
        byte[] body;
        if ("xlsx".equalsIgnoreCase(format) || "excel".equalsIgnoreCase(format)) {
            body = exportService.toExcel(report);
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDisposition(ContentDisposition.attachment().filename(base + ".xlsx").build());
        } else {
            body = exportService.toPdf(report);
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.attachment().filename(base + ".pdf").build());
        }
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
