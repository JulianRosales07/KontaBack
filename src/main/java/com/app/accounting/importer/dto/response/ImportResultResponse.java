package com.app.accounting.importer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResultResponse {
    private boolean success;
    private UUID voucherId;
    private String voucherNumber;
    private int totalRowsProcessed;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private String message;
    @Builder.Default
    private List<RowErrorDetail> errors = new ArrayList<>();
}
