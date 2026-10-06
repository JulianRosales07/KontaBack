package com.app.accounting.voucher.exception;

import com.app.accounting.common.exception.ResourceNotFoundException;

import java.util.UUID;

public class VoucherNotFoundException extends ResourceNotFoundException {
    public VoucherNotFoundException(UUID id) {
        super("Comprobante contable con ID '" + id + "' no fue encontrado");
    }

    public VoucherNotFoundException(String voucherNumber) {
        super("Comprobante contable con número '" + voucherNumber + "' no fue encontrado");
    }
}
