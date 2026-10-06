package com.app.accounting.associate.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotaTemplateResponse {
    private UUID id;
    private String name;
    private String description;
    private BigDecimal totalAmount;
    private Boolean isActive;
    private List<QuotaTemplateItemResponse> items;
}
