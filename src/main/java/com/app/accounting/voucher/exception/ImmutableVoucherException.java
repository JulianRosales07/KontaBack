package com.app.accounting.voucher.exception;

import com.app.accounting.common.exception.BusinessRuleException;

public class ImmutableVoucherException extends BusinessRuleException {
    public ImmutableVoucherException(String voucherNumber) {
        super(String.format("El comprobante contable '%s' ya se encuentra asentado (POSTED) y es inmutable. Para anularlo o corregirlo debe generar un comprobante de contrapartida (reversión).", voucherNumber));
    }
}
