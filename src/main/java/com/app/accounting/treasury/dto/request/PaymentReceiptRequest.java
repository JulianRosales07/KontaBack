package com.app.accounting.treasury.dto.request;

import com.app.accounting.treasury.entity.PaymentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentReceiptRequest {

    @NotNull(message = "El tipo de pago es obligatorio (CASH_IN para recibo de caja, CASH_OUT para egreso)")
    private PaymentType paymentType;

    @NotBlank(message = "El número de recibo o comprobante es obligatorio (ej: RC-8402 o CE-1020)")
    private String receiptNumber;

    @NotNull(message = "La fecha de pago es obligatoria")
    private LocalDate paymentDate;

    @NotNull(message = "El ID de la factura o documento de deuda asociado es obligatorio")
    private UUID debtDocumentId;

    @Builder.Default
    private String cashAccountCode = "110505"; // Por defecto Caja General (110505) o Bancos (111005)

    private UUID cashAccountId;

    @NotNull(message = "El monto pagado es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto pagado debe ser mayor a cero")
    private BigDecimal amountPaid;

    private String description;
}
