package com.app.accounting.voucher.dto.response;

import com.app.accounting.voucher.entity.VoucherStatus;
import com.app.accounting.voucher.entity.VoucherType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalVoucherResponse {
    private UUID id;
    private String voucherNumber;
    private VoucherType voucherType;
    private LocalDate issueDate;
    private String description;
    private VoucherStatus status;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    @Builder.Default
    private List<JournalEntryResponse> entries = new ArrayList<>();
}
