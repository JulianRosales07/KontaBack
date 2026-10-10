package com.app.accounting.associate.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAssociateReceiptRequest {

    @NotNull(message = "El ID del asociado (tercero) es obligatorio")
    private UUID thirdPartyId;

    private UUID templateId;

    @NotNull(message = "La fecha de pago es obligatoria")
    private LocalDate paymentDate;

    /** ID de la cuenta de caja/banco; alternativamente cashAccountCode (por defecto 110505). */
    private UUID cashAccountId;

    private String cashAccountCode;

    @NotNull(message = "El monto total es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto total debe ser mayor a 0")
    private BigDecimal totalAmount;

    @NotEmpty(message = "Debe incluir al menos un concepto en el desglose")
    @Valid
    private List<ReceiptItemRequest> items;

    private String notes;
}
