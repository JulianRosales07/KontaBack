package com.app.accounting.party.exception;

import com.app.accounting.common.exception.ResourceNotFoundException;

import java.util.UUID;

public class ThirdPartyNotFoundException extends ResourceNotFoundException {
    public ThirdPartyNotFoundException(String identificationNumber) {
        super("Tercero con identificación '" + identificationNumber + "' no fue encontrado");
    }

    public ThirdPartyNotFoundException(UUID id) {
        super("Tercero con ID '" + id + "' no fue encontrado");
    }
}
