package com.app.accounting.tax.dto.request;

import com.app.accounting.tax.entity.TaxType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxTemplateRequest {

    @NotBlank(message = "El nombre de la plantilla de impuesto es obligatorio")
    private String name;

    @NotNull(message = "El tipo de impuesto es obligatorio (IVA, RETEFUENTE, RETEICA, IMPOCONSUMO)")
    private TaxType taxType;

    @NotNull(message = "La tasa porcentual es obligatoria")
    @DecimalMin(value = "0.0", inclusive = true, message = "La tasa no puede ser negativa")
    private BigDecimal ratePercentage;

    @NotBlank(message = "El código de cuenta para ventas es obligatorio")
    private String salesAccountCode;

    @NotBlank(message = "El código de cuenta para compras es obligatorio")
    private String purchaseAccountCode;

    @Builder.Default
    private Boolean isActive = true;
}
