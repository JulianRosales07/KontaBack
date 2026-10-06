package com.app.accounting.report.dto.response;

import com.app.accounting.puc.entity.AccountNature;
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
public class TrialBalanceItemResponse {
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private AccountNature nature;
    private BigDecimal initialBalance;
    private BigDecimal periodDebit;
    private BigDecimal periodCredit;
    private BigDecimal finalBalance;
}
