package com.app.accounting.voucher.exception;

import com.app.accounting.common.exception.BusinessRuleException;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class UnbalancedVoucherException extends BusinessRuleException {

    private final BigDecimal totalDebit;
    private final BigDecimal totalCredit;
    private final BigDecimal difference;

    public UnbalancedVoucherException(BigDecimal totalDebit, BigDecimal totalCredit) {
        super(String.format(
                "El comprobante contable no cumple con la regla de partida doble. Total Débitos: %s, Total Créditos: %s, Diferencia: %s",
                totalDebit,
                totalCredit,
                totalDebit.subtract(totalCredit).abs()
        ));
        this.totalDebit = totalDebit;
        this.totalCredit = totalCredit;
        this.difference = totalDebit.subtract(totalCredit).abs();
    }
}
