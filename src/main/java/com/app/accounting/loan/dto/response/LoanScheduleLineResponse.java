package com.app.accounting.loan.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanScheduleLineResponse {
    private UUID id;
    private int number;
    private LocalDate dueDate;
    private BigDecimal openingBalance;
    private BigDecimal principal;
    private BigDecimal interest;
    private BigDecimal total;
    private BigDecimal closingBalance;
    private BigDecimal principalPaid;
    private BigDecimal interestPaid;
    private BigDecimal pending;
    /** PENDING | PARTIAL | PAID */
    private String status;
    private LocalDate paidDate;
    private boolean overdue;
    private long daysOverdue;
}
