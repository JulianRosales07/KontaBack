package com.app.accounting.expense.dto.response;

import com.app.accounting.billing.dto.response.DebtDocumentResponse;
import com.app.accounting.tax.dto.response.TaxCalculationResult;
import com.app.accounting.treasury.dto.response.PaymentReceiptResponse;
import com.app.accounting.voucher.dto.response.JournalVoucherResponse;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquivalentDocumentResponse {
    @Builder.Default
    private String documentType = "DOCUMENTO_EQUIVALENTE";
    private boolean paidImmediately;
    private DebtDocumentResponse debtDocument;
    private JournalVoucherResponse journalVoucher;
    private TaxCalculationResult taxCalculation;
    private PaymentReceiptResponse paymentReceipt;
}
