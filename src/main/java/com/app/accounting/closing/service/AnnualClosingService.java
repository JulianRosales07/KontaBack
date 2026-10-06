package com.app.accounting.closing.service;

import com.app.accounting.closing.dto.response.AnnualClosingResponse;

public interface AnnualClosingService {
    AnnualClosingResponse executeYearEndClosing(int year);
}
