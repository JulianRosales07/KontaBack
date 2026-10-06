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
public class PartyBalanceSheetResponse {
    private LocalDate asOfDate;
    private BigDecimal totalAssets;
    private BigDecimal totalLiabilities;
    private BigDecimal totalEquity;
    private BigDecimal periodIncome;
    private BigDecimal totalLiabilitiesAndEquity;
    private boolean isBalanced;
    private List<PartyBalanceItemResponse> assetItems;
    private List<PartyBalanceItemResponse> liabilityItems;
    private List<PartyBalanceItemResponse> equityItems;
}
