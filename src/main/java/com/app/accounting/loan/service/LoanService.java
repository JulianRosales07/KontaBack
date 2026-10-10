package com.app.accounting.loan.service;

import com.app.accounting.loan.dto.request.CreateLoanRequest;
import com.app.accounting.loan.dto.request.LegacyLoanImportRequest;
import com.app.accounting.loan.dto.request.LoanPaymentRequest;
import com.app.accounting.loan.dto.request.LoanPreviewRequest;
import com.app.accounting.loan.dto.response.LegacyImportResultResponse;
import com.app.accounting.loan.dto.response.LoanPaymentResponse;
import com.app.accounting.loan.dto.response.LoanPreviewResponse;
import com.app.accounting.loan.dto.response.LoanResponse;
import com.app.accounting.loan.entity.LoanStatus;

import java.util.List;
import java.util.UUID;

public interface LoanService {

    LoanPreviewResponse preview(LoanPreviewRequest request);

    LoanResponse createLoan(CreateLoanRequest request);

    List<LoanResponse> getLoans(LoanStatus status, UUID thirdPartyId, boolean includeInstallments);

    LoanResponse getLoan(UUID id);

    LoanPaymentResponse registerPayment(UUID loanId, LoanPaymentRequest request);

    List<LoanPaymentResponse> getPayments(UUID loanId);

    LoanPaymentResponse getReceipt(String receiptNumber);

    List<LoanPaymentResponse> getPaymentsBetween(java.time.LocalDate from, java.time.LocalDate to);

    LegacyImportResultResponse importLegacy(LegacyLoanImportRequest request);
}
