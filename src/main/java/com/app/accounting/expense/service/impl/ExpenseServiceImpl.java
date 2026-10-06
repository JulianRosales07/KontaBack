package com.app.accounting.expense.service.impl;

import com.app.accounting.billing.dto.response.DebtDocumentResponse;
import com.app.accounting.billing.entity.DebtDocument;
import com.app.accounting.billing.entity.DebtType;
import com.app.accounting.billing.entity.DocumentStatus;
import com.app.accounting.billing.repository.DebtDocumentRepository;
import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.common.exception.DuplicateResourceException;
import com.app.accounting.common.util.BigDecimalUtils;
import com.app.accounting.expense.dto.request.EquivalentDocumentRequest;
import com.app.accounting.expense.dto.request.PurchaseInvoiceRequest;
import com.app.accounting.expense.dto.response.EquivalentDocumentResponse;
import com.app.accounting.expense.dto.response.PurchaseInvoiceResponse;
import com.app.accounting.expense.service.ExpenseService;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.party.service.ThirdPartyService;
import com.app.accounting.puc.entity.Account;
import com.app.accounting.puc.service.AccountService;
import com.app.accounting.tax.dto.request.TaxCalculationRequest;
import com.app.accounting.tax.dto.response.TaxCalculationResult;
import com.app.accounting.tax.dto.response.TaxItemDetail;
import com.app.accounting.tax.service.TaxEngineService;
import com.app.accounting.treasury.dto.response.PaymentReceiptResponse;
import com.app.accounting.treasury.entity.PaymentReceipt;
import com.app.accounting.treasury.entity.PaymentType;
import com.app.accounting.treasury.repository.PaymentReceiptRepository;
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
public class ExpenseServiceImpl implements ExpenseService {

    private final DebtDocumentRepository debtDocumentRepository;
    private final ThirdPartyService thirdPartyService;
    private final TaxEngineService taxEngineService;
    private final VoucherService voucherService;
    private final JournalVoucherRepository voucherRepository;
    private final AccountService accountService;
    private final PaymentReceiptRepository paymentReceiptRepository;

    private static final String SUPPLIERS_PAYABLE_ACCOUNT = "220505"; // Proveedores Nacionales


    @Override
    @Transactional
    public PurchaseInvoiceResponse createPurchaseInvoice(PurchaseInvoiceRequest request) {
        if (debtDocumentRepository.existsByDocumentNumber(request.getDocumentNumber())) {
            throw new DuplicateResourceException("Ya existe un documento con el número: " + request.getDocumentNumber());
        }

        ThirdParty supplier = resolveThirdParty(request);

        // 1. Calcular impuestos de compras (isSale = false)
        TaxCalculationRequest taxReq = TaxCalculationRequest.builder()
                .subtotal(request.getSubtotal())
                .isSale(false)
                .taxTemplateIds(request.getTaxTemplateIds())
                .build();
        TaxCalculationResult taxResult = taxEngineService.calculateTaxes(taxReq);

        // 2. Crear documento de cuenta por pagar DebtDocument (PAYABLE)
        DebtDocument debtDocument = DebtDocument.builder()
                .type(DebtType.PAYABLE)
                .documentNumber(request.getDocumentNumber().trim())
                .thirdParty(supplier)
                .issueDate(request.getIssueDate())
                .dueDate(request.getDueDate())
                .subtotal(taxResult.getSubtotal())
                .taxTotal(taxResult.getTaxTotal())
                .retentionTotal(taxResult.getRetentionTotal())
                .totalAmount(taxResult.getTotalAmount())
                .remainingBalance(taxResult.getTotalAmount())
                .status(DocumentStatus.PENDING)
                .build();

        DebtDocument savedDoc = debtDocumentRepository.save(debtDocument);

        // 3. Generar Comprobante de Causación automático (Partida Doble)
        String voucherNum = "CP-" + request.getDocumentNumber().trim();
        List<JournalEntryRequest> entries = new ArrayList<>();

        // Débito: Cuenta de Gasto / Inventario (513505 o 143505) por la base gravable
        String expenseAccount = (request.getExpenseAccountCode() != null && !request.getExpenseAccountCode().isBlank())
                ? request.getExpenseAccountCode().trim()
                : "513505";

        entries.add(JournalEntryRequest.builder()
                .accountCode(expenseAccount)
                .thirdPartyId(supplier.getId())
                .referenceNumber(savedDoc.getDocumentNumber())
                .description("Gasto/Compra - Factura Proveedor " + savedDoc.getDocumentNumber())
                .debit(taxResult.getSubtotal())
                .credit(BigDecimal.ZERO)
                .build());

        // Débito: IVA Descontable (240810)
        for (TaxItemDetail item : taxResult.getItems()) {
            if (!item.isRetention() && BigDecimalUtils.isPositive(item.getTaxAmount())) {
                entries.add(JournalEntryRequest.builder()
                        .accountCode(item.getAccountCode())
                        .thirdPartyId(supplier.getId())
                        .referenceNumber(savedDoc.getDocumentNumber())
                        .description(item.getName() + " descontable - Factura " + savedDoc.getDocumentNumber())
                        .debit(item.getTaxAmount())
                        .credit(BigDecimal.ZERO)
                        .build());
            }
        }

        // Crédito: Retenciones en la fuente por pagar (2365xx / 2368xx)
        for (TaxItemDetail item : taxResult.getItems()) {
            if (item.isRetention() && BigDecimalUtils.isPositive(item.getTaxAmount())) {
                entries.add(JournalEntryRequest.builder()
                        .accountCode(item.getAccountCode())
                        .thirdPartyId(supplier.getId())
                        .referenceNumber(savedDoc.getDocumentNumber())
                        .description(item.getName() + " retenida - Factura " + savedDoc.getDocumentNumber())
                        .debit(BigDecimal.ZERO)
                        .credit(item.getTaxAmount())
                        .build());
            }
        }

        // Crédito: Proveedores Nacionales (220505) por el total neto a pagar
        entries.add(JournalEntryRequest.builder()
                .accountCode(SUPPLIERS_PAYABLE_ACCOUNT)
                .thirdPartyId(supplier.getId())
                .referenceNumber(savedDoc.getDocumentNumber())
                .description("Cuentas por pagar proveedores - Factura " + savedDoc.getDocumentNumber())
                .debit(BigDecimal.ZERO)
                .credit(taxResult.getTotalAmount())
                .build());

        JournalVoucherRequest voucherReq = JournalVoucherRequest.builder()
                .voucherNumber(voucherNum)
                .voucherType(VoucherType.NOTA_CONTABLE)
                .issueDate(request.getIssueDate())
                .description("Causación automática de Compra/Gasto " + savedDoc.getDocumentNumber() + " - " + supplier.getFullName())
                .entries(entries)
                .build();

        JournalVoucherResponse voucherResp = voucherService.createAndPostVoucher(voucherReq);

        // Asociar comprobante al documento de deuda
        JournalVoucher voucherEntity = voucherRepository.findById(voucherResp.getId()).orElse(null);
        savedDoc.setVoucher(voucherEntity);
        debtDocumentRepository.save(savedDoc);

        return PurchaseInvoiceResponse.builder()
                .debtDocument(toResponse(savedDoc))
                .journalVoucher(voucherResp)
                .taxCalculation(taxResult)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DebtDocumentResponse> getPayables(UUID thirdPartyId, DocumentStatus status, Pageable pageable) {
        Specification<DebtDocument> spec = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("type"), DebtType.PAYABLE));

            if (thirdPartyId != null) {
                predicates.add(cb.equal(root.get("thirdParty").get("id"), thirdPartyId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return debtDocumentRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public EquivalentDocumentResponse createEquivalentDocument(EquivalentDocumentRequest request) {
        if (debtDocumentRepository.existsByDocumentNumber(request.getDocumentNumber())) {
            throw new DuplicateResourceException("Ya existe un documento con el número: " + request.getDocumentNumber());
        }

        ThirdParty supplier = resolveThirdParty(request.getThirdPartyId(), request.getThirdPartyIdentification());

        // 1. Calcular retenciones en compras / gastos (isSale = false)
        TaxCalculationRequest taxReq = TaxCalculationRequest.builder()
                .subtotal(request.getSubtotal())
                .isSale(false)
                .taxTemplateIds(request.getTaxTemplateIds())
                .build();
        TaxCalculationResult taxResult = taxEngineService.calculateTaxes(taxReq);

        LocalDate dueDate = request.getDueDate() != null ? request.getDueDate() : request.getIssueDate();
        boolean isPaid = request.isPayImmediately();

        // 2. Crear documento de cuenta por pagar / gasto DebtDocument
        DebtDocument debtDocument = DebtDocument.builder()
                .type(DebtType.PAYABLE)
                .documentNumber(request.getDocumentNumber().trim())
                .thirdParty(supplier)
                .issueDate(request.getIssueDate())
                .dueDate(dueDate)
                .subtotal(taxResult.getSubtotal())
                .taxTotal(taxResult.getTaxTotal())
                .retentionTotal(taxResult.getRetentionTotal())
                .totalAmount(taxResult.getTotalAmount())
                .remainingBalance(isPaid ? BigDecimal.ZERO : taxResult.getTotalAmount())
                .status(isPaid ? DocumentStatus.PAID : DocumentStatus.PENDING)
                .build();

        DebtDocument savedDoc = debtDocumentRepository.save(debtDocument);

        String expenseAccount = (request.getExpenseAccountCode() != null && !request.getExpenseAccountCode().isBlank())
                ? request.getExpenseAccountCode().trim()
                : "513505";

        List<JournalEntryRequest> entries = new ArrayList<>();

        // Débito: Cuenta de Gasto por la base
        entries.add(JournalEntryRequest.builder()
                .accountCode(expenseAccount)
                .thirdPartyId(supplier.getId())
                .referenceNumber(savedDoc.getDocumentNumber())
                .description((request.getDescription() != null && !request.getDescription().isBlank())
                        ? request.getDescription().trim()
                        : "Documento equivalente / soporte " + savedDoc.getDocumentNumber())
                .debit(taxResult.getSubtotal())
                .credit(BigDecimal.ZERO)
                .build());

        // Débito: IVA Descontable si hubiera
        for (TaxItemDetail item : taxResult.getItems()) {
            if (!item.isRetention() && BigDecimalUtils.isPositive(item.getTaxAmount())) {
                entries.add(JournalEntryRequest.builder()
                        .accountCode(item.getAccountCode())
                        .thirdPartyId(supplier.getId())
                        .referenceNumber(savedDoc.getDocumentNumber())
                        .description(item.getName() + " descontable - " + savedDoc.getDocumentNumber())
                        .debit(item.getTaxAmount())
                        .credit(BigDecimal.ZERO)
                        .build());
            }
        }

        // Crédito: Retenciones practicadas por pagar
        for (TaxItemDetail item : taxResult.getItems()) {
            if (item.isRetention() && BigDecimalUtils.isPositive(item.getTaxAmount())) {
                entries.add(JournalEntryRequest.builder()
                        .accountCode(item.getAccountCode())
                        .thirdPartyId(supplier.getId())
                        .referenceNumber(savedDoc.getDocumentNumber())
                        .description(item.getName() + " retenida - " + savedDoc.getDocumentNumber())
                        .debit(BigDecimal.ZERO)
                        .credit(item.getTaxAmount())
                        .build());
            }
        }

        Account cashAccount = null;
        JournalVoucherRequest voucherReq;
        String voucherNum;

        if (isPaid) {
            // Se emite como Recibo / Comprobante de Egreso directo (CASH_OUT)
            cashAccount = resolveCashAccount(request.getCashAccountId(), request.getCashAccountCode());
            voucherNum = "CE-" + request.getDocumentNumber().trim();

            // Crédito: Caja o Banco por el neto pagado
            entries.add(JournalEntryRequest.builder()
                    .accountCode(cashAccount.getCode())
                    .thirdPartyId(supplier.getId())
                    .referenceNumber(savedDoc.getDocumentNumber())
                    .description("Pago de contado / egreso por documento equivalente " + savedDoc.getDocumentNumber())
                    .debit(BigDecimal.ZERO)
                    .credit(taxResult.getTotalAmount())
                    .build());

            voucherReq = JournalVoucherRequest.builder()
                    .voucherNumber(voucherNum)
                    .voucherType(VoucherType.EGRESO)
                    .issueDate(request.getIssueDate())
                    .description("Recibo / Comprobante de Egreso por Documento Equivalente " + savedDoc.getDocumentNumber() + " - " + supplier.getFullName())
                    .entries(entries)
                    .build();
        } else {
            // Se emite como Comprobante de Causación a Crédito (NOTA_CONTABLE)
            voucherNum = "CP-" + request.getDocumentNumber().trim();

            // Crédito: Cuentas por pagar / Proveedores Nacionales
            entries.add(JournalEntryRequest.builder()
                    .accountCode(SUPPLIERS_PAYABLE_ACCOUNT)
                    .thirdPartyId(supplier.getId())
                    .referenceNumber(savedDoc.getDocumentNumber())
                    .description("Cuenta por pagar por documento equivalente " + savedDoc.getDocumentNumber())
                    .debit(BigDecimal.ZERO)
                    .credit(taxResult.getTotalAmount())
                    .build());

            voucherReq = JournalVoucherRequest.builder()
                    .voucherNumber(voucherNum)
                    .voucherType(VoucherType.NOTA_CONTABLE)
                    .issueDate(request.getIssueDate())
                    .description("Comprobante de Causación por Documento Equivalente " + savedDoc.getDocumentNumber() + " - " + supplier.getFullName())
                    .entries(entries)
                    .build();
        }

        JournalVoucherResponse voucherResp = voucherService.createAndPostVoucher(voucherReq);
        JournalVoucher voucherEntity = voucherRepository.findById(voucherResp.getId()).orElse(null);

        savedDoc.setVoucher(voucherEntity);
        debtDocumentRepository.save(savedDoc);

        PaymentReceiptResponse receiptResp = null;
        if (isPaid) {
            PaymentReceipt receipt = PaymentReceipt.builder()
                    .paymentType(PaymentType.CASH_OUT)
                    .receiptNumber("REC-" + request.getDocumentNumber().trim())
                    .paymentDate(request.getIssueDate())
                    .appliedToDocument(savedDoc)
                    .cashOrBankAccount(cashAccount)
                    .amountPaid(taxResult.getTotalAmount())
                    .voucher(voucherEntity)
                    .build();

            PaymentReceipt savedReceipt = paymentReceiptRepository.save(receipt);

            receiptResp = PaymentReceiptResponse.builder()
                    .id(savedReceipt.getId())
                    .paymentType(PaymentType.CASH_OUT)
                    .receiptNumber(savedReceipt.getReceiptNumber())
                    .paymentDate(savedReceipt.getPaymentDate())
                    .debtDocumentId(savedDoc.getId())
                    .debtDocumentNumber(savedDoc.getDocumentNumber())
                    .thirdPartyName(supplier.getFullName())
                    .cashAccountCode(cashAccount.getCode())
                    .cashAccountName(cashAccount.getName())
                    .amountPaid(taxResult.getTotalAmount())
                    .remainingDocumentBalance(BigDecimal.ZERO)
                    .documentStatus(DocumentStatus.PAID)
                    .voucherId(voucherResp.getId())
                    .voucherNumber(voucherResp.getVoucherNumber())
                    .build();
        }

        return EquivalentDocumentResponse.builder()
                .documentType("DOCUMENTO_EQUIVALENTE")
                .paidImmediately(isPaid)
                .debtDocument(toResponse(savedDoc))
                .journalVoucher(voucherResp)
                .taxCalculation(taxResult)
                .paymentReceipt(receiptResp)
                .build();
    }

    private Account resolveCashAccount(UUID cashAccountId, String cashAccountCode) {
        if (cashAccountId != null) {
            return accountService.getAccountEntityById(cashAccountId);
        }
        String code = (cashAccountCode != null && !cashAccountCode.isBlank())
                ? cashAccountCode.trim()
                : "110505";
        return accountService.getAccountEntityByCode(code);
    }

    private ThirdParty resolveThirdParty(PurchaseInvoiceRequest req) {
        return resolveThirdParty(req.getThirdPartyId(), req.getThirdPartyIdentification());
    }

    private ThirdParty resolveThirdParty(UUID thirdPartyId, String thirdPartyIdentification) {
        if (thirdPartyId != null) {
            return thirdPartyService.getThirdPartyEntityById(thirdPartyId);
        }
        if (thirdPartyIdentification != null && !thirdPartyIdentification.isBlank()) {
            return thirdPartyService.getThirdPartyEntityByIdentification(thirdPartyIdentification.trim());
        }
        throw new BusinessRuleException("Se debe especificar el tercero / proveedor mediante su ID o número de identificación");
    }

    private DebtDocumentResponse toResponse(DebtDocument doc) {
        return DebtDocumentResponse.builder()
                .id(doc.getId())
                .type(doc.getType())
                .documentNumber(doc.getDocumentNumber())
                .thirdPartyId(doc.getThirdParty() != null ? doc.getThirdParty().getId() : null)
                .thirdPartyName(doc.getThirdParty() != null ? doc.getThirdParty().getFullName() : null)
                .thirdPartyIdentification(doc.getThirdParty() != null ? doc.getThirdParty().getIdentificationNumber() : null)
                .issueDate(doc.getIssueDate())
                .dueDate(doc.getDueDate())
                .subtotal(doc.getSubtotal())
                .taxTotal(doc.getTaxTotal())
                .retentionTotal(doc.getRetentionTotal())
                .totalAmount(doc.getTotalAmount())
                .remainingBalance(doc.getRemainingBalance())
                .status(doc.getStatus())
                .voucherId(doc.getVoucher() != null ? doc.getVoucher().getId() : null)
                .voucherNumber(doc.getVoucher() != null ? doc.getVoucher().getVoucherNumber() : null)
                .build();
    }
}
