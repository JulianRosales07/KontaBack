package com.app.accounting.importer.monthly;

import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.importer.monthly.MonthlyReport.Line;
import com.app.accounting.importer.monthly.MonthlyReport.Section;
import org.apache.poi.ss.usermodel.*;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee el informe mensual en Excel. Usa los valores calculados que guardó Excel (incluidas las celdas que
 * apuntan al libro externo del mes anterior), sin reevaluar fórmulas.
 */
public final class MonthlyReportParser {

    private static final String[] MONTHS = {"ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO", "JULIO", "AGOSTO",
            "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE"};

    private MonthlyReportParser() {
    }

    public static MonthlyReport parse(InputStream in) {
        try (Workbook wb = WorkbookFactory.create(in)) {
            MonthlyReport report = new MonthlyReport();
            Sheet esfa = sheet(wb, "ESFA");
            Sheet bal = sheet(wb, "BAL,PRUEBA", "BAL PRUEBA", "BALANCE DE PRUEBA");
            Sheet planilla = sheet(wb, "PLANILLA");
            if (bal == null || planilla == null) {
                throw new BusinessRuleException("El archivo no tiene las hojas PLANILLA y BAL,PRUEBA del informe mensual");
            }
            if (esfa != null) parseEsfa(esfa, report);
            parsePlanilla(planilla, report);
            parseTrialBalance(bal, report);
            return report;
        } catch (IOException e) {
            throw new BusinessRuleException("No se pudo leer el archivo Excel: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ ESFA

    private static void parseEsfa(Sheet sheet, MonthlyReport r) {
        String lastLabel = "";
        for (Row row : sheet) {
            String a = text(row, 0);
            String na = NameMatcher.normalize(a);
            if (row.getRowNum() == 0 && !a.isBlank()) r.organizationName = a.trim();
            if (na.startsWith("NIT")) r.organizationNit = a.replaceAll("(?i)nit", "").trim();
            if (r.period == null) r.period = periodFrom(na);
            BigDecimal b = number(row, 1);
            if (b != null && !na.isBlank()) r.esfa.put(na, b);
            if (na.equals("TERRENOS")) r.esfaLand = b;
            else if (na.equals("EDIFICACIONES")) r.esfaBuildings = b;
            else if (na.startsWith("MUEBLES Y EQUIPO")) r.esfaOfficeEquipment = b;
            else if (na.startsWith("DEPRECIACION ACUMULADA")) {
                if (lastLabel.equals("EDIFICACIONES")) r.esfaBuildingsDepreciation = b;
                else if (lastLabel.startsWith("MUEBLES Y EQUIPO")) r.esfaOfficeEquipmentDepreciation = b;
            }
            if (!na.isBlank()) lastLabel = na;
        }
    }

    static YearMonth periodFrom(String normalizedText) {
        Matcher m = Pattern.compile("(ENERO|FEBRERO|MARZO|ABRIL|MAYO|JUNIO|JULIO|AGOSTO|SEPTIEMBRE|OCTUBRE|NOVIEMBRE|DICIEMBRE) DE (\\d{4})")
                .matcher(normalizedText);
        if (!m.find()) return null;
        return YearMonth.of(Integer.parseInt(m.group(2)), Arrays.asList(MONTHS).indexOf(m.group(1)) + 1);
    }

    // ------------------------------------------------------------------ PLANILLA

    private static void parsePlanilla(Sheet sheet, MonthlyReport r) {
        Map<Integer, String> columns = new LinkedHashMap<>();
        int totalCol = -1;
        for (Row row : sheet) {
            if (columns.isEmpty()) {
                Map<Integer, String> found = new LinkedHashMap<>();
                int tc = -1;
                for (Cell cell : row) {
                    String h = NameMatcher.normalize(cellText(cell));
                    if (h.isBlank()) continue;
                    if (h.equals("TOTAL")) tc = cell.getColumnIndex();
                    else if (cell.getColumnIndex() >= 2) found.put(cell.getColumnIndex(), planillaConcept(h));
                }
                if (tc > 0 && found.containsValue("APORTES")) {
                    columns.putAll(found);
                    totalCol = tc;
                    r.planillaConcepts.addAll(found.values());
                }
                continue;
            }
            String name = text(row, 0).trim();
            Map<String, BigDecimal> values = new LinkedHashMap<>();
            for (Map.Entry<Integer, String> col : columns.entrySet()) {
                BigDecimal v = number(row, col.getKey());
                if (v != null && v.signum() != 0) values.merge(col.getValue(), v, BigDecimal::add);
            }
            BigDecimal total = number(row, totalCol);
            if (name.isBlank()) {
                if (total != null && total.signum() != 0) {
                    for (Map.Entry<Integer, String> col : columns.entrySet()) {
                        BigDecimal v = number(row, col.getKey());
                        if (v != null) r.planillaTotals.merge(col.getValue(), v, BigDecimal::add);
                    }
                    r.planillaGrandTotal = total;
                }
                continue;
            }
            r.planilla.add(new MonthlyReport.PlanillaRow(row.getRowNum() + 1, name, blankToNull(text(row, 1)), values,
                    total != null ? total : values.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)));
        }
        if (columns.isEmpty()) {
            throw new BusinessRuleException("No se encontró el encabezado de la hoja PLANILLA (APORTES … TOTAL)");
        }
    }

    /** Encabezado de la planilla → concepto canónico. */
    static String planillaConcept(String h) {
        String x = h.replace(" ", "");
        if (x.startsWith("AEXTRA") || x.contains("EXTRA")) return "A_EXTRA";
        if (x.startsWith("APORTE")) return "APORTES";
        if (x.contains("SOLIDAR")) return "F_SOLIDARIDAD";
        if (x.contains("ADMON") || x.contains("ADMIN")) return "C_ADMON";
        if (x.startsWith("PRESTAMO")) return "PRESTAMO";
        if (x.startsWith("INTERES")) return "INTERES";
        if (x.contains("EXEQ")) return "PREEXEQ";
        if (x.equals("TYR") || x.contains("TURISMO")) return "TYR";
        if (x.startsWith("BIGO") || x.startsWith("BINGO")) return "BINGO";
        if (x.equals("FDE")) return "FDE";
        if (x.startsWith("MULTA")) return "MULTAS";
        if (x.startsWith("OTRO") || x.contains("ARRIENDO")) return "OTROS";
        throw new BusinessRuleException("Columna de la PLANILLA sin concepto conocido: " + h);
    }

    // ------------------------------------------------------------------ BAL,PRUEBA

    private enum Zone {NONE, CASH, BANK_HEADER, BANK, INVESTMENTS, RECEIVABLES, PPE, PAYABLES, EXTRA, TOURISM, PREEXEQ, SOLIDARITY, EQUITY, INCOME, EXPENSES, DONE}

    private static void parseTrialBalance(Sheet sheet, MonthlyReport r) {
        Zone zone = Zone.NONE;
        Section section = null;
        String lastPayableLabel = null;
        for (Row row : sheet) {
            String label = text(row, 0).trim();
            String n = NameMatcher.normalize(label);
            if (r.period == null) r.period = periodFrom(n);
            Line line = line(row);
            if (n.isBlank() && !line.hasValues()) continue;

            // ---- encabezados de sección
            if (n.equals("CAJA")) { zone = Zone.CASH; continue; }
            if (n.startsWith("BANCOS Y OTRAS ENTIDADES")) { zone = Zone.BANK_HEADER; continue; }
            if (n.equals("INVERSIONES")) { zone = Zone.INVESTMENTS; r.investmentsTotal = line; continue; }
            if (n.equals("CUENTAS POR COBRAR")) { zone = Zone.RECEIVABLES; section = r.receivables; continue; }
            if (n.startsWith("PROPIEDADES PLANTA Y EQUIPO")) { zone = Zone.PPE; r.ppeTotal = line; continue; }
            if (n.equals("TOTAL ACTIVO")) { r.totalAssets = line; zone = Zone.NONE; continue; }
            if (n.equals("PASIVO")) { zone = Zone.NONE; continue; }
            if (n.equals("CUENTAS POR PAGAR")) { zone = Zone.PAYABLES; section = r.payables; r.payables.totals = line; continue; }
            if (n.startsWith("OTROS EXCEDENTE DE APORTES")) { zone = Zone.EXTRA; section = r.extraContributions; section.totals = line; continue; }
            if (n.equals("TURISMO Y RECREACION")) { zone = Zone.TOURISM; section = r.tourism; section.totals = line; continue; }
            // el título de preexequiales está dentro de los pasivos; en caja/bancos una fila así es un egreso
            if ((n.contains("PRE EXEQUIAL") || n.contains("PREEXEQUIAL")) && zone != Zone.CASH && zone != Zone.BANK) {
                zone = Zone.PREEXEQ;
                section = r.preexequiales;
                section.totals = line;
                continue;
            }
            if (n.equals("FONDO DE SOLIDARIDAD")) { zone = Zone.SOLIDARITY; section = r.solidarity; continue; }
            if (n.equals("TOTAL PASIVO")) { r.totalLiabilities = line; zone = Zone.NONE; continue; }
            if (n.equals("PATRIMONIO") || n.equals("CAPITAL SOCIAL") || n.equals("APORTES SOCIALES")) { zone = Zone.EQUITY; continue; }
            if (n.equals("APORTES ORDINARIOS")) { zone = Zone.EQUITY; section = r.contributions; section.totals = line; continue; }
            if (n.startsWith("VALORIZACION")) { r.valuations.add(line); section = null; continue; }
            if (n.startsWith("RESULTADOS DE EJERCICIOS ANTER")) { r.retainedEarnings = line; section = null; continue; }
            if (n.equals("RESULTADO DEL EJERCICIO")) { r.currentResult = line; continue; }
            if (n.equals("TOTAL PATRIMONIO")) { r.totalEquity = line; continue; }
            if (n.startsWith("TOTAL PASIVO MAS PATRIMONIO")) { r.totalLiabilitiesAndEquity = line; continue; }
            if (n.equals("INGRESOS")) {
                if (r.incomeTotal == null) r.incomeTotal = line;
                zone = Zone.INCOME;
                continue;
            }
            if (n.equals("GASTOS")) { zone = Zone.EXPENSES; r.expenseTotal = line; continue; }
            if (n.startsWith("UTILIDAD EJERCICIO") || n.startsWith("UTILIDAD DEL EJERCICIO") || n.startsWith("EXCEDENTE DEL EJERCICIO")) {
                r.resultLine = line;
                zone = Zone.DONE;
                continue;
            }

            // ---- contenido según la sección
            switch (zone) {
                case CASH -> {
                    if (n.startsWith("SALDO ANTERIOR")) r.cashOpening = line;
                    else if (n.startsWith("MOVIMIENTO DEL MES")) r.cashMovement = line;
                    else if (line.hasValues() || line.ref() != null) r.cashRows.add(line);
                }
                case BANK_HEADER -> {
                    if (n.equals("BANCOS COMERCIALES")) continue;
                    r.bankName = label;
                    zone = Zone.BANK;
                }
                case BANK -> {
                    if (n.startsWith("SALDO ANTERIOR")) r.bankOpening = line;
                    else if (line.hasValues()) r.bankRows.add(line);
                }
                case INVESTMENTS -> {
                    if (line.hasValues()) r.investmentRows.add(line);
                }
                case RECEIVABLES -> {
                    if (n.startsWith("CATEGORIA")) r.receivables.totals = line;
                    else if (line.hasValues()) r.receivables.rows.add(line);
                }
                case PPE -> {
                    if (line.hasValues()) r.ppeRows.add(line);
                }
                case PAYABLES -> {
                    if (n.equals("OTROS")) zone = Zone.NONE; // subtítulo que precede a "OTROS - EXCEDENTE DE APORTES"
                    else if (line.hasValues()) r.payables.rows.add(line);
                    else if (!n.isBlank()) lastPayableLabel = label;
                }
                case EXTRA, TOURISM, PREEXEQ, EQUITY -> {
                    if (section != null && (line.hasValues() || line.ref() != null)) section.rows.add(line);
                }
                case SOLIDARITY -> {
                    if (n.startsWith("SALDO ANTERIOR")) r.solidarity.totals = line;
                    else if (line.hasValues()) r.solidarity.rows.add(line);
                }
                case INCOME -> {
                    if (line.hasValues()) r.incomeRows.add(line);
                }
                case EXPENSES -> {
                    if (line.hasValues()) r.expenseRows.add(line);
                }
                default -> {
                }
            }
        }
        r.payablesDescription = lastPayableLabel;
        if (r.cashOpening == null || r.bankOpening == null || r.contributions.totals == null || r.incomeTotal == null || r.expenseTotal == null) {
            throw new BusinessRuleException("La hoja BAL,PRUEBA no tiene la estructura esperada (caja, bancos, aportes, ingresos y gastos)");
        }
    }

    // ------------------------------------------------------------------ celdas

    private static Line line(Row row) {
        return new Line(row.getRowNum() + 1, text(row, 0).trim(), blankToNull(text(row, 1)),
                number(row, 2), number(row, 3), number(row, 4), number(row, 5), number(row, 6));
    }

    private static Sheet sheet(Workbook wb, String... names) {
        for (String name : names) {
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                if (NameMatcher.normalize(wb.getSheetName(i)).equals(NameMatcher.normalize(name))) return wb.getSheetAt(i);
            }
        }
        return null;
    }

    private static String text(Row row, int col) {
        Cell cell = row.getCell(col);
        return cell == null ? "" : cellText(cell);
    }

    private static String cellText(Cell cell) {
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            default -> "";
        };
    }

    /** Valor numérico de la celda (o del resultado guardado de su fórmula); null si está vacía o es texto. */
    static BigDecimal number(Row row, int col) {
        if (col < 0) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
        }
        if (type == CellType.STRING) {
            String s = cell.getStringCellValue().trim().replace("$", "").replace(".", "").replace(",", ".");
            if (s.matches("-?\\d+(\\.\\d+)?")) return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
        }
        return null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
