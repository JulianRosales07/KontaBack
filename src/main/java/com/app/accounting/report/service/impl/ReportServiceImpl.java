package com.app.accounting.report.service.impl;

import com.app.accounting.common.util.BigDecimalUtils;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.party.service.ThirdPartyService;
import com.app.accounting.puc.dto.response.AccountResponse;
import com.app.accounting.puc.entity.Account;
import com.app.accounting.puc.entity.AccountNature;
import com.app.accounting.puc.service.AccountService;
import com.app.accounting.report.dto.response.*;
import com.app.accounting.report.service.ReportService;
import com.app.accounting.voucher.entity.JournalEntry;
import com.app.accounting.voucher.repository.AccountBalanceProjection;
import com.app.accounting.voucher.repository.JournalEntryRepository;
import com.app.accounting.voucher.repository.PartyAccountBalanceProjection;
import com.app.accounting.voucher.repository.PartyBalanceProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final JournalEntryRepository journalEntryRepository;
    private final AccountService accountService;
    private final ThirdPartyService thirdPartyService;

    @Override
    @Transactional(readOnly = true)
    public TrialBalanceResponse generateTrialBalance(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            startDate = LocalDate.of(LocalDate.now().getYear(), 1, 1);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        List<AccountBalanceProjection> priorBalances = journalEntryRepository.findAccountBalancesBeforeDate(startDate);
        List<AccountBalanceProjection> periodBalances = journalEntryRepository.findAccountBalancesBetweenDates(startDate, endDate);

        Map<String, IntermediateBalance> balanceMap = new HashMap<>();

        // 1. Cargar saldos anteriores a startDate
        for (AccountBalanceProjection p : priorBalances) {
            IntermediateBalance item = balanceMap.computeIfAbsent(p.getAccountCode(), k -> new IntermediateBalance(
                    p.getAccountId(),
                    p.getAccountCode(),
                    p.getAccountName(),
                    AccountNature.valueOf(p.getAccountNature())
            ));
            item.priorDebit = p.getTotalDebit() != null ? p.getTotalDebit() : BigDecimal.ZERO;
            item.priorCredit = p.getTotalCredit() != null ? p.getTotalCredit() : BigDecimal.ZERO;
        }

        // 2. Cargar movimientos del periodo
        for (AccountBalanceProjection p : periodBalances) {
            IntermediateBalance item = balanceMap.computeIfAbsent(p.getAccountCode(), k -> new IntermediateBalance(
                    p.getAccountId(),
                    p.getAccountCode(),
                    p.getAccountName(),
                    AccountNature.valueOf(p.getAccountNature())
            ));
            item.periodDebit = p.getTotalDebit() != null ? p.getTotalDebit() : BigDecimal.ZERO;
            item.periodCredit = p.getTotalCredit() != null ? p.getTotalCredit() : BigDecimal.ZERO;
        }

        // Si no hay transacciones pero existen cuentas activas, incluirlas con saldo 0
        List<AccountResponse> activeAccounts = accountService.getAllActiveAccounts();
        for (AccountResponse acc : activeAccounts) {
            balanceMap.putIfAbsent(acc.getCode(), new IntermediateBalance(
                    acc.getId(),
                    acc.getCode(),
                    acc.getName(),
                    acc.getNature()
            ));
        }

        List<TrialBalanceItemResponse> items = new ArrayList<>();
        BigDecimal totalInitialDebit = BigDecimal.ZERO;
        BigDecimal totalInitialCredit = BigDecimal.ZERO;
        BigDecimal totalPeriodDebit = BigDecimal.ZERO;
        BigDecimal totalPeriodCredit = BigDecimal.ZERO;
        BigDecimal totalFinalDebit = BigDecimal.ZERO;
        BigDecimal totalFinalCredit = BigDecimal.ZERO;

        List<IntermediateBalance> sortedList = balanceMap.values().stream()
                .sorted(Comparator.comparing(b -> b.code))
                .toList();

        for (IntermediateBalance b : sortedList) {
            BigDecimal initialBal;
            BigDecimal finalBal;

            if (b.nature == AccountNature.DEBIT) {
                initialBal = b.priorDebit.subtract(b.priorCredit);
                finalBal = initialBal.add(b.periodDebit).subtract(b.periodCredit);

                if (initialBal.compareTo(BigDecimal.ZERO) >= 0) {
                    totalInitialDebit = totalInitialDebit.add(initialBal);
                } else {
                    totalInitialCredit = totalInitialCredit.add(initialBal.abs());
                }

                if (finalBal.compareTo(BigDecimal.ZERO) >= 0) {
                    totalFinalDebit = totalFinalDebit.add(finalBal);
                } else {
                    totalFinalCredit = totalFinalCredit.add(finalBal.abs());
                }
            } else {
                initialBal = b.priorCredit.subtract(b.priorDebit);
                finalBal = initialBal.add(b.periodCredit).subtract(b.periodDebit);

                if (initialBal.compareTo(BigDecimal.ZERO) >= 0) {
                    totalInitialCredit = totalInitialCredit.add(initialBal);
                } else {
                    totalInitialDebit = totalInitialDebit.add(initialBal.abs());
                }

                if (finalBal.compareTo(BigDecimal.ZERO) >= 0) {
                    totalFinalCredit = totalFinalCredit.add(finalBal);
                } else {
                    totalFinalDebit = totalFinalDebit.add(finalBal.abs());
                }
            }

            totalPeriodDebit = totalPeriodDebit.add(b.periodDebit);
            totalPeriodCredit = totalPeriodCredit.add(b.periodCredit);

            items.add(TrialBalanceItemResponse.builder()
                    .accountId(b.id)
                    .accountCode(b.code)
                    .accountName(b.name)
                    .nature(b.nature)
                    .initialBalance(BigDecimalUtils.scale(initialBal))
                    .periodDebit(BigDecimalUtils.scale(b.periodDebit))
                    .periodCredit(BigDecimalUtils.scale(b.periodCredit))
                    .finalBalance(BigDecimalUtils.scale(finalBal))
                    .build());
        }

        return TrialBalanceResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalInitialDebit(BigDecimalUtils.scale(totalInitialDebit))
                .totalInitialCredit(BigDecimalUtils.scale(totalInitialCredit))
                .totalPeriodDebit(BigDecimalUtils.scale(totalPeriodDebit))
                .totalPeriodCredit(BigDecimalUtils.scale(totalPeriodCredit))
                .totalFinalDebit(BigDecimalUtils.scale(totalFinalDebit))
                .totalFinalCredit(BigDecimalUtils.scale(totalFinalCredit))
                .items(items)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerReportResponse generateGeneralLedger(String accountCode, LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            startDate = LocalDate.of(LocalDate.now().getYear(), 1, 1);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        Account account = accountService.getAccountEntityByCode(accountCode);

        // Calcular saldo anterior
        List<AccountBalanceProjection> priors = journalEntryRepository.findAccountBalancesBeforeDate(startDate);
        BigDecimal priorDebit = BigDecimal.ZERO;
        BigDecimal priorCredit = BigDecimal.ZERO;
        for (AccountBalanceProjection p : priors) {
            if (p.getAccountCode().equals(accountCode)) {
                priorDebit = p.getTotalDebit() != null ? p.getTotalDebit() : BigDecimal.ZERO;
                priorCredit = p.getTotalCredit() != null ? p.getTotalCredit() : BigDecimal.ZERO;
                break;
            }
        }

        BigDecimal runningBalance = (account.getNature() == AccountNature.DEBIT)
                ? priorDebit.subtract(priorCredit)
                : priorCredit.subtract(priorDebit);

        BigDecimal initialBalance = runningBalance;
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        Page<JournalEntry> entriesPage = journalEntryRepository.findLedgerEntries(
                accountCode,
                startDate,
                endDate,
                PageRequest.of(0, 1000)
        );

        List<LedgerEntryResponse> ledgerEntries = new ArrayList<>();
        for (JournalEntry e : entriesPage.getContent()) {
            BigDecimal debit = e.getDebit() != null ? e.getDebit() : BigDecimal.ZERO;
            BigDecimal credit = e.getCredit() != null ? e.getCredit() : BigDecimal.ZERO;

            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);

            if (account.getNature() == AccountNature.DEBIT) {
                runningBalance = runningBalance.add(debit).subtract(credit);
            } else {
                runningBalance = runningBalance.add(credit).subtract(debit);
            }

            ledgerEntries.add(LedgerEntryResponse.builder()
                    .date(e.getVoucher().getIssueDate())
                    .voucherNumber(e.getVoucher().getVoucherNumber())
                    .referenceNumber(e.getReferenceNumber())
                    .thirdPartyName(e.getThirdParty() != null ? e.getThirdParty().getFullName() : null)
                    .description(e.getDescription() != null ? e.getDescription() : e.getVoucher().getDescription())
                    .debit(BigDecimalUtils.scale(debit))
                    .credit(BigDecimalUtils.scale(credit))
                    .runningBalance(BigDecimalUtils.scale(runningBalance))
                    .build());
        }

        return LedgerReportResponse.builder()
                .accountCode(account.getCode())
                .accountName(account.getName())
                .startDate(startDate)
                .endDate(endDate)
                .initialBalance(BigDecimalUtils.scale(initialBalance))
                .totalDebit(BigDecimalUtils.scale(totalDebit))
                .totalCredit(BigDecimalUtils.scale(totalCredit))
                .finalBalance(BigDecimalUtils.scale(runningBalance))
                .entries(ledgerEntries)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PartyAuxiliaryReportResponse generatePartyAuxiliary(UUID accountId, UUID partyId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            startDate = LocalDate.of(LocalDate.now().getYear(), 1, 1);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        Account account = accountService.getAccountEntityById(accountId);
        ThirdParty party = thirdPartyService.getThirdPartyEntityById(partyId);

        // Saldo anterior acumulado para esta cuenta y este tercero
        PartyBalanceProjection prior = journalEntryRepository.findPartyBalanceBeforeDate(accountId, partyId, startDate);
        BigDecimal priorDebit = (prior != null && prior.getTotalDebit() != null) ? prior.getTotalDebit() : BigDecimal.ZERO;
        BigDecimal priorCredit = (prior != null && prior.getTotalCredit() != null) ? prior.getTotalCredit() : BigDecimal.ZERO;

        BigDecimal initialBal = (account.getNature() == AccountNature.DEBIT)
                ? priorDebit.subtract(priorCredit)
                : priorCredit.subtract(priorDebit);

        BigDecimal runningBalance = initialBal;
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        // Movimientos del periodo
        List<JournalEntry> entries = journalEntryRepository.findPartyAuxiliaryEntries(accountId, partyId, startDate, endDate);
        List<PartyAuxiliaryEntryResponse> itemResponses = new ArrayList<>();

        for (JournalEntry entry : entries) {
            BigDecimal debit = entry.getDebit() != null ? entry.getDebit() : BigDecimal.ZERO;
            BigDecimal credit = entry.getCredit() != null ? entry.getCredit() : BigDecimal.ZERO;

            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);

            if (account.getNature() == AccountNature.DEBIT) {
                runningBalance = runningBalance.add(debit).subtract(credit);
            } else {
                runningBalance = runningBalance.add(credit).subtract(debit);
            }

            itemResponses.add(PartyAuxiliaryEntryResponse.builder()
                    .issueDate(entry.getVoucher().getIssueDate())
                    .voucherNumber(entry.getVoucher().getVoucherNumber())
                    .referenceNumber(entry.getReferenceNumber())
                    .description(entry.getDescription() != null ? entry.getDescription() : entry.getVoucher().getDescription())
                    .debit(BigDecimalUtils.scale(debit))
                    .credit(BigDecimalUtils.scale(credit))
                    .runningBalance(BigDecimalUtils.scale(runningBalance))
                    .build());
        }

        return PartyAuxiliaryReportResponse.builder()
                .accountId(account.getId())
                .accountCode(account.getCode())
                .accountName(account.getName())
                .accountNature(account.getNature())
                .partyId(party.getId())
                .partyIdentification(party.getIdentificationNumber())
                .partyName(party.getFullName())
                .startDate(startDate)
                .endDate(endDate)
                .initialBalance(BigDecimalUtils.scale(initialBal))
                .totalDebit(BigDecimalUtils.scale(totalDebit))
                .totalCredit(BigDecimalUtils.scale(totalCredit))
                .finalBalance(BigDecimalUtils.scale(runningBalance))
                .entries(itemResponses)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public IncomeStatementResponse generateIncomeStatement(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            startDate = LocalDate.of(LocalDate.now().getYear(), 1, 1);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        List<AccountBalanceProjection> periodBalances = journalEntryRepository.findAccountBalancesBetweenDates(startDate, endDate);

        BigDecimal totalRevenues = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;
        BigDecimal totalCosts = BigDecimal.ZERO;

        List<FinancialReportItemResponse> revenueItems = new ArrayList<>();
        List<FinancialReportItemResponse> costItems = new ArrayList<>();
        List<FinancialReportItemResponse> expenseItems = new ArrayList<>();

        for (AccountBalanceProjection p : periodBalances) {
            String code = p.getAccountCode();
            BigDecimal debit = p.getTotalDebit() != null ? p.getTotalDebit() : BigDecimal.ZERO;
            BigDecimal credit = p.getTotalCredit() != null ? p.getTotalCredit() : BigDecimal.ZERO;

            if (code.startsWith("4")) {
                // Ingresos (Naturaleza Crédito): Saldo = Créditos - Débitos
                BigDecimal net = credit.subtract(debit);
                totalRevenues = totalRevenues.add(net);
                revenueItems.add(FinancialReportItemResponse.builder()
                        .accountId(p.getAccountId())
                        .accountCode(code)
                        .accountName(p.getAccountName())
                        .nature(AccountNature.CREDIT)
                        .debit(BigDecimalUtils.scale(debit))
                        .credit(BigDecimalUtils.scale(credit))
                        .netBalance(BigDecimalUtils.scale(net))
                        .build());
            } else if (code.startsWith("5")) {
                // Gastos (Naturaleza Débito): Saldo = Débitos - Créditos
                BigDecimal net = debit.subtract(credit);
                totalExpenses = totalExpenses.add(net);
                expenseItems.add(FinancialReportItemResponse.builder()
                        .accountId(p.getAccountId())
                        .accountCode(code)
                        .accountName(p.getAccountName())
                        .nature(AccountNature.DEBIT)
                        .debit(BigDecimalUtils.scale(debit))
                        .credit(BigDecimalUtils.scale(credit))
                        .netBalance(BigDecimalUtils.scale(net))
                        .build());
            } else if (code.startsWith("6")) {
                // Costos (Naturaleza Débito): Saldo = Débitos - Créditos
                BigDecimal net = debit.subtract(credit);
                totalCosts = totalCosts.add(net);
                costItems.add(FinancialReportItemResponse.builder()
                        .accountId(p.getAccountId())
                        .accountCode(code)
                        .accountName(p.getAccountName())
                        .nature(AccountNature.DEBIT)
                        .debit(BigDecimalUtils.scale(debit))
                        .credit(BigDecimalUtils.scale(credit))
                        .netBalance(BigDecimalUtils.scale(net))
                        .build());
            }
        }

        totalRevenues = BigDecimalUtils.scale(totalRevenues);
        totalCosts = BigDecimalUtils.scale(totalCosts);
        totalExpenses = BigDecimalUtils.scale(totalExpenses);

        BigDecimal grossProfit = BigDecimalUtils.scale(totalRevenues.subtract(totalCosts));
        // Utilidad del periodo: (Créditos 4 - Débitos 4) - (Débitos 5 y 6 - Créditos 5 y 6)
        BigDecimal netOperatingIncome = BigDecimalUtils.scale(totalRevenues.subtract(totalExpenses.add(totalCosts)));

        revenueItems.sort(Comparator.comparing(FinancialReportItemResponse::getAccountCode));
        costItems.sort(Comparator.comparing(FinancialReportItemResponse::getAccountCode));
        expenseItems.sort(Comparator.comparing(FinancialReportItemResponse::getAccountCode));

        return IncomeStatementResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalRevenues(totalRevenues)
                .totalCosts(totalCosts)
                .grossProfit(grossProfit)
                .totalExpenses(totalExpenses)
                .netOperatingIncome(netOperatingIncome)
                .revenueItems(revenueItems)
                .costItems(costItems)
                .expenseItems(expenseItems)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public BalanceSheetResponse generateBalanceSheet(LocalDate asOfDate) {
        if (asOfDate == null) {
            asOfDate = LocalDate.now();
        }

        List<AccountBalanceProjection> balances = journalEntryRepository.findAccountBalancesUpToDate(asOfDate);

        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        BigDecimal totalEquity = BigDecimal.ZERO;

        BigDecimal periodRevenues = BigDecimal.ZERO;
        BigDecimal periodExpensesAndCosts = BigDecimal.ZERO;

        List<FinancialReportItemResponse> assetItems = new ArrayList<>();
        List<FinancialReportItemResponse> liabilityItems = new ArrayList<>();
        List<FinancialReportItemResponse> equityItems = new ArrayList<>();

        for (AccountBalanceProjection p : balances) {
            String code = p.getAccountCode();
            AccountNature nature = AccountNature.valueOf(p.getAccountNature());
            BigDecimal debit = p.getTotalDebit() != null ? p.getTotalDebit() : BigDecimal.ZERO;
            BigDecimal credit = p.getTotalCredit() != null ? p.getTotalCredit() : BigDecimal.ZERO;

            if (code.startsWith("1")) {
                // Activos: Débitos - Créditos
                BigDecimal net = (nature == AccountNature.DEBIT) ? debit.subtract(credit) : credit.subtract(debit);
                totalAssets = totalAssets.add(net);
                assetItems.add(FinancialReportItemResponse.builder()
                        .accountId(p.getAccountId())
                        .accountCode(code)
                        .accountName(p.getAccountName())
                        .nature(nature)
                        .debit(BigDecimalUtils.scale(debit))
                        .credit(BigDecimalUtils.scale(credit))
                        .netBalance(BigDecimalUtils.scale(net))
                        .build());
            } else if (code.startsWith("2")) {
                // Pasivos: Créditos - Débitos
                BigDecimal net = (nature == AccountNature.CREDIT) ? credit.subtract(debit) : debit.subtract(credit);
                totalLiabilities = totalLiabilities.add(net);
                liabilityItems.add(FinancialReportItemResponse.builder()
                        .accountId(p.getAccountId())
                        .accountCode(code)
                        .accountName(p.getAccountName())
                        .nature(nature)
                        .debit(BigDecimalUtils.scale(debit))
                        .credit(BigDecimalUtils.scale(credit))
                        .netBalance(BigDecimalUtils.scale(net))
                        .build());
            } else if (code.startsWith("3")) {
                // Patrimonio: Créditos - Débitos
                BigDecimal net = (nature == AccountNature.CREDIT) ? credit.subtract(debit) : debit.subtract(credit);
                totalEquity = totalEquity.add(net);
                equityItems.add(FinancialReportItemResponse.builder()
                        .accountId(p.getAccountId())
                        .accountCode(code)
                        .accountName(p.getAccountName())
                        .nature(nature)
                        .debit(BigDecimalUtils.scale(debit))
                        .credit(BigDecimalUtils.scale(credit))
                        .netBalance(BigDecimalUtils.scale(net))
                        .build());
            } else if (code.startsWith("4")) {
                periodRevenues = periodRevenues.add(credit.subtract(debit));
            } else if (code.startsWith("5") || code.startsWith("6")) {
                periodExpensesAndCosts = periodExpensesAndCosts.add(debit.subtract(credit));
            }
        }

        totalAssets = BigDecimalUtils.scale(totalAssets);
        totalLiabilities = BigDecimalUtils.scale(totalLiabilities);
        totalEquity = BigDecimalUtils.scale(totalEquity);

        BigDecimal periodIncome = BigDecimalUtils.scale(periodRevenues.subtract(periodExpensesAndCosts));
        BigDecimal totalLiabilitiesAndEquity = BigDecimalUtils.scale(totalLiabilities.add(totalEquity).add(periodIncome));
        boolean isBalanced = BigDecimalUtils.areEqual(totalAssets, totalLiabilitiesAndEquity);

        assetItems.sort(Comparator.comparing(FinancialReportItemResponse::getAccountCode));
        liabilityItems.sort(Comparator.comparing(FinancialReportItemResponse::getAccountCode));
        equityItems.sort(Comparator.comparing(FinancialReportItemResponse::getAccountCode));

        return BalanceSheetResponse.builder()
                .asOfDate(asOfDate)
                .totalAssets(totalAssets)
                .totalLiabilities(totalLiabilities)
                .totalEquity(totalEquity)
                .periodIncome(periodIncome)
                .totalLiabilitiesAndEquity(totalLiabilitiesAndEquity)
                .isBalanced(isBalanced)
                .assetItems(assetItems)
                .liabilityItems(liabilityItems)
                .equityItems(equityItems)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PartyTrialBalanceResponse generatePartyTrialBalance(LocalDate startDate, LocalDate endDate, UUID partyId) {
        if (startDate == null) {
            startDate = LocalDate.of(LocalDate.now().getYear(), 1, 1);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        List<PartyAccountBalanceProjection> priorBalances = (partyId != null)
                ? journalEntryRepository.findPartyAccountBalancesBeforeDateAndParty(startDate, partyId)
                : journalEntryRepository.findPartyAccountBalancesBeforeDate(startDate);

        List<PartyAccountBalanceProjection> periodBalances = (partyId != null)
                ? journalEntryRepository.findPartyAccountBalancesBetweenDatesAndParty(startDate, endDate, partyId)
                : journalEntryRepository.findPartyAccountBalancesBetweenDates(startDate, endDate);

        Map<String, PartyIntermediateBalance> balanceMap = new HashMap<>();

        for (PartyAccountBalanceProjection p : priorBalances) {
            String key = p.getAccountCode() + "_" + (p.getPartyId() != null ? p.getPartyId().toString() : "NONE");
            PartyIntermediateBalance item = balanceMap.computeIfAbsent(key, k -> new PartyIntermediateBalance(
                    p.getAccountId(),
                    p.getAccountCode(),
                    p.getAccountName(),
                    AccountNature.valueOf(p.getAccountNature()),
                    p.getPartyId(),
                    p.getPartyIdentification(),
                    p.getPartyName()
            ));
            item.priorDebit = p.getTotalDebit() != null ? p.getTotalDebit() : BigDecimal.ZERO;
            item.priorCredit = p.getTotalCredit() != null ? p.getTotalCredit() : BigDecimal.ZERO;
        }

        for (PartyAccountBalanceProjection p : periodBalances) {
            String key = p.getAccountCode() + "_" + (p.getPartyId() != null ? p.getPartyId().toString() : "NONE");
            PartyIntermediateBalance item = balanceMap.computeIfAbsent(key, k -> new PartyIntermediateBalance(
                    p.getAccountId(),
                    p.getAccountCode(),
                    p.getAccountName(),
                    AccountNature.valueOf(p.getAccountNature()),
                    p.getPartyId(),
                    p.getPartyIdentification(),
                    p.getPartyName()
            ));
            item.periodDebit = p.getTotalDebit() != null ? p.getTotalDebit() : BigDecimal.ZERO;
            item.periodCredit = p.getTotalCredit() != null ? p.getTotalCredit() : BigDecimal.ZERO;
        }

        List<PartyTrialBalanceItemResponse> items = new ArrayList<>();
        BigDecimal totalInitialDebit = BigDecimal.ZERO;
        BigDecimal totalInitialCredit = BigDecimal.ZERO;
        BigDecimal totalPeriodDebit = BigDecimal.ZERO;
        BigDecimal totalPeriodCredit = BigDecimal.ZERO;
        BigDecimal totalFinalDebit = BigDecimal.ZERO;
        BigDecimal totalFinalCredit = BigDecimal.ZERO;

        List<PartyIntermediateBalance> sortedList = balanceMap.values().stream()
                .sorted(Comparator.comparing((PartyIntermediateBalance b) -> b.accountCode)
                        .thenComparing(b -> b.partyIdentification != null ? b.partyIdentification : ""))
                .toList();

        for (PartyIntermediateBalance b : sortedList) {
            BigDecimal initialBal;
            BigDecimal finalBal;

            if (b.nature == AccountNature.DEBIT) {
                initialBal = b.priorDebit.subtract(b.priorCredit);
                finalBal = initialBal.add(b.periodDebit).subtract(b.periodCredit);

                if (initialBal.compareTo(BigDecimal.ZERO) >= 0) {
                    totalInitialDebit = totalInitialDebit.add(initialBal);
                } else {
                    totalInitialCredit = totalInitialCredit.add(initialBal.abs());
                }

                if (finalBal.compareTo(BigDecimal.ZERO) >= 0) {
                    totalFinalDebit = totalFinalDebit.add(finalBal);
                } else {
                    totalFinalCredit = totalFinalCredit.add(finalBal.abs());
                }
            } else {
                initialBal = b.priorCredit.subtract(b.priorDebit);
                finalBal = initialBal.add(b.periodCredit).subtract(b.periodDebit);

                if (initialBal.compareTo(BigDecimal.ZERO) >= 0) {
                    totalInitialCredit = totalInitialCredit.add(initialBal);
                } else {
                    totalInitialDebit = totalInitialDebit.add(initialBal.abs());
                }

                if (finalBal.compareTo(BigDecimal.ZERO) >= 0) {
                    totalFinalCredit = totalFinalCredit.add(finalBal);
                } else {
                    totalFinalDebit = totalFinalDebit.add(finalBal.abs());
                }
            }

            totalPeriodDebit = totalPeriodDebit.add(b.periodDebit);
            totalPeriodCredit = totalPeriodCredit.add(b.periodCredit);

            items.add(PartyTrialBalanceItemResponse.builder()
                    .accountId(b.accountId)
                    .accountCode(b.accountCode)
                    .accountName(b.accountName)
                    .accountNature(b.nature)
                    .partyId(b.partyId)
                    .partyIdentification(b.partyIdentification)
                    .partyName(b.partyName)
                    .initialBalance(BigDecimalUtils.scale(initialBal))
                    .periodDebit(BigDecimalUtils.scale(b.periodDebit))
                    .periodCredit(BigDecimalUtils.scale(b.periodCredit))
                    .finalBalance(BigDecimalUtils.scale(finalBal))
                    .build());
        }

        return PartyTrialBalanceResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalInitialDebit(BigDecimalUtils.scale(totalInitialDebit))
                .totalInitialCredit(BigDecimalUtils.scale(totalInitialCredit))
                .totalPeriodDebit(BigDecimalUtils.scale(totalPeriodDebit))
                .totalPeriodCredit(BigDecimalUtils.scale(totalPeriodCredit))
                .totalFinalDebit(BigDecimalUtils.scale(totalFinalDebit))
                .totalFinalCredit(BigDecimalUtils.scale(totalFinalCredit))
                .items(items)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PartyIncomeStatementResponse generatePartyIncomeStatement(LocalDate startDate, LocalDate endDate, UUID partyId) {
        if (startDate == null) {
            startDate = LocalDate.of(LocalDate.now().getYear(), 1, 1);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        List<PartyAccountBalanceProjection> periodBalances = (partyId != null)
                ? journalEntryRepository.findPartyAccountBalancesBetweenDatesAndParty(startDate, endDate, partyId)
                : journalEntryRepository.findPartyAccountBalancesBetweenDates(startDate, endDate);

        BigDecimal totalRevenues = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;
        BigDecimal totalCosts = BigDecimal.ZERO;

        Map<String, PartyIncomeAccumulator> partyMap = new LinkedHashMap<>();

        for (PartyAccountBalanceProjection p : periodBalances) {
            String code = p.getAccountCode();
            BigDecimal debit = p.getTotalDebit() != null ? p.getTotalDebit() : BigDecimal.ZERO;
            BigDecimal credit = p.getTotalCredit() != null ? p.getTotalCredit() : BigDecimal.ZERO;

            if (!code.startsWith("4") && !code.startsWith("5") && !code.startsWith("6")) {
                continue;
            }

            String partyKey = (p.getPartyId() != null) ? p.getPartyId().toString() : "GLOBAL";
            PartyIncomeAccumulator acc = partyMap.computeIfAbsent(partyKey, k -> new PartyIncomeAccumulator(
                    p.getPartyId(),
                    p.getPartyIdentification(),
                    p.getPartyName()
            ));

            if (code.startsWith("4")) {
                BigDecimal net = credit.subtract(debit);
                totalRevenues = totalRevenues.add(net);
                acc.totalRevenues = acc.totalRevenues.add(net);
                acc.revenueItems.add(FinancialReportItemResponse.builder()
                        .accountId(p.getAccountId())
                        .accountCode(code)
                        .accountName(p.getAccountName())
                        .nature(AccountNature.CREDIT)
                        .debit(BigDecimalUtils.scale(debit))
                        .credit(BigDecimalUtils.scale(credit))
                        .netBalance(BigDecimalUtils.scale(net))
                        .build());
            } else if (code.startsWith("5")) {
                BigDecimal net = debit.subtract(credit);
                totalExpenses = totalExpenses.add(net);
                acc.totalExpenses = acc.totalExpenses.add(net);
                acc.expenseItems.add(FinancialReportItemResponse.builder()
                        .accountId(p.getAccountId())
                        .accountCode(code)
                        .accountName(p.getAccountName())
                        .nature(AccountNature.DEBIT)
                        .debit(BigDecimalUtils.scale(debit))
                        .credit(BigDecimalUtils.scale(credit))
                        .netBalance(BigDecimalUtils.scale(net))
                        .build());
            } else if (code.startsWith("6")) {
                BigDecimal net = debit.subtract(credit);
                totalCosts = totalCosts.add(net);
                acc.totalCosts = acc.totalCosts.add(net);
                acc.costItems.add(FinancialReportItemResponse.builder()
                        .accountId(p.getAccountId())
                        .accountCode(code)
                        .accountName(p.getAccountName())
                        .nature(AccountNature.DEBIT)
                        .debit(BigDecimalUtils.scale(debit))
                        .credit(BigDecimalUtils.scale(credit))
                        .netBalance(BigDecimalUtils.scale(net))
                        .build());
            }
        }

        totalRevenues = BigDecimalUtils.scale(totalRevenues);
        totalCosts = BigDecimalUtils.scale(totalCosts);
        totalExpenses = BigDecimalUtils.scale(totalExpenses);

        BigDecimal grossProfit = BigDecimalUtils.scale(totalRevenues.subtract(totalCosts));
        BigDecimal netOperatingIncome = BigDecimalUtils.scale(totalRevenues.subtract(totalExpenses.add(totalCosts)));

        List<PartyIncomeItemResponse> partyResponses = new ArrayList<>();
        for (PartyIncomeAccumulator a : partyMap.values()) {
            a.revenueItems.sort(Comparator.comparing(FinancialReportItemResponse::getAccountCode));
            a.costItems.sort(Comparator.comparing(FinancialReportItemResponse::getAccountCode));
            a.expenseItems.sort(Comparator.comparing(FinancialReportItemResponse::getAccountCode));

            BigDecimal pRev = BigDecimalUtils.scale(a.totalRevenues);
            BigDecimal pCost = BigDecimalUtils.scale(a.totalCosts);
            BigDecimal pExp = BigDecimalUtils.scale(a.totalExpenses);
            BigDecimal pGross = BigDecimalUtils.scale(pRev.subtract(pCost));
            BigDecimal pNet = BigDecimalUtils.scale(pRev.subtract(pExp.add(pCost)));

            partyResponses.add(PartyIncomeItemResponse.builder()
                    .partyId(a.partyId)
                    .partyIdentification(a.partyIdentification)
                    .partyName(a.partyName)
                    .totalRevenues(pRev)
                    .totalCosts(pCost)
                    .grossProfit(pGross)
                    .totalExpenses(pExp)
                    .netResult(pNet)
                    .revenueItems(a.revenueItems)
                    .costItems(a.costItems)
                    .expenseItems(a.expenseItems)
                    .build());
        }

        partyResponses.sort(Comparator.comparing((PartyIncomeItemResponse p) -> p.getTotalRevenues()).reversed()
                .thenComparing(p -> p.getPartyName() != null ? p.getPartyName() : ""));

        return PartyIncomeStatementResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalRevenues(totalRevenues)
                .totalCosts(totalCosts)
                .grossProfit(grossProfit)
                .totalExpenses(totalExpenses)
                .netOperatingIncome(netOperatingIncome)
                .parties(partyResponses)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PartyBalanceSheetResponse generatePartyBalanceSheet(LocalDate asOfDate, UUID partyId) {
        if (asOfDate == null) {
            asOfDate = LocalDate.now();
        }

        List<PartyAccountBalanceProjection> balances = (partyId != null)
                ? journalEntryRepository.findPartyAccountBalancesUpToDateAndParty(asOfDate, partyId)
                : journalEntryRepository.findPartyAccountBalancesUpToDate(asOfDate);

        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        BigDecimal totalEquity = BigDecimal.ZERO;

        BigDecimal periodRevenues = BigDecimal.ZERO;
        BigDecimal periodExpensesAndCosts = BigDecimal.ZERO;

        List<PartyBalanceItemResponse> assetItems = new ArrayList<>();
        List<PartyBalanceItemResponse> liabilityItems = new ArrayList<>();
        List<PartyBalanceItemResponse> equityItems = new ArrayList<>();

        for (PartyAccountBalanceProjection p : balances) {
            String code = p.getAccountCode();
            AccountNature nature = AccountNature.valueOf(p.getAccountNature());
            BigDecimal debit = p.getTotalDebit() != null ? p.getTotalDebit() : BigDecimal.ZERO;
            BigDecimal credit = p.getTotalCredit() != null ? p.getTotalCredit() : BigDecimal.ZERO;

            if (code.startsWith("1")) {
                BigDecimal net = (nature == AccountNature.DEBIT) ? debit.subtract(credit) : credit.subtract(debit);
                totalAssets = totalAssets.add(net);
                assetItems.add(PartyBalanceItemResponse.builder()
                        .accountId(p.getAccountId())
                        .accountCode(code)
                        .accountName(p.getAccountName())
                        .nature(nature)
                        .partyId(p.getPartyId())
                        .partyIdentification(p.getPartyIdentification())
                        .partyName(p.getPartyName())
                        .debit(BigDecimalUtils.scale(debit))
                        .credit(BigDecimalUtils.scale(credit))
                        .netBalance(BigDecimalUtils.scale(net))
                        .build());
            } else if (code.startsWith("2")) {
                BigDecimal net = (nature == AccountNature.CREDIT) ? credit.subtract(debit) : debit.subtract(credit);
                totalLiabilities = totalLiabilities.add(net);
                liabilityItems.add(PartyBalanceItemResponse.builder()
                        .accountId(p.getAccountId())
                        .accountCode(code)
                        .accountName(p.getAccountName())
                        .nature(nature)
                        .partyId(p.getPartyId())
                        .partyIdentification(p.getPartyIdentification())
                        .partyName(p.getPartyName())
                        .debit(BigDecimalUtils.scale(debit))
                        .credit(BigDecimalUtils.scale(credit))
                        .netBalance(BigDecimalUtils.scale(net))
                        .build());
            } else if (code.startsWith("3")) {
                BigDecimal net = (nature == AccountNature.CREDIT) ? credit.subtract(debit) : debit.subtract(credit);
                totalEquity = totalEquity.add(net);
                equityItems.add(PartyBalanceItemResponse.builder()
                        .accountId(p.getAccountId())
                        .accountCode(code)
                        .accountName(p.getAccountName())
                        .nature(nature)
                        .partyId(p.getPartyId())
                        .partyIdentification(p.getPartyIdentification())
                        .partyName(p.getPartyName())
                        .debit(BigDecimalUtils.scale(debit))
                        .credit(BigDecimalUtils.scale(credit))
                        .netBalance(BigDecimalUtils.scale(net))
                        .build());
            } else if (code.startsWith("4")) {
                periodRevenues = periodRevenues.add(credit.subtract(debit));
            } else if (code.startsWith("5") || code.startsWith("6")) {
                periodExpensesAndCosts = periodExpensesAndCosts.add(debit.subtract(credit));
            }
        }

        totalAssets = BigDecimalUtils.scale(totalAssets);
        totalLiabilities = BigDecimalUtils.scale(totalLiabilities);
        totalEquity = BigDecimalUtils.scale(totalEquity);

        BigDecimal periodIncome = BigDecimalUtils.scale(periodRevenues.subtract(periodExpensesAndCosts));
        BigDecimal totalLiabilitiesAndEquity = BigDecimalUtils.scale(totalLiabilities.add(totalEquity).add(periodIncome));
        boolean isBalanced = BigDecimalUtils.areEqual(totalAssets, totalLiabilitiesAndEquity);

        assetItems.sort(Comparator.comparing(PartyBalanceItemResponse::getAccountCode)
                .thenComparing(item -> item.getPartyIdentification() != null ? item.getPartyIdentification() : ""));
        liabilityItems.sort(Comparator.comparing(PartyBalanceItemResponse::getAccountCode)
                .thenComparing(item -> item.getPartyIdentification() != null ? item.getPartyIdentification() : ""));
        equityItems.sort(Comparator.comparing(PartyBalanceItemResponse::getAccountCode)
                .thenComparing(item -> item.getPartyIdentification() != null ? item.getPartyIdentification() : ""));

        return PartyBalanceSheetResponse.builder()
                .asOfDate(asOfDate)
                .totalAssets(totalAssets)
                .totalLiabilities(totalLiabilities)
                .totalEquity(totalEquity)
                .periodIncome(periodIncome)
                .totalLiabilitiesAndEquity(totalLiabilitiesAndEquity)
                .isBalanced(isBalanced)
                .assetItems(assetItems)
                .liabilityItems(liabilityItems)
                .equityItems(equityItems)
                .build();
    }

    private static class IntermediateBalance {
        UUID id;
        String code;
        String name;
        AccountNature nature;
        BigDecimal priorDebit = BigDecimal.ZERO;
        BigDecimal priorCredit = BigDecimal.ZERO;
        BigDecimal periodDebit = BigDecimal.ZERO;
        BigDecimal periodCredit = BigDecimal.ZERO;

        IntermediateBalance(UUID id, String code, String name, AccountNature nature) {
            this.id = id;
            this.code = code;
            this.name = name;
            this.nature = nature;
        }
    }

    private static class PartyIntermediateBalance {
        UUID accountId;
        String accountCode;
        String accountName;
        AccountNature nature;
        UUID partyId;
        String partyIdentification;
        String partyName;
        BigDecimal priorDebit = BigDecimal.ZERO;
        BigDecimal priorCredit = BigDecimal.ZERO;
        BigDecimal periodDebit = BigDecimal.ZERO;
        BigDecimal periodCredit = BigDecimal.ZERO;

        PartyIntermediateBalance(UUID accountId, String accountCode, String accountName, AccountNature nature,
                                 UUID partyId, String partyIdentification, String partyName) {
            this.accountId = accountId;
            this.accountCode = accountCode;
            this.accountName = accountName;
            this.nature = nature;
            this.partyId = partyId;
            this.partyIdentification = partyIdentification;
            this.partyName = partyName;
        }
    }

    private static class PartyIncomeAccumulator {
        UUID partyId;
        String partyIdentification;
        String partyName;
        BigDecimal totalRevenues = BigDecimal.ZERO;
        BigDecimal totalCosts = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;
        List<FinancialReportItemResponse> revenueItems = new ArrayList<>();
        List<FinancialReportItemResponse> costItems = new ArrayList<>();
        List<FinancialReportItemResponse> expenseItems = new ArrayList<>();

        PartyIncomeAccumulator(UUID partyId, String partyIdentification, String partyName) {
            this.partyId = partyId;
            this.partyIdentification = partyIdentification;
            this.partyName = partyName;
        }
    }
}

