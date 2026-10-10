package com.app.accounting.association;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mapeo concepto → cuenta PUC de la asociación, validado por la contadora (informe mensual de abril 2026).
 * Es la única fuente de los códigos que usan el importador del informe mensual, la planilla de recaudos
 * y los reportes por fondo; el resto de cuentas se toma del PUC tal como está.
 */
public final class AssociationChart {

    private AssociationChart() {
    }

    // ---------------------------------------------------------------- Activo
    public static final String CASH = "110505";
    public static final String BANKS = "112005";
    public static final String INVESTMENTS_CDT = "122505";
    public static final String LOANS_TO_ASSOCIATES = "136505";
    public static final String OTHER_RECEIVABLES = "138095";
    public static final String LAND = "150405";
    public static final String BUILDINGS = "151605";
    public static final String OFFICE_EQUIPMENT = "152405";
    public static final String DEPRECIATION_BUILDINGS = "159205";
    public static final String DEPRECIATION_OFFICE_EQUIPMENT = "159215";

    // ---------------------------------------------------------------- Pasivo
    public static final String SOLIDARITY_FUND = "238025";
    public static final String TOURISM_FUND = "238035";
    public static final String ADVANCE_INCOME = "270595";
    public static final String THIRD_PARTY_FUNDS = "281505";
    public static final String EXTRA_CONTRIBUTIONS = "289510";

    // ---------------------------------------------------------------- Patrimonio
    public static final String CONTRIBUTIONS = "311505";
    public static final String CURRENT_RESULT = "360505";
    public static final String RETAINED_EARNINGS = "370505";
    public static final String VALUATION_BUILDINGS = "381005";
    public static final String VALUATION_OFFICE_EQUIPMENT = "381010";

    // ---------------------------------------------------------------- Ingresos
    public static final String ADMINISTRATION_FEE = "417010";
    public static final String BINGO = "41709501";
    public static final String EVENTS = "41709502";
    public static final String PARTIES = "41709503";
    public static final String RAFFLES = "41709504";
    public static final String LOAN_INTEREST = "421005";
    public static final String BANK_YIELDS = "42109501";
    public static final String RENTS = "422010";
    public static final String SPECIFIC_PURPOSE_FUND = "429505";
    public static final String FINES = "429510";

    // ---------------------------------------------------------------- Gastos
    public static final String MAINTENANCE_BUILDINGS = "514510";
    public static final String MAINTENANCE_OFFICE_EQUIPMENT = "514520";
    public static final String DEPRECIATION_EXPENSE_BUILDINGS = "516005";
    public static final String DEPRECIATION_EXPENSE_OFFICE_EQUIPMENT = "516015";
    public static final String STATIONERY = "519530";
    public static final String STAFF_COLLABORATION = "51959501";
    public static final String BINGO_PRIZES = "51959502";
    public static final String BANK_FEES = "530505";
    public static final String SOCIAL_ACTIVITIES = "53959501";

    /** Definición de una cuenta: nombre y si exige tercero. */
    public record Definition(String name, boolean requiresThirdParty) {
    }

    /**
     * Cuentas del mapeo y sus padres, con el nombre que deben tener si hay que crearlas.
     * Las cuentas existentes no se renombran desde aquí (los renombres aprobados van en la migración V11).
     */
    public static final Map<String, Definition> DEFINITIONS = new LinkedHashMap<>();

    static {
        def("1", "ACTIVO");
        def("11", "EFECTIVO Y EQUIVALENTES AL EFECTIVO");
        def("1105", "CAJA");
        def(CASH, "CAJA GENERAL");
        def("1120", "CUENTAS DE AHORRO");
        def(BANKS, "BANCOS");
        def("12", "INVERSIONES");
        def("1225", "CERTIFICADOS");
        def(INVESTMENTS_CDT, "CERTIFICADOS DE DEPOSITO A TERMINO (C.D.T.)");
        def("13", "DEUDORES");
        def("1365", "CUENTAS POR COBRAR A TRABAJADORES Y ASOCIADOS");
        def(LOANS_TO_ASSOCIATES, "PRESTAMOS A ASOCIADOS", true);
        def("1380", "DEUDORES VARIOS");
        def(OTHER_RECEIVABLES, "OTROS DEUDORES (PREEXEQUIALES Y VARIOS)", true);
        def("15", "PROPIEDADES, PLANTA Y EQUIPO");
        def("1504", "TERRENOS");
        def(LAND, "URBANOS");
        def("1516", "CONSTRUCCIONES Y EDIFICACIONES");
        def(BUILDINGS, "EDIFICIOS");
        def("1524", "EQUIPO DE OFICINA");
        def(OFFICE_EQUIPMENT, "MUEBLES Y ENSERES");
        def("1592", "DEPRECIACION ACUMULADA");
        def(DEPRECIATION_BUILDINGS, "CONSTRUCCIONES Y EDIFICACIONES");
        def(DEPRECIATION_OFFICE_EQUIPMENT, "EQUIPO DE OFICINA");

        def("2", "PASIVO");
        def("23", "CUENTAS POR PAGAR");
        def("2380", "ACREEDORES VARIOS");
        def(SOLIDARITY_FUND, "FONDO DE SOLIDARIDAD", true);
        def(TOURISM_FUND, "FONDO DE TURISMO Y RECREACION", true);
        def("27", "DIFERIDOS");
        def("2705", "INGRESOS RECIBIDOS POR ANTICIPADO");
        def(ADVANCE_INCOME, "OTROS");
        def("28", "OTROS PASIVOS");
        def("2815", "INGRESOS RECIBIDOS PARA TERCEROS");
        def(THIRD_PARTY_FUNDS, "VALORES RECIBIDOS PARA TERCEROS (PREEXEQUIALES)", true);
        def("2895", "DIVERSOS");
        def(EXTRA_CONTRIBUTIONS, "APORTES EXTRAORDINARIOS DE ASOCIADOS", true);

        def("3", "PATRIMONIO");
        def("31", "CAPITAL SOCIAL");
        def("3115", "APORTES SOCIALES");
        def(CONTRIBUTIONS, "APORTES SOCIALES ORDINARIOS", true);
        def("36", "RESULTADOS DEL EJERCICIO");
        def("3605", "UTILIDAD DEL EJERCICIO");
        def(CURRENT_RESULT, "EXCEDENTE DEL EJERCICIO");
        def("37", "RESULTADOS DE EJERCICIOS ANTERIORES");
        def("3705", "UTILIDADES ACUMULADAS");
        def(RETAINED_EARNINGS, "EXCEDENTES DE EJERCICIOS ANTERIORES");
        def("38", "SUPERAVIT POR VALORIZACIONES");
        def("3810", "DE PROPIEDADES PLANTA Y EQUIPO");
        def(VALUATION_BUILDINGS, "VALORIZACION CASA SEDE");
        def(VALUATION_OFFICE_EQUIPMENT, "VALORIZACION MUEBLES Y EQUIPO DE OFICINA");

        def("4", "INGRESOS");
        def("41", "OPERACIONALES");
        def("4170", "ACTIVIDADES DE ASOCIACION");
        def(ADMINISTRATION_FEE, "CUOTAS DE ADMINISTRACION");
        def("417095", "ACTIVIDADES CONEXAS");
        def(BINGO, "INGRESOS POR BINGOS");
        def(EVENTS, "PLATOS Y EVENTOS");
        def(PARTIES, "FIESTAS");
        def(RAFFLES, "RIFAS");
        def("42", "NO OPERACIONALES");
        def("4210", "FINANCIEROS");
        def(LOAN_INTEREST, "INTERESES SOBRE PRESTAMOS A ASOCIADOS");
        def("421095", "OTROS");
        def(BANK_YIELDS, "RENDIMIENTOS CUENTA DE AHORROS");
        def("4220", "ARRENDAMIENTOS");
        def(RENTS, "CONSTRUCCIONES Y EDIFICIOS");
        def("4295", "DIVERSOS");
        def(SPECIFIC_PURPOSE_FUND, "FONDO DE DESTINACION ESPECIFICA");
        def(FINES, "MULTAS E INASISTENCIA");

        def("5", "GASTOS");
        def("51", "OPERACIONALES DE ADMINISTRACION");
        def("5145", "MANTENIMIENTO Y REPARACIONES");
        def(MAINTENANCE_BUILDINGS, "CONSTRUCCIONES Y EDIFICACIONES");
        def(MAINTENANCE_OFFICE_EQUIPMENT, "EQUIPO DE OFICINA");
        def("5160", "DEPRECIACIONES");
        def(DEPRECIATION_EXPENSE_BUILDINGS, "CONSTRUCCIONES Y EDIFICACIONES");
        def(DEPRECIATION_EXPENSE_OFFICE_EQUIPMENT, "EQUIPO DE OFICINA");
        def("5195", "DIVERSOS");
        def(STATIONERY, "UTILES, PAPELERIA Y FOTOCOPIAS");
        def("519595", "OTROS");
        def(STAFF_COLLABORATION, "COLABORACION PERSONAL ADMINISTRATIVO");
        def(BINGO_PRIZES, "PREMIOS BINGO");
        def("53", "NO OPERACIONALES");
        def("5305", "FINANCIEROS");
        def(BANK_FEES, "GASTOS BANCARIOS");
        def("5395", "GASTOS DIVERSOS");
        def("539595", "OTROS");
        def(SOCIAL_ACTIVITIES, "ACTIVIDADES SOCIALES DE ASOCIADOS");
    }

    private static void def(String code, String name) {
        def(code, name, false);
    }

    private static void def(String code, String name, boolean requiresThirdParty) {
        DEFINITIONS.put(code, new Definition(name, requiresThirdParty));
    }

    /** Nivel PUC según la longitud del código: clase, grupo, cuenta, subcuenta, auxiliar. */
    public static int levelOf(String code) {
        return switch (code.length()) {
            case 1 -> 1;
            case 2 -> 2;
            case 4 -> 3;
            case 6 -> 4;
            default -> 5;
        };
    }

    /** Código del padre inmediato (1 → null, 11 → 1, 1105 → 11, 110505 → 1105, 11050501 → 110505). */
    public static String parentOf(String code) {
        return switch (code.length()) {
            case 1 -> null;
            case 2 -> code.substring(0, 1);
            case 4 -> code.substring(0, 2);
            case 6 -> code.substring(0, 4);
            default -> code.substring(0, code.length() - 2);
        };
    }

    /** Naturaleza por clase: 1, 5, 6 y 7 débito; 2, 3 y 4 crédito. */
    public static boolean isDebitNature(String code) {
        char c = code.charAt(0);
        return c == '1' || c == '5' || c == '6' || c == '7';
    }
}
