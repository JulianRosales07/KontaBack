package com.app.accounting.expense.dto.response;

import com.app.accounting.billing.dto.response.DebtDocumentResponse;
import com.app.accounting.tax.dto.response.TaxCalculationResult;
import com.app.accounting.voucher.dto.response.JournalVoucherResponse;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseInvoiceResponse {
    private DebtDocumentResponse debtDocument;
    private JournalVoucherResponse journalVoucher;
    private TaxCalculationResult taxCalculation;
}
