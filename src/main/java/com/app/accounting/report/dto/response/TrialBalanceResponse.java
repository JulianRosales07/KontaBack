package com.app.accounting.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrialBalanceResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalInitialDebit;
    private BigDecimal totalInitialCredit;
    private BigDecimal totalPeriodDebit;
    private BigDecimal totalPeriodCredit;
    private BigDecimal totalFinalDebit;
    private BigDecimal totalFinalCredit;
    @Builder.Default
    private List<TrialBalanceItemResponse> items = new ArrayList<>();
}
