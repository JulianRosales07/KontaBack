package com.app.accounting.loan.dto.request;

import com.app.accounting.loan.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanPaymentRequest {
    @NotNull(message = "La fecha del abono es obligatoria")
    private LocalDate paymentDate;

    @NotNull(message = "El medio de pago es obligatorio")
    private PaymentMethod paymentMethod;

    /** Cuenta de caja/banco que recibe el dinero (por defecto 110505 en efectivo, 111005 en los demás medios). */
    private String cashAccountCode;

    /** Comprobante externo: número de consignación, transferencia o recibo físico. */
    @Size(max = 100)
    private String reference;

    @Size(max = 500)
    private String notes;

    @NotEmpty(message = "Debe indicar al menos una cuota a abonar")
    @Valid
    @Builder.Default
    private List<Allocation> allocations = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Allocation {
        @NotNull(message = "La cuota es obligatoria")
        private UUID installmentId;

        @NotNull(message = "El valor abonado es obligatorio")
        @DecimalMin(value = "1", message = "El abono debe ser mayor a cero")
        private BigDecimal amount;
    }
}
