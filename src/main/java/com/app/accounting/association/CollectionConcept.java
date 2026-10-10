package com.app.accounting.association;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Columnas de la planilla de recaudos (hoja PLANILLA del informe mensual), en el mismo orden del Excel.
 * Cada columna agrupa las cuentas acreditadas en el comprobante de ingreso del asociado.
 */
public enum CollectionConcept {
    APORTES("Aportes", AssociationChart.CONTRIBUTIONS.substring(0, 4)),
    APORTE_EXTRA("A. extra", AssociationChart.EXTRA_CONTRIBUTIONS),
    FONDO_SOLIDARIDAD("F. solidaridad", AssociationChart.SOLIDARITY_FUND),
    CUOTA_ADMINISTRACION("C. admón.", AssociationChart.ADMINISTRATION_FEE),
    PRESTAMO("Préstamo", "1365", "1380"),
    INTERES("Interés", AssociationChart.LOAN_INTEREST),
    PREEXEQUIAL("Preexeq.", "2815"),
    TURISMO("T y R", AssociationChart.TOURISM_FUND),
    BINGO("Bingo", AssociationChart.BINGO),
    FDE("FDE", AssociationChart.SPECIFIC_PURPOSE_FUND),
    MULTAS("Multas", AssociationChart.FINES),
    OTROS("Otros");

    private final String label;
    private final List<String> prefixes;

    CollectionConcept(String label, String... prefixes) {
        this.label = label;
        this.prefixes = List.of(prefixes);
    }

    public String label() {
        return label;
    }

    public String key() {
        return name().toLowerCase();
    }

    /** Concepto de una cuenta acreditada: gana el prefijo más largo; lo que no encaja va a OTROS. */
    public static CollectionConcept of(String accountCode) {
        return Stream.of(values())
                .flatMap(c -> c.prefixes.stream().filter(accountCode::startsWith).map(p -> new Object[]{c, p.length()}))
                .max(Comparator.comparingInt(o -> (Integer) o[1]))
                .map(o -> (CollectionConcept) o[0])
                .orElse(OTROS);
    }
}
