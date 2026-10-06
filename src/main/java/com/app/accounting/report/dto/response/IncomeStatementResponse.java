package com.app.accounting.report.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncomeStatementResponse {
    private LocalDate startDate;
    private LocalDate endDate;

    // Totales Agregados
    private BigDecimal totalRevenues;    // Sum(Créditos 4 - Débitos 4)
    private BigDecimal totalCosts;       // Sum(Débitos 6 - Créditos 6)
    private BigDecimal grossProfit;      // totalRevenues - totalCosts
    private BigDecimal totalExpenses;    // Sum(Débitos 5 - Créditos 5)
    private BigDecimal netOperatingIncome; // Utilidad del periodo: (Créditos 4 - Débitos 4) - (Débitos 5 y 6 - Créditos 5 y 6)

    @Builder.Default
    private List<FinancialReportItemResponse> revenueItems = new ArrayList<>(); // Clase 4

    @Builder.Default
    private List<FinancialReportItemResponse> costItems = new ArrayList<>();    // Clase 6

    @Builder.Default
    private List<FinancialReportItemResponse> expenseItems = new ArrayList<>(); // Clase 5
}
