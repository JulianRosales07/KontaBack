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
public class LedgerReportResponse {
    private String accountCode;
    private String accountName;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal initialBalance;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private BigDecimal finalBalance;
    @Builder.Default
    private List<LedgerEntryResponse> entries = new ArrayList<>();
}
