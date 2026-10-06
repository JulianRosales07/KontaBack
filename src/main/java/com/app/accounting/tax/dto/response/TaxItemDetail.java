package com.app.accounting.tax.dto.response;

import com.app.accounting.puc.entity.AccountNature;
import com.app.accounting.tax.entity.TaxType;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxItemDetail {
    private UUID templateId;
    private String name;
    private TaxType taxType;
    private BigDecimal ratePercentage;
    private BigDecimal baseAmount;
    private BigDecimal taxAmount;
    private String accountCode;
    private AccountNature accountNature; // DEBIT o CREDIT
    private boolean isRetention; // true si es retención (ReteFuente, ReteICA)
}
