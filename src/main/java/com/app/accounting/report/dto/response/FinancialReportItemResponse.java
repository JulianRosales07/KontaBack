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
public class FinancialReportItemResponse {
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private AccountNature nature;
    private BigDecimal debit;
    private BigDecimal credit;
    private BigDecimal netBalance;
}
