package com.app.accounting.billing.dto.response;

import com.app.accounting.tax.dto.response.TaxCalculationResult;
import com.app.accounting.voucher.dto.response.JournalVoucherResponse;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesInvoiceResponse {
    private DebtDocumentResponse debtDocument;
    private JournalVoucherResponse journalVoucher;
    private TaxCalculationResult taxCalculation;
}
