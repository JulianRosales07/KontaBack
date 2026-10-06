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
public class PartyIncomeStatementResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalRevenues;
    private BigDecimal totalCosts;
    private BigDecimal grossProfit;
    private BigDecimal totalExpenses;
    private BigDecimal netOperatingIncome;
    private List<PartyIncomeItemResponse> parties;
}
