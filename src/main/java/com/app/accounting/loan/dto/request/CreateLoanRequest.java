package com.app.accounting.loan.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateLoanRequest {
    @NotNull(message = "El asociado es obligatorio")
    private UUID thirdPartyId;

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

    @NotNull(message = "La fecha de desembolso es obligatoria")
    private LocalDate disbursementDate;

    @NotNull(message = "La fecha de la primera cuota es obligatoria")
    private LocalDate firstPaymentDate;

    /** Caja o banco desde donde sale el dinero (por defecto 110505). */
    private String disbursementAccountCode;
    /** Cuenta de cartera (por defecto 136505). */
    private String loanAccountCode;
    /** Cuenta de ingreso por intereses (por defecto 421005). */
    private String interestAccountCode;

    @Size(max = 500)
    private String notes;
}
