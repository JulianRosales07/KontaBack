package com.app.accounting.report.dto.response;

import com.app.accounting.puc.entity.AccountNature;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartyAuxiliaryReportResponse {
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private AccountNature accountNature;

    private UUID partyId;
    private String partyIdentification;
    private String partyName;

    private LocalDate startDate;
    private LocalDate endDate;

    private BigDecimal initialBalance;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private BigDecimal finalBalance;

    @Builder.Default
    private List<PartyAuxiliaryEntryResponse> entries = new ArrayList<>();
}
