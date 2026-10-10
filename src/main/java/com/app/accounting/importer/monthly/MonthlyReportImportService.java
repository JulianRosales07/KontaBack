package com.app.accounting.importer.monthly;

import com.app.accounting.association.AccountProvisioningService;
import com.app.accounting.association.AssociationChart;
import com.app.accounting.association.CollectionConcept;
import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.importer.monthly.MonthlyReport.Line;
import com.app.accounting.importer.monthly.MonthlyReport.Section;
import com.app.accounting.importer.monthly.MonthlyReportImportResponse.ReconciliationLine;
import com.app.accounting.importer.monthly.MonthlyReportImportResponse.VoucherSummary;
import com.app.accounting.investment.Investment;
import com.app.accounting.investment.InvestmentRepository;
import com.app.accounting.party.entity.PersonType;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.party.repository.ThirdPartyRepository;
import com.app.accounting.puc.entity.Account;
import com.app.accounting.puc.repository.AccountRepository;
import com.app.accounting.report.dto.response.FinancialReportResponse;
import com.app.accounting.report.service.FinancialReportService;
import com.app.accounting.voucher.dto.request.JournalEntryRequest;
import com.app.accounting.voucher.dto.request.JournalVoucherRequest;
import com.app.accounting.voucher.entity.VoucherType;
import com.app.accounting.voucher.repository.JournalVoucherRepository;
import com.app.accounting.voucher.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;

import static com.app.accounting.importer.monthly.MonthlyReport.nz;

/**
 * Importa el informe mensual de la asociación (Excel) a la contabilidad:
 * <ol>
 *   <li>Comprobante de APERTURA al cierre del mes anterior con los saldos por asociado y lo acumulado del año.
 *       El saldo inicial de cada sección es "saldo final reportado − movimiento del mes", de modo que el sistema
 *       llega exactamente al saldo final del Excel; las diferencias con la columna "saldo anterior" se informan.</li>
 *   <li>Un comprobante de ingreso por recibo de la PLANILLA (caja/bancos según BAL,PRUEBA).</li>
 *   <li>Un comprobante de egreso por cada CE, distribuido según las columnas de débito de BAL,PRUEBA.</li>
 *   <li>Rendimientos y consignaciones bancarias, depreciación del mes y registro de inversiones.</li>
 * </ol>
 * Todo ocurre en una sola transacción. En modo simulación ({@code dryRun}) se ejecuta y concilia igual,
 * y al final se revierte.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyReportImportService {

    private final AccountProvisioningService provisioning;
    private final AccountRepository accountRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final VoucherService voucherService;
    private final JournalVoucherRepository voucherRepository;
    private final InvestmentRepository investmentRepository;
    private final FinancialReportService reportService;
    private final PlatformTransactionManager transactionManager;

    private static final Map<String, String> PLANILLA_ACCOUNTS = Map.ofEntries(
            Map.entry("APORTES", AssociationChart.CONTRIBUTIONS),
            Map.entry("A_EXTRA", AssociationChart.EXTRA_CONTRIBUTIONS),
            Map.entry("F_SOLIDARIDAD", AssociationChart.SOLIDARITY_FUND),
            Map.entry("C_ADMON", AssociationChart.ADMINISTRATION_FEE),
            Map.entry("INTERES", AssociationChart.LOAN_INTEREST),
            Map.entry("PREEXEQ", AssociationChart.THIRD_PARTY_FUNDS),
            Map.entry("TYR", AssociationChart.TOURISM_FUND),
            Map.entry("BINGO", AssociationChart.BINGO),
            Map.entry("FDE", AssociationChart.SPECIFIC_PURPOSE_FUND),
            Map.entry("MULTAS", AssociationChart.FINES),
            Map.entry("OTROS", AssociationChart.RENTS));

    /** Concepto de la planilla → columna de la planilla del sistema (para conciliar). */
    private static final Map<String, CollectionConcept> PLANILLA_COLUMNS = Map.ofEntries(
            Map.entry("APORTES", CollectionConcept.APORTES),
            Map.entry("A_EXTRA", CollectionConcept.APORTE_EXTRA),
            Map.entry("F_SOLIDARIDAD", CollectionConcept.FONDO_SOLIDARIDAD),
            Map.entry("C_ADMON", CollectionConcept.CUOTA_ADMINISTRACION),
            Map.entry("PRESTAMO", CollectionConcept.PRESTAMO),
            Map.entry("INTERES", CollectionConcept.INTERES),
            Map.entry("PREEXEQ", CollectionConcept.PREEXEQUIAL),
            Map.entry("TYR", CollectionConcept.TURISMO),
            Map.entry("BINGO", CollectionConcept.BINGO),
            Map.entry("FDE", CollectionConcept.FDE),
            Map.entry("MULTAS", CollectionConcept.MULTAS),
            Map.entry("OTROS", CollectionConcept.OTROS));

    public MonthlyReportImportResponse importReport(InputStream in, String fileName, boolean dryRun) {
        MonthlyReport report = MonthlyReportParser.parse(in);
        MonthlyReportImportResponse response = MonthlyReportImportResponse.builder()
                .fileName(fileName)
                .dryRun(dryRun)
                .organizationName(report.organizationName)
                .period(report.period != null ? report.period.toString() : null)
                .build();
        if (report.period == null) {
            response.getErrors().add("No se pudo identificar el mes del informe (se busca \"<MES> DE <AÑO>\" en los títulos)");
            response.setMessage("Importación no realizada");
            return response;
        }
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            tx.executeWithoutResult(status -> {
                new Run(report, response).execute();
                if (dryRun || !response.getErrors().isEmpty()) {
                    status.setRollbackOnly();
                }
            });
        } catch (ImportAborted e) {
            // los errores ya están en la respuesta
        } catch (BusinessRuleException e) {
            response.getErrors().add(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Error importando el informe mensual", e);
            response.getErrors().add("Error inesperado: " + e.getMessage());
        }
        boolean ok = response.getErrors().isEmpty();
        response.setApplied(ok && !dryRun);
        response.setMessage(!ok ? "Importación no realizada: revise los errores"
                : dryRun ? "Simulación completa: no se guardó nada. Conciliación con " + response.reconciliationFailures() + " diferencia(s)."
                : "Informe importado. Conciliación con " + response.reconciliationFailures() + " diferencia(s).");
        return response;
    }

    private static final class ImportAborted extends RuntimeException {
        ImportAborted() {
            super(null, null, false, false);
        }
    }

    /** Estado de una importación. */
    private final class Run {
        private final MonthlyReport r;
        private final MonthlyReportImportResponse out;
        private final LocalDate openingDate;
        private final LocalDate monthEnd;
        private final String tag;
        private final String monthLabel;
        private ThirdParty organization;
        private final List<ThirdParty> associates = new ArrayList<>();
        private final Map<Integer, ThirdParty> planillaParty = new HashMap<>();
        private String bankAccount;
        private final Map<UUID, BigDecimal> otherReceivables = new HashMap<>();
        private final List<JournalVoucherRequest> vouchers = new ArrayList<>();
        private BigDecimal plannedBankFromReceipts = BigDecimal.ZERO;

        Run(MonthlyReport r, MonthlyReportImportResponse out) {
            this.r = r;
            this.out = out;
            this.openingDate = r.period.atDay(1).minusDays(1);
            this.monthEnd = r.period.atEndOfMonth();
            this.tag = r.period.toString();
            this.monthLabel = r.period.getMonth().getDisplayName(TextStyle.FULL, Locale.of("es", "CO")) + " " + r.period.getYear();
        }

        void execute() {
            out.getAccountsCreated().addAll(provisioning.ensureAll());
            bankAccount = ensureBankAccount();
            organization = ensureOrganization();
            resolveAssociates();
            if (!out.getErrors().isEmpty()) throw new ImportAborted();

            vouchers.add(opening());
            vouchers.addAll(receipts());
            vouchers.addAll(disbursements());
            vouchers.addAll(bankMovements());
            JournalVoucherRequest depreciation = depreciation();
            if (depreciation != null) vouchers.add(depreciation);
            if (!out.getErrors().isEmpty()) throw new ImportAborted();

            List<String> existing = vouchers.stream().map(JournalVoucherRequest::getVoucherNumber)
                    .filter(voucherRepository::existsByVoucherNumber).toList();
            if (!existing.isEmpty()) {
                out.getErrors().add("Este informe ya fue importado: existen " + existing.size() + " de " + vouchers.size()
                        + " comprobantes (por ejemplo " + String.join(", ", existing.subList(0, Math.min(3, existing.size())))
                        + "). Para reimportarlo, anule primero esos comprobantes.");
            }
            if (!out.getErrors().isEmpty()) throw new ImportAborted();

            for (JournalVoucherRequest v : vouchers) {
                voucherService.createAndPostVoucher(v);
                BigDecimal total = v.getEntries().stream().map(JournalEntryRequest::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
                out.getVouchers().add(new VoucherSummary(v.getVoucherNumber(), v.getVoucherType().name(), v.getIssueDate(),
                        v.getDescription(), total, v.getEntries().size()));
            }
            registerInvestments();
            reconcile();
        }

        // ============================================================ terceros y cuentas

        private String ensureBankAccount() {
            String name = r.bankName != null ? r.bankName.trim() : "BANCO";
            String normalized = NameMatcher.normalize(name);
            for (Account a : accountRepository.findAll()) {
                if (a.getCode().startsWith(AssociationChart.BANKS) && a.getCode().length() == 8
                        && NameMatcher.normalize(a.getName()).equals(normalized)) {
                    return a.getCode();
                }
            }
            for (int i = 1; i < 100; i++) {
                String code = AssociationChart.BANKS + String.format("%02d", i);
                if (!accountRepository.existsByCode(code)) {
                    provisioning.ensure(code, name.length() > 150 ? name.substring(0, 150) : name, out.getAccountsCreated());
                    return code;
                }
            }
            throw new BusinessRuleException("No hay códigos auxiliares libres bajo " + AssociationChart.BANKS);
        }

        private ThirdParty ensureOrganization() {
            String nit = r.organizationNit != null ? r.organizationNit.replaceAll("[^0-9-]", "") : "";
            String id = nit.contains("-") ? nit.substring(0, nit.indexOf('-')) : nit;
            String dv = nit.contains("-") ? nit.substring(nit.indexOf('-') + 1) : null;
            if (id.isBlank()) id = "ORG-" + shortHash(r.organizationName);
            String finalId = id;
            return thirdPartyRepository.findByIdentificationNumber(id).orElseGet(() -> thirdPartyRepository.save(ThirdParty.builder()
                    .identificationNumber(finalId)
                    .dv(dv)
                    .fullName(r.organizationName != null ? r.organizationName : "ASOCIACION")
                    .type(PersonType.JURIDICA)
                    .idType("NIT")
                    .classification("Entidad")
                    .build()));
        }

        private void resolveAssociates() {
            List<ThirdParty> existing = thirdPartyRepository.findAll().stream().filter(ThirdParty::isAssociate).toList();
            for (MonthlyReport.PlanillaRow row : r.planilla) {
                ThirdParty match = NameMatcher.best(row.name(), existing, ThirdParty::getFullName);
                if (match == null) {
                    String id = "PEND-" + shortHash(NameMatcher.normalize(row.name()));
                    match = thirdPartyRepository.findByIdentificationNumber(id).orElse(null);
                }
                if (match == null) {
                    match = thirdPartyRepository.save(ThirdParty.builder()
                            .identificationNumber("PEND-" + shortHash(NameMatcher.normalize(row.name())))
                            .fullName(row.name().trim().replaceAll("\\s+", " "))
                            .type(PersonType.NATURAL)
                            .idType("PENDIENTE")
                            .classification("Asociado")
                            .associate(true)
                            .build());
                    out.getAssociatesCreated().add(match.getFullName());
                } else {
                    out.setAssociatesMatched(out.getAssociatesMatched() + 1);
                }
                planillaParty.put(row.excelRow(), match);
                if (!associates.contains(match)) associates.add(match);
            }
            if (!out.getAssociatesCreated().isEmpty()) {
                out.getWarnings().add(out.getAssociatesCreated().size() + " asociado(s) se crearon sin documento (identificación PEND-…). "
                        + "Actualice su cédula en Terceros.");
            }
        }

        /** Asociado de una fila de cualquier hoja (los nombres varían entre hojas). */
        private ThirdParty party(String label, String context) {
            String name = label.replaceAll("(?i)^(PRE)?E?XEQUIALES\\s+ASOCIAD[AO]\\s+", "").replaceAll("(?i)^PREEXEQUIALES\\s+", "");
            ThirdParty match = NameMatcher.best(name, associates, ThirdParty::getFullName);
            if (match == null) {
                out.getErrors().add(context + ": no se pudo identificar al asociado \"" + label + "\" entre los de la PLANILLA");
            }
            return match;
        }

        // ============================================================ apertura

        private JournalVoucherRequest opening() {
            Lines l = new Lines();
            String note = "Saldo inicial importado del informe de " + monthLabel;
            l.debit(AssociationChart.CASH, organization, "Caja · " + note, nz(r.cashOpening.c()));
            l.debit(bankAccount, organization, r.bankName + " · " + note, nz(r.bankOpening.c()));

            // Inversiones: capital e intereses causados del CDT
            BigDecimal investments = BigDecimal.ZERO;
            for (Line line : r.investmentRows) {
                l.debit(AssociationChart.INVESTMENTS_CDT, organization, line.label() + " · " + note, nz(line.c()));
                investments = investments.add(nz(line.c()));
            }
            check("Inversiones (saldo anterior)", r.investmentsTotal != null ? r.investmentsTotal.c() : null, investments);

            // Cuentas por cobrar por asociado (préstamos y preexequiales)
            BigDecimal receivables = BigDecimal.ZERO;
            for (Line line : r.receivables.rows) {
                if (nz(line.c()).signum() == 0) continue;
                ThirdParty p = party(line.label(), "CUENTAS POR COBRAR fila " + line.excelRow());
                boolean other = isPreexequialLabel(line.label());
                l.debit(other ? AssociationChart.OTHER_RECEIVABLES : AssociationChart.LOANS_TO_ASSOCIATES, p,
                        (other ? "Preexequial por cobrar" : "Saldo préstamo") + " · " + note, nz(line.c()));
                if (other && p != null) otherReceivables.merge(p.getId(), nz(line.c()), BigDecimal::add);
                receivables = receivables.add(nz(line.c()));
            }
            sectionOpening(l, "CUENTAS POR COBRAR", r.receivables, receivables, true, AssociationChart.LOANS_TO_ASSOCIATES);

            // Propiedad, planta y equipo
            Line buildingsAndLand = ppeRow("EDIFICACION");
            Line furniture = ppeRow("MUEBLES");
            Line depreciation = ppeRow("DEPRECIACION");
            BigDecimal land = nz(r.esfaLand);
            BigDecimal buildingCost = buildingsAndLand != null ? nz(buildingsAndLand.c()) : BigDecimal.ZERO;
            if (land.signum() > 0 && land.compareTo(buildingCost) < 0) {
                l.debit(AssociationChart.LAND, organization, "Terrenos · " + note, land);
                l.debit(AssociationChart.BUILDINGS, organization, "Edificaciones · " + note, buildingCost.subtract(land));
            } else {
                l.debit(AssociationChart.BUILDINGS, organization, "Edificaciones · " + note, buildingCost);
                if (buildingCost.signum() > 0) out.getWarnings().add("ESFA no separa terrenos de edificaciones: todo se registró en " + AssociationChart.BUILDINGS);
            }
            if (furniture != null) l.debit(AssociationChart.OFFICE_EQUIPMENT, organization, "Muebles y equipo de oficina · " + note, nz(furniture.c()));
            if (depreciation != null) {
                BigDecimal[] split = depreciationSplit(nz(depreciation.c()).abs());
                l.credit(AssociationChart.DEPRECIATION_BUILDINGS, organization, "Depreciación acumulada edificaciones · " + note, split[0]);
                l.credit(AssociationChart.DEPRECIATION_OFFICE_EQUIPMENT, organization, "Depreciación acumulada muebles y equipo · " + note, split[1]);
            }

            // Pasivos por asociado
            payablesOpening(l, note);
            partySectionOpening(l, "APORTES EXTRAS", r.extraContributions, AssociationChart.EXTRA_CONTRIBUTIONS, note);
            partySectionOpening(l, "TURISMO Y RECREACION", r.tourism, AssociationChart.TOURISM_FUND, note);
            partySectionOpening(l, "PRE-EXEQUIALES", r.preexequiales, AssociationChart.THIRD_PARTY_FUNDS, note);
            partySectionOpening(l, "FONDO DE SOLIDARIDAD", r.solidarity, AssociationChart.SOLIDARITY_FUND, note);

            // Patrimonio
            partySectionOpening(l, "APORTES SOCIALES", r.contributions, AssociationChart.CONTRIBUTIONS, note);
            for (Line v : r.valuations) {
                String n = NameMatcher.normalize(v.label());
                String code = n.contains("MUEBLE") ? AssociationChart.VALUATION_OFFICE_EQUIPMENT : AssociationChart.VALUATION_BUILDINGS;
                l.credit(code, organization, v.label() + " · " + note, nz(v.c()));
            }
            if (r.retainedEarnings != null) {
                l.credit(AssociationChart.RETAINED_EARNINGS, organization, "Excedentes de ejercicios anteriores · " + note, nz(r.retainedEarnings.c()));
            }

            // Resultados acumulados del año hasta el mes anterior
            for (Line line : r.incomeRows) {
                String code = incomeAccount(line.label());
                if (code == null) {
                    out.getErrors().add("BAL,PRUEBA fila " + line.excelRow() + ": ingreso sin cuenta asignada \"" + line.label() + "\"");
                    continue;
                }
                l.credit(code, organization, line.label() + " (acumulado a " + openingDate + ")", nz(line.c()));
            }
            for (Line line : r.expenseRows) {
                if (isDepreciationLabel(line.label())) {
                    BigDecimal[] split = depreciationSplit(nz(line.c()));
                    l.debit(AssociationChart.DEPRECIATION_EXPENSE_BUILDINGS, organization, "Depreciación edificaciones (acumulado a " + openingDate + ")", split[0]);
                    l.debit(AssociationChart.DEPRECIATION_EXPENSE_OFFICE_EQUIPMENT, organization, "Depreciación muebles y equipo (acumulado a " + openingDate + ")", split[1]);
                    continue;
                }
                String code = expenseAccount(line.label());
                if (code == null) {
                    out.getErrors().add("BAL,PRUEBA fila " + line.excelRow() + ": gasto sin cuenta asignada \"" + line.label() + "\"");
                    continue;
                }
                l.debit(code, organization, line.label() + " (acumulado a " + openingDate + ")", nz(line.c()));
            }

            BigDecimal diff = l.debits().subtract(l.credits());
            if (diff.signum() != 0) {
                out.getErrors().add("La apertura no cuadra: débitos " + l.debits().toPlainString() + " vs créditos " + l.credits().toPlainString()
                        + " (diferencia " + diff.toPlainString() + "). Revise los saldos anteriores del informe.");
            }
            return voucher("APERTURA-" + tag, VoucherType.APERTURA, openingDate, "Saldos iniciales al " + openingDate + " · informe de " + monthLabel, l);
        }

        /** Sección por asociado: saldos anteriores por fila y ajuste de cuadre contra "saldo final − movimiento". */
        private void partySectionOpening(Lines l, String name, Section s, String account, String note) {
            BigDecimal sum = BigDecimal.ZERO;
            for (Line line : s.rows) {
                if (nz(line.c()).signum() == 0) continue;
                ThirdParty p = party(line.label(), name + " fila " + line.excelRow());
                l.credit(account, p, "Saldo " + name.toLowerCase(Locale.ROOT) + " · " + note, nz(line.c()));
                sum = sum.add(nz(line.c()));
            }
            sectionOpening(l, name, s, sum, false, account);
        }

        /**
         * Saldo inicial de la sección = saldo final reportado − movimiento del mes. Si la suma por asociado no llega
         * a ese valor, la diferencia se registra a nombre de la asociación y se informa.
         */
        private void sectionOpening(Lines l, String name, Section s, BigDecimal partySum, boolean debitNature, String account) {
            if (s.totals == null) return;
            Line t = s.totals;
            BigDecimal movement = debitNature ? nz(t.d()).subtract(nz(t.e())) : nz(t.e()).subtract(nz(t.d()));
            BigDecimal expected = t.f() != null ? t.f().subtract(movement) : nz(t.c());
            if (t.c() != null && expected.compareTo(t.c()) != 0) {
                out.getWarnings().add(name + ": el saldo anterior del Excel (" + money(t.c()) + ") no coincide con saldo final − movimiento ("
                        + money(expected) + "). Se usa " + money(expected) + " para llegar al saldo final reportado ("
                        + money(t.f()) + "). Diferencia " + money(expected.subtract(t.c())) + ".");
            }
            BigDecimal gap = expected.subtract(partySum);
            if (gap.signum() != 0) {
                String desc = partySum.signum() == 0
                        ? "Saldo " + name.toLowerCase(Locale.ROOT) + " (el informe no lo detalla por asociado)"
                        : "Ajuste de cuadre " + name.toLowerCase(Locale.ROOT) + " (diferencia entre el total y la suma por asociado)";
                if (debitNature) l.debit(account, organization, desc, gap);
                else l.credit(account, organization, desc, gap);
                if (partySum.signum() != 0) {
                    out.getWarnings().add(name + ": la suma por asociado (" + money(partySum) + ") difiere del total (" + money(expected)
                            + "); la diferencia " + money(gap) + " quedó a nombre de la asociación.");
                }
            }
        }

        private void payablesOpening(Lines l, String note) {
            Line t = r.payables.totals;
            if (t == null) return;
            BigDecimal movement = r.payables.rows.stream().map(x -> nz(x.e()).subtract(nz(x.d()))).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal opening = nz(t.f()).subtract(movement);
            l.credit(AssociationChart.ADVANCE_INCOME, organization, "Cuentas por pagar / ingresos recibidos por anticipado · " + note, opening);
        }

        // ============================================================ recaudos (PLANILLA)

        private List<JournalVoucherRequest> receipts() {
            List<JournalVoucherRequest> list = new ArrayList<>();
            Map<String, Line> cashByRef = new HashMap<>();
            for (Line line : r.cashRows) {
                if (line.ref() != null && line.ref().toUpperCase(Locale.ROOT).startsWith("RB")) cashByRef.put(line.ref().trim(), line);
            }
            int seq = 0;
            for (MonthlyReport.PlanillaRow row : r.planilla) {
                if (row.total().signum() == 0) continue;
                seq++;
                ThirdParty p = planillaParty.get(row.excelRow());
                String number = row.receipt() != null ? row.receipt() : "RB-" + tag + "-" + seq;
                Line cashLine = row.receipt() != null ? cashByRef.get(row.receipt()) : null;
                if (cashLine == null) {
                    cashLine = r.cashRows.stream().filter(x -> NameMatcher.normalize(x.label()).equals(NameMatcher.normalize(row.name())))
                            .findFirst().orElse(null);
                }
                BigDecimal cash = cashLine != null && cashLine.d() != null ? cashLine.d() : row.total();
                if (cashLine == null) {
                    out.getWarnings().add("Recibo " + number + " (" + row.name() + "): no aparece en la caja de BAL,PRUEBA; se registró como efectivo.");
                }
                BigDecimal bank = row.total().subtract(cash);
                if (bank.signum() < 0) {
                    out.getErrors().add("Recibo " + number + ": lo registrado en caja (" + money(cash) + ") supera el total de la planilla (" + money(row.total()) + ")");
                    continue;
                }
                plannedBankFromReceipts = plannedBankFromReceipts.add(bank);

                Lines l = new Lines();
                l.debit(AssociationChart.CASH, p, "Recaudo en efectivo " + number, cash);
                l.debit(bankAccount, p, "Recaudo consignado " + number, bank);
                for (Map.Entry<String, BigDecimal> v : row.values().entrySet()) {
                    String concept = v.getKey();
                    BigDecimal amount = v.getValue();
                    if ("PRESTAMO".equals(concept)) {
                        BigDecimal other = otherReceivables.getOrDefault(p.getId(), BigDecimal.ZERO).min(amount).max(BigDecimal.ZERO);
                        if (other.signum() > 0) {
                            l.credit(AssociationChart.OTHER_RECEIVABLES, p, "Abono preexequial por cobrar", other);
                            otherReceivables.put(p.getId(), otherReceivables.get(p.getId()).subtract(other));
                        }
                        l.credit(AssociationChart.LOANS_TO_ASSOCIATES, p, "Abono a capital del préstamo", amount.subtract(other));
                    } else {
                        l.credit(PLANILLA_ACCOUNTS.get(concept), p, conceptLabel(concept), amount);
                    }
                }
                if (l.debits().compareTo(l.credits()) != 0) {
                    out.getErrors().add("PLANILLA fila " + row.excelRow() + " (" + row.name() + "): la suma de conceptos no coincide con el total");
                    continue;
                }
                list.add(voucher(number, VoucherType.INGRESO, monthEnd, "Recaudo " + row.name() + " · informe de " + monthLabel, l));
            }
            return list;
        }

        // ============================================================ egresos (CE)

        /** Movimiento débito del mes en BAL,PRUEBA pendiente de asignar a un comprobante de egreso. */
        private final class Claim {
            final String section;
            final String account;
            final ThirdParty party;
            final String matchText;
            final String ref;
            final String description;
            BigDecimal open;

            Claim(String section, String account, ThirdParty party, String matchText, String ref, String description, BigDecimal amount) {
                this.section = section;
                this.account = account;
                this.party = party;
                this.matchText = matchText;
                this.ref = ref;
                this.description = description;
                this.open = amount;
            }
        }

        private List<JournalVoucherRequest> disbursements() {
            List<Claim> claims = new ArrayList<>();
            for (Line line : r.expenseRows) {
                if (nz(line.d()).signum() == 0 || isDepreciationLabel(line.label())) continue;
                String code = expenseAccount(line.label());
                if (code == null) {
                    out.getErrors().add("BAL,PRUEBA fila " + line.excelRow() + ": gasto sin cuenta asignada \"" + line.label() + "\"");
                    continue;
                }
                claims.add(new Claim("GASTOS", code, null, line.label(), line.ref(), line.label(), line.d()));
            }
            partyClaims(claims, r.receivables, "CXC", null, "Desembolso / cargo");
            partyClaims(claims, r.extraContributions, "APORTES_EXTRAS", AssociationChart.EXTRA_CONTRIBUTIONS, "Retiro de aportes extraordinarios");
            partyClaims(claims, r.tourism, "TURISMO", AssociationChart.TOURISM_FUND, "Uso / devolución fondo de turismo y recreación");
            partyClaims(claims, r.preexequiales, "PREEXEQ", AssociationChart.THIRD_PARTY_FUNDS, "Pago preexequial a la funeraria");
            partyClaims(claims, r.solidarity, "SOLIDARIDAD", AssociationChart.SOLIDARITY_FUND, "Auxilio del fondo de solidaridad");
            partyClaims(claims, r.contributions, "APORTES", AssociationChart.CONTRIBUTIONS, "Devolución de aportes sociales");

            List<JournalVoucherRequest> list = new ArrayList<>();
            List<Line> ces = new ArrayList<>();
            r.cashRows.stream().filter(x -> nz(x.e()).signum() > 0).forEach(ces::add);
            r.bankRows.stream().filter(x -> nz(x.e()).signum() > 0).forEach(ces::add);
            for (Line ce : ces) {
                boolean fromBank = r.bankRows.contains(ce);
                String number = ce.ref() != null ? ce.ref() : "CE-" + tag + "-" + ce.excelRow();
                BigDecimal remaining = ce.e();
                Lines l = new Lines();
                // a) mismo número de CE
                for (Claim c : claims) {
                    if (remaining.signum() == 0) break;
                    if (c.open.signum() > 0 && c.ref != null && c.ref.equalsIgnoreCase(number)) {
                        remaining = take(l, c, remaining);
                    }
                }
                // b) el nombre del asociado o del concepto aparece en la descripción del CE
                if (remaining.signum() > 0) {
                    List<Claim> byName = claims.stream().filter(c -> c.open.signum() > 0 && NameMatcher.commonTokens(ce.label(), c.matchText) >= 2)
                            .sorted(Comparator.comparingInt((Claim c) -> -NameMatcher.commonTokens(ce.label(), c.matchText))).toList();
                    for (Claim c : byName) {
                        if (remaining.signum() == 0) break;
                        if (c.ref != null && !c.ref.equalsIgnoreCase(number)) {
                            out.getWarnings().add(number + " (" + ce.label() + ") se registró en la cuenta de la línea del Excel donde está incluido: \""
                                    + c.description + "\" (" + c.ref + ").");
                        }
                        remaining = take(l, c, remaining);
                    }
                }
                // c) una sección cuyos movimientos pendientes suman exactamente lo que falta (p. ej. pago de preexequiales)
                if (remaining.signum() > 0) {
                    Map<String, BigDecimal> bySection = new LinkedHashMap<>();
                    claims.stream().filter(c -> c.open.signum() > 0).forEach(c -> bySection.merge(c.section, c.open, BigDecimal::add));
                    for (Map.Entry<String, BigDecimal> s : bySection.entrySet()) {
                        if (s.getValue().compareTo(remaining) == 0) {
                            for (Claim c : claims) {
                                if (c.section.equals(s.getKey()) && c.open.signum() > 0) remaining = take(l, c, remaining);
                            }
                            break;
                        }
                    }
                }
                if (remaining.signum() > 0) {
                    out.getErrors().add("No se pudo distribuir " + money(remaining) + " del egreso " + number + " (" + ce.label()
                            + "): no hay movimientos débito en BAL,PRUEBA que lo expliquen");
                    continue;
                }
                l.credit(fromBank ? bankAccount : AssociationChart.CASH, organization, ce.label(), ce.e());
                list.add(voucher(number, VoucherType.EGRESO, monthEnd, ce.label() + " · informe de " + monthLabel, l));
            }
            for (Claim c : claims) {
                if (c.open.signum() > 0) {
                    out.getErrors().add("Movimiento sin comprobante de egreso: " + c.description + " por " + money(c.open)
                            + (c.party != null ? " (" + c.party.getFullName() + ")" : ""));
                }
            }
            return list;
        }

        private void partyClaims(List<Claim> claims, Section s, String section, String account, String description) {
            for (Line line : s.rows) {
                if (nz(line.d()).signum() == 0) continue;
                ThirdParty p = party(line.label(), section + " fila " + line.excelRow());
                if (p == null) continue;
                String code = account != null ? account
                        : isPreexequialLabel(line.label()) ? AssociationChart.OTHER_RECEIVABLES : AssociationChart.LOANS_TO_ASSOCIATES;
                claims.add(new Claim(section, code, p, p.getFullName(), line.ref(), description, line.d()));
            }
        }

        private BigDecimal take(Lines l, Claim c, BigDecimal remaining) {
            BigDecimal amount = c.open.min(remaining);
            l.debit(c.account, c.party != null ? c.party : organization, c.description, amount);
            c.open = c.open.subtract(amount);
            return remaining.subtract(amount);
        }

        // ============================================================ bancos y depreciación

        private List<JournalVoucherRequest> bankMovements() {
            List<JournalVoucherRequest> list = new ArrayList<>();
            BigDecimal others = BigDecimal.ZERO;
            BigDecimal unidentified = BigDecimal.ZERO;
            int n = 0;
            for (Line line : r.bankRows) {
                BigDecimal amount = nz(line.d());
                if (amount.signum() == 0) continue;
                String label = NameMatcher.normalize(line.label());
                if (label.contains("FINANCIER") || label.contains("RENDIMIENTO")) {
                    Lines l = new Lines();
                    l.debit(bankAccount, organization, line.label(), amount);
                    l.credit(AssociationChart.BANK_YIELDS, organization, line.label(), amount);
                    list.add(voucher("NB-" + tag + "-REND" + (++n > 1 ? "-" + n : ""), VoucherType.INGRESO, monthEnd,
                            "Rendimientos financieros " + r.bankName + " · informe de " + monthLabel, l));
                } else if (label.contains("SIN IDENTIFICAR")) {
                    String desc = "Consignación sin identificar" + (r.payablesDescription != null ? " (" + r.payablesDescription + ")" : "");
                    Lines l = new Lines();
                    l.debit(bankAccount, organization, desc, amount);
                    l.credit(AssociationChart.ADVANCE_INCOME, organization, desc, amount);
                    list.add(voucher("NB-" + tag + "-CSG" + (++n > 1 ? "-" + n : ""), VoucherType.INGRESO, monthEnd, desc + " · informe de " + monthLabel, l));
                    unidentified = unidentified.add(amount);
                } else {
                    others = others.add(amount);
                }
            }
            if (others.compareTo(plannedBankFromReceipts) != 0) {
                out.getWarnings().add("Bancos: las consignaciones de asociados del Excel suman " + money(others)
                        + " y la parte no recibida en caja de los recibos suma " + money(plannedBankFromReceipts) + ".");
            }
            BigDecimal payablesMovement = r.payables.rows.stream().map(x -> nz(x.e())).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (payablesMovement.compareTo(unidentified) != 0) {
                out.getWarnings().add("Cuentas por pagar: el Excel registra " + money(payablesMovement) + " en el mes y la consignación sin identificar es "
                        + money(unidentified) + ".");
            }
            return list;
        }

        private JournalVoucherRequest depreciation() {
            Line dep = ppeRow("DEPRECIACION");
            if (dep == null || nz(dep.d()).signum() == 0) return null;
            BigDecimal month = dep.d().abs();
            BigDecimal[] split = depreciationSplit(month);
            Lines l = new Lines();
            l.debit(AssociationChart.DEPRECIATION_EXPENSE_BUILDINGS, organization, "Depreciación edificaciones " + monthLabel, split[0]);
            l.debit(AssociationChart.DEPRECIATION_EXPENSE_OFFICE_EQUIPMENT, organization, "Depreciación muebles y equipo " + monthLabel, split[1]);
            l.credit(AssociationChart.DEPRECIATION_BUILDINGS, organization, "Depreciación edificaciones " + monthLabel, split[0]);
            l.credit(AssociationChart.DEPRECIATION_OFFICE_EQUIPMENT, organization, "Depreciación muebles y equipo " + monthLabel, split[1]);
            BigDecimal expense = r.expenseRows.stream().filter(x -> isDepreciationLabel(x.label())).map(x -> nz(x.d()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            check("Depreciación del mes (activo vs gasto)", expense, month);
            return voucher("NC-" + tag + "-DEP", VoucherType.NOTA_CONTABLE, monthEnd, "Depreciación del mes · informe de " + monthLabel, l);
        }

        /**
         * Reparte un valor de depreciación entre edificaciones y muebles con la proporción de la depreciación
         * acumulada del ESFA; sin ESFA, todo va a edificaciones.
         */
        private BigDecimal[] depreciationSplit(BigDecimal total) {
            BigDecimal b = nz(r.esfaBuildingsDepreciation).abs();
            BigDecimal f = nz(r.esfaOfficeEquipmentDepreciation).abs();
            if (b.add(f).signum() == 0) return new BigDecimal[]{total, BigDecimal.ZERO};
            BigDecimal buildings = total.multiply(b).divide(b.add(f), 0, RoundingMode.HALF_UP);
            return new BigDecimal[]{buildings, total.subtract(buildings)};
        }

        private Line ppeRow(String startsWith) {
            return r.ppeRows.stream().filter(x -> NameMatcher.normalize(x.label()).startsWith(startsWith)).findFirst().orElse(null);
        }

        // ============================================================ inversiones

        private void registerInvestments() {
            Line principal = null;
            for (Line line : r.investmentRows) {
                String n = NameMatcher.normalize(line.label());
                if (n.startsWith("INTERES") && principal != null) {
                    saveInvestment(principal, nz(line.c()));
                    principal = null;
                } else {
                    if (principal != null) saveInvestment(principal, BigDecimal.ZERO);
                    principal = line;
                }
            }
            if (principal != null) saveInvestment(principal, BigDecimal.ZERO);
        }

        private void saveInvestment(Line line, BigDecimal interest) {
            String n = line.label().trim();
            String type = NameMatcher.normalize(n).startsWith("CDT") ? "CDT" : "OTRA";
            String entity = n.replaceFirst("(?i)^CDT\\s*", "").trim();
            if (investmentRepository.existsByEntityNameIgnoreCaseAndInstrumentType(entity, type)) return;
            investmentRepository.save(Investment.builder()
                    .entityName(entity)
                    .instrumentType(type)
                    .account(provisioning.ensure(AssociationChart.INVESTMENTS_CDT, null))
                    .principal(nz(line.c()))
                    .accruedInterest(interest)
                    .notes("Registrada desde el informe de " + monthLabel + ". Complete número, tasa y fechas.")
                    .build());
            out.getWarnings().add("Inversión " + type + " " + entity + " registrada sin número, tasa ni fechas: complételos en Inversiones.");
        }

        // ============================================================ conciliación

        private void reconcile() {
            LocalDate from = r.period.atDay(1);
            if (r.cashMovement != null && r.cashMovement.g() != null && r.cashMovement.f() != null
                    && r.cashMovement.g().compareTo(r.cashMovement.f()) != 0) {
                out.getWarnings().add("Caja: el arqueo físico del Excel (" + money(r.cashMovement.g()) + ") difiere del saldo en libros ("
                        + money(r.cashMovement.f()) + ") en " + money(r.cashMovement.f().subtract(r.cashMovement.g()))
                        + ". El sistema registra el saldo en libros; la diferencia requiere revisión.");
            }
            FinancialReportResponse esfa = report("ESTADO_SITUACION", from);
            compare(esfa, "ESFA", "Total activos", "Total activos", r.totalAssets);
            compare(esfa, "ESFA", "Total pasivos", "Total pasivos", r.totalLiabilities);
            compare(esfa, "ESFA", "Total patrimonio", "Total patrimonio", r.totalEquity);
            compare(esfa, "ESFA", "Excedente del ejercicio", summaryStartingWith(esfa, "Excedente del ejercicio", "Déficit del ejercicio"), r.resultLine);

            FinancialReportResponse er = report("ESTADO_RESULTADOS", from);
            compare(er, "ESTADO RESULTADOS", "Ingresos acumulados", "Ingresos acumulados", r.incomeTotal);
            compare(er, "ESTADO RESULTADOS", "Gastos acumulados", "Gastos y costos acumulados", r.expenseTotal);

            compare(report("CAJA", from), "BAL,PRUEBA", "Caja: saldo final", "Saldo final", r.cashMovement);
            compare(report("BANCOS", from), "BAL,PRUEBA", "Bancos: saldo final", "Saldo final", r.bankOpening);
            compare(report("INVERSIONES", from), "BAL,PRUEBA", "Inversiones: saldo contable", "Saldo contable grupo 12", r.investmentsTotal);
            compare(report("PROPIEDAD_PLANTA", from), "ESFA", "Propiedad, planta y equipo neto", "Valor neto en libros", r.ppeTotal);
            compareLedger("CXC_ASOCIADOS", "CUENTAS POR C", r.receivables, from, true);
            compareLedger("APORTES_SOCIALES", "APORTES", r.contributions, from, false);
            compareLedger("APORTES_EXTRAS", "APORTES EXTRAS", r.extraContributions, from, false);
            compareLedger("TURISMO_RECREACION", "TURISMO Y R", r.tourism, from, false);
            compareLedger("FONDO_SOLIDARIDAD", "BAL,PRUEBA", r.solidarity, from, false);
            compareLedger("PREEXEQUIALES", "BAL,PRUEBA", r.preexequiales, from, false);

            FinancialReportResponse planilla = report("PLANILLA_RECAUDOS", from);
            add("PLANILLA_RECAUDOS", "PLANILLA", "Total recaudado", r.planillaGrandTotal, planilla.getTotals().get("total"));
            for (Map.Entry<String, BigDecimal> t : r.planillaTotals.entrySet()) {
                CollectionConcept c = PLANILLA_COLUMNS.get(t.getKey());
                add("PLANILLA_RECAUDOS", "PLANILLA", "Columna " + conceptLabel(t.getKey()), t.getValue(),
                        c != null ? planilla.getTotals().getOrDefault(c.key(), BigDecimal.ZERO) : null);
            }
        }

        private FinancialReportResponse report(String code, LocalDate from) {
            String method = "CAJA".equals(code) ? "CASH" : null;
            return reportService.generate(code, new FinancialReportService.Filters(from, monthEnd, null, null, null, method));
        }

        private void compare(FinancialReportResponse report, String sheet, String concept, String summaryLabel, Line excelLine) {
            if (excelLine == null || excelLine.f() == null) return;
            BigDecimal system = report.getSummary().stream().filter(s -> s.getLabel().equals(summaryLabel))
                    .map(FinancialReportResponse.SummaryItem::getValue).findFirst().orElse(null);
            add(report.getCode(), sheet, concept, excelLine.f(), system);
        }

        private String summaryStartingWith(FinancialReportResponse report, String... labels) {
            return report.getSummary().stream().map(FinancialReportResponse.SummaryItem::getLabel)
                    .filter(l -> Arrays.asList(labels).contains(l)).findFirst().orElse(labels[0]);
        }

        /** Total del auxiliar y, por asociado, saldo final del sistema contra la columna F del Excel. */
        private void compareLedger(String code, String sheet, Section s, LocalDate from, boolean debitNature) {
            if (s.totals == null) return;
            FinancialReportResponse ledger = report(code, from);
            add(code, sheet, "Saldo final total", s.totals.f(), ledger.getTotals().get("closing"));
            Map<String, BigDecimal> system = new HashMap<>();
            for (FinancialReportResponse.Row row : ledger.getRows()) {
                Object party = row.getCells().get("party");
                Object closing = row.getCells().get("closing");
                if ("DETAIL".equals(row.getRowType()) && party != null && closing instanceof BigDecimal b) {
                    system.merge(NameMatcher.normalize(party.toString()), b, BigDecimal::add);
                }
            }
            Map<String, BigDecimal> excel = new HashMap<>();
            for (Line line : s.rows) {
                if (line.f() == null) continue;
                ThirdParty p = NameMatcher.best(line.label().replaceAll("(?i)^(PRE)?E?XEQUIALES\\s+ASOCIAD[AO]\\s+", ""), associates, ThirdParty::getFullName);
                if (p != null) excel.merge(NameMatcher.normalize(p.getFullName()), line.f(), BigDecimal::add);
            }
            for (Map.Entry<String, BigDecimal> e : excel.entrySet()) {
                BigDecimal sys = system.getOrDefault(e.getKey(), BigDecimal.ZERO);
                if (sys.compareTo(e.getValue()) != 0) {
                    out.getWarnings().add(sheet + ": " + e.getKey() + " termina en " + money(sys) + " en el sistema y en "
                            + money(e.getValue()) + " en el Excel (los recibos de la PLANILLA no coinciden con la fila del asociado en esta hoja).");
                }
            }
        }

        private void add(String report, String sheet, String concept, BigDecimal excel, BigDecimal system) {
            if (excel == null) return;
            BigDecimal sys = system != null ? system : BigDecimal.ZERO;
            BigDecimal diff = sys.subtract(excel);
            out.getReconciliation().add(new ReconciliationLine(report, sheet, concept, excel, sys, diff, diff.abs().compareTo(new BigDecimal("0.5")) < 0));
        }

        private void check(String what, BigDecimal excel, BigDecimal computed) {
            if (excel != null && excel.compareTo(computed) != 0) {
                out.getWarnings().add(what + ": el Excel muestra " + money(excel) + " y el detalle suma " + money(computed) + ".");
            }
        }

        private JournalVoucherRequest voucher(String number, VoucherType type, LocalDate date, String description, Lines l) {
            return JournalVoucherRequest.builder()
                    .voucherNumber(number)
                    .voucherType(type)
                    .issueDate(date)
                    .description(description.length() > 500 ? description.substring(0, 500) : description)
                    .entries(l.entries)
                    .build();
        }
    }

    /** Acumulador de líneas: omite valores en cero y voltea los negativos al lado contrario. */
    private static final class Lines {
        final List<JournalEntryRequest> entries = new ArrayList<>();

        void debit(String account, ThirdParty party, String description, BigDecimal amount) {
            add(account, party, description, amount, true);
        }

        void credit(String account, ThirdParty party, String description, BigDecimal amount) {
            add(account, party, description, amount, false);
        }

        private void add(String account, ThirdParty party, String description, BigDecimal amount, boolean debit) {
            if (amount == null || amount.signum() == 0) return;
            boolean side = amount.signum() > 0 == debit;
            BigDecimal value = amount.abs();
            entries.add(JournalEntryRequest.builder()
                    .accountCode(account)
                    .thirdPartyId(party != null ? party.getId() : null)
                    .description(description != null && description.length() > 500 ? description.substring(0, 500) : description)
                    .debit(side ? value : BigDecimal.ZERO)
                    .credit(side ? BigDecimal.ZERO : value)
                    .build());
        }

        BigDecimal debits() {
            return entries.stream().map(JournalEntryRequest::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal credits() {
            return entries.stream().map(JournalEntryRequest::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    // ================================================================== reglas de conceptos del informe

    static String incomeAccount(String label) {
        String n = NameMatcher.normalize(label);
        if (n.contains("ADMINISTRACION")) return AssociationChart.ADMINISTRATION_FEE;
        if (n.contains("DESTINACION ESPECIFICA")) return AssociationChart.SPECIFIC_PURPOSE_FUND;
        if (n.contains("MULTA")) return AssociationChart.FINES;
        if (n.contains("BINGO")) return AssociationChart.BINGO;
        if (n.contains("PLATO") || n.contains("EVENTO")) return AssociationChart.EVENTS;
        if (n.contains("FIESTA")) return AssociationChart.PARTIES;
        if (n.contains("RIFA")) return AssociationChart.RAFFLES;
        if (n.contains("FINANCIERO") || n.contains("RENDIMIENTO")) return AssociationChart.BANK_YIELDS;
        if (n.contains("INTERES")) return AssociationChart.LOAN_INTEREST;
        if (n.contains("ARRENDAMIENTO") || n.contains("ARRIENDO")) return AssociationChart.RENTS;
        return null;
    }

    static String expenseAccount(String label) {
        String n = NameMatcher.normalize(label);
        if (n.contains("PREMIO") && n.contains("BINGO")) return AssociationChart.BINGO_PRIZES;
        if (n.contains("PANTALLA") || n.contains("EQUIPO DE OFICINA")) return AssociationChart.MAINTENANCE_OFFICE_EQUIPMENT;
        if (n.contains("PAPELERIA") || n.startsWith("IVA ")) return AssociationChart.STATIONERY;
        if (n.contains("SANITARIO") || n.contains("SOLDADURA") || n.contains("MANTENIMIENTO") || n.contains("REPARACION"))
            return AssociationChart.MAINTENANCE_BUILDINGS;
        if (n.contains("PALANQUETA") || n.contains("SEMANA SANTA") || n.contains("MKDPS") || n.contains("MERCADO") || n.contains("ATUN")
                || n.contains("AYUNO")) return AssociationChart.SOCIAL_ACTIVITIES;
        if (n.contains("BANCARI") || n.contains("COMISION")) return AssociationChart.BANK_FEES;
        if (n.contains("COLAB") || n.contains("REUNION")) return AssociationChart.STAFF_COLLABORATION;
        return null;
    }

    static boolean isDepreciationLabel(String label) {
        return NameMatcher.normalize(label).startsWith("DEPRECIACION");
    }

    static boolean isPreexequialLabel(String label) {
        String n = NameMatcher.normalize(label);
        return n.startsWith("PREXEQUIAL") || n.startsWith("PREEXEQUIAL") || n.startsWith("PRE EXEQUIAL");
    }

    private static String conceptLabel(String concept) {
        return switch (concept) {
            case "APORTES" -> "Aportes sociales";
            case "A_EXTRA" -> "Aporte extraordinario";
            case "F_SOLIDARIDAD" -> "Fondo de solidaridad";
            case "C_ADMON" -> "Cuota de administración";
            case "PRESTAMO" -> "Abono a préstamo";
            case "INTERES" -> "Intereses del préstamo";
            case "PREEXEQ" -> "Preexequial";
            case "TYR" -> "Turismo y recreación";
            case "BINGO" -> "Bingo";
            case "FDE" -> "Fondo de destinación específica";
            case "MULTAS" -> "Multas e inasistencia";
            case "OTROS" -> "Arrendamientos y otros";
            default -> concept;
        };
    }

    private static String money(BigDecimal v) {
        if (v == null) return "$0";
        java.text.NumberFormat f = java.text.NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));
        f.setMaximumFractionDigits(0);
        return f.format(v);
    }

    private static String shortHash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 10).toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            return Integer.toHexString(Objects.hashCode(s));
        }
    }

    /** Permite pasar el mes manualmente cuando el título del informe no lo indica. */
    static YearMonth parsePeriod(String value) {
        return value == null || value.isBlank() ? null : YearMonth.parse(value.trim());
    }
}
