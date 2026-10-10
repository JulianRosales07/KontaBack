package com.app.accounting.maintenance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseCleanupRequest {
    private boolean cleanAssociateReceipts;
    private boolean cleanLoans;
    private boolean cleanDebtAndTreasury;
    private boolean cleanJournalVouchers;
    private boolean cleanThirdParties;
    private boolean cleanQuotaTemplates;
}
