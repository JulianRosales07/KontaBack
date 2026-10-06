package com.app.accounting.billing.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerAgingResponse {
    private UUID thirdPartyId;
    private String identificationNumber;
    private String fullName;
    private String phone;
    private String email;
    private BigDecimal totalBalance;
    private BigDecimal currentBalance;   // Por vencer (al día)
    private BigDecimal balance1To30;     // 1 a 30 días de mora
    private BigDecimal balance31To60;    // 31 a 60 días de mora
    private BigDecimal balance61To90;    // 61 a 90 días de mora
    private BigDecimal balanceOver90;    // Más de 90 días de mora
    private List<AgingDocumentItemResponse> documents;
}
