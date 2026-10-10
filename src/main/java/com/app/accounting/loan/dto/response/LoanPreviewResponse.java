package com.app.accounting.loan.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanPreviewResponse {
    private BigDecimal principalAmount;
    private BigDecimal monthlyRate;
    private int termMonths;
    private BigDecimal principalPerInstallment;
    private BigDecimal firstInstallment;
    private BigDecimal totalInterest;
    private BigDecimal totalPayable;
    private List<LoanScheduleLineResponse> schedule;
}
