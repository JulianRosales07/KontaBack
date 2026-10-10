package com.app.accounting.associate.service.impl;

import com.app.accounting.associate.dto.request.CreateAssociateReceiptRequest;
import com.app.accounting.associate.dto.request.ReceiptItemRequest;
import com.app.accounting.associate.dto.response.AssociateReceiptResponse;
import com.app.accounting.associate.dto.response.AssociateStatementResponse;
import com.app.accounting.associate.dto.response.QuotaTemplateItemResponse;
import com.app.accounting.associate.dto.response.QuotaTemplateResponse;
import com.app.accounting.associate.entity.AssociateReceipt;
import com.app.accounting.associate.entity.AssociateReceiptItem;
import com.app.accounting.associate.entity.QuotaCategory;
import com.app.accounting.associate.entity.QuotaTemplate;
import com.app.accounting.associate.entity.QuotaTemplateItem;
import com.app.accounting.associate.repository.AssociateReceiptRepository;
import com.app.accounting.associate.repository.QuotaTemplateRepository;
import com.app.accounting.associate.service.AssociateService;
import java.util.Optional;
import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.common.exception.ResourceNotFoundException;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.party.repository.ThirdPartyRepository;
import com.app.accounting.puc.entity.Account;
import com.app.accounting.puc.repository.AccountRepository;
import com.app.accounting.voucher.dto.request.JournalEntryRequest;
import com.app.accounting.voucher.dto.request.JournalVoucherRequest;
import com.app.accounting.voucher.dto.response.JournalVoucherResponse;
import com.app.accounting.voucher.entity.JournalVoucher;
import com.app.accounting.voucher.entity.VoucherStatus;
import com.app.accounting.voucher.entity.VoucherType;
import com.app.accounting.voucher.repository.JournalVoucherRepository;
import com.app.accounting.voucher.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssociateServiceImpl implements AssociateService {

    private final QuotaTemplateRepository quotaTemplateRepository;
    private final AssociateReceiptRepository associateReceiptRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final AccountRepository accountRepository;
    private final VoucherService voucherService;
    private final JournalVoucherRepository journalVoucherRepository;
    private final com.app.accounting.common.document.PdfExportService pdfExportService;
    private final com.app.accounting.common.document.ExcelExportService excelExportService;

    @Override
    @Transactional(readOnly = true)
    public List<QuotaTemplateResponse> getActiveTemplates() {
        return quotaTemplateRepository.findByIsActiveTrue().stream()
                .map(this::mapTemplateToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public QuotaTemplateResponse getTemplateById(UUID id) {
        QuotaTemplate template = quotaTemplateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla de cuota no encontrada con ID: " + id));
        return mapTemplateToResponse(template);
    }

    @Override
    @Transactional
    public AssociateReceiptResponse registerReceipt(CreateAssociateReceiptRequest request) {
        ThirdParty associate = thirdPartyRepository.findById(request.getThirdPartyId())
                .orElseThrow(() -> new ResourceNotFoundException("Asociado (Tercero) no encontrado con ID: " + request.getThirdPartyId()));

        Account cashAccount = resolveAccount(request.getCashAccountId(),
                request.getCashAccountCode() != null ? request.getCashAccountCode() : (request.getCashAccountId() == null ? "110505" : null));

        QuotaTemplate template = null;
        if (request.getTemplateId() != null) {
            template = quotaTemplateRepository.findById(request.getTemplateId()).orElse(null);
        }

        // 1. Validar que la suma de los conceptos sea igual al total recibido
        BigDecimal itemsSum = request.getItems().stream()
                .map(ReceiptItemRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (itemsSum.compareTo(request.getTotalAmount()) != 0) {
            throw new BusinessRuleException(String.format(
                    "La suma de los conceptos ($%s) no coincide con el total ingresado ($%s)",
                    itemsSum.setScale(2, RoundingMode.HALF_UP),
                    request.getTotalAmount().setScale(2, RoundingMode.HALF_UP)
            ));
        }

        // 2. Generar consecutivo de recibo de caja para el asociado
        long nextNum = associateReceiptRepository.countAllReceipts() + 1;
        String receiptNumber = String.format("RC-ASOC-%05d", nextNum);

        // 3. Generar el asiento contable (JournalVoucher) con sumas iguales
        String voucherNumber = String.format("CP-ING-%05d", nextNum);
        String description = "Recaudo de Cuota Asociado: " + associate.getFullName() + " [" + receiptNumber + "]";

        List<JournalEntryRequest> entries = new ArrayList<>();

        // Línea Débito: Entrada a Caja o Banco
        entries.add(JournalEntryRequest.builder()
                .accountId(cashAccount.getId())
                .accountCode(cashAccount.getCode())
                .thirdPartyId(associate.getId())
                .referenceNumber(receiptNumber)
                .description("Recaudo cuota de asociado en efectivo/banco - " + receiptNumber)
                .debit(request.getTotalAmount())
                .credit(BigDecimal.ZERO)
                .build());

        // Líneas Crédito: Desglose de conceptos (Administración, Aportes, Fondos)
        for (ReceiptItemRequest item : request.getItems()) {
            Account itemAccount = resolveAccount(item.getAccountId(), item.getAccountCode());

            entries.add(JournalEntryRequest.builder()
                    .accountId(itemAccount.getId())
                    .accountCode(itemAccount.getCode())
                    .thirdPartyId(associate.getId())
                    .referenceNumber(receiptNumber)
                    .description(item.getConceptName() + " (" + associate.getFullName() + ")")
                    .debit(BigDecimal.ZERO)
                    .credit(item.getAmount())
                    .build());
        }

        JournalVoucherRequest voucherRequest = JournalVoucherRequest.builder()
                .voucherNumber(voucherNumber)
                .voucherType(VoucherType.INGRESO)
                .issueDate(request.getPaymentDate())
                .description(description)
                .status(VoucherStatus.POSTED)
                .entries(entries)
                .build();

        JournalVoucherResponse postedVoucher = voucherService.createAndPostVoucher(voucherRequest);
        JournalVoucher voucherEntity = journalVoucherRepository.findById(postedVoucher.getId())
                .orElse(null);

        // 4. Guardar el Recibo del Asociado
        AssociateReceipt receipt = AssociateReceipt.builder()
                .receiptNumber(receiptNumber)
                .thirdParty(associate)
                .template(template)
                .paymentDate(request.getPaymentDate())
                .totalAmount(request.getTotalAmount())
                .cashAccount(cashAccount)
                .voucher(voucherEntity)
                .notes(request.getNotes())
                .build();

        List<AssociateReceiptItem> receiptItems = new ArrayList<>();
        for (ReceiptItemRequest itemReq : request.getItems()) {
            Account acc = resolveAccount(itemReq.getAccountId(), itemReq.getAccountCode());
            receiptItems.add(AssociateReceiptItem.builder()
                    .receipt(receipt)
                    .conceptName(itemReq.getConceptName())
                    .category(itemReq.getCategory())
                    .account(acc)
                    .amount(itemReq.getAmount())
                    .build());
        }
        receipt.setItems(receiptItems);

        AssociateReceipt saved = associateReceiptRepository.save(receipt);
        log.info("Recaudo de asociado registrado exitosamente: {} por valor de ${}", receiptNumber, request.getTotalAmount());

        return mapReceiptToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AssociateReceiptResponse> getAllReceipts(Pageable pageable) {
        return associateReceiptRepository.findAllByOrderByPaymentDateDesc(pageable)
                .map(this::mapReceiptToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AssociateReceiptResponse getReceiptById(UUID id) {
        AssociateReceipt receipt = associateReceiptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recibo de asociado no encontrado con ID: " + id));
        return mapReceiptToResponse(receipt);
    }

    @Override
    @Transactional(readOnly = true)
    public AssociateStatementResponse getAssociateStatement(UUID thirdPartyId) {
        ThirdParty associate = thirdPartyRepository.findById(thirdPartyId)
                .orElseThrow(() -> new ResourceNotFoundException("Asociado no encontrado con ID: " + thirdPartyId));

        BigDecimal totalEquity = Optional.ofNullable(associateReceiptRepository.sumTotalByCategoryAndThirdParty(thirdPartyId, QuotaCategory.EQUITY_SAVING)).orElse(BigDecimal.ZERO);
        BigDecimal totalSocial = Optional.ofNullable(associateReceiptRepository.sumTotalByCategoryAndThirdParty(thirdPartyId, QuotaCategory.SOCIAL_FUND)).orElse(BigDecimal.ZERO);
        BigDecimal totalAdmin = Optional.ofNullable(associateReceiptRepository.sumTotalByCategoryAndThirdParty(thirdPartyId, QuotaCategory.INCOME)).orElse(BigDecimal.ZERO);
        BigDecimal totalSavings = Optional.ofNullable(associateReceiptRepository.sumTotalByCategoryAndThirdParty(thirdPartyId, QuotaCategory.SAVINGS)).orElse(BigDecimal.ZERO);
        BigDecimal totalOverall = totalEquity.add(totalSavings).add(totalSocial).add(totalAdmin);

        List<AssociateReceiptResponse> history = associateReceiptRepository
                .findByThirdPartyIdOrderByPaymentDateDesc(thirdPartyId).stream()
                .map(this::mapReceiptToResponse)
                .toList();

        return AssociateStatementResponse.builder()
                .thirdPartyId(associate.getId())
                .fullName(associate.getFullName())
                .identificationNumber(associate.getIdentificationNumber())
                .email(associate.getEmail())
                .phone(associate.getPhone())
                .totalEquitySavings(totalEquity)
                .totalSavings(totalSavings)
                .totalSocialFunds(totalSocial)
                .totalAdministrationFees(totalAdmin)
                .totalPaidOverall(totalOverall)
                .receiptsHistory(history)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssociateReceiptResponse> getReceiptsByAssociate(UUID thirdPartyId) {
        return associateReceiptRepository.findByThirdPartyIdOrderByPaymentDateDesc(thirdPartyId).stream()
                .map(this::mapReceiptToResponse)
                .toList();
    }

    private Account resolveAccount(UUID id, String code) {
        if (id != null) {
            return accountRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Cuenta contable no encontrada con ID: " + id));
        }
        if (code != null && !code.isBlank()) {
            return accountRepository.findByCode(code.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("La cuenta PUC " + code + " no existe en el plan de cuentas"));
        }
        throw new BusinessRuleException("Cada concepto debe indicar la cuenta contable (ID o código)");
    }

    private QuotaTemplateResponse mapTemplateToResponse(QuotaTemplate template) {
        List<QuotaTemplateItemResponse> itemResponses = template.getItems().stream()
                .map(item -> QuotaTemplateItemResponse.builder()
                        .id(item.getId())
                        .conceptName(item.getConceptName())
                        .category(item.getCategory())
                        .accountId(item.getAccount().getId())
                        .accountCode(item.getAccount().getCode())
                        .accountName(item.getAccount().getName())
                        .amount(item.getAmount())
                        .percentage(item.getPercentage())
                        .isMandatory(item.getIsMandatory())
                        .displayOrder(item.getDisplayOrder())
                        .build())
                .toList();

        return QuotaTemplateResponse.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .totalAmount(template.getTotalAmount())
                .isActive(template.getIsActive())
                .items(itemResponses)
                .build();
    }

    private AssociateReceiptResponse mapReceiptToResponse(AssociateReceipt receipt) {
        List<AssociateReceiptResponse.AssociateReceiptItemResponse> items = receipt.getItems().stream()
                .map(item -> AssociateReceiptResponse.AssociateReceiptItemResponse.builder()
                        .id(item.getId())
                        .conceptName(item.getConceptName())
                        .category(item.getCategory())
                        .accountId(item.getAccount().getId())
                        .accountCode(item.getAccount().getCode())
                        .accountName(item.getAccount().getName())
                        .amount(item.getAmount())
                        .build())
                .toList();

        return AssociateReceiptResponse.builder()
                .id(receipt.getId())
                .receiptNumber(receipt.getReceiptNumber())
                .thirdPartyId(receipt.getThirdParty().getId())
                .thirdPartyName(receipt.getThirdParty().getFullName())
                .thirdPartyIdNumber(receipt.getThirdParty().getIdentificationNumber())
                .templateId(receipt.getTemplate() != null ? receipt.getTemplate().getId() : null)
                .templateName(receipt.getTemplate() != null ? receipt.getTemplate().getName() : null)
                .paymentDate(receipt.getPaymentDate())
                .totalAmount(receipt.getTotalAmount())
                .cashAccountId(receipt.getCashAccount().getId())
                .cashAccountCode(receipt.getCashAccount().getCode())
                .cashAccountName(receipt.getCashAccount().getName())
                .voucherId(receipt.getVoucher() != null ? receipt.getVoucher().getId() : null)
                .voucherNumber(receipt.getVoucher() != null ? receipt.getVoucher().getVoucherNumber() : null)
                .notes(receipt.getNotes())
                .createdAt(receipt.getCreatedAt())
                .items(items)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generateAssociateReceiptPdf(UUID id) {
        AssociateReceipt receipt = associateReceiptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recibo de asociado no encontrado con ID: " + id));

        AssociateReceiptResponse resp = mapReceiptToResponse(receipt);
        return pdfExportService.generateAssociateReceiptPdf(
                resp.getReceiptNumber(),
                resp.getPaymentDate(),
                resp.getThirdPartyName(),
                resp.getThirdPartyIdNumber(),
                receipt.getThirdParty() != null ? receipt.getThirdParty().getPhone() : null,
                receipt.getThirdParty() != null ? receipt.getThirdParty().getEmail() : null,
                resp.getCashAccountCode(),
                resp.getCashAccountName(),
                resp.getVoucherNumber(),
                resp.getTemplateName(),
                resp.getTotalAmount(),
                resp.getItems()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportAssociateStatement(UUID thirdPartyId, String format) {
        AssociateStatementResponse statement = getAssociateStatement(thirdPartyId);
        if ("excel".equalsIgnoreCase(format) || "xlsx".equalsIgnoreCase(format)) {
            return excelExportService.generateAssociateStatementExcel(statement);
        }
        return pdfExportService.generateAssociateStatementPdf(statement);
    }
}
