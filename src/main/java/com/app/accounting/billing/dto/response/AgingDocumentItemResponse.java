package com.app.accounting.billing.dto.response;

import com.app.accounting.billing.entity.DocumentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgingDocumentItemResponse {
    private UUID documentId;
    private String documentNumber;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private long daysOverdue; // 0 si no está vencida, >0 si está vencida
    private BigDecimal originalAmount;
    private BigDecimal remainingBalance;
    private DocumentStatus status;
    private String bucket; // CURRENT, DAYS_1_TO_30, DAYS_31_TO_60, DAYS_61_TO_90, OVER_90
}
