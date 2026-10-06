package com.app.accounting.voucher.repository;

import java.math.BigDecimal;
import java.util.UUID;

public interface PartyAccountBalanceProjection {
    UUID getAccountId();
    String getAccountCode();
    String getAccountName();
    String getAccountNature();
    UUID getPartyId();
    String getPartyIdentification();
    String getPartyName();
    BigDecimal getTotalDebit();
    BigDecimal getTotalCredit();
}
