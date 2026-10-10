package com.app.accounting.report.service;

import com.app.accounting.association.AssociationChart;
import com.app.accounting.association.CollectionConcept;
import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.investment.Investment;
import com.app.accounting.investment.InvestmentRepository;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.party.repository.ThirdPartyRepository;
import com.app.accounting.puc.entity.Account;
import com.app.accounting.puc.repository.AccountRepository;
import com.app.accounting.report.dto.response.FinancialReportResponse;
import com.app.accounting.report.dto.response.FinancialReportResponse.Column;
import com.app.accounting.report.dto.response.FinancialReportResponse.Row;
import com.app.accounting.report.dto.response.FinancialReportResponse.SummaryItem;
import com.app.accounting.report.repository.ReportEntryRepository;
import com.app.accounting.voucher.entity.JournalEntry;
import com.app.accounting.voucher.entity.JournalVoucher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Estados y auxiliares contables equivalentes a las hojas del informe mensual de la asociación
 * (BAL. PRUEBA, ESFA, ESTADO RESULTADOS, PLANILLA, CUENTAS POR C, APORTES, APORTES EXTRAS, TURISMO Y R...).
 * Todo se calcula desde los asientos asentados (POSTED); no hay cifras fijas.
 */
@Component
@RequiredArgsConstructor
public class AccountingStatementsBuilder {

    static final LocalDate BEGINNING = LocalDate.of(1900, 1, 1);
    private static final String UNCLOSED_CODE = "3*";

    private final ReportEntryRepository entryRepository;
    private final AccountRepository accountRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final InvestmentRepository investmentRepository;

    /** Débitos y créditos acumulados. */
    record Sums(BigDecimal debit, BigDecimal credit) {
        static final Sums ZERO = new Sums(BigDecimal.ZERO, BigDecimal.ZERO);

        Sums plus(Sums o) {
            return new Sums(debit.add(o.debit), credit.add(o.credit));
        }

        Sums minus(Sums o) {
            return new Sums(debit.subtract(o.debit), credit.subtract(o.credit));
        }

        BigDecimal net() {
            return debit.subtract(credit);
        }
    }

    /** Ventana de saldos por cuenta: apertura (neto débito − crédito), débitos y créditos del periodo. */
    record Window(BigDecimal openingNet, BigDecimal debit, BigDecimal credit) {
        static final Window ZERO = new Window(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        Window plus(Window o) {
            return new Window(openingNet.add(o.openingNet), debit.add(o.debit), credit.add(o.credit));
        }

        BigDecimal closingNet() {
            return openingNet.add(debit).subtract(credit);
        }

        boolean isZero() {
            return openingNet.signum() == 0 && debit.signum() == 0 && credit.signum() == 0;
        }
    }

    // ================================================================== BALANCE DE PRUEBA

    void trialBalance(FinancialReportResponse r, FinancialReportService.Filters f) {
        LocalDate yearStart = f.from().withDayOfYear(1);
        Map<String, Account> accounts = accountIndex();
        Map<String, Window> leaves = windows(f.from(), f.to(), yearStart, null);

        // Resultados de años anteriores que no se cerraron contablemente: se presentan dentro del patrimonio
        BigDecimal unclosed = sums(BEGINNING, yearStart.minusDays(1), false).entrySet().stream()
                .filter(e -> isResult(e.getKey()))
                .map(e -> e.getValue().net())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        r.setSubtitle("Saldo anterior, débitos, créditos y saldo final por cuenta PUC · saldos según la naturaleza de cada cuenta"
                + " · cuentas de resultado acumuladas desde " + yearStart);
        r.getColumns().addAll(List.of(col("code", "Código", "TEXT"), col("name", "Cuenta", "TEXT"),
                col("opening", "Saldo anterior", "MONEY"), money("debit", "Débitos"), money("credit", "Créditos"),
                col("closing", "Saldo final", "MONEY")));

        Map<String, Window> rolled = new TreeMap<>();
        for (Map.Entry<String, Window> leaf : leaves.entrySet()) {
            for (String code : selfAndAncestors(leaf.getKey(), accounts)) {
                rolled.merge(code, leaf.getValue(), Window::plus);
            }
        }
        if (unclosed.signum() != 0) {
            Window w = new Window(unclosed, BigDecimal.ZERO, BigDecimal.ZERO);
            rolled.merge("3", w, Window::plus);
            rolled.put(UNCLOSED_CODE, w);
        }

        Map<String, Map<UUID, Window>> parties = f.byParty() ? partyWindows("", f.from(), f.to(), yearStart) : Map.of();
        Map<UUID, ThirdParty> partyIndex = partyIndex(parties.values().stream().flatMap(m -> m.keySet().stream()).toList());
        Integer maxLevel = f.level();

        for (Map.Entry<String, Window> e : rolled.entrySet()) {
            String code = e.getKey();
            Window w = e.getValue();
            if (w.isZero()) continue;
            if (f.accountCode() != null && !code.startsWith(f.accountCode()) && !f.accountCode().startsWith(code)) continue;
            int level = UNCLOSED_CODE.equals(code) ? 4 : levelOf(code, accounts);
            if (maxLevel != null && level > maxLevel) continue;
            boolean debitNature = UNCLOSED_CODE.equals(code) ? false : isDebitNature(code, accounts);
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("code", UNCLOSED_CODE.equals(code) ? "" : code);
            c.put("name", UNCLOSED_CODE.equals(code) ? "RESULTADOS DE EJERCICIOS ANTERIORES SIN CIERRE CONTABLE" : nameOf(code, accounts));
            c.put("opening", signed(w.openingNet(), debitNature));
            c.put("debit", w.debit());
            c.put("credit", w.credit());
            c.put("closing", signed(w.closingNet(), debitNature));
            c.put("_level", level);
            c.put("_accountCode", code);
            r.getRows().add(Row.builder().rowType(level == 1 ? "GROUP" : "DETAIL").cells(c).build());

            Map<UUID, Window> byParty = parties.get(code);
            if (byParty != null && leaves.containsKey(code)) {
                byParty.entrySet().stream()
                        .filter(p -> !p.getValue().isZero())
                        .sorted(Comparator.comparing(p -> partyName(partyIndex.get(p.getKey()))))
                        .forEach(p -> {
                            Map<String, Object> pc = new LinkedHashMap<>();
                            ThirdParty tp = partyIndex.get(p.getKey());
                            pc.put("code", tp != null ? tp.getIdentificationNumber() : "");
                            pc.put("name", "   " + partyName(tp));
                            pc.put("opening", signed(p.getValue().openingNet(), debitNature));
                            pc.put("debit", p.getValue().debit());
                            pc.put("credit", p.getValue().credit());
                            pc.put("closing", signed(p.getValue().closingNet(), debitNature));
                            pc.put("_level", level + 1);
                            pc.put("_partyId", p.getKey());
                            pc.put("_accountCode", code);
                            r.getRows().add(Row.builder().cells(pc).build());
                        });
            }
        }

        BigDecimal debits = leaves.values().stream().map(Window::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = leaves.values().stream().map(Window::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal openingCheck = leaves.values().stream().map(Window::openingNet).reduce(unclosed, BigDecimal::add);
        r.getSummary().add(summary("Total débitos", debits, "positive"));
        r.getSummary().add(summary("Total créditos", credits, "negative"));
        r.getSummary().add(summary("Diferencia débitos − créditos", debits.subtract(credits), debits.compareTo(credits) == 0 ? "neutral" : "negative"));
        r.getSummary().add(summary("Descuadre de saldos anteriores", openingCheck, openingCheck.signum() == 0 ? "neutral" : "negative"));
        r.getTotals().put("debit", debits);
        r.getTotals().put("credit", credits);
    }

    // ================================================================== ESTADO DE SITUACIÓN FINANCIERA

    void financialPosition(FinancialReportResponse r, FinancialReportService.Filters f) {
        LocalDate cut = f.to();
        LocalDate yearStart = cut.withDayOfYear(1);
        Map<String, Account> accounts = accountIndex();
        Map<String, Sums> balances = sums(BEGINNING, cut, false);
        BigDecimal yearResult = BigDecimal.ZERO;   // crédito − débito de las cuentas de resultado del año
        BigDecimal unclosed = BigDecimal.ZERO;
        for (Map.Entry<String, Sums> e : sums(yearStart, cut, false).entrySet()) {
            if (isResult(e.getKey())) yearResult = yearResult.subtract(e.getValue().net());
        }
        for (Map.Entry<String, Sums> e : sums(BEGINNING, yearStart.minusDays(1), false).entrySet()) {
            if (isResult(e.getKey())) unclosed = unclosed.subtract(e.getValue().net());
        }
        int detailLevel = f.level() != null ? Math.max(2, f.level()) : 4;

        r.setTitle("ESTADO DE SITUACIÓN FINANCIERA");
        r.setSubtitle("A corte de " + cut + " · cifras en pesos colombianos");
        r.getColumns().addAll(List.of(col("code", "Código", "TEXT"), col("name", "Concepto", "TEXT"), col("value", "Valor (COP)", "MONEY")));

        BigDecimal currentAssets = section(r, "ACTIVO CORRIENTE", balances, accounts, detailLevel, code -> code.startsWith("1") && code.length() >= 2 && code.charAt(1) <= '4', true);
        BigDecimal nonCurrentAssets = section(r, "ACTIVO NO CORRIENTE", balances, accounts, detailLevel, code -> code.startsWith("1") && code.length() >= 2 && code.charAt(1) > '4', true);
        BigDecimal assets = currentAssets.add(nonCurrentAssets);
        r.getRows().add(row("SUBTOTAL", Map.of("name", "TOTAL ACTIVOS", "value", assets)));

        BigDecimal liabilities = section(r, "PASIVO CORRIENTE", balances, accounts, detailLevel, code -> code.startsWith("2"), false);
        r.getRows().add(row("SUBTOTAL", Map.of("name", "TOTAL PASIVOS", "value", liabilities)));

        BigDecimal equity = section(r, "PATRIMONIO", balances, accounts, detailLevel, code -> code.startsWith("3"), false);
        if (unclosed.signum() != 0) {
            r.getRows().add(row("DETAIL", Map.of("code", "", "name", "Resultados de ejercicios anteriores sin cierre contable", "value", unclosed)));
            equity = equity.add(unclosed);
        }
        r.getRows().add(row("DETAIL", Map.of("code", AssociationChart.CURRENT_RESULT, "name",
                yearResult.signum() >= 0 ? "Excedente del ejercicio" : "Déficit del ejercicio", "value", yearResult)));
        equity = equity.add(yearResult);
        r.getRows().add(row("SUBTOTAL", Map.of("name", "TOTAL PATRIMONIO", "value", equity)));
        r.getRows().add(row("SUBTOTAL", Map.of("name", "TOTAL PASIVO + PATRIMONIO", "value", liabilities.add(equity))));

        BigDecimal difference = assets.subtract(liabilities).subtract(equity);
        r.getSummary().add(summary("Total activos", assets, "primary"));
        r.getSummary().add(summary("Total pasivos", liabilities, "negative"));
        r.getSummary().add(summary("Total patrimonio", equity, "positive"));
        r.getSummary().add(summary(yearResult.signum() >= 0 ? "Excedente del ejercicio" : "Déficit del ejercicio", yearResult, yearResult.signum() >= 0 ? "positive" : "negative"));
        r.getSummary().add(summary("Diferencia (A − P − Pt)", difference, difference.signum() == 0 ? "neutral" : "negative"));
    }

    /** Sección del ESFA: líneas al nivel de detalle pedido, agrupadas por grupo PUC (2 dígitos). */
    private BigDecimal section(FinancialReportResponse r, String title, Map<String, Sums> balances, Map<String, Account> accounts,
                               int detailLevel, Predicate<String> include, boolean debitSide) {
        Map<String, BigDecimal> lines = new TreeMap<>();
        for (Map.Entry<String, Sums> e : balances.entrySet()) {
            String code = e.getKey();
            if (!include.test(code)) continue;
            String key = ancestorAtLevel(code, detailLevel, accounts);
            lines.merge(key, signed(e.getValue().net(), debitSide), BigDecimal::add);
        }
        lines.values().removeIf(v -> v.signum() == 0);
        r.getRows().add(row("GROUP", Map.of("name", title)));
        BigDecimal total = BigDecimal.ZERO;
        Map<String, List<Map.Entry<String, BigDecimal>>> byGroup = lines.entrySet().stream()
                .collect(Collectors.groupingBy(e -> e.getKey().substring(0, Math.min(2, e.getKey().length())), TreeMap::new, Collectors.toList()));
        for (Map.Entry<String, List<Map.Entry<String, BigDecimal>>> g : byGroup.entrySet()) {
            BigDecimal groupTotal = BigDecimal.ZERO;
            boolean showGroup = detailLevel > 2 && g.getValue().size() > 1;
            for (Map.Entry<String, BigDecimal> line : g.getValue()) {
                r.getRows().add(row("DETAIL", Map.of("code", line.getKey(), "name", nameOf(line.getKey(), accounts), "value", line.getValue(),
                        "_accountCode", line.getKey())));
                groupTotal = groupTotal.add(line.getValue());
            }
            if (showGroup) {
                r.getRows().add(row("SUBTOTAL", Map.of("code", g.getKey(), "name", "Total " + nameOf(g.getKey(), accounts).toLowerCase(Locale.ROOT), "value", groupTotal)));
            }
            total = total.add(groupTotal);
        }
        r.getRows().add(row("SUBTOTAL", Map.of("name", "Total " + title.toLowerCase(Locale.ROOT), "value", total)));
        return total;
    }

    // ================================================================== ESTADO DE RESULTADOS

    void incomeStatement(FinancialReportResponse r, FinancialReportService.Filters f) {
        LocalDate yearStart = f.to().withDayOfYear(1);
        LocalDate periodFrom = f.from().isBefore(yearStart) ? yearStart : f.from();
        Map<String, Account> accounts = accountIndex();
        Map<String, Sums> previous = periodFrom.isAfter(yearStart) ? sums(yearStart, periodFrom.minusDays(1), true) : Map.of();
        Map<String, Sums> period = sums(periodFrom, f.to(), true);

        r.setTitle("ESTADO DE RESULTADOS INTEGRAL");
        r.setSubtitle("Acumulado del " + yearStart + " al " + f.to() + " · periodo " + periodFrom + " a " + f.to()
                + " · excluye el comprobante de cierre anual");
        r.getColumns().addAll(List.of(col("code", "Código", "TEXT"), col("name", "Concepto", "TEXT"),
                money("previous", "Acumulado anterior"), money("period", "Periodo"), money("accumulated", "Acumulado")));

        BigDecimal[] income = resultSection(r, "INGRESOS", "4", previous, period, accounts, false);
        BigDecimal[] expenses = resultSection(r, "GASTOS", "5", previous, period, accounts, true);
        BigDecimal[] costs = resultSection(r, "COSTOS", "6", previous, period, accounts, true);
        BigDecimal[] production = resultSection(r, "COSTOS DE PRODUCCIÓN", "7", previous, period, accounts, true);
        BigDecimal[] result = new BigDecimal[3];
        for (int i = 0; i < 3; i++) {
            result[i] = income[i].subtract(expenses[i]).subtract(costs[i]).subtract(production[i]);
        }
        r.getRows().add(row("SUBTOTAL", Map.of("name", "EXCEDENTE (DÉFICIT) NETO DEL EJERCICIO",
                "previous", result[0], "period", result[1], "accumulated", result[2])));

        r.getSummary().add(summary("Ingresos acumulados", income[2], "positive"));
        r.getSummary().add(summary("Gastos y costos acumulados", expenses[2].add(costs[2]).add(production[2]), "negative"));
        r.getSummary().add(summary(result[2].signum() >= 0 ? "Excedente acumulado" : "Déficit acumulado", result[2], "primary"));
        r.getSummary().add(summary("Resultado del periodo", result[1], result[1].signum() >= 0 ? "positive" : "negative"));
    }

    private BigDecimal[] resultSection(FinancialReportResponse r, String title, String classCode, Map<String, Sums> previous,
                                       Map<String, Sums> period, Map<String, Account> accounts, boolean debitNature) {
        Set<String> codes = new TreeSet<>();
        previous.keySet().stream().filter(c -> c.startsWith(classCode)).forEach(codes::add);
        period.keySet().stream().filter(c -> c.startsWith(classCode)).forEach(codes::add);
        BigDecimal[] totals = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        if (codes.isEmpty()) return totals;

        r.getRows().add(row("GROUP", Map.of("name", title)));
        Map<String, List<String>> byGroup = codes.stream()
                .collect(Collectors.groupingBy(c -> c.substring(0, Math.min(2, c.length())), TreeMap::new, Collectors.toList()));
        for (Map.Entry<String, List<String>> g : byGroup.entrySet()) {
            BigDecimal[] sub = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
            List<Map<String, Object>> lines = new ArrayList<>();
            for (String code : g.getValue()) {
                BigDecimal prev = signed(previous.getOrDefault(code, Sums.ZERO).net(), debitNature);
                BigDecimal per = signed(period.getOrDefault(code, Sums.ZERO).net(), debitNature);
                if (prev.signum() == 0 && per.signum() == 0) continue;
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("code", code);
                c.put("name", nameOf(code, accounts));
                c.put("previous", prev);
                c.put("period", per);
                c.put("accumulated", prev.add(per));
                c.put("_accountCode", code);
                lines.add(c);
                sub[0] = sub[0].add(prev);
                sub[1] = sub[1].add(per);
            }
            if (lines.isEmpty()) continue;
            r.getRows().add(row("DETAIL", Map.of("code", g.getKey(), "name", nameOf(g.getKey(), accounts), "_level", 2)));
            lines.forEach(c -> r.getRows().add(Row.builder().cells(c).build()));
            r.getRows().add(row("SUBTOTAL", Map.of("code", g.getKey(), "name", "Total " + nameOf(g.getKey(), accounts).toLowerCase(Locale.ROOT),
                    "previous", sub[0], "period", sub[1], "accumulated", sub[0].add(sub[1]))));
            totals[0] = totals[0].add(sub[0]);
            totals[1] = totals[1].add(sub[1]);
        }
        totals[2] = totals[0].add(totals[1]);
        r.getRows().add(row("SUBTOTAL", Map.of("name", "TOTAL " + title, "previous", totals[0], "period", totals[1], "accumulated", totals[2])));
        return totals;
    }

    // ================================================================== AUXILIARES POR ASOCIADO

    /** Especificación de un auxiliar por tercero (equivale a las hojas CUENTAS POR C, APORTES, APORTES EXTRAS, TURISMO Y R...). */
    record PartyLedgerSpec(String subtitle, List<String> prefixes, String debitLabel, String creditLabel) {
    }

    static final Map<String, PartyLedgerSpec> PARTY_LEDGERS = new LinkedHashMap<>();

    static {
        PARTY_LEDGERS.put("CXC_ASOCIADOS", new PartyLedgerSpec("Préstamos y otras cuentas por cobrar a asociados (saldo anterior + cargos − abonos)",
                List.of("1365", "1380"), "Cargos (desembolsos)", "Abonos"));
        PARTY_LEDGERS.put("APORTES_SOCIALES", new PartyLedgerSpec("Aportes sociales ordinarios por asociado (saldo anterior − retiros + aportes)",
                List.of("3115"), "Retiros / devoluciones", "Aportes"));
        PARTY_LEDGERS.put("APORTES_EXTRAS", new PartyLedgerSpec("Aportes extraordinarios (excedente de aportes) por asociado — pasivo con el asociado",
                List.of(AssociationChart.EXTRA_CONTRIBUTIONS), "Retiros / devoluciones", "Aportes"));
        PARTY_LEDGERS.put("TURISMO_RECREACION", new PartyLedgerSpec("Fondo de turismo y recreación por asociado",
                List.of(AssociationChart.TOURISM_FUND), "Usos / devoluciones", "Aportes"));
        PARTY_LEDGERS.put("FONDO_SOLIDARIDAD", new PartyLedgerSpec("Fondo de solidaridad: aportes por asociado y auxilios pagados",
                List.of(AssociationChart.SOLIDARITY_FUND), "Auxilios / usos", "Aportes"));
        PARTY_LEDGERS.put("PREEXEQUIALES", new PartyLedgerSpec("Preexequiales recaudados para terceros y pagados a la funeraria",
                List.of("2815"), "Pagos a la funeraria", "Recaudos"));
    }

    void partyLedger(FinancialReportResponse r, FinancialReportService.Filters f, PartyLedgerSpec spec) {
        Map<String, Account> accounts = accountIndex();
        r.setSubtitle(spec.subtitle());
        boolean detail = f.thirdPartyId() != null;
        if (detail) {
            r.getColumns().addAll(List.of(col("date", "Fecha", "DATE"), col("voucher", "Comprobante", "TEXT"), col("account", "Cuenta", "TEXT"),
                    col("concept", "Concepto", "TEXT"), money("debit", spec.debitLabel()), money("credit", spec.creditLabel()),
                    col("balance", "Saldo", "MONEY")));
        } else {
            r.getColumns().addAll(List.of(col("party", "Asociado / tercero", "TEXT"), col("document", "Documento", "TEXT"),
                    money("opening", "Saldo anterior"), money("debit", spec.debitLabel()), money("credit", spec.creditLabel()),
                    money("closing", "Saldo final")));
        }

        BigDecimal[] grand = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        int partiesWithBalance = 0;
        boolean multi = spec.prefixes().size() > 1;
        for (String prefix : spec.prefixes()) {
            boolean debitNature = AssociationChart.isDebitNature(prefix);
            Map<UUID, Window> byParty = new HashMap<>();
            Map<String, Map<UUID, Window>> windows = partyWindows(prefix, f.from(), f.to(), null);
            windows.values().forEach(m -> m.forEach((id, w) -> byParty.merge(id, w, Window::plus)));
            if (f.thirdPartyId() != null) byParty.keySet().retainAll(Set.of(f.thirdPartyId()));
            byParty.values().removeIf(Window::isZero);
            if (byParty.isEmpty()) continue;
            if (multi) r.getRows().add(row("GROUP", Map.of(detail ? "concept" : "party", prefix + " - " + nameOf(prefix, accounts))));

            BigDecimal[] sub = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
            Map<UUID, ThirdParty> index = partyIndex(byParty.keySet());
            List<Map.Entry<UUID, Window>> sorted = byParty.entrySet().stream()
                    .sorted(Comparator.comparing(e -> partyName(index.get(e.getKey())))).toList();
            for (Map.Entry<UUID, Window> e : sorted) {
                Window w = e.getValue();
                BigDecimal opening = signed(w.openingNet(), debitNature);
                BigDecimal closing = signed(w.closingNet(), debitNature);
                if (detail) {
                    movementRows(r, prefix, e.getKey(), f, opening, debitNature);
                } else {
                    ThirdParty tp = index.get(e.getKey());
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("party", partyName(tp));
                    c.put("document", tp != null ? tp.getIdentificationNumber() : "");
                    c.put("opening", opening);
                    c.put("debit", w.debit());
                    c.put("credit", w.credit());
                    c.put("closing", closing);
                    if (e.getKey() != null) c.put("_partyId", e.getKey());
                    r.getRows().add(Row.builder().cells(c).build());
                }
                if (closing.signum() != 0) partiesWithBalance++;
                sub[0] = sub[0].add(opening);
                sub[1] = sub[1].add(w.debit());
                sub[2] = sub[2].add(w.credit());
                sub[3] = sub[3].add(closing);
            }
            if (multi || detail) {
                r.getRows().add(row("SUBTOTAL", detail
                        ? Map.of("concept", "Saldo final " + nameOf(prefix, accounts).toLowerCase(Locale.ROOT), "debit", sub[1], "credit", sub[2], "balance", sub[3])
                        : Map.of("party", "Subtotal " + nameOf(prefix, accounts).toLowerCase(Locale.ROOT), "opening", sub[0], "debit", sub[1], "credit", sub[2], "closing", sub[3])));
            }
            for (int i = 0; i < 4; i++) grand[i] = grand[i].add(sub[i]);
        }
        r.getSummary().add(summary("Saldo anterior", grand[0], "neutral"));
        r.getSummary().add(summary(spec.debitLabel(), grand[1], "negative"));
        r.getSummary().add(summary(spec.creditLabel(), grand[2], "positive"));
        r.getSummary().add(summary("Saldo final", grand[3], "primary"));
        if (!detail) {
            r.getSummary().add(summary("Asociados con saldo", BigDecimal.valueOf(partiesWithBalance), "neutral", "NUMBER"));
            r.getTotals().put("opening", grand[0]);
            r.getTotals().put("closing", grand[3]);
        }
        r.getTotals().put("debit", grand[1]);
        r.getTotals().put("credit", grand[2]);
    }

    private void movementRows(FinancialReportResponse r, String prefix, UUID partyId, FinancialReportService.Filters f,
                              BigDecimal opening, boolean debitNature) {
        r.getRows().add(row("DETAIL", Map.of("concept", "Saldo anterior", "balance", opening)));
        BigDecimal running = opening;
        for (JournalEntry e : entryRepository.findPostedByAccountPrefix(prefix, f.from(), f.to())) {
            if (e.getThirdParty() == null || !e.getThirdParty().getId().equals(partyId)) continue;
            running = running.add(signed(e.getDebit().subtract(e.getCredit()), debitNature));
            r.getRows().add(entryRow(e, running));
        }
    }

    private Row entryRow(JournalEntry e, BigDecimal running) {
        JournalVoucher v = e.getVoucher();
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("date", v.getIssueDate());
        c.put("voucher", v.getVoucherNumber());
        c.put("account", e.getAccount().getCode() + " - " + e.getAccount().getName());
        c.put("concept", e.getDescription() != null && !e.getDescription().isBlank() ? e.getDescription() : v.getDescription());
        c.put("debit", e.getDebit());
        c.put("credit", e.getCredit());
        c.put("balance", running);
        c.put("_voucherId", v.getId());
        return Row.builder().cells(c).build();
    }

    // ================================================================== FONDOS Y ACTIVIDADES

    void funds(FinancialReportResponse r, FinancialReportService.Filters f) {
        Map<String, Account> accounts = accountIndex();
        Map<String, Window> leaves = windows(f.from(), f.to(), f.from().withDayOfYear(1), null);
        r.setSubtitle("Fondos con destinación específica, recursos de terceros y actividades de la asociación");
        r.getColumns().addAll(List.of(col("code", "Código", "TEXT"), col("name", "Fondo / actividad", "TEXT"),
                money("opening", "Saldo anterior"), money("debit", "Usos (débitos)"), money("credit", "Recaudos (créditos)"),
                money("closing", "Saldo final")));

        BigDecimal[] funds = ledgerBlock(r, "FONDOS Y PASIVOS CON EL ASOCIADO", leaves, accounts,
                code -> code.startsWith("2380") || code.startsWith("2815") || code.startsWith("2895") || code.startsWith("2705"), false);
        BigDecimal[] activities = ledgerBlock(r, "ACTIVIDADES (INGRESOS Y GASTOS DEL AÑO)", leaves, accounts,
                code -> code.startsWith("417095") || code.startsWith("4295") || code.startsWith(AssociationChart.BINGO_PRIZES)
                        || code.startsWith(AssociationChart.SOCIAL_ACTIVITIES), false);
        r.getSummary().add(summary("Saldo de fondos", funds[3], "primary"));
        r.getSummary().add(summary("Recaudos a fondos del periodo", funds[2], "positive"));
        r.getSummary().add(summary("Usos de fondos del periodo", funds[1], "negative"));
        r.getSummary().add(summary("Resultado neto de actividades (año)", activities[3], activities[3].signum() >= 0 ? "positive" : "negative"));
        r.getTotals().put("debit", funds[1].add(activities[1]));
        r.getTotals().put("credit", funds[2].add(activities[2]));
    }

    /** Bloque saldo anterior / débitos / créditos / saldo final por cuenta hoja, con signo crédito o débito. */
    private BigDecimal[] ledgerBlock(FinancialReportResponse r, String title, Map<String, Window> leaves, Map<String, Account> accounts,
                                     Predicate<String> include, boolean debitNature) {
        BigDecimal[] t = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        List<Map.Entry<String, Window>> lines = leaves.entrySet().stream()
                .filter(e -> include.test(e.getKey()) && !e.getValue().isZero()).sorted(Map.Entry.comparingByKey()).toList();
        if (lines.isEmpty()) return t;
        r.getRows().add(row("GROUP", Map.of("name", title)));
        for (Map.Entry<String, Window> e : lines) {
            Window w = e.getValue();
            BigDecimal opening = signed(w.openingNet(), debitNature);
            BigDecimal closing = signed(w.closingNet(), debitNature);
            r.getRows().add(row("DETAIL", Map.of("code", e.getKey(), "name", nameOf(e.getKey(), accounts), "opening", opening,
                    "debit", w.debit(), "credit", w.credit(), "closing", closing, "_accountCode", e.getKey())));
            t[0] = t[0].add(opening);
            t[1] = t[1].add(w.debit());
            t[2] = t[2].add(w.credit());
            t[3] = t[3].add(closing);
        }
        r.getRows().add(row("SUBTOTAL", Map.of("name", "Total " + title.toLowerCase(Locale.ROOT), "opening", t[0], "debit", t[1],
                "credit", t[2], "closing", t[3])));
        return t;
    }

    // ================================================================== PROPIEDAD, PLANTA Y EQUIPO

    void fixedAssets(FinancialReportResponse r, FinancialReportService.Filters f) {
        Map<String, Account> accounts = accountIndex();
        Map<String, Window> leaves = windows(f.from(), f.to(), f.from().withDayOfYear(1), null);
        r.setSubtitle("Costo, depreciación acumulada y valor neto en libros · depreciación del periodo según cuentas 5160");
        r.getColumns().addAll(List.of(col("code", "Código", "TEXT"), col("name", "Activo", "TEXT"),
                money("opening", "Saldo anterior"), money("debit", "Adiciones (débitos)"), money("credit", "Bajas / depreciación (créditos)"),
                money("closing", "Saldo final")));
        BigDecimal[] cost = ledgerBlock(r, "COSTO", leaves, accounts, code -> code.startsWith("15") && !code.startsWith("159"), true);
        BigDecimal[] depreciation = ledgerBlock(r, "DEPRECIACIÓN ACUMULADA", leaves, accounts, code -> code.startsWith("159"), true);
        BigDecimal net = cost[3].add(depreciation[3]);
        r.getRows().add(row("SUBTOTAL", Map.of("name", "VALOR NETO EN LIBROS", "opening", cost[0].add(depreciation[0]), "closing", net)));
        BigDecimal periodDepreciation = leaves.entrySet().stream().filter(e -> e.getKey().startsWith("5160"))
                .map(e -> e.getValue().debit().subtract(e.getValue().credit())).reduce(BigDecimal.ZERO, BigDecimal::add);
        r.getSummary().add(summary("Costo bruto", cost[3], "neutral"));
        r.getSummary().add(summary("Depreciación acumulada", depreciation[3], "negative"));
        r.getSummary().add(summary("Valor neto en libros", net, "primary"));
        r.getSummary().add(summary("Depreciación del periodo", periodDepreciation, "negative"));
    }

    // ================================================================== INVERSIONES

    void investments(FinancialReportResponse r, FinancialReportService.Filters f) {
        r.setSubtitle("Títulos registrados (CDT y otros) frente al saldo contable del grupo 12 a la fecha de corte");
        r.getColumns().addAll(List.of(col("entity", "Entidad", "TEXT"), col("type", "Tipo", "TEXT"), col("number", "Número", "TEXT"),
                col("account", "Cuenta", "TEXT"), col("start", "Constitución", "DATE"), col("maturity", "Vencimiento", "DATE"),
                col("days", "Días al venc.", "NUMBER"), col("rate", "Tasa E.A.", "PERCENT"), money("principal", "Capital"),
                money("interest", "Intereses"), money("total", "Total"), col("status", "Estado", "TEXT")));
        List<Investment> list = investmentRepository.findAllWithAccount();
        BigDecimal principal = BigDecimal.ZERO;
        BigDecimal interest = BigDecimal.ZERO;
        for (Investment i : list) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("entity", i.getEntityName());
            c.put("type", i.getInstrumentType());
            c.put("number", i.getInstrumentNumber() != null ? i.getInstrumentNumber() : "");
            c.put("account", i.getAccount().getCode() + " - " + i.getAccount().getName());
            c.put("start", i.getStartDate());
            c.put("maturity", i.getMaturityDate());
            c.put("days", i.getMaturityDate() != null ? ChronoUnit.DAYS.between(f.to(), i.getMaturityDate()) : null);
            c.put("rate", i.getAnnualRate());
            c.put("principal", i.getPrincipal());
            c.put("interest", i.getAccruedInterest());
            c.put("total", i.getPrincipal().add(i.getAccruedInterest()));
            c.put("status", "ACTIVE".equals(i.getStatus()) ? "Vigente" : "Redimida");
            c.put("_accountCode", i.getAccount().getCode());
            r.getRows().add(Row.builder().cells(c).build());
            if ("ACTIVE".equals(i.getStatus())) {
                principal = principal.add(i.getPrincipal());
                interest = interest.add(i.getAccruedInterest());
            }
        }
        BigDecimal ledger = sums(BEGINNING, f.to(), false).entrySet().stream()
                .filter(e -> e.getKey().startsWith("12")).map(e -> e.getValue().net()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal registered = principal.add(interest);
        r.getSummary().add(summary("Total inversiones registradas", registered, "primary"));
        r.getSummary().add(summary("Saldo contable grupo 12", ledger, "neutral"));
        r.getSummary().add(summary("Diferencia registro − contabilidad", registered.subtract(ledger), registered.compareTo(ledger) == 0 ? "neutral" : "negative"));
        r.getSummary().add(summary("Inversiones vigentes", BigDecimal.valueOf(list.stream().filter(i -> "ACTIVE".equals(i.getStatus())).count()), "neutral", "NUMBER"));
        r.getTotals().put("principal", principal);
        r.getTotals().put("interest", interest);
        r.getTotals().put("total", registered);
    }

    // ================================================================== PLANILLA DE RECAUDOS

    void collectionSheet(FinancialReportResponse r, FinancialReportService.Filters f) {
        r.setSubtitle("Recaudos de asociados del periodo distribuidos por concepto (equivale a la hoja PLANILLA)");
        r.getColumns().add(col("party", "Asociado", "TEXT"));
        r.getColumns().add(col("receipt", "Recibo", "TEXT"));
        for (CollectionConcept c : CollectionConcept.values()) {
            r.getColumns().add(money(c.key(), c.label()));
        }
        r.getColumns().add(money("total", "Total"));
        r.getColumns().add(money("cash", "Caja"));
        r.getColumns().add(money("bank", "Bancos"));

        Map<UUID, List<JournalEntry>> byVoucher = new LinkedHashMap<>();
        Map<UUID, JournalVoucher> vouchers = new HashMap<>();
        for (JournalEntry e : entryRepository.findPostedIncomeVoucherEntries(f.from(), f.to())) {
            byVoucher.computeIfAbsent(e.getVoucher().getId(), k -> new ArrayList<>()).add(e);
            vouchers.put(e.getVoucher().getId(), e.getVoucher());
        }
        // Un recibo por asociado; los ingresos sin asociado (rendimientos, consignaciones sin identificar) quedan fuera de la planilla
        record Receipt(ThirdParty party, JournalVoucher voucher, Map<String, BigDecimal> values) {
        }
        List<Receipt> receipts = new ArrayList<>();
        BigDecimal outside = BigDecimal.ZERO;
        for (Map.Entry<UUID, List<JournalEntry>> v : byVoucher.entrySet()) {
            ThirdParty party = v.getValue().stream().map(JournalEntry::getThirdParty).filter(Objects::nonNull)
                    .filter(ThirdParty::isAssociate).findFirst().orElse(null);
            BigDecimal cashIn = v.getValue().stream().filter(e -> e.getAccount().getCode().startsWith("11"))
                    .map(e -> e.getDebit().subtract(e.getCredit())).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (party == null || (f.thirdPartyId() != null && !party.getId().equals(f.thirdPartyId()))) {
                if (party == null) outside = outside.add(cashIn);
                continue;
            }
            Map<String, BigDecimal> values = new LinkedHashMap<>();
            BigDecimal cash = BigDecimal.ZERO;
            BigDecimal bank = BigDecimal.ZERO;
            for (JournalEntry e : v.getValue()) {
                String code = e.getAccount().getCode();
                if (code.startsWith("11")) {
                    BigDecimal amount = e.getDebit().subtract(e.getCredit());
                    if (code.startsWith("1105")) cash = cash.add(amount);
                    else bank = bank.add(amount);
                } else {
                    values.merge(CollectionConcept.of(code).key(), e.getCredit().subtract(e.getDebit()), BigDecimal::add);
                }
            }
            values.put("cash", cash);
            values.put("bank", bank);
            values.put("total", cash.add(bank));
            receipts.add(new Receipt(party, vouchers.get(v.getKey()), values));
        }

        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        Set<UUID> paid = new HashSet<>();
        receipts.sort(Comparator.comparing((Receipt x) -> x.party().getFullName()).thenComparing(x -> x.voucher().getVoucherNumber()));
        for (Receipt rc : receipts) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("party", rc.party().getFullName());
            c.put("receipt", rc.voucher().getVoucherNumber());
            rc.values().forEach((k, v) -> {
                c.put(k, v);
                totals.merge(k, v, BigDecimal::add);
            });
            c.put("_voucherId", rc.voucher().getId());
            c.put("_partyId", rc.party().getId());
            r.getRows().add(Row.builder().cells(c).build());
            paid.add(rc.party().getId());
        }
        long unpaid = 0;
        if (f.thirdPartyId() == null) {
            List<ThirdParty> pending = thirdPartyRepository.findAll().stream()
                    .filter(ThirdParty::isAssociate).filter(t -> "ACTIVE".equals(t.getStatus()))
                    .filter(t -> !paid.contains(t.getId()))
                    .sorted(Comparator.comparing(ThirdParty::getFullName)).toList();
            if (!pending.isEmpty()) {
                r.getRows().add(row("GROUP", Map.of("party", "ASOCIADOS SIN RECAUDO EN EL PERIODO")));
                for (ThirdParty t : pending) {
                    r.getRows().add(row("DETAIL", Map.of("party", t.getFullName(), "receipt", "Sin recaudo", "total", BigDecimal.ZERO, "_partyId", t.getId())));
                }
            }
            unpaid = pending.size();
        }
        r.getTotals().putAll(totals);
        r.getSummary().add(summary("Total recaudado", totals.getOrDefault("total", BigDecimal.ZERO), "primary"));
        r.getSummary().add(summary("En caja", totals.getOrDefault("cash", BigDecimal.ZERO), "neutral"));
        r.getSummary().add(summary("En bancos", totals.getOrDefault("bank", BigDecimal.ZERO), "neutral"));
        r.getSummary().add(summary("Recibos", BigDecimal.valueOf(receipts.size()), "neutral", "NUMBER"));
        r.getSummary().add(summary("Asociados sin recaudo", BigDecimal.valueOf(unpaid), unpaid > 0 ? "negative" : "neutral", "NUMBER"));
        if (outside.signum() != 0) {
            r.getSummary().add(summary("Otros ingresos sin asociado (fuera de la planilla)", outside, "neutral"));
        }
    }

    // ================================================================== ESTADO DE CUENTA DEL ASOCIADO

    void associateStatement(FinancialReportResponse r, FinancialReportService.Filters f) {
        if (f.thirdPartyId() == null) {
            throw new BusinessRuleException("Seleccione un asociado para generar su estado de cuenta");
        }
        ThirdParty party = thirdPartyRepository.findById(f.thirdPartyId())
                .orElseThrow(() -> new BusinessRuleException("El asociado no existe"));
        Map<String, Account> accounts = accountIndex();
        r.setTitle("ESTADO DE CUENTA DEL ASOCIADO");
        r.setSubtitle(party.getFullName() + " · " + party.getIdentificationNumber() + " · movimientos del " + f.from() + " al " + f.to());
        r.getColumns().addAll(List.of(col("date", "Fecha", "DATE"), col("voucher", "Comprobante", "TEXT"), col("account", "Cuenta", "TEXT"),
                col("concept", "Concepto", "TEXT"), money("debit", "Débito"), money("credit", "Crédito"), col("balance", "Saldo", "MONEY")));

        Map<String, List<JournalEntry>> byAccount = new TreeMap<>();
        for (JournalEntry e : entryRepository.findPostedByParty(party.getId(), BEGINNING, f.to())) {
            byAccount.computeIfAbsent(e.getAccount().getCode(), k -> new ArrayList<>()).add(e);
        }
        BigDecimal inFavor = BigDecimal.ZERO;
        BigDecimal owed = BigDecimal.ZERO;
        for (Map.Entry<String, List<JournalEntry>> a : byAccount.entrySet()) {
            String code = a.getKey();
            // Solo saldos del asociado: lo que debe (13) y lo que se le debe (pasivos 2 y aportes 3). Caja, bancos e
            // ingresos de sus recibos son de la asociación y se ven en la planilla.
            if (!(code.startsWith("13") || code.startsWith("2") || code.startsWith("3"))) continue;
            boolean debitNature = isDebitNature(code, accounts);
            BigDecimal opening = BigDecimal.ZERO;
            List<JournalEntry> period = new ArrayList<>();
            for (JournalEntry e : a.getValue()) {
                if (e.getVoucher().getIssueDate().isBefore(f.from())) {
                    opening = opening.add(signed(e.getDebit().subtract(e.getCredit()), debitNature));
                } else {
                    period.add(e);
                }
            }
            if (opening.signum() == 0 && period.isEmpty()) continue;
            r.getRows().add(row("GROUP", Map.of("concept", code + " - " + nameOf(code, accounts))));
            r.getRows().add(row("DETAIL", Map.of("concept", "Saldo anterior", "balance", opening)));
            BigDecimal running = opening;
            for (JournalEntry e : period) {
                running = running.add(signed(e.getDebit().subtract(e.getCredit()), debitNature));
                r.getRows().add(entryRow(e, running));
            }
            r.getRows().add(row("SUBTOTAL", Map.of("concept", "Saldo " + nameOf(code, accounts).toLowerCase(Locale.ROOT), "balance", running)));
            if (code.startsWith("1")) {
                owed = owed.add(running);
            } else {
                inFavor = inFavor.add(running);
                r.getSummary().add(summary(capitalize(nameOf(code, accounts)), running, "positive"));
            }
        }
        r.getSummary().add(0, summary("Saldo a favor del asociado", inFavor, "primary"));
        r.getSummary().add(1, summary("Saldo por cobrar al asociado", owed, owed.signum() > 0 ? "negative" : "neutral"));
        r.getSummary().add(2, summary("Saldo neto", inFavor.subtract(owed), "neutral"));
    }

    // ================================================================== TABLERO

    /** Indicadores y series para el tablero de reportes. */
    public Map<String, Object> dashboard(LocalDate from, LocalDate to) {
        Map<String, Sums> balances = sums(BEGINNING, to, false);
        LocalDate yearStart = to.withDayOfYear(1);
        Map<String, Sums> year = sums(yearStart, to, true);
        Map<String, Object> out = new LinkedHashMap<>();

        BigDecimal cash = prefixNet(balances, "1105");
        BigDecimal banks = prefixNet(balances, "1110").add(prefixNet(balances, "1120"));
        BigDecimal investments = prefixNet(balances, "12");
        BigDecimal receivables = prefixNet(balances, "13");
        BigDecimal assets = prefixNet(balances, "1");
        BigDecimal liabilities = prefixNet(balances, "2").negate();
        BigDecimal income = prefixNet(year, "4").negate();
        BigDecimal expenses = prefixNet(year, "5").add(prefixNet(year, "6")).add(prefixNet(year, "7"));
        BigDecimal unclosed = sums(BEGINNING, yearStart.minusDays(1), false).entrySet().stream()
                .filter(e -> isResult(e.getKey())).map(e -> e.getValue().net()).reduce(BigDecimal.ZERO, BigDecimal::add).negate();
        BigDecimal yearResultWithClosing = sums(yearStart, to, false).entrySet().stream()
                .filter(e -> isResult(e.getKey())).map(e -> e.getValue().net()).reduce(BigDecimal.ZERO, BigDecimal::add).negate();
        BigDecimal equity = prefixNet(balances, "3").negate().add(unclosed).add(yearResultWithClosing);

        Map<String, BigDecimal> kpis = new LinkedHashMap<>();
        kpis.put("cash", cash);
        kpis.put("banks", banks);
        kpis.put("investments", investments);
        kpis.put("receivables", receivables);
        kpis.put("contributions", prefixNet(balances, "3115").negate());
        kpis.put("extraContributions", prefixNet(balances, AssociationChart.EXTRA_CONTRIBUTIONS).negate());
        kpis.put("solidarityFund", prefixNet(balances, AssociationChart.SOLIDARITY_FUND).negate());
        kpis.put("tourismFund", prefixNet(balances, AssociationChart.TOURISM_FUND).negate());
        kpis.put("assets", assets);
        kpis.put("liabilities", liabilities);
        kpis.put("equity", equity);
        kpis.put("income", income);
        kpis.put("expenses", expenses);
        kpis.put("result", income.subtract(expenses));
        out.put("kpis", kpis);

        // Serie mensual de los últimos 12 meses (o desde "from" si es más reciente)
        List<Map<String, Object>> monthly = new ArrayList<>();
        LocalDate start = to.withDayOfMonth(1).minusMonths(11);
        if (from != null && from.withDayOfMonth(1).isAfter(start)) start = from.withDayOfMonth(1);
        for (LocalDate m = start; !m.isAfter(to); m = m.plusMonths(1)) {
            LocalDate end = m.plusMonths(1).minusDays(1).isAfter(to) ? to : m.plusMonths(1).minusDays(1);
            Map<String, Sums> s = sums(m, end, true);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("month", m.toString().substring(0, 7));
            point.put("income", prefixNet(s, "4").negate());
            point.put("expenses", prefixNet(s, "5").add(prefixNet(s, "6")).add(prefixNet(s, "7")));
            point.put("collections", entryRepository.findPostedIncomeVoucherEntries(m, end).stream()
                    .filter(e -> e.getAccount().getCode().startsWith("11"))
                    .map(e -> e.getDebit().subtract(e.getCredit())).reduce(BigDecimal.ZERO, BigDecimal::add));
            monthly.add(point);
        }
        out.put("monthly", monthly);

        Map<String, Account> accounts = accountIndex();
        out.put("incomeComposition", composition(year, "4", accounts, true));
        out.put("expenseComposition", composition(year, "5", accounts, false));
        out.put("balanceComposition", List.of(
                Map.of("label", "Pasivos", "value", liabilities),
                Map.of("label", "Patrimonio", "value", equity)));
        return out;
    }

    private List<Map<String, Object>> composition(Map<String, Sums> sums, String classCode, Map<String, Account> accounts, boolean credit) {
        Map<String, BigDecimal> byAccount = new TreeMap<>();
        sums.forEach((code, s) -> {
            if (!code.startsWith(classCode)) return;
            String key = ancestorAtLevel(code, 4, accounts);
            byAccount.merge(key, credit ? s.net().negate() : s.net(), BigDecimal::add);
        });
        return byAccount.entrySet().stream().filter(e -> e.getValue().signum() != 0)
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(e -> Map.<String, Object>of("code", e.getKey(), "label", capitalize(nameOf(e.getKey(), accounts)), "value", e.getValue()))
                .toList();
    }

    // ================================================================== helpers

    Map<String, Sums> sums(LocalDate from, LocalDate to, boolean excludeClosing) {
        Map<String, Sums> map = new HashMap<>();
        if (to.isBefore(from)) return map;
        for (Object[] row : entryRepository.sumsByAccount(from, to, excludeClosing)) {
            map.put((String) row[0], new Sums(toBig(row[1]), toBig(row[2])));
        }
        return map;
    }

    /**
     * Ventanas por cuenta hoja: apertura (todo lo anterior a {@code from}; para cuentas de resultado solo desde
     * {@code resultStart}) y movimiento del periodo.
     */
    private Map<String, Window> windows(LocalDate from, LocalDate to, LocalDate resultStart, String prefix) {
        Map<String, Sums> before = sums(BEGINNING, from.minusDays(1), false);
        Map<String, Sums> beforeYear = sums(BEGINNING, resultStart.minusDays(1), false);
        Map<String, Sums> period = sums(from, to, false);
        Set<String> codes = new TreeSet<>(before.keySet());
        codes.addAll(period.keySet());
        Map<String, Window> out = new TreeMap<>();
        for (String code : codes) {
            if (prefix != null && !code.startsWith(prefix)) continue;
            Sums open = before.getOrDefault(code, Sums.ZERO);
            if (isResult(code)) open = open.minus(beforeYear.getOrDefault(code, Sums.ZERO));
            Sums mov = period.getOrDefault(code, Sums.ZERO);
            out.put(code, new Window(open.net(), mov.debit(), mov.credit()));
        }
        return out;
    }

    /** Ventanas por cuenta y tercero; {@code resultStart} null = apertura con toda la historia. */
    private Map<String, Map<UUID, Window>> partyWindows(String prefix, LocalDate from, LocalDate to, LocalDate resultStart) {
        Map<String, Map<UUID, Window>> out = new TreeMap<>();
        for (Object[] row : entryRepository.sumsByAccountAndParty(prefix, BEGINNING, from.minusDays(1))) {
            String code = (String) row[0];
            if (resultStart != null && isResult(code)) continue;
            out.computeIfAbsent(code, k -> new HashMap<>())
                    .merge((UUID) row[1], new Window(toBig(row[2]).subtract(toBig(row[3])), BigDecimal.ZERO, BigDecimal.ZERO), Window::plus);
        }
        if (resultStart != null && resultStart.isBefore(from)) {
            for (Object[] row : entryRepository.sumsByAccountAndParty(prefix, resultStart, from.minusDays(1))) {
                String code = (String) row[0];
                if (!isResult(code)) continue;
                out.computeIfAbsent(code, k -> new HashMap<>())
                        .merge((UUID) row[1], new Window(toBig(row[2]).subtract(toBig(row[3])), BigDecimal.ZERO, BigDecimal.ZERO), Window::plus);
            }
        }
        for (Object[] row : entryRepository.sumsByAccountAndParty(prefix, from, to)) {
            out.computeIfAbsent((String) row[0], k -> new HashMap<>())
                    .merge((UUID) row[1], new Window(BigDecimal.ZERO, toBig(row[2]), toBig(row[3])), Window::plus);
        }
        return out;
    }

    private Map<String, Account> accountIndex() {
        Map<String, Account> map = new HashMap<>();
        for (Account a : accountRepository.findAll()) {
            map.put(a.getCode(), a);
        }
        return map;
    }

    private Map<UUID, ThirdParty> partyIndex(Collection<UUID> ids) {
        Map<UUID, ThirdParty> map = new HashMap<>();
        List<UUID> nonNull = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (!nonNull.isEmpty()) {
            thirdPartyRepository.findAllById(nonNull).forEach(t -> map.put(t.getId(), t));
        }
        return map;
    }

    private static String partyName(ThirdParty t) {
        return t != null ? t.getFullName() : "SIN TERCERO ASIGNADO";
    }

    /** Códigos existentes que son prefijo del código (incluido él mismo), del más general al más específico. */
    private static List<String> selfAndAncestors(String code, Map<String, Account> accounts) {
        List<String> out = new ArrayList<>();
        for (int len = 1; len < code.length(); len++) {
            String p = code.substring(0, len);
            if (accounts.containsKey(p)) out.add(p);
        }
        out.add(code);
        return out;
    }

    /** Ancestro existente con el nivel indicado (o la propia cuenta si es más general). */
    private static String ancestorAtLevel(String code, int level, Map<String, Account> accounts) {
        String best = code;
        for (String c : selfAndAncestors(code, accounts)) {
            if (levelOf(c, accounts) <= level) best = c;
        }
        return best;
    }

    private static int levelOf(String code, Map<String, Account> accounts) {
        Account a = accounts.get(code);
        return a != null && a.getLevel() != null ? a.getLevel() : AssociationChart.levelOf(code);
    }

    /**
     * Los saldos se presentan según la naturaleza de la clase (como el informe en Excel): así las cuentas
     * correctoras, por ejemplo la depreciación acumulada, aparecen restando dentro de su grupo.
     */
    private static boolean isDebitNature(String code, Map<String, Account> accounts) {
        return AssociationChart.isDebitNature(code);
    }

    private static String nameOf(String code, Map<String, Account> accounts) {
        Account a = accounts.get(code);
        return a != null ? a.getName() : code;
    }

    private static boolean isResult(String code) {
        char c = code.charAt(0);
        return c == '4' || c == '5' || c == '6' || c == '7';
    }

    private static BigDecimal signed(BigDecimal debitMinusCredit, boolean debitNature) {
        return debitNature ? debitMinusCredit : debitMinusCredit.negate();
    }

    private static BigDecimal prefixNet(Map<String, Sums> sums, String prefix) {
        return sums.entrySet().stream().filter(e -> e.getKey().startsWith(prefix))
                .map(e -> e.getValue().net()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal toBig(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        return new BigDecimal(o.toString());
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return "";
        String lower = s.toLowerCase(Locale.of("es", "CO"));
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
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
