package com.app.accounting.billing.service;

import com.app.accounting.billing.dto.request.SalesInvoiceRequest;
import com.app.accounting.billing.dto.response.DebtDocumentResponse;
import com.app.accounting.billing.dto.response.SalesInvoiceResponse;
import com.app.accounting.billing.dto.response.CustomerStatementResponse;
import com.app.accounting.billing.dto.response.ReceivableAgingReportResponse;
import com.app.accounting.billing.entity.DebtDocument;
import com.app.accounting.billing.entity.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface BillingService {

    SalesInvoiceResponse createSalesInvoice(SalesInvoiceRequest request);

    DebtDocumentResponse getDocumentById(UUID id);

    DebtDocumentResponse getDocumentByNumber(String documentNumber);

    Page<DebtDocumentResponse> getReceivables(UUID thirdPartyId, DocumentStatus status, Pageable pageable);

    DebtDocument getDebtDocumentEntity(UUID id);

    void updateRemainingBalance(DebtDocument document, java.math.BigDecimal amountPaid);

    ReceivableAgingReportResponse getReceivablesAging(LocalDate asOfDate, UUID thirdPartyId);

    CustomerStatementResponse getCustomerStatement(UUID thirdPartyId);

    byte[] exportCustomerStatement(UUID thirdPartyId, String format);
}

