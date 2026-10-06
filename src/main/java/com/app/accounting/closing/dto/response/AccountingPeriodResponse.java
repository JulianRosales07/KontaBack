package com.app.accounting.closing.dto.response;

import com.app.accounting.closing.entity.PeriodStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountingPeriodResponse {
    private UUID id;
    private Integer year;
    private Integer month;
    private PeriodStatus status;
    private Instant closedAt;
    private String closedBy;
}
