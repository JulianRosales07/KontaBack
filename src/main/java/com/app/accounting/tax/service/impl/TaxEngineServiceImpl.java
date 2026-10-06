package com.app.accounting.tax.service.impl;

import com.app.accounting.common.exception.ResourceNotFoundException;
import com.app.accounting.common.util.BigDecimalUtils;
import com.app.accounting.puc.entity.AccountNature;
import com.app.accounting.tax.dto.request.TaxCalculationRequest;
import com.app.accounting.tax.dto.request.TaxTemplateRequest;
import com.app.accounting.tax.dto.response.TaxCalculationResult;
import com.app.accounting.tax.dto.response.TaxItemDetail;
import com.app.accounting.tax.dto.response.TaxTemplateResponse;
import com.app.accounting.tax.entity.TaxTemplate;
import com.app.accounting.tax.entity.TaxType;
import com.app.accounting.tax.repository.TaxTemplateRepository;
import com.app.accounting.tax.service.TaxEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaxEngineServiceImpl implements TaxEngineService {

    private final TaxTemplateRepository taxTemplateRepository;
    private final com.app.accounting.party.service.ThirdPartyService thirdPartyService;
    private final com.app.accounting.voucher.repository.JournalEntryRepository entryRepository;
    private final com.app.accounting.common.document.PdfExportService pdfExportService;

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.0000");

    @Override
    @Transactional(readOnly = true)
    public List<TaxTemplateResponse> getAllActiveTemplates() {
        return taxTemplateRepository.findByIsActiveTrue()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TaxTemplateResponse getTemplateById(UUID id) {
        TaxTemplate template = taxTemplateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla de impuesto no encontrada con ID: " + id));
        return toResponse(template);
    }

    @Override
    @Transactional
    public TaxTemplateResponse createTemplate(TaxTemplateRequest request) {
        TaxTemplate entity = TaxTemplate.builder()
                .name(request.getName())
                .taxType(request.getTaxType())
                .ratePercentage(BigDecimalUtils.scale(request.getRatePercentage()))
                .salesAccountCode(request.getSalesAccountCode().trim())
                .purchaseAccountCode(request.getPurchaseAccountCode().trim())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();
        return toResponse(taxTemplateRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public TaxCalculationResult calculateTaxes(TaxCalculationRequest request) {
        BigDecimal subtotal = BigDecimalUtils.scale(request.getSubtotal() != null ? request.getSubtotal() : BigDecimal.ZERO);
        List<TaxTemplate> templates = resolveTemplates(request.getTaxTemplateIds());

        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal retentionTotal = BigDecimal.ZERO;
        List<TaxItemDetail> items = new ArrayList<>();

        for (TaxTemplate template : templates) {
            BigDecimal rate = template.getRatePercentage();
            BigDecimal taxAmount = subtotal.multiply(rate)
                    .divide(ONE_HUNDRED, BigDecimalUtils.DEFAULT_SCALE, RoundingMode.HALF_EVEN);
            taxAmount = BigDecimalUtils.scale(taxAmount);

            boolean isRetention = (template.getTaxType() == TaxType.RETEFUENTE || template.getTaxType() == TaxType.RETEICA);
            String accountCode;
            AccountNature nature;

            if (request.isSale()) {
                accountCode = template.getSalesAccountCode();
                if (isRetention) {
                    nature = AccountNature.DEBIT; // Anticipo de retención a favor practicada por cliente
                    retentionTotal = retentionTotal.add(taxAmount);
                } else {
                    nature = AccountNature.CREDIT; // IVA Generado o Impoconsumo generado
                    taxTotal = taxTotal.add(taxAmount);
                }
            } else {
                accountCode = template.getPurchaseAccountCode();
                if (isRetention) {
                    nature = AccountNature.CREDIT; // Retención por pagar retenida al proveedor
                    retentionTotal = retentionTotal.add(taxAmount);
                } else {
                    nature = AccountNature.DEBIT; // IVA Descontable o Gasto por Impoconsumo
                    taxTotal = taxTotal.add(taxAmount);
                }
            }

            items.add(TaxItemDetail.builder()
                    .templateId(template.getId())
                    .name(template.getName())
                    .taxType(template.getTaxType())
                    .ratePercentage(rate)
                    .baseAmount(subtotal)
                    .taxAmount(taxAmount)
                    .accountCode(accountCode)
                    .accountNature(nature)
                    .isRetention(isRetention)
                    .build());
        }

        taxTotal = BigDecimalUtils.scale(taxTotal);
        retentionTotal = BigDecimalUtils.scale(retentionTotal);
        BigDecimal totalAmount = BigDecimalUtils.scale(subtotal.add(taxTotal).subtract(retentionTotal));

        return TaxCalculationResult.builder()
                .subtotal(subtotal)
                .taxTotal(taxTotal)
                .retentionTotal(retentionTotal)
                .totalAmount(totalAmount)
                .items(items)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxTemplate> resolveTemplates(List<UUID> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) {
            return List.of();
        }
        return taxTemplateRepository.findAllById(templateIds);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generateWithholdingCertificatePdf(UUID partyId, int year) {
        com.app.accounting.party.entity.ThirdParty party = thirdPartyService.getThirdPartyEntityById(partyId);

        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        List<com.app.accounting.voucher.repository.PartyAccountBalanceProjection> balances = entryRepository
                .findPartyAccountBalancesBetweenDatesAndParty(startDate, endDate, partyId);

        List<com.app.accounting.common.document.PdfExportService.WithholdingCertificateItem> certItems = new ArrayList<>();
        BigDecimal totalWithheld = BigDecimal.ZERO;

        for (com.app.accounting.voucher.repository.PartyAccountBalanceProjection p : balances) {
            String code = p.getAccountCode();
            if (code == null) continue;

            boolean isWithholdingAccount = code.startsWith("2365") || code.startsWith("2367") || code.startsWith("2368")
                    || code.startsWith("1355");

            if (!isWithholdingAccount) {
                continue;
            }

            BigDecimal credit = p.getTotalCredit() != null ? p.getTotalCredit() : BigDecimal.ZERO;
            BigDecimal debit = p.getTotalDebit() != null ? p.getTotalDebit() : BigDecimal.ZERO;

            BigDecimal withheld;
            if (code.startsWith("23")) {
                withheld = credit.subtract(debit);
            } else {
                withheld = debit.subtract(credit);
            }

            if (withheld.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal rate = findRateForAccount(code);
            BigDecimal taxableBase;
            if (rate.compareTo(BigDecimal.ZERO) > 0) {
                taxableBase = withheld.divide(rate, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100.0000"));
            } else {
                taxableBase = withheld.multiply(new BigDecimal("10.0000"));
            }

            certItems.add(new com.app.accounting.common.document.PdfExportService.WithholdingCertificateItem(
                    p.getAccountName() != null ? p.getAccountName() : "Retención aplicada",
                    code,
                    taxableBase,
                    withheld
            ));

            totalWithheld = totalWithheld.add(withheld);
        }

        if (certItems.isEmpty()) {
            certItems.add(new com.app.accounting.common.document.PdfExportService.WithholdingCertificateItem(
                    "Retención en la Fuente general (Sin retenciones en el año gravable)",
                    "236540",
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            ));
        }

        return pdfExportService.generateWithholdingCertificatePdf(
                year,
                party.getFullName(),
                party.getIdentificationNumber() + (party.getDv() != null ? "-" + party.getDv() : ""),
                certItems,
                totalWithheld
        );
    }

    private BigDecimal findRateForAccount(String accountCode) {
        return taxTemplateRepository.findByIsActiveTrue().stream()
                .filter(t -> accountCode.equals(t.getPurchaseAccountCode()) || accountCode.equals(t.getSalesAccountCode()))
                .map(TaxTemplate::getRatePercentage)
                .findFirst()
                .orElse(new BigDecimal("2.5000"));
    }

    private TaxTemplateResponse toResponse(TaxTemplate entity) {
        return TaxTemplateResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .taxType(entity.getTaxType())
                .ratePercentage(entity.getRatePercentage())
                .salesAccountCode(entity.getSalesAccountCode())
                .purchaseAccountCode(entity.getPurchaseAccountCode())
                .isActive(entity.getIsActive())
                .build();
    }
}
