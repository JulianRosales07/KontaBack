package com.app.accounting.closing.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnualClosingResponse {
    private Integer year;
    private String voucherNumber;
    private UUID voucherId;
    private BigDecimal totalRevenues;
    private BigDecimal totalExpensesAndCosts;
    private BigDecimal netResult;
    private String resultType; // UTILIDAD, PERDIDA, EQUILIBRIO
    private String destinationAccountCode; // 3605 o 3610
    private String message;
}
