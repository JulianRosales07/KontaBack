package com.app.accounting.billing.service.impl;

import com.app.accounting.billing.dto.request.SalesInvoiceRequest;
import com.app.accounting.billing.dto.response.*;
import com.app.accounting.billing.entity.DebtDocument;
import com.app.accounting.billing.entity.DebtType;
import com.app.accounting.billing.entity.DocumentStatus;
import com.app.accounting.billing.repository.DebtDocumentRepository;
import com.app.accounting.billing.service.BillingService;
import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.common.exception.DuplicateResourceException;
import com.app.accounting.common.exception.ResourceNotFoundException;
import com.app.accounting.common.util.BigDecimalUtils;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.party.service.ThirdPartyService;
import com.app.accounting.tax.dto.request.TaxCalculationRequest;
import com.app.accounting.tax.dto.response.TaxCalculationResult;
import com.app.accounting.tax.dto.response.TaxItemDetail;
import com.app.accounting.tax.service.TaxEngineService;
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
import java.util.*;

@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private final DebtDocumentRepository debtDocumentRepository;
    private final ThirdPartyService thirdPartyService;
    private final TaxEngineService taxEngineService;
    private final VoucherService voucherService;
    private final JournalVoucherRepository voucherRepository;
    private final com.app.accounting.common.document.PdfExportService pdfExportService;
    private final com.app.accounting.common.document.ExcelExportService excelExportService;

    private static final String CLIENTS_RECEIVABLE_ACCOUNT = "130505"; // Clientes Nacionales

    @Override
    @Transactional
    public SalesInvoiceResponse createSalesInvoice(SalesInvoiceRequest request) {
        if (debtDocumentRepository.existsByDocumentNumber(request.getDocumentNumber())) {
            throw new DuplicateResourceException("Ya existe un documento con el número: " + request.getDocumentNumber());
        }

        ThirdParty client = resolveThirdParty(request);

        // 1. Calcular impuestos
        TaxCalculationRequest taxReq = TaxCalculationRequest.builder()
                .subtotal(request.getSubtotal())
                .isSale(true)
                .taxTemplateIds(request.getTaxTemplateIds())
                .build();
        TaxCalculationResult taxResult = taxEngineService.calculateTaxes(taxReq);

        // 2. Crear documento de cartera DebtDocument
        DebtDocument debtDocument = DebtDocument.builder()
                .type(DebtType.RECEIVABLE)
                .documentNumber(request.getDocumentNumber().trim())
                .thirdParty(client)
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

        // 3. Generar comprobante contable automático (Partida Doble)
        String voucherNum = "CV-" + request.getDocumentNumber().trim();
        List<JournalEntryRequest> entries = new ArrayList<>();

        // Débito: Clientes Nacionales (130505) por el total neto a cobrar
        entries.add(JournalEntryRequest.builder()
                .accountCode(CLIENTS_RECEIVABLE_ACCOUNT)
                .thirdPartyId(client.getId())
                .referenceNumber(savedDoc.getDocumentNumber())
                .description("Cuentas por cobrar clientes - Factura " + savedDoc.getDocumentNumber())
                .debit(taxResult.getTotalAmount())
                .credit(BigDecimal.ZERO)
                .build());

        // Débito: Retenciones a favor practicadas (135515, 135518, etc.)
        for (TaxItemDetail item : taxResult.getItems()) {
            if (item.isRetention() && BigDecimalUtils.isPositive(item.getTaxAmount())) {
                entries.add(JournalEntryRequest.builder()
                        .accountCode(item.getAccountCode())
                        .thirdPartyId(client.getId())
                        .referenceNumber(savedDoc.getDocumentNumber())
                        .description(item.getName() + " practicada - Factura " + savedDoc.getDocumentNumber())
                        .debit(item.getTaxAmount())
                        .credit(BigDecimal.ZERO)
                        .build());
            }
        }

        // Crédito: Ingresos operacionales (4135xx / 413505) por la base gravable (subtotal)
        String incomeAccount = (request.getIncomeAccountCode() != null && !request.getIncomeAccountCode().isBlank())
                ? request.getIncomeAccountCode().trim()
                : "413505";

        entries.add(JournalEntryRequest.builder()
                .accountCode(incomeAccount)
                .thirdPartyId(client.getId())
                .referenceNumber(savedDoc.getDocumentNumber())
                .description("Ingresos por venta - Factura " + savedDoc.getDocumentNumber())
                .debit(BigDecimal.ZERO)
                .credit(taxResult.getSubtotal())
                .build());

        // Crédito: Impuestos generados (IVA Generado 240805, etc.)
        for (TaxItemDetail item : taxResult.getItems()) {
            if (!item.isRetention() && BigDecimalUtils.isPositive(item.getTaxAmount())) {
                entries.add(JournalEntryRequest.builder()
                        .accountCode(item.getAccountCode())
                        .thirdPartyId(client.getId())
                        .referenceNumber(savedDoc.getDocumentNumber())
                        .description(item.getName() + " generado - Factura " + savedDoc.getDocumentNumber())
                        .debit(BigDecimal.ZERO)
                        .credit(item.getTaxAmount())
                        .build());
            }
        }

        JournalVoucherRequest voucherReq = JournalVoucherRequest.builder()
                .voucherNumber(voucherNum)
                .voucherType(VoucherType.NOTA_CONTABLE)
                .issueDate(request.getIssueDate())
                .description("Causación automática de Factura de Venta " + savedDoc.getDocumentNumber() + " - " + client.getFullName())
                .entries(entries)
                .build();

        JournalVoucherResponse voucherResp = voucherService.createAndPostVoucher(voucherReq);

        // Asociar comprobante al documento de cartera
        JournalVoucher voucherEntity = voucherRepository.findById(voucherResp.getId()).orElse(null);
        savedDoc.setVoucher(voucherEntity);
        debtDocumentRepository.save(savedDoc);

        return SalesInvoiceResponse.builder()
                .debtDocument(toResponse(savedDoc))
                .journalVoucher(voucherResp)
                .taxCalculation(taxResult)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DebtDocumentResponse getDocumentById(UUID id) {
        DebtDocument doc = debtDocumentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Documento no encontrado con ID: " + id));
        return toResponse(doc);
    }

    @Override
    @Transactional(readOnly = true)
    public DebtDocumentResponse getDocumentByNumber(String documentNumber) {
        DebtDocument doc = debtDocumentRepository.findByDocumentNumberWithDetails(documentNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Documento no encontrado con número: " + documentNumber));
        return toResponse(doc);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DebtDocumentResponse> getReceivables(UUID thirdPartyId, DocumentStatus status, Pageable pageable) {
        Specification<DebtDocument> spec = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("type"), DebtType.RECEIVABLE));

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
    @Transactional(readOnly = true)
    public DebtDocument getDebtDocumentEntity(UUID id) {
        return debtDocumentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Documento de deuda no encontrado con ID: " + id));
    }

    @Override
    @Transactional
    public void updateRemainingBalance(DebtDocument document, BigDecimal amountPaid) {
        BigDecimal newRemaining = document.getRemainingBalance().subtract(amountPaid);
        newRemaining = BigDecimalUtils.scale(newRemaining);

        if (BigDecimalUtils.isNegative(newRemaining)) {
            throw new BusinessRuleException(String.format(
                    "El valor pagado (%s) excede el saldo pendiente (%s) del documento %s",
                    amountPaid,
                    document.getRemainingBalance(),
                    document.getDocumentNumber()
            ));
        }

        document.setRemainingBalance(newRemaining);
        if (BigDecimalUtils.isZero(newRemaining)) {
            document.setStatus(DocumentStatus.PAID);
        } else {
            document.setStatus(DocumentStatus.PARTIALLY_PAID);
        }

        debtDocumentRepository.save(document);
    }

    @Override
    @Transactional(readOnly = true)
    public ReceivableAgingReportResponse getReceivablesAging(LocalDate asOfDate, UUID thirdPartyId) {
        if (asOfDate == null) {
            asOfDate = LocalDate.now();
        }

        List<DebtDocument> pendingDocs = (thirdPartyId != null)
                ? debtDocumentRepository.findPendingReceivablesByThirdPartyWithDetails(thirdPartyId)
                : debtDocumentRepository.findPendingReceivablesWithDetails();

        Map<UUID, CustomerAgingResponse> customerMap = new LinkedHashMap<>();
        BigDecimal totalReceivables = BigDecimal.ZERO;
        BigDecimal totalCurrent = BigDecimal.ZERO;
        BigDecimal total1To30 = BigDecimal.ZERO;
        BigDecimal total31To60 = BigDecimal.ZERO;
        BigDecimal total61To90 = BigDecimal.ZERO;
        BigDecimal totalOver90 = BigDecimal.ZERO;
        int docCount = 0;

        for (DebtDocument doc : pendingDocs) {
            BigDecimal balance = doc.getRemainingBalance() != null ? doc.getRemainingBalance() : BigDecimal.ZERO;
            if (BigDecimalUtils.isZero(balance) || BigDecimalUtils.isNegative(balance)) {
                continue;
            }
            docCount++;
            totalReceivables = totalReceivables.add(balance);

            long daysOverdue = 0;
            if (asOfDate.isAfter(doc.getDueDate())) {
                daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(doc.getDueDate(), asOfDate);
            }

            String bucket;
            if (daysOverdue <= 0) {
                bucket = "CURRENT";
                totalCurrent = totalCurrent.add(balance);
            } else if (daysOverdue <= 30) {
                bucket = "DAYS_1_TO_30";
                total1To30 = total1To30.add(balance);
            } else if (daysOverdue <= 60) {
                bucket = "DAYS_31_TO_60";
                total31To60 = total31To60.add(balance);
            } else if (daysOverdue <= 90) {
                bucket = "DAYS_61_TO_90";
                total61To90 = total61To90.add(balance);
            } else {
                bucket = "OVER_90";
                totalOver90 = totalOver90.add(balance);
            }

            ThirdParty tp = doc.getThirdParty();
            CustomerAgingResponse cust = customerMap.computeIfAbsent(tp.getId(), k -> CustomerAgingResponse.builder()
                    .thirdPartyId(tp.getId())
                    .identificationNumber(tp.getIdentificationNumber())
                    .fullName(tp.getFullName())
                    .phone(tp.getPhone())
                    .email(tp.getEmail())
                    .totalBalance(BigDecimal.ZERO)
                    .currentBalance(BigDecimal.ZERO)
                    .balance1To30(BigDecimal.ZERO)
                    .balance31To60(BigDecimal.ZERO)
                    .balance61To90(BigDecimal.ZERO)
                    .balanceOver90(BigDecimal.ZERO)
                    .documents(new ArrayList<>())
                    .build());

            cust.setTotalBalance(cust.getTotalBalance().add(balance));
            switch (bucket) {
                case "CURRENT" -> cust.setCurrentBalance(cust.getCurrentBalance().add(balance));
                case "DAYS_1_TO_30" -> cust.setBalance1To30(cust.getBalance1To30().add(balance));
                case "DAYS_31_TO_60" -> cust.setBalance31To60(cust.getBalance31To60().add(balance));
                case "DAYS_61_TO_90" -> cust.setBalance61To90(cust.getBalance61To90().add(balance));
                default -> cust.setBalanceOver90(cust.getBalanceOver90().add(balance));
            }

            cust.getDocuments().add(AgingDocumentItemResponse.builder()
                    .documentId(doc.getId())
                    .documentNumber(doc.getDocumentNumber())
                    .issueDate(doc.getIssueDate())
                    .dueDate(doc.getDueDate())
                    .daysOverdue(daysOverdue)
                    .originalAmount(BigDecimalUtils.scale(doc.getTotalAmount()))
                    .remainingBalance(BigDecimalUtils.scale(balance))
                    .status(doc.getStatus())
                    .bucket(bucket)
                    .build());
        }

        List<CustomerAgingResponse> customerList = new ArrayList<>(customerMap.values());
        for (CustomerAgingResponse c : customerList) {
            c.setTotalBalance(BigDecimalUtils.scale(c.getTotalBalance()));
            c.setCurrentBalance(BigDecimalUtils.scale(c.getCurrentBalance()));
            c.setBalance1To30(BigDecimalUtils.scale(c.getBalance1To30()));
            c.setBalance31To60(BigDecimalUtils.scale(c.getBalance31To60()));
            c.setBalance61To90(BigDecimalUtils.scale(c.getBalance61To90()));
            c.setBalanceOver90(BigDecimalUtils.scale(c.getBalanceOver90()));
        }

        return ReceivableAgingReportResponse.builder()
                .asOfDate(asOfDate)
                .totalReceivables(BigDecimalUtils.scale(totalReceivables))
                .totalCurrent(BigDecimalUtils.scale(totalCurrent))
                .total1To30(BigDecimalUtils.scale(total1To30))
                .total31To60(BigDecimalUtils.scale(total31To60))
                .total61To90(BigDecimalUtils.scale(total61To90))
                .totalOver90(BigDecimalUtils.scale(totalOver90))
                .customerCount(customerList.size())
                .documentCount(docCount)
                .customers(customerList)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerStatementResponse getCustomerStatement(UUID thirdPartyId) {
        ThirdParty party = thirdPartyService.getThirdPartyEntityById(thirdPartyId);
        List<DebtDocument> docs = debtDocumentRepository.findReceivablesByThirdPartyWithDetails(thirdPartyId);

        BigDecimal totalInvoiced = BigDecimal.ZERO;
        BigDecimal currentBalance = BigDecimal.ZERO;
        int pendingCount = 0;
        int overdueCount = 0;
        LocalDate today = LocalDate.now();

        List<DebtDocumentResponse> docResponses = new ArrayList<>();
        for (DebtDocument d : docs) {
            BigDecimal total = d.getTotalAmount() != null ? d.getTotalAmount() : BigDecimal.ZERO;
            BigDecimal rem = d.getRemainingBalance() != null ? d.getRemainingBalance() : BigDecimal.ZERO;

            totalInvoiced = totalInvoiced.add(total);
            currentBalance = currentBalance.add(rem);

            if (d.getStatus() == DocumentStatus.PENDING || d.getStatus() == DocumentStatus.PARTIALLY_PAID) {
                pendingCount++;
                if (today.isAfter(d.getDueDate())) {
                    overdueCount++;
                }
            }

            docResponses.add(toResponse(d));
        }

        BigDecimal totalPaid = totalInvoiced.subtract(currentBalance);

        return CustomerStatementResponse.builder()
                .thirdPartyId(party.getId())
                .identificationNumber(party.getIdentificationNumber())
                .fullName(party.getFullName())
                .phone(party.getPhone())
                .email(party.getEmail())
                .totalInvoiced(BigDecimalUtils.scale(totalInvoiced))
                .totalPaid(BigDecimalUtils.scale(totalPaid))
                .currentBalance(BigDecimalUtils.scale(currentBalance))
                .totalInvoices(docs.size())
                .pendingInvoices(pendingCount)
                .overdueInvoices(overdueCount)
                .documents(docResponses)
                .build();
    }

    private ThirdParty resolveThirdParty(SalesInvoiceRequest req) {
        if (req.getThirdPartyId() != null) {
            return thirdPartyService.getThirdPartyEntityById(req.getThirdPartyId());
        }
        if (req.getThirdPartyIdentification() != null && !req.getThirdPartyIdentification().isBlank()) {
            return thirdPartyService.getThirdPartyEntityByIdentification(req.getThirdPartyIdentification().trim());
        }
        throw new BusinessRuleException("Se debe especificar el cliente (tercero) mediante su ID o número de identificación");
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportCustomerStatement(UUID thirdPartyId, String format) {
        CustomerStatementResponse statement = getCustomerStatement(thirdPartyId);
        if ("excel".equalsIgnoreCase(format) || "xlsx".equalsIgnoreCase(format)) {
            return excelExportService.generateCustomerStatementExcel(statement);
        }
        return pdfExportService.generateCustomerStatementPdf(statement);
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
