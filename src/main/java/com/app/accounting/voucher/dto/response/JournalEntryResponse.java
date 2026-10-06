package com.app.accounting.voucher.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryResponse {
    private UUID id;
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private UUID thirdPartyId;
    private String thirdPartyIdentification;
    private String thirdPartyName;
    private String referenceNumber;
    private String description;
    private BigDecimal debit;
    private BigDecimal credit;
}
