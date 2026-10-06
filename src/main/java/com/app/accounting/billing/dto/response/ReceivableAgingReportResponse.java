package com.app.accounting.billing.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceivableAgingReportResponse {
    private LocalDate asOfDate;
    private BigDecimal totalReceivables;
    private BigDecimal totalCurrent;
    private BigDecimal total1To30;
    private BigDecimal total31To60;
    private BigDecimal total61To90;
    private BigDecimal totalOver90;
    private int customerCount;
    private int documentCount;
    private List<CustomerAgingResponse> customers;
}
