package com.app.accounting.treasury.service.impl;

import com.app.accounting.billing.entity.DebtDocument;
import com.app.accounting.billing.entity.DebtType;
import com.app.accounting.billing.service.BillingService;
import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.common.exception.DuplicateResourceException;
import com.app.accounting.common.exception.ResourceNotFoundException;
import com.app.accounting.common.util.BigDecimalUtils;
import com.app.accounting.puc.entity.Account;
import com.app.accounting.puc.service.AccountService;
import com.app.accounting.treasury.dto.request.PaymentReceiptRequest;
import com.app.accounting.treasury.dto.response.PaymentReceiptResponse;
import com.app.accounting.treasury.entity.PaymentReceipt;
import com.app.accounting.treasury.entity.PaymentType;
import com.app.accounting.treasury.repository.PaymentReceiptRepository;
import com.app.accounting.treasury.service.TreasuryService;
import com.app.accounting.voucher.dto.request.JournalEntryRequest;
import com.app.accounting.voucher.dto.request.JournalVoucherRequest;
import com.app.accounting.voucher.dto.response.JournalVoucherResponse;
import com.app.accounting.voucher.entity.JournalVoucher;
import com.app.accounting.voucher.entity.VoucherType;
import com.app.accounting.voucher.repository.JournalVoucherRepository;
import com.app.accounting.voucher.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TreasuryServiceImpl implements TreasuryService {

    private final PaymentReceiptRepository paymentReceiptRepository;
    private final BillingService billingService;
    private final AccountService accountService;
    private final VoucherService voucherService;
    private final JournalVoucherRepository voucherRepository;
    private final com.app.accounting.common.document.PdfExportService pdfExportService;

    private static final String CLIENTS_ACCOUNT = "130505";   // Clientes Nacionales
    private static final String SUPPLIERS_ACCOUNT = "220505"; // Proveedores Nacionales

    @Override
    @Transactional
    public PaymentReceiptResponse processPaymentReceipt(PaymentReceiptRequest request) {
        if (paymentReceiptRepository.existsByReceiptNumber(request.getReceiptNumber())) {
            throw new DuplicateResourceException("Ya existe un recibo/comprobante con el número: " + request.getReceiptNumber());
        }

        DebtDocument debtDocument = billingService.getDebtDocumentEntity(request.getDebtDocumentId());
        Account cashAccount = resolveCashAccount(request);
        BigDecimal amountPaid = BigDecimalUtils.scale(request.getAmountPaid());

        if (amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("El monto a pagar debe ser mayor a cero");
        }

        // 1. Validar correspondencia de tipo de pago y tipo de deuda
        if (request.getPaymentType() == PaymentType.CASH_IN) {
            if (debtDocument.getType() != DebtType.RECEIVABLE) {
                throw new BusinessRuleException("Un Recibo de Caja (CASH_IN) solo puede aplicarse a una Cuenta por Cobrar (RECEIVABLE)");
            }
        } else {
            if (debtDocument.getType() != DebtType.PAYABLE) {
                throw new BusinessRuleException("Un Comprobante de Egreso (CASH_OUT) solo puede aplicarse a una Cuenta por Pagar (PAYABLE)");
            }
        }

        // 2. Amortizar saldo en el documento de cartera
        billingService.updateRemainingBalance(debtDocument, amountPaid);

        // 3. Generar comprobante contable automático
        JournalVoucherRequest voucherReq;
        List<JournalEntryRequest> entries = new ArrayList<>();

        if (request.getPaymentType() == PaymentType.CASH_IN) {
            // Recibo de Caja (Ingreso):
            // Débito: Caja / Bancos
            entries.add(JournalEntryRequest.builder()
                    .accountCode(cashAccount.getCode())
                    .thirdPartyId(debtDocument.getThirdParty().getId())
                    .referenceNumber(request.getReceiptNumber())
                    .description("Recibo de caja " + request.getReceiptNumber() + " - Abono a factura " + debtDocument.getDocumentNumber())
                    .debit(amountPaid)
                    .credit(BigDecimal.ZERO)
                    .build());

            // Crédito: Clientes Nacionales (130505)
            entries.add(JournalEntryRequest.builder()
                    .accountCode(CLIENTS_ACCOUNT)
                    .thirdPartyId(debtDocument.getThirdParty().getId())
                    .referenceNumber(debtDocument.getDocumentNumber())
                    .description("Abono a cartera de clientes - Factura " + debtDocument.getDocumentNumber())
                    .debit(BigDecimal.ZERO)
                    .credit(amountPaid)
                    .build());

            voucherReq = JournalVoucherRequest.builder()
                    .voucherNumber("RC-VOUCHER-" + request.getReceiptNumber().trim())
                    .voucherType(VoucherType.INGRESO)
                    .issueDate(request.getPaymentDate())
                    .description("Recibo de Caja " + request.getReceiptNumber() + " por abono a factura " + debtDocument.getDocumentNumber())
                    .entries(entries)
                    .build();

        } else {
            // Comprobante de Egreso:
            // Débito: Proveedores Nacionales (220505)
            entries.add(JournalEntryRequest.builder()
                    .accountCode(SUPPLIERS_ACCOUNT)
                    .thirdPartyId(debtDocument.getThirdParty().getId())
                    .referenceNumber(debtDocument.getDocumentNumber())
                    .description("Pago a proveedor - Factura " + debtDocument.getDocumentNumber())
                    .debit(amountPaid)
                    .credit(BigDecimal.ZERO)
                    .build());

            // Crédito: Caja / Bancos
            entries.add(JournalEntryRequest.builder()
                    .accountCode(cashAccount.getCode())
                    .thirdPartyId(debtDocument.getThirdParty().getId())
                    .referenceNumber(request.getReceiptNumber())
                    .description("Egreso de tesorería " + request.getReceiptNumber() + " para pago de factura " + debtDocument.getDocumentNumber())
                    .debit(BigDecimal.ZERO)
                    .credit(amountPaid)
                    .build());

            voucherReq = JournalVoucherRequest.builder()
                    .voucherNumber("CE-VOUCHER-" + request.getReceiptNumber().trim())
                    .voucherType(VoucherType.EGRESO)
                    .issueDate(request.getPaymentDate())
                    .description("Comprobante de Egreso " + request.getReceiptNumber() + " por pago a factura " + debtDocument.getDocumentNumber())
                    .entries(entries)
                    .build();
        }

        JournalVoucherResponse voucherResp = voucherService.createAndPostVoucher(voucherReq);
        JournalVoucher voucherEntity = voucherRepository.findById(voucherResp.getId()).orElse(null);

        // 4. Guardar PaymentReceipt
        PaymentReceipt receipt = PaymentReceipt.builder()
                .paymentType(request.getPaymentType())
                .receiptNumber(request.getReceiptNumber().trim())
                .paymentDate(request.getPaymentDate())
                .appliedToDocument(debtDocument)
                .cashOrBankAccount(cashAccount)
                .amountPaid(amountPaid)
                .voucher(voucherEntity)
                .build();

        PaymentReceipt savedReceipt = paymentReceiptRepository.save(receipt);

        return toResponse(savedReceipt);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentReceiptResponse getReceiptById(UUID id) {
        PaymentReceipt receipt = paymentReceiptRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recibo no encontrado con ID: " + id));
        return toResponse(receipt);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentReceiptResponse getReceiptByNumber(String receiptNumber) {
        PaymentReceipt receipt = paymentReceiptRepository.findByReceiptNumber(receiptNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Recibo no encontrado con número: " + receiptNumber));
        return toResponse(receipt);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentReceiptResponse> getReceipts(PaymentType paymentType, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        Specification<PaymentReceipt> spec = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();

            if (paymentType != null) {
                predicates.add(cb.equal(root.get("paymentType"), paymentType));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("paymentDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("paymentDate"), endDate));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return paymentReceiptRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentReceiptResponse> getReceiptsByDocumentId(UUID documentId) {
        return paymentReceiptRepository.findByAppliedToDocumentId(documentId).stream()
                .map(this::toResponse)
                .toList();
    }

    private Account resolveCashAccount(PaymentReceiptRequest req) {
        if (req.getCashAccountId() != null) {
            return accountService.getAccountEntityById(req.getCashAccountId());
        }
        String code = (req.getCashAccountCode() != null && !req.getCashAccountCode().isBlank())
                ? req.getCashAccountCode().trim()
                : "110505";
        return accountService.getAccountEntityByCode(code);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generatePaymentReceiptPdf(UUID id) {
        PaymentReceipt receipt = paymentReceiptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recibo de tesorería no encontrado con ID: " + id));
        return pdfExportService.generatePaymentReceiptPdf(receipt);
    }

    private PaymentReceiptResponse toResponse(PaymentReceipt r) {
        DebtDocument doc = r.getAppliedToDocument();
        return PaymentReceiptResponse.builder()
                .id(r.getId())
                .paymentType(r.getPaymentType())
                .receiptNumber(r.getReceiptNumber())
                .paymentDate(r.getPaymentDate())
                .debtDocumentId(doc != null ? doc.getId() : null)
                .debtDocumentNumber(doc != null ? doc.getDocumentNumber() : null)
                .thirdPartyName(doc != null && doc.getThirdParty() != null ? doc.getThirdParty().getFullName() : null)
                .cashAccountCode(r.getCashOrBankAccount().getCode())
                .cashAccountName(r.getCashOrBankAccount().getName())
                .amountPaid(r.getAmountPaid())
                .remainingDocumentBalance(doc != null ? doc.getRemainingBalance() : null)
                .documentStatus(doc != null ? doc.getStatus() : null)
                .voucherId(r.getVoucher() != null ? r.getVoucher().getId() : null)
                .voucherNumber(r.getVoucher() != null ? r.getVoucher().getVoucherNumber() : null)
                .build();
    }
}
