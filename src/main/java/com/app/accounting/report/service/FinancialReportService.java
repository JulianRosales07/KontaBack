package com.app.accounting.report.service;

import com.app.accounting.associate.dto.response.AssociateSummaryResponse;
import com.app.accounting.associate.entity.AssociateReceipt;
import com.app.accounting.associate.entity.AssociateReceiptItem;
import com.app.accounting.associate.entity.QuotaCategory;
import com.app.accounting.associate.repository.AssociateReceiptRepository;
import com.app.accounting.associate.service.AssociateDirectoryService;
import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.loan.dto.response.LoanResponse;
import com.app.accounting.loan.dto.response.LoanScheduleLineResponse;
import com.app.accounting.loan.entity.LoanPayment;
import com.app.accounting.loan.entity.LoanStatus;
import com.app.accounting.loan.repository.LoanPaymentRepository;
import com.app.accounting.loan.service.LoanService;
import com.app.accounting.report.dto.response.FinancialReportResponse;
import com.app.accounting.report.dto.response.FinancialReportResponse.Column;
import com.app.accounting.report.dto.response.FinancialReportResponse.Row;
import com.app.accounting.report.dto.response.FinancialReportResponse.SummaryItem;
import com.app.accounting.report.repository.ReportEntryRepository;
import com.app.accounting.security.repository.UserRepository;
import com.app.accounting.voucher.entity.JournalEntry;
import com.app.accounting.voucher.entity.VoucherType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;

/**
 * Reportes financieros de la asociación. Todas las cifras salen de las mismas fuentes que la contabilidad
 * (asientos POSTED, préstamos/cuotas/abonos y recibos de asociados), sin cálculos paralelos.
 */
@Service
@RequiredArgsConstructor
public class FinancialReportService {

    /** Catálogo: código → título. */
    public static final Map<String, String> CATALOG = new LinkedHashMap<>();
    /** Código → grupo del menú. */
    public static final Map<String, String> GROUPS = new LinkedHashMap<>();
    /** Código → hoja equivalente del informe mensual en Excel (si aplica). */
    public static final Map<String, String> EXCEL_SHEETS = new LinkedHashMap<>();

    static {
        add("ESTADO_SITUACION", "Estado de situación financiera", "Estados financieros", "ESFA");
        add("ESTADO_RESULTADOS", "Estado de resultados", "Estados financieros", "ESTADO RESULTADOS");
        add("BALANCE_PRUEBA", "Balance de prueba", "Estados financieros", "BAL,PRUEBA");
        add("PLANILLA_RECAUDOS", "Planilla de recaudos por concepto", "Recaudos y asociados", "PLANILLA");
        add("RECAUDOS", "Recaudos (cuotas y abonos a préstamos)", "Recaudos y asociados", null);
        add("ESTADO_CUENTA_ASOCIADO", "Estado de cuenta del asociado", "Recaudos y asociados", null);
        add("APORTES_SOCIALES", "Aportes sociales por asociado", "Recaudos y asociados", "APORTES");
        add("APORTES_EXTRAS", "Aportes extraordinarios por asociado", "Recaudos y asociados", "APORTES EXTRAS");
        add("APORTES", "Aportes recibidos en recibos", "Recaudos y asociados", null);
        add("AHORROS", "Ahorros de asociados", "Recaudos y asociados", null);
        add("CUENTAS_POR_PAGAR", "Cuentas por pagar (aportes y ahorros)", "Recaudos y asociados", null);
        add("CXC_ASOCIADOS", "Cuentas por cobrar a asociados", "Cartera", "CUENTAS POR C");
        add("CUENTAS_POR_COBRAR", "Cuentas por cobrar (préstamos activos)", "Cartera", null);
        add("CARTERA", "Cartera por edades de mora", "Cartera", null);
        add("PRESTAMOS", "Préstamos desembolsados", "Cartera", null);
        add("INTERESES", "Intereses recaudados", "Cartera", null);
        add("FONDO_SOLIDARIDAD", "Fondo de solidaridad", "Fondos y actividades", "BAL,PRUEBA (Fondo de solidaridad)");
        add("TURISMO_RECREACION", "Fondo de turismo y recreación", "Fondos y actividades", "TURISMO Y R");
        add("PREEXEQUIALES", "Preexequiales", "Fondos y actividades", "BAL,PRUEBA (Pre-exequiales)");
        add("FONDOS", "Fondos y actividades", "Fondos y actividades", null);
        add("CAJA", "Reporte de caja", "Tesorería", "BAL,PRUEBA (Caja)");
        add("MOVIMIENTOS_CAJA", "Movimientos del disponible por cuenta", "Tesorería", null);
        add("BANCOS", "Bancos", "Tesorería", "BAL,PRUEBA (Bancos)");
        add("INVERSIONES", "Inversiones (CDT)", "Tesorería", "BAL,PRUEBA (Inversiones)");
        add("PROPIEDAD_PLANTA", "Propiedad, planta y equipo", "Activos", "ESFA (Activos no corrientes)");
        add("INGRESOS", "Ingresos por cuenta PUC", "Ingresos y gastos", null);
        add("EGRESOS", "Egresos (gastos) por cuenta PUC", "Ingresos y gastos", null);
    }

    private static void add(String code, String title, String group, String sheet) {
        CATALOG.put(code, title);
        GROUPS.put(code, group);
        if (sheet != null) EXCEL_SHEETS.put(code, sheet);
    }

    /**
     * Filtros comunes. {@code level}: nivel PUC máximo a mostrar (1 clase … 5 auxiliar);
     * {@code byParty}: detalle por tercero en el balance de prueba.
     */
    public record Filters(LocalDate from, LocalDate to, String accountCode, VoucherType voucherType,
                          UUID thirdPartyId, String paymentMethod, Integer level, boolean byParty) {

        public Filters(LocalDate from, LocalDate to, String accountCode, VoucherType voucherType,
                       UUID thirdPartyId, String paymentMethod) {
            this(from, to, accountCode, voucherType, thirdPartyId, paymentMethod, null, false);
        }
    }

    private final ReportEntryRepository entryRepository;
    private final LoanService loanService;
    private final LoanPaymentRepository loanPaymentRepository;
    private final AssociateReceiptRepository receiptRepository;
    private final AssociateDirectoryService directoryService;
    private final UserRepository userRepository;
    private final com.app.accounting.party.repository.ThirdPartyRepository thirdPartyRepository;
    private final AccountingStatementsBuilder statements;

    @Value("${app.organization.name:Asociación}")
    private String organizationName;

    @Value("${app.organization.nit:}")
    private String organizationNit;

    @Transactional(readOnly = true)
    public FinancialReportResponse generate(String code, Filters f) {
        String key = code == null ? "" : code.toUpperCase(Locale.ROOT);
        if (!CATALOG.containsKey(key)) {
            throw new BusinessRuleException("Reporte desconocido: " + code);
        }
        LocalDate to = f.to() != null ? f.to() : LocalDate.now();
        LocalDate from = f.from() != null ? f.from() : to.withDayOfMonth(1);
        if (from.isAfter(to)) {
            throw new BusinessRuleException("La fecha inicial no puede ser posterior a la fecha final");
        }
        Filters filters = new Filters(from, to, blankToNull(f.accountCode()), f.voucherType(), f.thirdPartyId(), blankToNull(f.paymentMethod()),
                f.level(), f.byParty());

        FinancialReportResponse report = FinancialReportResponse.builder()
                .code(key)
                .title(CATALOG.get(key).toUpperCase(Locale.of("es", "CO")))
                .organizationName(organizationName)
                .organizationNit(organizationNit)
                .periodFrom(from)
                .periodTo(to)
                .generatedAt(OffsetDateTime.now())
                .generatedBy(currentUserName())
                .build();
        describeFilters(report, filters);

        switch (key) {
            case "CAJA" -> cash(report, filters, false);
            case "MOVIMIENTOS_CAJA" -> cash(report, filters, true);
            case "INGRESOS" -> byAccountClass(report, filters, List.of("4"), true);
            case "EGRESOS" -> byAccountClass(report, filters, List.of("5", "6"), false);
            case "RECAUDOS" -> collections(report, filters);
            case "INTERESES" -> interests(report, filters);
            case "CARTERA" -> portfolio(report, filters);
            case "PRESTAMOS" -> loans(report, filters);
            case "CUENTAS_POR_COBRAR" -> receivables(report, filters);
            case "APORTES" -> associateConcept(report, filters, QuotaCategory.EQUITY_SAVING, "Aportes");
            case "AHORROS" -> associateConcept(report, filters, QuotaCategory.SAVINGS, "Ahorros");
            case "CUENTAS_POR_PAGAR" -> payables(report, filters);
            case "BALANCE_PRUEBA" -> statements.trialBalance(report, filters);
            case "ESTADO_SITUACION" -> statements.financialPosition(report, filters);
            case "ESTADO_RESULTADOS" -> statements.incomeStatement(report, filters);
            case "PLANILLA_RECAUDOS" -> statements.collectionSheet(report, filters);
            case "ESTADO_CUENTA_ASOCIADO" -> statements.associateStatement(report, filters);
            case "CXC_ASOCIADOS", "APORTES_SOCIALES", "APORTES_EXTRAS", "TURISMO_RECREACION", "FONDO_SOLIDARIDAD", "PREEXEQUIALES" ->
                    statements.partyLedger(report, filters, AccountingStatementsBuilder.PARTY_LEDGERS.get(key));
            case "FONDOS" -> statements.funds(report, filters);
            case "BANCOS" -> cash(report, new Filters(from, to, filters.accountCode(), filters.voucherType(), filters.thirdPartyId(), "BANK",
                    filters.level(), filters.byParty()), true);
            case "INVERSIONES" -> statements.investments(report, filters);
            case "PROPIEDAD_PLANTA" -> statements.fixedAssets(report, filters);
            default -> throw new IllegalStateException();
        }
        return report;
    }

    // ================================================================== CAJA

    private void cash(FinancialReportResponse r, Filters f, boolean groupByAccount) {
        List<String> prefixes = f.accountCode() != null ? List.of(f.accountCode())
                : "CASH".equalsIgnoreCase(f.paymentMethod()) ? List.of("1105")
                : "BANK".equalsIgnoreCase(f.paymentMethod()) ? List.of("1110", "1120") : List.of("11");
        r.setSubtitle("Cuentas del disponible " + String.join(", ", prefixes.stream().map(p -> p + "*").toList())
                + " · Ingresos = débitos, egresos = créditos");

        BigDecimal opening = BigDecimal.ZERO;
        List<JournalEntry> all = new ArrayList<>();
        for (String prefix : prefixes) {
            opening = opening.add(nz(entryRepository.sumDebitMinusCreditBefore(prefix, f.from())));
            all.addAll(entryRepository.findPostedByAccountPrefix(prefix, f.from(), f.to()));
        }
        all.sort(Comparator.comparing((JournalEntry e) -> e.getVoucher().getIssueDate())
                .thenComparing(e -> e.getVoucher().getVoucherNumber()));
        BigDecimal allIn = sum(all, JournalEntry::getDebit);
        BigDecimal allOut = sum(all, JournalEntry::getCredit);
        List<JournalEntry> entries = all.stream().filter(e -> matchesEntryFilters(e, f)).toList();
        BigDecimal in = sum(entries, JournalEntry::getDebit);
        BigDecimal out = sum(entries, JournalEntry::getCredit);
        boolean filtered = entries.size() != all.size();

        r.getColumns().addAll(List.of(
                col("date", "Fecha", "DATE"), col("voucher", "Comprobante", "TEXT"), col("type", "Tipo", "TEXT"),
                col("account", "Cuenta", "TEXT"), col("concept", "Concepto", "TEXT"), col("party", "Tercero", "TEXT"),
                money("income", "Ingreso"), money("expense", "Egreso"), col("balance", "Saldo", "MONEY")));

        r.getSummary().add(summary("Saldo inicial", opening, "neutral"));
        r.getSummary().add(summary(filtered ? "Ingresos (filtrados)" : "Total ingresos", in, "positive"));
        r.getSummary().add(summary(filtered ? "Egresos (filtrados)" : "Total egresos", out, "negative"));
        r.getSummary().add(summary("Saldo final", opening.add(allIn).subtract(allOut), "primary"));
        if (filtered) {
            r.setSubtitle(r.getSubtitle() + " · El detalle está filtrado; los saldos corresponden a la cuenta completa");
        }

        if (!groupByAccount) {
            r.getRows().add(row("GROUP", Map.of("concept", "SALDO INICIAL", "balance", opening)));
            BigDecimal running = opening;
            for (JournalEntry e : entries) {
                running = running.add(e.getDebit()).subtract(e.getCredit());
                r.getRows().add(entryRow(e, running));
            }
        } else {
            Map<String, List<JournalEntry>> byAccount = new TreeMap<>();
            for (JournalEntry e : entries) {
                byAccount.computeIfAbsent(e.getAccount().getCode(), k -> new ArrayList<>()).add(e);
            }
            for (Map.Entry<String, List<JournalEntry>> group : byAccount.entrySet()) {
                JournalEntry first = group.getValue().getFirst();
                BigDecimal accOpening = nz(entryRepository.sumDebitMinusCreditBefore(group.getKey(), f.from()));
                String label = group.getKey() + " - " + first.getAccount().getName();
                r.getRows().add(row("GROUP", Map.of("account", label, "concept", "Saldo inicial", "balance", accOpening)));
                BigDecimal running = accOpening;
                for (JournalEntry e : group.getValue()) {
                    running = running.add(e.getDebit()).subtract(e.getCredit());
                    r.getRows().add(entryRow(e, running));
                }
                r.getRows().add(row("SUBTOTAL", Map.of("concept", "Subtotal " + label,
                        "income", sum(group.getValue(), JournalEntry::getDebit),
                        "expense", sum(group.getValue(), JournalEntry::getCredit),
                        "balance", running)));
            }
        }
        r.getTotals().put("income", in);
        r.getTotals().put("expense", out);
    }

    private Row entryRow(JournalEntry e, BigDecimal running) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("date", e.getVoucher().getIssueDate());
        c.put("voucher", e.getVoucher().getVoucherNumber());
        c.put("type", voucherTypeLabel(e.getVoucher().getVoucherType()));
        c.put("account", e.getAccount().getCode() + " - " + e.getAccount().getName());
        c.put("concept", e.getDescription() != null && !e.getDescription().isBlank() ? e.getDescription() : e.getVoucher().getDescription());
        c.put("party", e.getThirdParty() != null ? e.getThirdParty().getFullName() : "");
        c.put("income", e.getDebit());
        c.put("expense", e.getCredit());
        c.put("balance", running);
        c.put("_voucherId", e.getVoucher().getId());
        return Row.builder().cells(c).build();
    }

    // ================================================================== INGRESOS / EGRESOS

    private void byAccountClass(FinancialReportResponse r, Filters f, List<String> classes, boolean creditNature) {
        r.setSubtitle(creditNature ? "Cuentas clase 4 · valor = créditos − débitos" : "Cuentas clases 5 y 6 · valor = débitos − créditos");
        r.getColumns().addAll(List.of(
                col("date", "Fecha", "DATE"), col("voucher", "Comprobante", "TEXT"), col("concept", "Concepto", "TEXT"),
                col("party", "Tercero", "TEXT"), money("debit", "Débito"), money("credit", "Crédito"), money("value", "Valor")));

        List<JournalEntry> entries = new ArrayList<>();
        for (String c : classes) {
            entries.addAll(entryRepository.findPostedByAccountPrefix(f.accountCode() != null && f.accountCode().startsWith(c) ? f.accountCode() : c, f.from(), f.to()));
        }
        entries = entries.stream()
                .filter(e -> f.accountCode() == null || e.getAccount().getCode().startsWith(f.accountCode()))
                .filter(e -> matchesEntryFilters(e, f))
                .toList();

        Map<String, List<JournalEntry>> byAccount = new TreeMap<>();
        for (JournalEntry e : entries) {
            byAccount.computeIfAbsent(e.getAccount().getCode(), k -> new ArrayList<>()).add(e);
        }
        BigDecimal grand = BigDecimal.ZERO;
        for (Map.Entry<String, List<JournalEntry>> group : byAccount.entrySet()) {
            String label = group.getKey() + " - " + group.getValue().getFirst().getAccount().getName();
            r.getRows().add(row("GROUP", Map.of("concept", label)));
            BigDecimal subtotal = BigDecimal.ZERO;
            for (JournalEntry e : group.getValue()) {
                BigDecimal value = creditNature ? e.getCredit().subtract(e.getDebit()) : e.getDebit().subtract(e.getCredit());
                subtotal = subtotal.add(value);
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("date", e.getVoucher().getIssueDate());
                c.put("voucher", e.getVoucher().getVoucherNumber());
                c.put("concept", e.getDescription() != null ? e.getDescription() : e.getVoucher().getDescription());
                c.put("party", e.getThirdParty() != null ? e.getThirdParty().getFullName() : "");
                c.put("debit", e.getDebit());
                c.put("credit", e.getCredit());
                c.put("value", value);
                c.put("_voucherId", e.getVoucher().getId());
                r.getRows().add(Row.builder().cells(c).build());
            }
            r.getRows().add(row("SUBTOTAL", Map.of("concept", "Subtotal " + label,
                    "debit", sum(group.getValue(), JournalEntry::getDebit),
                    "credit", sum(group.getValue(), JournalEntry::getCredit),
                    "value", subtotal)));
            grand = grand.add(subtotal);
        }
        r.getSummary().add(summary(creditNature ? "Total ingresos" : "Total egresos", grand, creditNature ? "positive" : "negative"));
        r.getSummary().add(summary("Cuentas con movimiento", BigDecimal.valueOf(byAccount.size()), "neutral", "NUMBER"));
        r.getTotals().put("debit", sum(entries, JournalEntry::getDebit));
        r.getTotals().put("credit", sum(entries, JournalEntry::getCredit));
        r.getTotals().put("value", grand);
    }

    // ================================================================== RECAUDOS / INTERESES

    private void collections(FinancialReportResponse r, Filters f) {
        r.setSubtitle("Recibos de cuotas de asociados y abonos a préstamos del periodo");
        r.getColumns().addAll(List.of(
                col("date", "Fecha", "DATE"), col("receipt", "Recibo", "TEXT"), col("party", "Asociado", "TEXT"),
                col("document", "Documento", "TEXT"), col("detail", "Detalle", "TEXT"), col("method", "Medio de pago", "TEXT"),
                money("principal", "Capital"), money("interest", "Interés"), money("other", "Cuotas / aportes"), money("total", "Total")));

        List<AssociateReceipt> receipts = receiptRepository.findByPaymentDateBetweenWithDetails(f.from(), f.to()).stream()
                .filter(rc -> f.thirdPartyId() == null || rc.getThirdParty().getId().equals(f.thirdPartyId()))
                .filter(rc -> matchesMethod(rc.getCashAccount() != null ? rc.getCashAccount().getCode() : null, f.paymentMethod()))
                .toList();
        List<LoanPayment> payments = loanPaymentRepository.findByPaymentDateBetweenWithDetails(f.from(), f.to()).stream()
                .filter(p -> f.thirdPartyId() == null || p.getThirdParty().getId().equals(f.thirdPartyId()))
                .filter(p -> matchesMethod(p.getCashAccount() != null ? p.getCashAccount().getCode() : null, f.paymentMethod()))
                .toList();

        BigDecimal quotaTotal = BigDecimal.ZERO;
        if (!receipts.isEmpty()) {
            r.getRows().add(row("GROUP", Map.of("detail", "CUOTAS, APORTES Y FONDOS")));
            for (AssociateReceipt rc : receipts) {
                BigDecimal loanPart = rc.getItems().stream()
                        .filter(i -> i.getCategory().name().startsWith("LOAN_"))
                        .map(AssociateReceiptItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("date", rc.getPaymentDate());
                c.put("receipt", rc.getReceiptNumber());
                c.put("party", rc.getThirdParty().getFullName());
                c.put("document", rc.getThirdParty().getIdentificationNumber());
                c.put("detail", rc.getItems().size() + " concepto(s)" + (rc.getNotes() != null ? " · " + rc.getNotes() : ""));
                c.put("method", rc.getCashAccount() != null ? rc.getCashAccount().getCode() + " " + rc.getCashAccount().getName() : "");
                c.put("principal", BigDecimal.ZERO);
                c.put("interest", BigDecimal.ZERO);
                c.put("other", rc.getTotalAmount().subtract(loanPart));
                c.put("total", rc.getTotalAmount());
                r.getRows().add(Row.builder().cells(c).build());
                quotaTotal = quotaTotal.add(rc.getTotalAmount());
            }
            r.getRows().add(row("SUBTOTAL", Map.of("detail", "Subtotal cuotas", "other", quotaTotal, "total", quotaTotal)));
        }

        BigDecimal loanPrincipal = BigDecimal.ZERO;
        BigDecimal loanInterest = BigDecimal.ZERO;
        if (!payments.isEmpty()) {
            r.getRows().add(row("GROUP", Map.of("detail", "ABONOS A PRÉSTAMOS")));
            for (LoanPayment p : payments) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("date", p.getPaymentDate());
                c.put("receipt", p.getReceiptNumber());
                c.put("party", p.getThirdParty().getFullName());
                c.put("document", p.getThirdParty().getIdentificationNumber());
                c.put("detail", p.getLoan().getLoanNumber() + " · cuota " + p.getInstallment().getInstallmentNumber());
                c.put("method", paymentMethodLabel(p.getPaymentMethod().name()));
                c.put("principal", p.getPrincipalApplied());
                c.put("interest", p.getInterestApplied());
                c.put("other", BigDecimal.ZERO);
                c.put("total", p.getAmount());
                r.getRows().add(Row.builder().cells(c).build());
                loanPrincipal = loanPrincipal.add(p.getPrincipalApplied());
                loanInterest = loanInterest.add(p.getInterestApplied());
            }
            r.getRows().add(row("SUBTOTAL", Map.of("detail", "Subtotal préstamos", "principal", loanPrincipal,
                    "interest", loanInterest, "total", loanPrincipal.add(loanInterest))));
        }

        BigDecimal total = quotaTotal.add(loanPrincipal).add(loanInterest);
        r.getSummary().add(summary("Total recaudado", total, "primary"));
        r.getSummary().add(summary("Cuotas y aportes", quotaTotal, "neutral"));
        r.getSummary().add(summary("Capital recuperado", loanPrincipal, "positive"));
        r.getSummary().add(summary("Intereses", loanInterest, "positive"));
        r.getTotals().put("principal", loanPrincipal);
        r.getTotals().put("interest", loanInterest);
        r.getTotals().put("other", quotaTotal);
        r.getTotals().put("total", total);
    }

    private void interests(FinancialReportResponse r, Filters f) {
        r.setSubtitle("Intereses efectivamente pagados en abonos a préstamos");
        r.getColumns().addAll(List.of(
                col("date", "Fecha", "DATE"), col("receipt", "Recibo", "TEXT"), col("party", "Asociado", "TEXT"),
                col("loan", "Préstamo", "TEXT"), col("installment", "Cuota", "NUMBER"), money("interest", "Interés")));
        List<LoanPayment> payments = loanPaymentRepository.findByPaymentDateBetweenWithDetails(f.from(), f.to()).stream()
                .filter(p -> p.getInterestApplied().signum() > 0)
                .filter(p -> f.thirdPartyId() == null || p.getThirdParty().getId().equals(f.thirdPartyId()))
                .toList();
        BigDecimal total = BigDecimal.ZERO;
        for (LoanPayment p : payments) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("date", p.getPaymentDate());
            c.put("receipt", p.getReceiptNumber());
            c.put("party", p.getThirdParty().getFullName());
            c.put("loan", p.getLoan().getLoanNumber());
            c.put("installment", p.getInstallment().getInstallmentNumber());
            c.put("interest", p.getInterestApplied());
            r.getRows().add(Row.builder().cells(c).build());
            total = total.add(p.getInterestApplied());
        }
        r.getSummary().add(summary("Intereses recaudados", total, "positive"));
        r.getSummary().add(summary("Abonos con interés", BigDecimal.valueOf(payments.size()), "neutral", "NUMBER"));
        r.getTotals().put("interest", total);
    }

    // ================================================================== PRÉSTAMOS / CARTERA / CXC

    private List<LoanResponse> loansFor(Filters f, LoanStatus status, boolean withInstallments) {
        return loanService.getLoans(status, f.thirdPartyId(), withInstallments);
    }

    private void loans(FinancialReportResponse r, Filters f) {
        r.setSubtitle("Préstamos con fecha de desembolso dentro del periodo");
        r.getColumns().addAll(List.of(
                col("loan", "Préstamo", "TEXT"), col("party", "Asociado", "TEXT"), col("document", "Documento", "TEXT"),
                col("date", "Desembolso", "DATE"), money("principal", "Valor"), col("rate", "Tasa mensual", "PERCENT"),
                col("term", "Cuotas", "NUMBER"), money("interest", "Interés total"), money("paid", "Capital pagado"),
                money("balance", "Saldo"), col("status", "Estado", "TEXT")));
        List<LoanResponse> loans = loansFor(f, null, false).stream()
                .filter(l -> !l.getDisbursementDate().isBefore(f.from()) && !l.getDisbursementDate().isAfter(f.to()))
                .toList();
        for (LoanResponse l : loans) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("loan", l.getLoanNumber());
            c.put("party", l.getThirdPartyName());
            c.put("document", l.getThirdPartyIdNumber());
            c.put("date", l.getDisbursementDate());
            c.put("principal", l.getPrincipalAmount());
            c.put("rate", l.getMonthlyRate());
            c.put("term", l.getTermMonths());
            c.put("interest", l.getTotalInterest());
            c.put("paid", l.getPaidPrincipal());
            c.put("balance", l.getOutstandingPrincipal());
            c.put("status", portfolioLabel(l.getPortfolioStatus()));
            r.getRows().add(Row.builder().cells(c).build());
        }
        BigDecimal disbursed = loans.stream().map(LoanResponse::getPrincipalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = loans.stream().map(LoanResponse::getOutstandingPrincipal).reduce(BigDecimal.ZERO, BigDecimal::add);
        r.getSummary().add(summary("Préstamos", BigDecimal.valueOf(loans.size()), "neutral", "NUMBER"));
        r.getSummary().add(summary("Total desembolsado", disbursed, "primary"));
        r.getSummary().add(summary("Saldo pendiente", balance, "negative"));
        r.getTotals().put("principal", disbursed);
        r.getTotals().put("interest", loans.stream().map(LoanResponse::getTotalInterest).reduce(BigDecimal.ZERO, BigDecimal::add));
        r.getTotals().put("paid", loans.stream().map(LoanResponse::getPaidPrincipal).reduce(BigDecimal.ZERO, BigDecimal::add));
        r.getTotals().put("balance", balance);
    }

    private void portfolio(FinancialReportResponse r, Filters f) {
        r.setSubtitle("Préstamos activos clasificados por días de mora a la fecha de corte");
        r.getColumns().addAll(List.of(
                col("party", "Asociado", "TEXT"), col("document", "Documento", "TEXT"), col("loan", "Préstamo", "TEXT"),
                money("principal", "Valor inicial"), money("balance", "Saldo capital"), col("overdueCount", "Cuotas vencidas", "NUMBER"),
                money("overdue", "Valor vencido"), col("days", "Días mora", "NUMBER"), col("status", "Estado", "TEXT")));

        String[] buckets = {"AL DÍA", "1 A 30 DÍAS", "31 A 60 DÍAS", "61 A 90 DÍAS", "MÁS DE 90 DÍAS"};
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (String b : buckets) {
            grouped.put(b, new ArrayList<>());
        }
        LocalDate cut = f.to();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal overdueTotal = BigDecimal.ZERO;
        for (LoanResponse l : loansFor(f, LoanStatus.ACTIVE, true)) {
            long maxDays = 0;
            int overdueCount = 0;
            BigDecimal overdue = BigDecimal.ZERO;
            for (LoanScheduleLineResponse i : l.getInstallments()) {
                if (!"PAID".equals(i.getStatus()) && i.getDueDate().isBefore(cut)) {
                    overdueCount++;
                    overdue = overdue.add(i.getPending());
                    maxDays = Math.max(maxDays, java.time.temporal.ChronoUnit.DAYS.between(i.getDueDate(), cut));
                }
            }
            String bucket = maxDays == 0 ? buckets[0] : maxDays <= 30 ? buckets[1] : maxDays <= 60 ? buckets[2] : maxDays <= 90 ? buckets[3] : buckets[4];
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("party", l.getThirdPartyName());
            c.put("document", l.getThirdPartyIdNumber());
            c.put("loan", l.getLoanNumber());
            c.put("principal", l.getPrincipalAmount());
            c.put("balance", l.getOutstandingPrincipal());
            c.put("overdueCount", overdueCount);
            c.put("overdue", overdue);
            c.put("days", maxDays);
            c.put("status", maxDays == 0 ? "Al día" : "En mora");
            grouped.get(bucket).add(c);
            total = total.add(l.getOutstandingPrincipal());
            overdueTotal = overdueTotal.add(overdue);
        }
        for (Map.Entry<String, List<Map<String, Object>>> g : grouped.entrySet()) {
            if (g.getValue().isEmpty()) {
                continue;
            }
            r.getRows().add(row("GROUP", Map.of("party", g.getKey())));
            BigDecimal sb = BigDecimal.ZERO;
            BigDecimal so = BigDecimal.ZERO;
            for (Map<String, Object> c : g.getValue()) {
                r.getRows().add(Row.builder().cells(c).build());
                sb = sb.add((BigDecimal) c.get("balance"));
                so = so.add((BigDecimal) c.get("overdue"));
            }
            r.getRows().add(row("SUBTOTAL", Map.of("party", "Subtotal " + g.getKey().toLowerCase(), "balance", sb, "overdue", so)));
        }
        BigDecimal currentPart = grouped.get(buckets[0]).stream().map(c -> (BigDecimal) c.get("balance")).reduce(BigDecimal.ZERO, BigDecimal::add);
        r.getSummary().add(summary("Cartera total", total, "primary"));
        r.getSummary().add(summary("Cartera al día", currentPart, "positive"));
        r.getSummary().add(summary("Cartera en mora", total.subtract(currentPart), "negative"));
        r.getSummary().add(summary("Valor vencido", overdueTotal, "negative"));
        r.getTotals().put("balance", total);
        r.getTotals().put("overdue", overdueTotal);
    }

    private void receivables(FinancialReportResponse r, Filters f) {
        r.setSubtitle("Obligaciones de los asociados con la asociación (préstamos activos)");
        r.getColumns().addAll(List.of(
                col("party", "Asociado", "TEXT"), col("document", "Documento", "TEXT"), col("loan", "Préstamo", "TEXT"),
                money("principal", "Valor inicial"), money("paidPrincipal", "Capital pagado"), money("paidInterest", "Interés pagado"),
                money("balance", "Saldo pendiente"), col("installments", "Cuotas", "TEXT"), col("next", "Próximo venc.", "DATE"),
                col("status", "Estado", "TEXT")));
        List<LoanResponse> loans = loansFor(f, LoanStatus.ACTIVE, false);
        for (LoanResponse l : loans) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("party", l.getThirdPartyName());
            c.put("document", l.getThirdPartyIdNumber());
            c.put("loan", l.getLoanNumber());
            c.put("principal", l.getPrincipalAmount());
            c.put("paidPrincipal", l.getPaidPrincipal());
            c.put("paidInterest", l.getPaidInterest());
            c.put("balance", l.getOutstandingPrincipal());
            c.put("installments", l.getPaidInstallments() + " / " + l.getTermMonths());
            c.put("next", l.getNextDueDate());
            c.put("status", portfolioLabel(l.getPortfolioStatus()));
            r.getRows().add(Row.builder().cells(c).build());
        }
        BigDecimal balance = loans.stream().map(LoanResponse::getOutstandingPrincipal).reduce(BigDecimal.ZERO, BigDecimal::add);
        r.getSummary().add(summary("Total por cobrar", balance, "primary"));
        r.getSummary().add(summary("Préstamos activos", BigDecimal.valueOf(loans.size()), "neutral", "NUMBER"));
        r.getSummary().add(summary("En mora", BigDecimal.valueOf(loans.stream().filter(l -> "EN_MORA".equals(l.getPortfolioStatus())).count()), "negative", "NUMBER"));
        r.getTotals().put("principal", loans.stream().map(LoanResponse::getPrincipalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        r.getTotals().put("paidPrincipal", loans.stream().map(LoanResponse::getPaidPrincipal).reduce(BigDecimal.ZERO, BigDecimal::add));
        r.getTotals().put("paidInterest", loans.stream().map(LoanResponse::getPaidInterest).reduce(BigDecimal.ZERO, BigDecimal::add));
        r.getTotals().put("balance", balance);
    }

    // ================================================================== APORTES / AHORROS / CXP

    private void associateConcept(FinancialReportResponse r, Filters f, QuotaCategory category, String label) {
        r.setSubtitle(label + " recibidos en el periodo, agrupados por asociado");
        r.getColumns().addAll(List.of(
                col("party", "Asociado", "TEXT"), col("document", "Documento", "TEXT"), col("date", "Fecha", "DATE"),
                col("receipt", "Recibo", "TEXT"), col("concept", "Concepto", "TEXT"), col("account", "Cuenta", "TEXT"),
                money("value", "Valor")));
        Map<UUID, List<Object[]>> byParty = new LinkedHashMap<>();
        for (AssociateReceipt rc : receiptRepository.findByPaymentDateBetweenWithDetails(f.from(), f.to())) {
            if (f.thirdPartyId() != null && !rc.getThirdParty().getId().equals(f.thirdPartyId())) {
                continue;
            }
            for (AssociateReceiptItem item : rc.getItems()) {
                if (item.getCategory() == category) {
                    byParty.computeIfAbsent(rc.getThirdParty().getId(), k -> new ArrayList<>()).add(new Object[]{rc, item});
                }
            }
        }
        BigDecimal grand = BigDecimal.ZERO;
        for (List<Object[]> lines : byParty.values()) {
            AssociateReceipt firstReceipt = (AssociateReceipt) lines.getFirst()[0];
            String name = firstReceipt.getThirdParty().getFullName();
            r.getRows().add(row("GROUP", Map.of("party", name, "document", firstReceipt.getThirdParty().getIdentificationNumber())));
            BigDecimal subtotal = BigDecimal.ZERO;
            for (Object[] pair : lines) {
                AssociateReceipt rc = (AssociateReceipt) pair[0];
                AssociateReceiptItem item = (AssociateReceiptItem) pair[1];
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("party", "");
                c.put("document", "");
                c.put("date", rc.getPaymentDate());
                c.put("receipt", rc.getReceiptNumber());
                c.put("concept", item.getConceptName());
                c.put("account", item.getAccount().getCode() + " - " + item.getAccount().getName());
                c.put("value", item.getAmount());
                r.getRows().add(Row.builder().cells(c).build());
                subtotal = subtotal.add(item.getAmount());
            }
            r.getRows().add(row("SUBTOTAL", Map.of("party", "Subtotal " + name, "value", subtotal)));
            grand = grand.add(subtotal);
        }
        r.getSummary().add(summary("Total " + label.toLowerCase() + " del periodo", grand, "primary"));
        r.getSummary().add(summary("Asociados", BigDecimal.valueOf(byParty.size()), "neutral", "NUMBER"));
        r.getTotals().put("value", grand);
    }

    private void payables(FinancialReportResponse r, Filters f) {
        r.setSubtitle("Saldos acumulados a favor de los asociados · aportes y ahorros se presentan por separado");
        r.getColumns().addAll(List.of(
                col("party", "Asociado", "TEXT"), col("document", "Documento", "TEXT"), money("contributions", "Aportes"),
                money("savings", "Ahorros"), money("total", "Saldo total"), col("movements", "Movimientos", "NUMBER"),
                col("last", "Última actualización", "DATE")));
        List<AssociateSummaryResponse> list = directoryService.payables(null).stream()
                .filter(a -> f.thirdPartyId() == null || a.getId().equals(f.thirdPartyId()))
                .toList();
        for (AssociateSummaryResponse a : list) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("party", a.getFullName());
            c.put("document", a.getIdentificationNumber());
            c.put("contributions", a.getContributions());
            c.put("savings", a.getSavings());
            c.put("total", a.getPayableBalance());
            c.put("movements", a.getMovements());
            c.put("last", a.getLastMovementDate());
            r.getRows().add(Row.builder().cells(c).build());
        }
        BigDecimal contributions = list.stream().map(AssociateSummaryResponse::getContributions).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal savings = list.stream().map(AssociateSummaryResponse::getSavings).reduce(BigDecimal.ZERO, BigDecimal::add);
        r.getSummary().add(summary("Total aportes", contributions, "primary"));
        r.getSummary().add(summary("Total ahorros", savings, "primary"));
        r.getSummary().add(summary("Total por pagar", contributions.add(savings), "negative"));
        r.getTotals().put("contributions", contributions);
        r.getTotals().put("savings", savings);
        r.getTotals().put("total", contributions.add(savings));
    }

    // ================================================================== helpers

    private boolean matchesEntryFilters(JournalEntry e, Filters f) {
        if (f.voucherType() != null && e.getVoucher().getVoucherType() != f.voucherType()) {
            return false;
        }
        return f.thirdPartyId() == null || (e.getThirdParty() != null && e.getThirdParty().getId().equals(f.thirdPartyId()));
    }

    private static boolean matchesMethod(String cashAccountCode, String method) {
        if (method == null) {
            return true;
        }
        if (cashAccountCode == null) {
            return false;
        }
        return "CASH".equalsIgnoreCase(method) ? cashAccountCode.startsWith("1105")
                : cashAccountCode.startsWith("1110") || cashAccountCode.startsWith("1120");
    }

    private void describeFilters(FinancialReportResponse r, Filters f) {
        if (f.accountCode() != null) r.getFilters().put("Cuenta", f.accountCode());
        if (f.voucherType() != null) r.getFilters().put("Tipo de movimiento", voucherTypeLabel(f.voucherType()));
        if (f.paymentMethod() != null) r.getFilters().put("Medio de pago", "CASH".equalsIgnoreCase(f.paymentMethod()) ? "Efectivo (caja)" : "Bancos");
        if (f.level() != null) r.getFilters().put("Nivel PUC", String.valueOf(f.level()));
        if (f.byParty()) r.getFilters().put("Detalle", "Por tercero");
        if (f.thirdPartyId() != null) {
            r.getFilters().put("Asociado", thirdPartyRepository.findById(f.thirdPartyId())
                    .map(p -> p.getFullName() + " (" + p.getIdentificationNumber() + ")")
                    .orElse(f.thirdPartyId().toString()));
        }
    }

    private String currentUserName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return "Sistema";
        }
        return userRepository.findByUsername(auth.getName())
                .map(u -> u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername())
                .orElse(auth.getName());
    }

    private static String voucherTypeLabel(VoucherType t) {
        return switch (t) {
            case INGRESO -> "Ingreso";
            case EGRESO -> "Egreso";
            case NOTA_CONTABLE -> "Nota contable";
            case APERTURA -> "Apertura";
        };
    }

    private static String paymentMethodLabel(String m) {
        return switch (m) {
            case "CASH" -> "Efectivo";
            case "TRANSFER" -> "Transferencia";
            case "CONSIGNMENT" -> "Consignación";
            case "PAYROLL_DEDUCTION" -> "Descuento de nómina";
            default -> "Otro";
        };
    }

    private static String portfolioLabel(String s) {
        return switch (s) {
            case "AL_DIA" -> "Al día";
            case "EN_MORA" -> "En mora";
            case "PAGADO" -> "Pagado";
            default -> "Anulado";
        };
    }

    private static <T> BigDecimal sum(List<T> list, Function<T, BigDecimal> f) {
        return list.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static Column col(String key, String label, String type) {
        return Column.builder().key(key).label(label).type(type).build();
    }

    private static Column money(String key, String label) {
        return Column.builder().key(key).label(label).type("MONEY").summable(true).build();
    }

    private static SummaryItem summary(String label, BigDecimal value, String tone) {
        return summary(label, value, tone, "MONEY");
    }

    private static SummaryItem summary(String label, BigDecimal value, String tone, String type) {
        return SummaryItem.builder().label(label).value(value).tone(tone).type(type).build();
    }

    private static Row row(String type, Map<String, Object> cells) {
        return Row.builder().rowType(type).cells(new LinkedHashMap<>(cells)).build();
    }
}
