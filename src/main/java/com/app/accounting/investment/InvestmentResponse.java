package com.app.accounting.investment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InvestmentResponse(UUID id, String entityName, String instrumentType, String instrumentNumber,
                                 String accountCode, String accountName, BigDecimal principal,
                                 BigDecimal accruedInterest, BigDecimal total, BigDecimal annualRate,
                                 LocalDate startDate, LocalDate maturityDate, String status, String notes) {

    public static InvestmentResponse of(Investment i) {
        return new InvestmentResponse(i.getId(), i.getEntityName(), i.getInstrumentType(), i.getInstrumentNumber(),
                i.getAccount().getCode(), i.getAccount().getName(), i.getPrincipal(), i.getAccruedInterest(),
                i.getPrincipal().add(i.getAccruedInterest()), i.getAnnualRate(), i.getStartDate(), i.getMaturityDate(),
                i.getStatus(), i.getNotes());
    }
}
