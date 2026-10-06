package com.app.accounting.report.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartyTrialBalanceResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalInitialDebit;
    private BigDecimal totalInitialCredit;
    private BigDecimal totalPeriodDebit;
    private BigDecimal totalPeriodCredit;
    private BigDecimal totalFinalDebit;
    private BigDecimal totalFinalCredit;
    private List<PartyTrialBalanceItemResponse> items;
}
