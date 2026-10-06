package com.app.accounting.associate.dto.response;

import com.app.accounting.associate.entity.QuotaCategory;
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
public class QuotaTemplateItemResponse {
    private UUID id;
    private String conceptName;
    private QuotaCategory category;
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private BigDecimal amount;
    private BigDecimal percentage;
    private Boolean isMandatory;
    private Integer displayOrder;
}
