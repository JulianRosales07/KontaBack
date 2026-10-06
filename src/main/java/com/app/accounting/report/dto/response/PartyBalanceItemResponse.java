package com.app.accounting.report.dto.response;

import com.app.accounting.puc.entity.AccountNature;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartyBalanceItemResponse {
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private AccountNature nature;
    private UUID partyId;
    private String partyIdentification;
    private String partyName;
    private BigDecimal debit;
    private BigDecimal credit;
    private BigDecimal netBalance;
}
