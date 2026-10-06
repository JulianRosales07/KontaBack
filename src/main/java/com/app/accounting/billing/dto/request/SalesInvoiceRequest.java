package com.app.accounting.billing.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesInvoiceRequest {

    @NotBlank(message = "El número de factura es obligatorio (ej: FAC-1001)")
    private String documentNumber;

    private UUID thirdPartyId;
    private String thirdPartyIdentification;

    @NotNull(message = "La fecha de emisión es obligatoria")
    private LocalDate issueDate;

    @NotNull(message = "La fecha de vencimiento es obligatoria")
    private LocalDate dueDate;

    @Builder.Default
    private String incomeAccountCode = "413505"; // Ingresos operacionales

    @NotNull(message = "La base gravable (subtotal) es obligatoria")
    @DecimalMin(value = "0.01", message = "El subtotal debe ser mayor a cero")
    private BigDecimal subtotal;

    @Builder.Default
    private List<UUID> taxTemplateIds = new ArrayList<>();

    private String description;
}
