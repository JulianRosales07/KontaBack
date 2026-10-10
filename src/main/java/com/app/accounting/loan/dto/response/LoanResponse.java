package com.app.accounting.loan.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanResponse {
    private UUID id;
    private String loanNumber;
    private UUID thirdPartyId;
    private String thirdPartyName;
    private String thirdPartyIdNumber;
    private BigDecimal principalAmount;
    private BigDecimal monthlyRate;
    private int termMonths;
    private String amortizationType;
    private LocalDate disbursementDate;
    private LocalDate firstPaymentDate;
    /** ACTIVE | PAID | CANCELLED */
    private String status;
    /** AL_DIA | EN_MORA | PAGADO | ANULADO */
    private String portfolioStatus;
    private BigDecimal outstandingPrincipal;
    private BigDecimal paidPrincipal;
    private BigDecimal paidInterest;
    private BigDecimal totalInterest;
    private BigDecimal totalPayable;
    private BigDecimal firstInstallmentAmount;
    private int paidInstallments;
    private int pendingInstallments;
    private int overdueInstallments;
    private BigDecimal overdueAmount;
    private java.time.LocalDate nextDueDate;
    private BigDecimal nextDueAmount;
    private Integer nextInstallmentNumber;
    private String loanAccountCode;
    private String loanAccountName;
    private String interestAccountCode;
    private String interestAccountName;
    private String disbursementAccountCode;
    private String disbursementVoucherNumber;
    private boolean legacy;
    private String notes;
    private List<LoanScheduleLineResponse> installments;
}
