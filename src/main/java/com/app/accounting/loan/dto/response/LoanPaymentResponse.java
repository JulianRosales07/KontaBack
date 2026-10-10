package com.app.accounting.loan.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Recibo de abono a préstamo (agrupa las cuotas pagadas con el mismo número de recibo). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanPaymentResponse {
    private String receiptNumber;
    private LocalDate paymentDate;
    private UUID loanId;
    private String loanNumber;
    private UUID thirdPartyId;
    private String thirdPartyName;
    private String thirdPartyIdNumber;
    private String paymentMethod;
    private String cashAccountCode;
    private String cashAccountName;
    private UUID voucherId;
    private String voucherNumber;
    private String reference;
    private String notes;
    private BigDecimal totalAmount;
    private BigDecimal principalApplied;
    private BigDecimal interestApplied;
    private BigDecimal loanOutstandingAfter;
    private String createdBy;
    private List<Line> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        private UUID installmentId;
        private int installmentNumber;
        private LocalDate dueDate;
        private BigDecimal amount;
        private BigDecimal principal;
        private BigDecimal interest;
        private String installmentStatus;
    }
}
