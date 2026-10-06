package com.app.accounting.voucher.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class JournalEntryRequest {

    private UUID accountId;

    @NotBlank(message = "El código contable es obligatorio")
    private String accountCode;

    private UUID thirdPartyId;
    private String thirdPartyIdentification;

    @Size(max = 50, message = "El número de referencia no puede superar 50 caracteres")
    private String referenceNumber;

    @Size(max = 500, message = "La descripción no puede superar 500 caracteres")
    private String description;

    @NotNull(message = "El valor débito es obligatorio")
    @DecimalMin(value = "0.0", inclusive = true, message = "El débito no puede ser negativo")
    @Builder.Default
    private BigDecimal debit = BigDecimal.ZERO;

    @NotNull(message = "El valor crédito es obligatorio")
    @DecimalMin(value = "0.0", inclusive = true, message = "El crédito no puede ser negativo")
    @Builder.Default
    private BigDecimal credit = BigDecimal.ZERO;
}
