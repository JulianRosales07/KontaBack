package com.app.accounting.associate.dto.response;

import com.app.accounting.associate.entity.QuotaCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssociateReceiptResponse {
    private UUID id;
    private String receiptNumber;
    private UUID thirdPartyId;
    private String thirdPartyName;
    private String thirdPartyIdNumber;
    private UUID templateId;
    private String templateName;
    private LocalDate paymentDate;
    private BigDecimal totalAmount;
    private UUID cashAccountId;
    private String cashAccountCode;
    private String cashAccountName;
    private UUID voucherId;
    private String voucherNumber;
    private String notes;
    private Instant createdAt;
    private List<AssociateReceiptItemResponse> items;
    /** Abono a préstamo registrado junto con este recaudo (si se incluyó). */
    private com.app.accounting.loan.dto.response.LoanPaymentResponse loanPayment;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssociateReceiptItemResponse {
        private UUID id;
        private String conceptName;
        private QuotaCategory category;
        private UUID accountId;
        private String accountCode;
        private String accountName;
        private BigDecimal amount;
    }
}
