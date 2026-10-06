package com.app.accounting.billing.dto.response;

import com.app.accounting.billing.entity.DebtType;
import com.app.accounting.billing.entity.DocumentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtDocumentResponse {
    private UUID id;
    private DebtType type;
    private String documentNumber;
    private UUID thirdPartyId;
    private String thirdPartyName;
    private String thirdPartyIdentification;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private BigDecimal subtotal;
    private BigDecimal taxTotal;
    private BigDecimal retentionTotal;
    private BigDecimal totalAmount;
    private BigDecimal remainingBalance;
    private DocumentStatus status;
    private UUID voucherId;
    private String voucherNumber;
}
