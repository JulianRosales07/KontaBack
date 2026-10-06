package com.app.accounting.tax.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxCalculationRequest {

    @NotNull(message = "La base gravable (subtotal) es obligatoria")
    @DecimalMin(value = "0.0", message = "La base gravable no puede ser negativa")
    private BigDecimal subtotal;

    @Builder.Default
    private boolean isSale = true; // true para Venta/Facturación, false para Compra/Gasto

    @Builder.Default
    private List<UUID> taxTemplateIds = new ArrayList<>();
}
