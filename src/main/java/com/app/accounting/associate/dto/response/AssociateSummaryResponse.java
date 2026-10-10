package com.app.accounting.associate.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Fila del directorio de asociados / cuentas por pagar (aportes y ahorros separados). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssociateSummaryResponse {
    private UUID id;
    private String associateCode;
    private String fullName;
    private String idType;
    private String identificationNumber;
    private String email;
    private String phone;
    private String address;
    private String classification;
    private boolean associate;
    /** ACTIVE | INACTIVE */
    private String status;

    private int activeLoans;
    private BigDecimal loansBalance;

    /** Aportes sociales acumulados (EQUITY_SAVING). */
    private BigDecimal contributions;
    /** Ahorros acumulados (SAVINGS). */
    private BigDecimal savings;
    /** Aportes + ahorros (obligación total con el asociado). */
    private BigDecimal payableBalance;
    private long movements;
    private LocalDate lastMovementDate;
}
