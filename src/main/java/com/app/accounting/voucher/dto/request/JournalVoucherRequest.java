package com.app.accounting.voucher.dto.request;

import com.app.accounting.voucher.entity.VoucherStatus;
import com.app.accounting.voucher.entity.VoucherType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalVoucherRequest {

    @NotBlank(message = "El número de comprobante es obligatorio")
    @Size(max = 50, message = "El número de comprobante no puede exceder 50 caracteres")
    private String voucherNumber;

    @NotNull(message = "El tipo de comprobante es obligatorio")
    private VoucherType voucherType;

    @NotNull(message = "La fecha de emisión es obligatoria")
    private LocalDate issueDate;

    @NotBlank(message = "La descripción o concepto es obligatoria")
    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
    private String description;

    @Builder.Default
    private VoucherStatus status = VoucherStatus.POSTED;

    @NotEmpty(message = "El comprobante debe contener al menos un asiento contable")
    @Valid
    @Builder.Default
    private List<JournalEntryRequest> entries = new ArrayList<>();
}
