package com.app.accounting.importer;

import com.app.accounting.importer.monthly.MonthlyReportImportResponse;
import com.app.accounting.importer.monthly.MonthlyReportImportResponse.ReconciliationLine;
import com.app.accounting.importer.monthly.MonthlyReportImportService;
import com.app.accounting.importer.monthly.NameMatcher;
import com.app.accounting.investment.InvestmentRepository;
import com.app.accounting.report.dto.response.FinancialReportResponse;
import com.app.accounting.report.service.FinancialReportService;
import com.app.accounting.report.service.ReportExportService;
import com.app.accounting.voucher.repository.JournalVoucherRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Importador del informe mensual y conciliación: lo que muestran los reportes del sistema después de importar
 * debe coincidir con las cifras del propio Excel (columna "saldo final" de cada hoja).
 */
@SpringBootTest
@ActiveProfiles("test")
class MonthlyReportImportTest {

    private static final Path REAL_REPORT = Path.of("Docs", "INFORME ASOC MES DE ABRIL DE 2026.xlsx");

    @Autowired private MonthlyReportImportService importService;
    @Autowired private FinancialReportService reportService;
    @Autowired private ReportExportService exportService;
    @Autowired private JournalVoucherRepository voucherRepository;
    @Autowired private InvestmentRepository investmentRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    /** Ejecuta dentro de una transacción que siempre se revierte (no deja datos para otras pruebas). */
    private void inRollback(Consumer<Void> body) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            try {
                body.accept(null);
            } finally {
                status.setRollbackOnly();
            }
        });
    }

    // ================================================================== libro sintético

    @Test
    @DisplayName("Simulación: concilia todo y no guarda nada")
    void dryRunDoesNotPersist() throws Exception {
        long before = voucherRepository.count();
        MonthlyReportImportResponse r = importService.importReport(new ByteArrayInputStream(syntheticWorkbook()), "sintetico.xlsx", true);

        assertThat(r.getErrors()).isEmpty();
        assertThat(r.isApplied()).isFalse();
        assertThat(r.getReconciliation()).isNotEmpty().allMatch(ReconciliationLine::ok);
        assertThat(voucherRepository.count()).isEqualTo(before);
        assertThat(voucherRepository.existsByVoucherNumber("APERTURA-2026-04")).isFalse();
    }

    @Test
    @DisplayName("Importación: apertura + recibos + egresos + bancos + depreciación, y los reportes cuadran con el Excel")
    void importReconcilesWithWorkbook() {
        inRollback(v -> {
            MonthlyReportImportResponse r = importService.importReport(new ByteArrayInputStream(syntheticWorkbookUnchecked()), "sintetico.xlsx", false);
            assertThat(r.getErrors()).isEmpty();
            assertThat(r.isApplied()).isTrue();
            assertThat(r.getVouchers()).extracting(MonthlyReportImportResponse.VoucherSummary::number)
                    .containsExactly("APERTURA-2026-04", "RB-1", "RB-2", "CE-1", "NB-2026-04-REND", "NC-2026-04-DEP");
            assertThat(r.getAssociatesCreated()).containsExactlyInAnyOrder("ANA PEREZ", "LUIS GOMEZ");
            assertThat(r.getReconciliation()).allMatch(ReconciliationLine::ok);

            LocalDate from = LocalDate.of(2026, 4, 1);
            LocalDate to = LocalDate.of(2026, 4, 30);
            FinancialReportResponse esfa = generate("ESTADO_SITUACION", from, to);
            assertThat(summary(esfa, "Total activos")).isEqualByComparingTo("9379500");
            assertThat(summary(esfa, "Total pasivos")).isEqualByComparingTo("421500");
            assertThat(summary(esfa, "Total patrimonio")).isEqualByComparingTo("8958000");
            assertThat(summary(esfa, "Diferencia (A − P − Pt)")).isEqualByComparingTo("0");

            FinancialReportResponse er = generate("ESTADO_RESULTADOS", from, to);
            assertThat(summary(er, "Ingresos acumulados")).isEqualByComparingTo("58400");
            assertThat(summary(er, "Gastos y costos acumulados")).isEqualByComparingTo("110000");
            assertThat(summary(er, "Resultado del periodo")).isEqualByComparingTo("-12000");

            // Balance de prueba: sumas iguales y saldos anteriores cuadrados
            FinancialReportResponse trial = generate("BALANCE_PRUEBA", from, to);
            assertThat(summary(trial, "Diferencia débitos − créditos")).isEqualByComparingTo("0");
            assertThat(summary(trial, "Descuadre de saldos anteriores")).isEqualByComparingTo("0");

            // Planilla: columnas por concepto y caja/bancos
            FinancialReportResponse planilla = generate("PLANILLA_RECAUDOS", from, to);
            assertThat(planilla.getTotals().get("total")).isEqualByComparingTo("89000");
            assertThat(planilla.getTotals().get("cash")).isEqualByComparingTo("79000");
            assertThat(planilla.getTotals().get("bank")).isEqualByComparingTo("10000");
            assertThat(planilla.getTotals().get("prestamo")).isEqualByComparingTo("20000");

            // Auxiliar por asociado con saldo anterior / movimiento / saldo final
            FinancialReportResponse extras = generate("APORTES_EXTRAS", from, to);
            assertThat(extras.getTotals().get("opening")).isEqualByComparingTo("50000");
            assertThat(extras.getTotals().get("closing")).isEqualByComparingTo("60000");

            // Estado de cuenta del asociado y exportación de los estados nuevos
            FinancialReportResponse cxc = generate("CXC_ASOCIADOS", from, to);
            Object partyId = cxc.getRows().stream().map(x -> x.getCells().get("_partyId")).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
            FinancialReportResponse statement = reportService.generate("ESTADO_CUENTA_ASOCIADO",
                    new FinancialReportService.Filters(from, to, null, null, (java.util.UUID) partyId, null));
            assertThat(summary(statement, "Saldo por cobrar al asociado")).isEqualByComparingTo("180000");

            assertThat(investmentRepository.findAllWithAccount()).anyMatch(i -> i.getEntityName().equals("BANCO X"));
            for (String code : List.of("ESTADO_SITUACION", "ESTADO_RESULTADOS", "BALANCE_PRUEBA", "PLANILLA_RECAUDOS", "INVERSIONES",
                    "PROPIEDAD_PLANTA", "FONDOS", "BANCOS", "TURISMO_RECREACION")) {
                FinancialReportResponse rep = generate(code, from, to);
                assertThat(new String(exportService.toPdf(rep), 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
                assertThat(exportService.toExcel(rep)).hasSizeGreaterThan(100);
            }

            // Idempotencia: el mismo informe no se puede importar dos veces
            MonthlyReportImportResponse again = importService.importReport(new ByteArrayInputStream(syntheticWorkbookUnchecked()), "sintetico.xlsx", true);
            assertThat(again.getErrors()).anyMatch(e -> e.contains("ya fue importado"));
        });
    }

    // ================================================================== informe real de abril 2026

    @Test
    @DisplayName("Conciliación con el informe real de abril 2026 (si el archivo está en Docs/)")
    void reconcilesRealAprilReport() {
        assumeTrue(Files.exists(REAL_REPORT), "El informe real no está en Docs/ (no se versiona)");
        inRollback(v -> {
            MonthlyReportImportResponse r;
            try (InputStream in = Files.newInputStream(REAL_REPORT)) {
                r = importService.importReport(in, REAL_REPORT.getFileName().toString(), false);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
            System.out.println("=== CONCILIACIÓN INFORME ABRIL 2026 ===");
            r.getReconciliation().forEach(l -> System.out.printf("%-6s | %-20s | %-24s | %-40s | excel %,.0f | sistema %,.0f | dif %,.0f%n",
                    l.ok() ? "OK" : "DIF", l.report(), l.sheet(), l.concept(), l.excel(), l.system(), l.difference()));
            System.out.println("=== OBSERVACIONES ===");
            r.getWarnings().forEach(w -> System.out.println(" - " + w));
            r.getErrors().forEach(e -> System.out.println(" ERROR " + e));
            System.out.println("Comprobantes: " + r.getVouchers().size() + " · asociados creados: " + r.getAssociatesCreated().size());

            assertThat(r.getErrors()).isEmpty();
            assertThat(r.getReconciliation()).isNotEmpty().allMatch(ReconciliationLine::ok);
        });
    }

    // ================================================================== emparejamiento de nombres

    @Test
    @DisplayName("Nombres escritos distinto entre hojas se emparejan con el asociado correcto")
    void nameMatching() {
        List<String> names = List.of("MERCEDES DEL SOCORRO LAZO DE LUNA", "MERCEDES VISITACION CALVACHE DE OCAÑA",
                "LAURA ELISA MARTINEZ DE LOPEZ", "ROCIO ELISABETH LOPEZ MARTINEZ", "LUCY RUBIELA LOPEZ MARTINEZ",
                "ANA LUCIA BANDA LUNA", "ANA LUCIA MORENO", "SOCORRO MARTINEZ", "ADRIANA LUNA");
        assertThat(NameMatcher.best("MERCEDES LASSO DE LUNA", names, s -> s)).isEqualTo("MERCEDES DEL SOCORRO LAZO DE LUNA");
        assertThat(NameMatcher.best("MERCEDES VISITACION CALVACHE", names, s -> s)).isEqualTo("MERCEDES VISITACION CALVACHE DE OCAÑA");
        assertThat(NameMatcher.best("LAURA MARTINEZ DE LOPEZ", names, s -> s)).isEqualTo("LAURA ELISA MARTINEZ DE LOPEZ");
        assertThat(NameMatcher.best("ANA LUCIA BANDA", names, s -> s)).isEqualTo("ANA LUCIA BANDA LUNA");
        assertThat(NameMatcher.best("SOCORRO MARTINEZ", names, s -> s)).isEqualTo("SOCORRO MARTINEZ");
        assertThat(NameMatcher.best("ADRIANA", names, s -> s)).isNull();
        assertThat(NameMatcher.best("PEDRO PEREZ", names, s -> s)).isNull();
    }

    // ================================================================== helpers

    private FinancialReportResponse generate(String code, LocalDate from, LocalDate to) {
        return reportService.generate(code, new FinancialReportService.Filters(from, to, null, null, null, null));
    }

    private static BigDecimal summary(FinancialReportResponse r, String label) {
        return r.getSummary().stream().filter(s -> s.getLabel().equals(label)).findFirst()
                .orElseThrow(() -> new AssertionError("Sin indicador '" + label + "' en " + r.getCode())).getValue();
    }

    private static byte[] syntheticWorkbookUnchecked() {
        try {
            return syntheticWorkbook();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Libro con la misma estructura del informe mensual (cifras inventadas y cuadradas):
     * apertura a marzo, dos recibos (uno con abono a préstamo y parte consignada), un CE, rendimientos y depreciación.
     */
    static byte[] syntheticWorkbook() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet planilla = wb.createSheet("PLANILLA");
            put(planilla, 1, null, null, "APORTES", "A.EXTRA", "F.SOLIDARID", "C.ADMON", "PRESTAMO", "INTERES", "PREEXEQ", "T Y R", "BIGO", "FDE", "MULTAS", "OTROS", "TOTAL");
            put(planilla, 2, "ANA PEREZ", "RB-1", 10000, 10000, 5250, 6250, null, null, null, 5500, null, null, null, null, 37000);
            put(planilla, 3, "LUIS GOMEZ", "RB-2", 10000, null, 5250, 6250, 20000, 5000, null, 5500, null, null, null, null, 52000);
            put(planilla, 4, null, null, 20000, 10000, 10500, 12500, 20000, 5000, 0, 11000, 0, 0, 0, 0, 89000);

            Sheet bal = wb.createSheet("BAL,PRUEBA");
            int i = 2;
            put(bal, i++, "ACTIVO");
            put(bal, i++, "CAJA");
            put(bal, i++, "SALDO ANTERIOR - MARZO", null, 100000);
            put(bal, i++, "MOVIMIENTO DEL MES", null, null, 79000, 10000, 169000);
            put(bal, i++, "ANA PEREZ", "RB-1", null, 37000);
            put(bal, i++, "LUIS GOMEZ", "RB-2", null, 42000);
            put(bal, i++, "COLABORACION REUNION", "CE-1", null, null, 10000);
            put(bal, i++, "BANCOS Y OTRAS ENTIDADES");
            put(bal, i++, "BANCOS COMERCIALES");
            put(bal, i++, "BANCO DE PRUEBA - AHORROS 123");
            put(bal, i++, "SALDO ANTERIOR", null, 1000000, 10500, 0, 1010500);
            put(bal, i++, "LUIS GOMEZ", "RB-2", null, 10000);
            put(bal, i++, "INGRESOS FINANCIEROS", null, null, 500);
            put(bal, i++, "INVERSIONES", null, 2100000, null, 0, 2100000);
            put(bal, i++, "CDT BANCO X", null, 2000000);
            put(bal, i++, "INTERESES", null, 100000);
            put(bal, i++, "CUENTAS POR COBRAR");
            put(bal, i++, "CATEGORIA A RIESGO NORMAL", null, 200000, 0, 20000, 180000);
            put(bal, i++, "LUIS GOMEZ", null, 200000, null, 20000, 180000);
            put(bal, i++, "PROPIEDADES PLANTA Y EQUIPO", null, 5940000, -20000, null, 5920000);
            put(bal, i++, "EDIFICACIONES", null, 5000000);
            put(bal, i++, "MUEBLES Y EQUIPO DE OFICINA", null, 1000000);
            put(bal, i++, "depreciacion", null, -60000, -20000);
            put(bal, i++, "TOTAL ACTIVO", null, null, null, null, 9379500);
            put(bal, i++, "PASIVO");
            put(bal, i++, "CUENTAS POR PAGAR", null, null, null, null, 0);
            put(bal, i++, "OTROS");
            put(bal, i++, "OTROS - EXCEDENTE DE APORTES", null, 50000, null, 10000, 60000);
            put(bal, i++, "ANA PEREZ", "RB-1", 50000, null, 10000, 60000);
            put(bal, i++, "TURISMO Y RECREACION", null, 40000, 0, 11000, 51000);
            put(bal, i++, "ANA PEREZ", "RB-1", 20000, null, 5500, 25500);
            put(bal, i++, "LUIS GOMEZ", "RB-2", 20000, null, 5500, 25500);
            put(bal, i++, "FONDO DE SOLIDARIDAD");
            put(bal, i++, "SALDO ANTERIOR", null, 300000, null, 10500, 310500);
            put(bal, i++, "ANA PEREZ", "RB-1", null, null, 5250);
            put(bal, i++, "LUIS GOMEZ", "RB-2", null, null, 5250);
            put(bal, i++, "TOTAL PASIVO", null, null, null, null, 421500);
            put(bal, i++, "PATRIMONIO");
            put(bal, i++, "APORTES ORDINARIOS", null, 8000000, 0, 20000, 8020000);
            put(bal, i++, "ANA PEREZ", "RB-1", 3000000, null, 10000, 3010000);
            put(bal, i++, "LUIS GOMEZ", "RB-2", 5000000, null, 10000, 5010000);
            put(bal, i++, "VALORIZACION PATRIMONIAL - CASA SEDE", null, 900000, null, null, 900000);
            put(bal, i++, "RESULTADOS DE EJERCICIOS ANTER", null, 89600, null, null, 89600);
            put(bal, i++, "RESULTADO DEL EJERCICIO", null, null, null, null, -51600);
            put(bal, i++, "TOTAL PATRIMONIO", null, null, null, null, 8958000);
            put(bal, i++, "TOTAL  PASIVO MAS PATRIMONIO", null, null, null, null, 9379500);
            put(bal, i++, "INGRESOS", null, 40400, null, 18000, 58400);
            put(bal, i++, "CUOTAS  DE ADMINISTRACION", null, 30000, null, 12500, 42500);
            put(bal, i++, "INTERESES VARIOS", null, 10000, null, 5000, 15000);
            put(bal, i++, "FINANCIEROS", null, 400, null, 500, 900);
            put(bal, i++, "GASTOS", null, 80000, 30000, null, 110000);
            put(bal, i++, "COLABORACION REUNION JD", "CE-1", 20000, 10000, null, 30000);
            put(bal, i++, "depreciaciones", null, 60000, 20000, null, 80000);
            put(bal, i, "UTILIDAD EJERCICIO", null, -39600, null, null, -51600);

            Sheet esfa = wb.createSheet("ESFA");
            put(esfa, 0, "ASOCIACION DE PRUEBA");
            put(esfa, 1, "NIT 900000001-5");
            put(esfa, 2, "ESTADO DE SITUACION FINANCIERA - A ABRIL DE 2026");
            put(esfa, 13, "TERRENOS", 2000000);
            put(esfa, 14, "EDIFICACIONES", 3000000);
            put(esfa, 15, "(Depreciación acumulada)", -50000);
            put(esfa, 16, "Muebles y Equipo de Oficina", 1000000);
            put(esfa, 17, "(Depreciación acumulada)", -30000);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void put(Sheet sheet, int rowIndex, Object... cells) {
        Row row = sheet.createRow(rowIndex);
        for (int c = 0; c < cells.length; c++) {
            Object v = cells[c];
            if (v == null) continue;
            if (v instanceof Number n) row.createCell(c).setCellValue(n.doubleValue());
            else row.createCell(c).setCellValue(v.toString());
        }
    }
}
