package com.app.accounting.importer.monthly;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contenido del informe mensual de la asociación (libro con hojas PLANILLA, BAL,PRUEBA, ESFA...).
 * Columnas de BAL,PRUEBA: C = saldo anterior, D = débitos/cargos/retiros del mes, E = créditos/abonos/aportes del mes,
 * F = saldo final reportado.
 */
public class MonthlyReport {

    public String organizationName;
    public String organizationNit;
    public YearMonth period;

    // ------------------------------------------------------------- PLANILLA
    public final List<String> planillaConcepts = new ArrayList<>();
    public final List<PlanillaRow> planilla = new ArrayList<>();
    public final Map<String, BigDecimal> planillaTotals = new LinkedHashMap<>();
    public BigDecimal planillaGrandTotal;

    public record PlanillaRow(int excelRow, String name, String receipt, Map<String, BigDecimal> values, BigDecimal total) {
    }

    // ------------------------------------------------------------- BAL,PRUEBA
    /** Fila genérica: etiqueta (col. A), referencia (col. B) y columnas C a G. */
    public record Line(int excelRow, String label, String ref, BigDecimal c, BigDecimal d, BigDecimal e, BigDecimal f, BigDecimal g) {
        public boolean hasValues() {
            return nz(c).signum() != 0 || nz(d).signum() != 0 || nz(e).signum() != 0;
        }
    }

    /** Sección con fila de totales y filas por tercero. */
    public static class Section {
        public final String key;
        public Line totals;
        public final List<Line> rows = new ArrayList<>();

        public Section(String key) {
            this.key = key;
        }
    }

    public Line cashOpening;
    public Line cashMovement;
    public final List<Line> cashRows = new ArrayList<>();

    public String bankName;
    public Line bankOpening;
    public final List<Line> bankRows = new ArrayList<>();

    public Line investmentsTotal;
    public final List<Line> investmentRows = new ArrayList<>();

    public final Section receivables = new Section("CXC");
    public Line ppeTotal;
    public final List<Line> ppeRows = new ArrayList<>();
    public Line totalAssets;

    public final Section payables = new Section("CXP");
    public String payablesDescription;
    public final Section extraContributions = new Section("APORTES_EXTRAS");
    public final Section tourism = new Section("TURISMO");
    public final Section preexequiales = new Section("PREEXEQUIALES");
    public final Section solidarity = new Section("SOLIDARIDAD");
    public Line totalLiabilities;

    public final Section contributions = new Section("APORTES");
    public final List<Line> valuations = new ArrayList<>();
    public Line retainedEarnings;
    public Line currentResult;
    public Line totalEquity;
    public Line totalLiabilitiesAndEquity;

    public Line incomeTotal;
    public final List<Line> incomeRows = new ArrayList<>();
    public Line expenseTotal;
    public final List<Line> expenseRows = new ArrayList<>();
    public Line resultLine;

    // ------------------------------------------------------------- ESFA
    public final Map<String, BigDecimal> esfa = new LinkedHashMap<>();
    public BigDecimal esfaLand;
    public BigDecimal esfaBuildings;
    public BigDecimal esfaBuildingsDepreciation;
    public BigDecimal esfaOfficeEquipment;
    public BigDecimal esfaOfficeEquipmentDepreciation;

    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
