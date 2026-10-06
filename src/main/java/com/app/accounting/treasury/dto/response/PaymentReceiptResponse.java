package com.app.accounting.treasury.dto.response;

import com.app.accounting.billing.entity.DocumentStatus;
import com.app.accounting.treasury.entity.PaymentType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentReceiptResponse {
    private UUID id;
    private PaymentType paymentType;
    private String receiptNumber;
    private LocalDate paymentDate;
    private UUID debtDocumentId;
    private String debtDocumentNumber;
    private String thirdPartyName;
    private String cashAccountCode;
    private String cashAccountName;
    private BigDecimal amountPaid;
    private BigDecimal remainingDocumentBalance;
    private DocumentStatus documentStatus;
    private UUID voucherId;
    private String voucherNumber;
}
