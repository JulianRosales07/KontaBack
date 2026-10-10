package com.app.accounting.importer.monthly;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Resultado de importar (o simular) el informe mensual: comprobantes, conciliación contra el Excel y observaciones. */
@Data
@Builder
public class MonthlyReportImportResponse {

    private String fileName;
    private String period;
    private String organizationName;
    private boolean dryRun;
    private boolean applied;
    private String message;

    @Builder.Default
    private List<String> accountsCreated = new ArrayList<>();
    @Builder.Default
    private List<String> associatesCreated = new ArrayList<>();
    private int associatesMatched;
    @Builder.Default
    private List<VoucherSummary> vouchers = new ArrayList<>();
    @Builder.Default
    private List<ReconciliationLine> reconciliation = new ArrayList<>();
    /** Inconsistencias del propio Excel o decisiones del importador que conviene revisar. */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    public record VoucherSummary(String number, String type, LocalDate date, String description, BigDecimal total, int lines) {
    }

    public record ReconciliationLine(String report, String sheet, String concept, BigDecimal excel, BigDecimal system,
                                     BigDecimal difference, boolean ok) {
    }

    public long reconciliationFailures() {
        return reconciliation.stream().filter(l -> !l.ok()).count();
    }
}
