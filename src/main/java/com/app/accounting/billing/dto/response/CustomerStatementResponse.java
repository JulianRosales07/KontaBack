package com.app.accounting.billing.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerStatementResponse {
    private UUID thirdPartyId;
    private String identificationNumber;
    private String fullName;
    private String phone;
    private String email;
    private BigDecimal totalInvoiced;
    private BigDecimal totalPaid;
    private BigDecimal currentBalance;
    private int totalInvoices;
    private int pendingInvoices;
    private int overdueInvoices;
    private List<DebtDocumentResponse> documents;
}
