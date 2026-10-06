package com.app.accounting.report.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartyIncomeItemResponse {
    private UUID partyId;
    private String partyIdentification;
    private String partyName;
    private BigDecimal totalRevenues;
    private BigDecimal totalCosts;
    private BigDecimal grossProfit;
    private BigDecimal totalExpenses;
    private BigDecimal netResult;
    private List<FinancialReportItemResponse> revenueItems;
    private List<FinancialReportItemResponse> costItems;
    private List<FinancialReportItemResponse> expenseItems;
}
