package com.app.accounting.investment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestmentRequest(
        @NotBlank(message = "La entidad es obligatoria") @Size(max = 150) String entityName,
        @Size(max = 30) String instrumentType,
        @Size(max = 60) String instrumentNumber,
        @NotBlank(message = "La cuenta PUC es obligatoria") String accountCode,
        @NotNull(message = "El capital es obligatorio") @DecimalMin("0.0") BigDecimal principal,
        @DecimalMin("0.0") BigDecimal accruedInterest,
        BigDecimal annualRate,
        LocalDate startDate,
        LocalDate maturityDate,
        @Size(max = 15) String status,
        @Size(max = 500) String notes) {
}
