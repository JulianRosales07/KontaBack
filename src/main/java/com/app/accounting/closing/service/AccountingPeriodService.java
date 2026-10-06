package com.app.accounting.closing.service;

import com.app.accounting.closing.dto.response.AccountingPeriodResponse;

import java.time.LocalDate;
import java.util.List;

public interface AccountingPeriodService {
    AccountingPeriodResponse openPeriod(Integer year, Integer month);
    AccountingPeriodResponse closePeriod(Integer year, Integer month, String closedBy);
    List<AccountingPeriodResponse> getPeriodsByYear(Integer year);
    boolean isPeriodOpen(LocalDate date);
    void validatePeriodIsOpen(LocalDate date);
}
