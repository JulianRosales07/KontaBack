package com.app.accounting.tax.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxCalculationResult {
    private BigDecimal subtotal;
    private BigDecimal taxTotal;         // Sum of IVA (+ Impoconsumo)
    private BigDecimal retentionTotal;   // Sum of Retefuente + ReteICA
    private BigDecimal totalAmount;      // subtotal + taxTotal - retentionTotal
    @Builder.Default
    private List<TaxItemDetail> items = new ArrayList<>();
}
