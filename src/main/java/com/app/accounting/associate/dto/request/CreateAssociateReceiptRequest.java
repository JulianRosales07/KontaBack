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

    /** Conceptos del recibo. Puede ir vacío si el recibo solo paga multas ({@link #fineIds}). */
    @Valid
    private List<ReceiptItemRequest> items;

    /** Multas pendientes del asociado que se pagan en este recibo (se agregan como ingreso 429510). */
    private List<UUID> fineIds;

    private String notes;

    /**
     * Abono a préstamo incluido en el mismo recaudo (cuota mensual + cuota del préstamo).
     * Se registra en la misma transacción: si falla una parte, no se guarda ninguna.
     */
    @Valid
    private LoanPortion loanPayment;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoanPortion {
        @NotNull(message = "El préstamo es obligatorio")
        private UUID loanId;

        private com.app.accounting.loan.entity.PaymentMethod paymentMethod;

        @NotEmpty(message = "Debe indicar las cuotas del préstamo a abonar")
        @Valid
        private List<com.app.accounting.loan.dto.request.LoanPaymentRequest.Allocation> allocations;
    }
}
