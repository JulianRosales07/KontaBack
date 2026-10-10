package com.app.accounting.fine;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Peticiones y respuestas del módulo de multas. */
public final class FineDtos {

    private FineDtos() {
    }

    public record FineTypeRequest(
            @NotBlank(message = "El nombre de la multa es obligatorio") @Size(max = 120) String name,
            @Size(max = 300) String description,
            @NotNull(message = "El valor es obligatorio") @DecimalMin(value = "1", message = "El valor debe ser mayor a cero") BigDecimal amount,
            Boolean active) {
    }

    public record FineTypeResponse(UUID id, String name, String description, BigDecimal amount, boolean active, boolean used) {
    }

    /** Multa para uno o varios asociados (p. ej. todos los que no asistieron a una reunión). */
    public record RegisterFinesRequest(
            @NotNull(message = "El tipo de multa es obligatorio") UUID fineTypeId,
            @NotNull(message = "La fecha es obligatoria") LocalDate fineDate,
            @Size(max = 300) String description,
            /** Valor distinto al del tipo para este registro (opcional). */
            @DecimalMin(value = "1", message = "El valor debe ser mayor a cero") BigDecimal amount,
            @NotEmpty(message = "Seleccione al menos un asociado") List<UUID> thirdPartyIds) {
    }

    public record RegisterFinesResponse(int created, List<String> skipped, BigDecimal total) {
    }

    public record PayFinesRequest(
            @NotEmpty(message = "Seleccione al menos una multa") List<UUID> fineIds,
            @NotNull(message = "La fecha de pago es obligatoria") LocalDate paymentDate,
            /** Cuenta de caja o bancos (por defecto 110505). */
            String cashAccountCode) {
    }

    public record PayFinesResponse(List<String> receipts, int finesPaid, BigDecimal total) {
    }

    public record VoidFineRequest(@NotBlank(message = "Indique el motivo") @Size(max = 300) String reason) {
    }

    public record FineResponse(UUID id, UUID thirdPartyId, String thirdPartyName, String thirdPartyDocument,
                               UUID fineTypeId, String fineTypeName, LocalDate fineDate, BigDecimal amount,
                               String description, String status, LocalDate paidDate, String receiptNumber,
                               UUID voucherId, String voidReason) {

        static FineResponse of(AssociateFine f) {
            return new FineResponse(f.getId(), f.getThirdParty().getId(), f.getThirdParty().getFullName(),
                    f.getThirdParty().getIdentificationNumber(), f.getFineType().getId(), f.getFineType().getName(),
                    f.getFineDate(), f.getAmount(), f.getDescription(), f.getStatus().name(), f.getPaidDate(),
                    f.getReceiptNumber(), f.getVoucher() != null ? f.getVoucher().getId() : null, f.getVoidReason());
        }
    }

    public record FineSummary(long pendingCount, BigDecimal pendingTotal, long paidCount, BigDecimal paidTotal,
                              long voidedCount, BigDecimal voidedTotal, long associatesWithPending) {
    }
}
