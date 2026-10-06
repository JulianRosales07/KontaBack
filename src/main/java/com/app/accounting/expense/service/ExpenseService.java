package com.app.accounting.expense.service;

import com.app.accounting.billing.dto.response.DebtDocumentResponse;
import com.app.accounting.billing.entity.DocumentStatus;
import com.app.accounting.expense.dto.request.EquivalentDocumentRequest;
import com.app.accounting.expense.dto.request.PurchaseInvoiceRequest;
import com.app.accounting.expense.dto.response.EquivalentDocumentResponse;
import com.app.accounting.expense.dto.response.PurchaseInvoiceResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ExpenseService {

    PurchaseInvoiceResponse createPurchaseInvoice(PurchaseInvoiceRequest request);

    EquivalentDocumentResponse createEquivalentDocument(EquivalentDocumentRequest request);

    Page<DebtDocumentResponse> getPayables(UUID thirdPartyId, DocumentStatus status, Pageable pageable);
}

