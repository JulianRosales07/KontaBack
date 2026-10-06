package com.app.accounting.report.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartyAuxiliaryEntryResponse {
    private LocalDate issueDate;
    private String voucherNumber;
    private String referenceNumber;
    private String description;
    private BigDecimal debit;
    private BigDecimal credit;
    private BigDecimal runningBalance;
}
