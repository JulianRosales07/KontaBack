package com.app.accounting.closing.service.impl;

import com.app.accounting.closing.dto.response.AccountingPeriodResponse;
import com.app.accounting.closing.entity.AccountingPeriod;
import com.app.accounting.closing.entity.PeriodStatus;
import com.app.accounting.closing.repository.AccountingPeriodRepository;
import com.app.accounting.closing.service.AccountingPeriodService;
import com.app.accounting.common.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountingPeriodServiceImpl implements AccountingPeriodService {

    private final AccountingPeriodRepository periodRepository;

    @Override
    @Transactional
    public AccountingPeriodResponse openPeriod(Integer year, Integer month) {
        validateMonth(month);
        AccountingPeriod period = periodRepository.findByYearAndMonth(year, month)
                .orElseGet(() -> AccountingPeriod.builder()
                        .year(year)
                        .month(month)
                        .build());

        period.setStatus(PeriodStatus.OPEN);
        period.setClosedAt(null);
        period.setClosedBy(null);

        AccountingPeriod saved = periodRepository.save(period);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public AccountingPeriodResponse closePeriod(Integer year, Integer month, String closedBy) {
        validateMonth(month);
        AccountingPeriod period = periodRepository.findByYearAndMonth(year, month)
                .orElseGet(() -> AccountingPeriod.builder()
                        .year(year)
                        .month(month)
                        .build());

        period.setStatus(PeriodStatus.CLOSED);
        period.setClosedAt(Instant.now());
        period.setClosedBy(closedBy != null ? closedBy : "SYSTEM");

        AccountingPeriod saved = periodRepository.save(period);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountingPeriodResponse> getPeriodsByYear(Integer year) {
        return periodRepository.findByYearOrderByMonthAsc(year)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPeriodOpen(LocalDate date) {
        if (date == null) {
            return true;
        }
        int year = date.getYear();
        int month = date.getMonthValue();
        return periodRepository.findByYearAndMonth(year, month)
                .map(p -> p.getStatus() == PeriodStatus.OPEN)
                .orElse(true);
    }

    @Override
    @Transactional(readOnly = true)
    public void validatePeriodIsOpen(LocalDate date) {
        if (!isPeriodOpen(date)) {
            int year = date.getYear();
            int month = date.getMonthValue();
            throw new BusinessRuleException(String.format(
                    "El periodo contable %d-%02d se encuentra CERRADO. No se permiten registros ni modificaciones contables.",
                    year, month
            ));
        }
    }

    private void validateMonth(Integer month) {
        if (month == null || month < 1 || month > 12) {
            throw new BusinessRuleException("El mes debe ser un valor entre 1 y 12");
        }
    }

    private AccountingPeriodResponse toResponse(AccountingPeriod p) {
        return AccountingPeriodResponse.builder()
                .id(p.getId())
                .year(p.getYear())
                .month(p.getMonth())
                .status(p.getStatus())
                .closedAt(p.getClosedAt())
                .closedBy(p.getClosedBy())
                .build();
    }
}
