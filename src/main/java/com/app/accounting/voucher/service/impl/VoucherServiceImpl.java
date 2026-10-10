package com.app.accounting.voucher.service.impl;

import com.app.accounting.puc.entity.Account;
import com.app.accounting.puc.service.AccountService;
import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.common.exception.DuplicateResourceException;
import com.app.accounting.common.util.BigDecimalUtils;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.party.service.ThirdPartyService;
import com.app.accounting.voucher.dto.request.JournalEntryRequest;
import com.app.accounting.voucher.dto.request.JournalVoucherRequest;
import com.app.accounting.voucher.dto.response.JournalVoucherResponse;
import com.app.accounting.voucher.entity.JournalEntry;
import com.app.accounting.voucher.entity.JournalVoucher;
import com.app.accounting.voucher.entity.VoucherStatus;
import com.app.accounting.voucher.entity.VoucherType;
import com.app.accounting.voucher.exception.ImmutableVoucherException;
import com.app.accounting.voucher.exception.UnbalancedVoucherException;
import com.app.accounting.voucher.exception.VoucherNotFoundException;
import com.app.accounting.voucher.mapper.JournalVoucherMapper;
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

import com.app.accounting.closing.service.AccountingPeriodService;
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
public class VoucherServiceImpl implements VoucherService {

    private final JournalVoucherRepository voucherRepository;
    private final AccountService accountService;
    private final ThirdPartyService thirdPartyService;
    private final JournalVoucherMapper voucherMapper;
    private final AccountingPeriodService accountingPeriodService;

    @Override
    @Transactional
    public JournalVoucherResponse createAndPostVoucher(JournalVoucherRequest request) {
        JournalVoucher voucher = buildVoucherEntity(request);
        accountingPeriodService.validatePeriodIsOpen(voucher.getIssueDate());
        voucher.setStatus(VoucherStatus.POSTED);

        validateDoubleEntry(voucher);

        JournalVoucher saved = voucherRepository.save(voucher);
        return voucherMapper.toVoucherResponse(saved);
    }

    @Override
    @Transactional
    public JournalVoucherResponse createDraftVoucher(JournalVoucherRequest request) {
        JournalVoucher voucher = buildVoucherEntity(request);
        accountingPeriodService.validatePeriodIsOpen(voucher.getIssueDate());
        voucher.setStatus(VoucherStatus.DRAFT);

        JournalVoucher saved = voucherRepository.save(voucher);
        return voucherMapper.toVoucherResponse(saved);
    }

    @Override
    @Transactional
    public JournalVoucherResponse postVoucher(UUID voucherId) {
        JournalVoucher voucher = voucherRepository.findByIdWithEntries(voucherId)
                .orElseThrow(() -> new VoucherNotFoundException(voucherId));

        accountingPeriodService.validatePeriodIsOpen(voucher.getIssueDate());

        if (voucher.getStatus() == VoucherStatus.POSTED) {
            throw new ImmutableVoucherException(voucher.getVoucherNumber());
        }

        if (voucher.getStatus() == VoucherStatus.VOIDED) {
            throw new BusinessRuleException("No se puede asentar un comprobante que ha sido anulado (VOIDED)");
        }

        validateDoubleEntry(voucher);
        voucher.setStatus(VoucherStatus.POSTED);

        JournalVoucher saved = voucherRepository.save(voucher);
        return voucherMapper.toVoucherResponse(saved);
    }

    @Override
    @Transactional
    public JournalVoucherResponse reverseVoucher(UUID voucherId, String reason) {
        JournalVoucher original = voucherRepository.findByIdWithEntries(voucherId)
                .orElseThrow(() -> new VoucherNotFoundException(voucherId));

        if (original.getStatus() != VoucherStatus.POSTED) {
            throw new BusinessRuleException("Solo se pueden revertir comprobantes en estado asentado (POSTED)");
        }

        accountingPeriodService.validatePeriodIsOpen(original.getIssueDate());
        accountingPeriodService.validatePeriodIsOpen(LocalDate.now());

        String reverseNumber = "REV-" + original.getVoucherNumber();
        if (voucherRepository.existsByVoucherNumber(reverseNumber)) {
            reverseNumber = "REV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() + "-" + original.getVoucherNumber();
        }

        JournalVoucher reversal = JournalVoucher.builder()
                .voucherNumber(reverseNumber)
                .voucherType(VoucherType.NOTA_CONTABLE)
                .issueDate(LocalDate.now())
                .description("Reversión de comprobante " + original.getVoucherNumber() + (reason != null ? ": " + reason : ""))
                .status(VoucherStatus.POSTED)
                .entries(new ArrayList<>())
                .build();

        for (JournalEntry entry : original.getEntries()) {
            JournalEntry reverseEntry = JournalEntry.builder()
                    .account(entry.getAccount())
                    .thirdParty(entry.getThirdParty())
                    .referenceNumber(entry.getReferenceNumber())
                    .description("Contrapartida de " + (entry.getDescription() != null ? entry.getDescription() : original.getVoucherNumber()))
                    .debit(entry.getCredit()) // Invertir crédito a débito
                    .credit(entry.getDebit()) // Invertir débito a crédito
                    .build();
            reversal.addEntry(reverseEntry);
        }

        validateDoubleEntry(reversal);

        original.setStatus(VoucherStatus.VOIDED);
        original.setDescription(original.getDescription() + " [REVERTIDO POR COMPROBANTE " + reverseNumber + "]");
        voucherRepository.save(original);

        JournalVoucher savedReversal = voucherRepository.save(reversal);
        return voucherMapper.toVoucherResponse(savedReversal);
    }

    @Override
    @Transactional(readOnly = true)
    public JournalVoucherResponse getVoucherById(UUID id) {
        JournalVoucher voucher = voucherRepository.findByIdWithEntries(id)
                .orElseThrow(() -> new VoucherNotFoundException(id));
        return voucherMapper.toVoucherResponse(voucher);
    }

    @Override
    @Transactional(readOnly = true)
    public JournalVoucherResponse getVoucherByNumber(String voucherNumber) {
        JournalVoucher voucher = voucherRepository.findByVoucherNumberWithEntries(voucherNumber)
                .orElseThrow(() -> new VoucherNotFoundException(voucherNumber));
        return voucherMapper.toVoucherResponse(voucher);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<JournalVoucherResponse> getAllVouchers(
            LocalDate startDate,
            LocalDate endDate,
            VoucherStatus status,
            Pageable pageable
    ) {
        return getAllVouchers(startDate, endDate, status, null, null, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<JournalVoucherResponse> getAllVouchers(
            LocalDate startDate,
            LocalDate endDate,
            VoucherStatus status,
            VoucherType type,
            String query,
            Pageable pageable
    ) {
        Specification<JournalVoucher> spec = (root, criteria, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("issueDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("issueDate"), endDate));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("voucherType"), type));
            }
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("voucherNumber")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return voucherRepository.findAll(spec, pageable)
                .map(voucherMapper::toVoucherResponse);
    }

    @Override
    @Transactional
    public void deleteDraftVoucher(UUID voucherId) {
        JournalVoucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new VoucherNotFoundException(voucherId));

        accountingPeriodService.validatePeriodIsOpen(voucher.getIssueDate());

        if (voucher.getStatus() == VoucherStatus.POSTED) {
            throw new ImmutableVoucherException(voucher.getVoucherNumber());
        }

        voucherRepository.delete(voucher);
    }

    private JournalVoucher buildVoucherEntity(JournalVoucherRequest request) {
        if (voucherRepository.existsByVoucherNumber(request.getVoucherNumber())) {
            throw new DuplicateResourceException("Ya existe un comprobante con el número: " + request.getVoucherNumber());
        }

        JournalVoucher voucher = JournalVoucher.builder()
                .voucherNumber(request.getVoucherNumber())
                .voucherType(request.getVoucherType())
                .issueDate(request.getIssueDate())
                .description(request.getDescription())
                .entries(new ArrayList<>())
                .build();

        for (JournalEntryRequest entryReq : request.getEntries()) {
            Account account = resolveAccount(entryReq);
            ThirdParty thirdParty = resolveThirdParty(entryReq, account);

            BigDecimal debit = BigDecimalUtils.scale(entryReq.getDebit() != null ? entryReq.getDebit() : BigDecimal.ZERO);
            BigDecimal credit = BigDecimalUtils.scale(entryReq.getCredit() != null ? entryReq.getCredit() : BigDecimal.ZERO);

            if (BigDecimalUtils.isZero(debit) && BigDecimalUtils.isZero(credit)) {
                throw new BusinessRuleException("El asiento contable debe tener un valor mayor a cero en Débito o Crédito");
            }

            JournalEntry entry = JournalEntry.builder()
                    .account(account)
                    .thirdParty(thirdParty)
                    .referenceNumber(entryReq.getReferenceNumber())
                    .description(entryReq.getDescription())
                    .debit(debit)
                    .credit(credit)
                    .build();

            voucher.addEntry(entry);
        }

        return voucher;
    }

    private void validateDoubleEntry(JournalVoucher voucher) {
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (JournalEntry entry : voucher.getEntries()) {
            if (entry.getDebit() != null) {
                totalDebit = totalDebit.add(entry.getDebit());
            }
            if (entry.getCredit() != null) {
                totalCredit = totalCredit.add(entry.getCredit());
            }
        }

        totalDebit = BigDecimalUtils.scale(totalDebit);
        totalCredit = BigDecimalUtils.scale(totalCredit);

        if (!BigDecimalUtils.areEqual(totalDebit, totalCredit)) {
            throw new UnbalancedVoucherException(totalDebit, totalCredit);
        }
    }

    private Account resolveAccount(JournalEntryRequest req) {
        if (req.getAccountId() != null) {
            return accountService.getAccountEntityById(req.getAccountId());
        }
        if (req.getAccountCode() != null && !req.getAccountCode().isBlank()) {
            return accountService.getAccountEntityByCode(req.getAccountCode().trim());
        }
        throw new BusinessRuleException("Se debe especificar el código o el ID de la cuenta contable");
    }

    private ThirdParty resolveThirdParty(JournalEntryRequest req, Account account) {
        ThirdParty thirdParty = null;
        if (req.getThirdPartyId() != null) {
            thirdParty = thirdPartyService.getThirdPartyEntityById(req.getThirdPartyId());
        } else if (req.getThirdPartyIdentification() != null && !req.getThirdPartyIdentification().isBlank()) {
            thirdParty = thirdPartyService.getThirdPartyEntityByIdentification(req.getThirdPartyIdentification().trim());
        }

        if (Boolean.TRUE.equals(account.getRequiresThirdParty()) && thirdParty == null) {
            throw new BusinessRuleException(String.format(
                    "La cuenta contable '%s - %s' exige registrar un tercero",
                    account.getCode(),
                    account.getName()
            ));
        }

        return thirdParty;
    }
}
