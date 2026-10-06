package com.app.accounting.treasury.service;

import com.app.accounting.treasury.dto.request.PaymentReceiptRequest;
import com.app.accounting.treasury.dto.response.PaymentReceiptResponse;
import com.app.accounting.treasury.entity.PaymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface TreasuryService {

    PaymentReceiptResponse processPaymentReceipt(PaymentReceiptRequest request);

    PaymentReceiptResponse getReceiptById(UUID id);

    PaymentReceiptResponse getReceiptByNumber(String receiptNumber);

    Page<PaymentReceiptResponse> getReceipts(PaymentType paymentType, LocalDate startDate, LocalDate endDate, Pageable pageable);

    java.util.List<PaymentReceiptResponse> getReceiptsByDocumentId(UUID documentId);

    byte[] generatePaymentReceiptPdf(UUID id);
}

