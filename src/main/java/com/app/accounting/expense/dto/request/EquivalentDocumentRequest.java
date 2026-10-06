package com.app.accounting.expense.dto.request;

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
public class EquivalentDocumentRequest {

    @NotBlank(message = "El número de documento equivalente / soporte es obligatorio (ej: DS-001 o DOC-EQ-101)")
    private String documentNumber;

    private UUID thirdPartyId;
    private String thirdPartyIdentification;

    @NotNull(message = "La fecha de emisión es obligatoria")
    private LocalDate issueDate;

    private LocalDate dueDate;

    @Builder.Default
    private String expenseAccountCode = "513505"; // Cuenta PUC de gasto (513505 Servicios, 5110 Honorarios, 5120 Arrendamientos, etc.)

    @NotNull(message = "El valor o base del documento equivalente es obligatorio")
    @DecimalMin(value = "0.01", message = "El subtotal debe ser mayor a cero")
    private BigDecimal subtotal;

    @Builder.Default
    private List<UUID> taxTemplateIds = new ArrayList<>(); // Retenciones en la fuente aplicables

    private String description;

    @Builder.Default
    private boolean payImmediately = false; // true si se crea como recibo de egreso directo de contado

    private UUID cashAccountId;
    private String cashAccountCode; // Default: 110505 si payImmediately es true
}
