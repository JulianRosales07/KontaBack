package com.app.accounting.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estructura común de todos los reportes financieros: encabezado, periodo, resumen, detalle con subtotales y totales.
 * La misma estructura alimenta la vista web, el PDF y el Excel para que los tres muestren exactamente las mismas cifras.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialReportResponse {

    private String code;
    private String title;
    private String subtitle;
    private String organizationName;
    private String organizationNit;
    private LocalDate periodFrom;
    private LocalDate periodTo;
    private OffsetDateTime generatedAt;
    private String generatedBy;

    @Builder.Default
    private Map<String, String> filters = new LinkedHashMap<>();

    /** Indicadores destacados (saldo inicial, ingresos, egresos, saldo final...). */
    @Builder.Default
    private List<SummaryItem> summary = new ArrayList<>();

    @Builder.Default
    private List<Column> columns = new ArrayList<>();

    /** Filas de detalle; las filas con rowType GROUP/SUBTOTAL estructuran el reporte. */
    @Builder.Default
    private List<Row> rows = new ArrayList<>();

    /** Totales generales por clave de columna monetaria/numérica. */
    @Builder.Default
    private Map<String, BigDecimal> totals = new LinkedHashMap<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryItem {
        private String label;
        private BigDecimal value;
        /** MONEY | NUMBER */
        @Builder.Default
        private String type = "MONEY";
        /** neutral | positive | negative | primary */
        @Builder.Default
        private String tone = "neutral";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Column {
        private String key;
        private String label;
        /** TEXT | MONEY | DATE | NUMBER | PERCENT */
        @Builder.Default
        private String type = "TEXT";
        /** Si true, la columna suma en subtotales/totales. */
        private boolean summable;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        /** DETAIL | GROUP | SUBTOTAL */
        @Builder.Default
        private String rowType = "DETAIL";
        @Builder.Default
        private Map<String, Object> cells = new LinkedHashMap<>();
    }
}
