package com.app.accounting.voucher.repository;

import java.math.BigDecimal;

public interface PartyBalanceProjection {
    BigDecimal getTotalDebit();
    BigDecimal getTotalCredit();
}
