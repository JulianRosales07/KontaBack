package com.app.accounting.associate.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssociateStatementResponse {
    private UUID thirdPartyId;
    private String fullName;
    private String identificationNumber;
    private String email;
    private String phone;

    // Totals by Category
    private BigDecimal totalEquitySavings;     // Total aportes acumulados (Patrimonio reembolsable)
    private BigDecimal totalSavings;           // Total ahorros acumulados (separado de los aportes)
    private BigDecimal totalSocialFunds;       // Total aportado a fondos sociales
    private BigDecimal totalAdministrationFees;// Total cuotas de sostenimiento pagadas
    private BigDecimal totalPaidOverall;       // Total global aportado

    private List<AssociateReceiptResponse> receiptsHistory;
}
