package com.app.accounting.report.service;

import com.app.accounting.report.dto.response.*;

import java.time.LocalDate;
import java.util.UUID;

public interface ReportService {

    TrialBalanceResponse generateTrialBalance(LocalDate startDate, LocalDate endDate);

    LedgerReportResponse generateGeneralLedger(String accountCode, LocalDate startDate, LocalDate endDate);

    PartyAuxiliaryReportResponse generatePartyAuxiliary(UUID accountId, UUID partyId, LocalDate startDate, LocalDate endDate);

    IncomeStatementResponse generateIncomeStatement(LocalDate startDate, LocalDate endDate);

    BalanceSheetResponse generateBalanceSheet(LocalDate asOfDate);

    PartyTrialBalanceResponse generatePartyTrialBalance(LocalDate startDate, LocalDate endDate, UUID partyId);

    PartyIncomeStatementResponse generatePartyIncomeStatement(LocalDate startDate, LocalDate endDate, UUID partyId);

    PartyBalanceSheetResponse generatePartyBalanceSheet(LocalDate asOfDate, UUID partyId);
}

