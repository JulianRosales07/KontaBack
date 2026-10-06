package com.app.accounting.puc.exception;

import com.app.accounting.common.exception.ResourceNotFoundException;

import java.util.UUID;

public class AccountNotFoundException extends ResourceNotFoundException {
    public AccountNotFoundException(String code) {
        super("Cuenta contable con código '" + code + "' no fue encontrada");
    }

    public AccountNotFoundException(UUID id) {
        super("Cuenta contable con ID '" + id + "' no fue encontrada");
    }
}
