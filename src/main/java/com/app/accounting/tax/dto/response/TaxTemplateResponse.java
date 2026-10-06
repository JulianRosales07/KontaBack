package com.app.accounting.tax.dto.response;

import com.app.accounting.tax.entity.TaxType;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxTemplateResponse {
    private UUID id;
    private String name;
    private TaxType taxType;
    private BigDecimal ratePercentage;
    private String salesAccountCode;
    private String purchaseAccountCode;
    private Boolean isActive;
}
