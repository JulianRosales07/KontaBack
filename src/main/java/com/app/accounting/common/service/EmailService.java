package com.app.accounting.common.service;

import java.math.BigDecimal;

public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String resetToken);
    void sendAssociateReceiptEmailWithPdf(String toEmail, String receiptNumber, String associateName, BigDecimal totalAmount, byte[] pdfBytes);
}
