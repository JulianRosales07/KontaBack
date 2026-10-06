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
public class BalanceSheetResponse {
    private LocalDate asOfDate;

    // Totales Clases 1, 2 y 3
    private BigDecimal totalAssets;                // Clase 1
    private BigDecimal totalLiabilities;           // Clase 2
    private BigDecimal totalEquity;                // Clase 3 (Capital, reservas, etc.)
    private BigDecimal periodIncome;               // Utilidad/Pérdida acumulada del ejercicio
    private BigDecimal totalLiabilitiesAndEquity;  // totalLiabilities + totalEquity + periodIncome
    private boolean isBalanced;                    // totalAssets == totalLiabilitiesAndEquity

    @Builder.Default
    private List<FinancialReportItemResponse> assetItems = new ArrayList<>();     // Clase 1

    @Builder.Default
    private List<FinancialReportItemResponse> liabilityItems = new ArrayList<>(); // Clase 2

    @Builder.Default
    private List<FinancialReportItemResponse> equityItems = new ArrayList<>();    // Clase 3
}
