package com.app.accounting.associate.dto.request;

import com.app.accounting.associate.entity.QuotaCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class ReceiptItemRequest {

    @NotBlank(message = "El nombre del concepto es obligatorio")
    private String conceptName;

    @NotNull(message = "La categoría contable es obligatoria")
    private QuotaCategory category;

    @NotNull(message = "El ID de la cuenta contable es obligatorio")
    private UUID accountId;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.0", inclusive = true, message = "El monto no puede ser negativo")
    private BigDecimal amount;
}
