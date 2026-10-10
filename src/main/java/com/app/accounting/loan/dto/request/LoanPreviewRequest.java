package com.app.accounting.loan.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanPreviewRequest {
    @NotNull(message = "El valor del préstamo es obligatorio")
    @DecimalMin(value = "1", message = "El valor del préstamo debe ser mayor a cero")
    private BigDecimal principalAmount;

    @NotNull(message = "La tasa de interés es obligatoria")
    @DecimalMin(value = "0", message = "La tasa no puede ser negativa")
    private BigDecimal monthlyRate;

    @NotNull(message = "El número de cuotas es obligatorio")
    @Min(value = 1, message = "Debe haber al menos una cuota")
    @Max(value = 360, message = "Máximo 360 cuotas")
    private Integer termMonths;

    @NotNull(message = "La fecha de la primera cuota es obligatoria")
    private LocalDate firstPaymentDate;
}
