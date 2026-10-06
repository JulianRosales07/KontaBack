package com.app.accounting.voucher.repository;

import java.math.BigDecimal;
import java.util.UUID;

public interface AccountBalanceProjection {
    UUID getAccountId();
    String getAccountCode();
    String getAccountName();
    String getAccountNature();
    BigDecimal getTotalDebit();
    BigDecimal getTotalCredit();
}
