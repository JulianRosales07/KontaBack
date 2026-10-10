package com.app.accounting.fine;

import com.app.accounting.association.AccountProvisioningService;
import com.app.accounting.association.AssociationChart;
import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.common.exception.ResourceNotFoundException;
import com.app.accounting.fine.FineDtos.*;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.party.repository.ThirdPartyRepository;
import com.app.accounting.voucher.dto.request.JournalEntryRequest;
import com.app.accounting.voucher.dto.request.JournalVoucherRequest;
import com.app.accounting.voucher.dto.response.JournalVoucherResponse;
import com.app.accounting.voucher.entity.JournalVoucher;
import com.app.accounting.voucher.entity.VoucherType;
import com.app.accounting.voucher.repository.JournalVoucherRepository;
import com.app.accounting.voucher.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Multas de asociados. Una multa pendiente no afecta la contabilidad; al cobrarse se registra un comprobante de
 * ingreso: débito caja/bancos, crédito 429510 Multas e inasistencia (a nombre del asociado). Así aparece en la
 * columna MULTAS de la planilla y en el estado de resultados.
 */
@Service
@RequiredArgsConstructor
public class FineService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final FineTypeRepository typeRepository;
    private final AssociateFineRepository fineRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final VoucherService voucherService;
    private final JournalVoucherRepository voucherRepository;
    private final AccountProvisioningService provisioning;

    // ================================================================== tipos de multa

    @Transactional(readOnly = true)
    public List<FineTypeResponse> types() {
        return typeRepository.findAllByOrderByActiveDescNameAsc().stream()
                .map(t -> new FineTypeResponse(t.getId(), t.getName(), t.getDescription(), t.getAmount(), t.isActive(),
                        fineRepository.existsByFineTypeId(t.getId())))
                .toList();
    }

    @Transactional
    public FineTypeResponse createType(FineTypeRequest r) {
        if (typeRepository.existsByNameIgnoreCase(r.name().trim())) {
            throw new BusinessRuleException("Ya existe una multa llamada \"" + r.name().trim() + "\"");
        }
        FineType t = typeRepository.save(FineType.builder()
                .name(r.name().trim())
                .description(blankToNull(r.description()))
                .amount(r.amount())
                .active(r.active() == null || r.active())
                .build());
        return new FineTypeResponse(t.getId(), t.getName(), t.getDescription(), t.getAmount(), t.isActive(), false);
    }

    @Transactional
    public FineTypeResponse updateType(UUID id, FineTypeRequest r) {
        FineType t = typeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("La multa no existe"));
        if (typeRepository.existsByNameIgnoreCaseAndIdNot(r.name().trim(), id)) {
            throw new BusinessRuleException("Ya existe una multa llamada \"" + r.name().trim() + "\"");
        }
        t.setName(r.name().trim());
        t.setDescription(blankToNull(r.description()));
        t.setAmount(r.amount()); // solo afecta multas nuevas
        if (r.active() != null) t.setActive(r.active());
        typeRepository.save(t);
        return new FineTypeResponse(t.getId(), t.getName(), t.getDescription(), t.getAmount(), t.isActive(),
                fineRepository.existsByFineTypeId(id));
    }

    /** Elimina el tipo si nunca se usó; si ya tiene multas, solo se desactiva para conservar el historial. */
    @Transactional
    public void deleteType(UUID id) {
        FineType t = typeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("La multa no existe"));
        if (fineRepository.existsByFineTypeId(id)) {
            t.setActive(false);
            typeRepository.save(t);
        } else {
            typeRepository.delete(t);
        }
    }

    // ================================================================== multas

    @Transactional
    public RegisterFinesResponse register(RegisterFinesRequest r) {
        FineType type = typeRepository.findById(r.fineTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("El tipo de multa no existe"));
        if (!type.isActive()) {
            throw new BusinessRuleException("La multa \"" + type.getName() + "\" está inactiva");
        }
        BigDecimal amount = r.amount() != null ? r.amount() : type.getAmount();
        List<String> skipped = new ArrayList<>();
        int created = 0;
        for (UUID partyId : new LinkedHashSet<>(r.thirdPartyIds())) {
            ThirdParty party = thirdPartyRepository.findById(partyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Asociado no encontrado: " + partyId));
            if (fineRepository.existsByThirdPartyIdAndFineTypeIdAndFineDateAndStatusNot(partyId, type.getId(), r.fineDate(),
                    AssociateFine.Status.VOIDED)) {
                skipped.add(party.getFullName() + " (ya tiene esta multa el " + r.fineDate().format(DAY) + ")");
                continue;
            }
            fineRepository.save(AssociateFine.builder()
                    .thirdParty(party)
                    .fineType(type)
                    .fineDate(r.fineDate())
                    .amount(amount)
                    .description(blankToNull(r.description()))
                    .build());
            created++;
        }
        return new RegisterFinesResponse(created, skipped, amount.multiply(BigDecimal.valueOf(created)));
    }

    @Transactional(readOnly = true)
    public List<FineResponse> search(AssociateFine.Status status, UUID partyId, UUID typeId, LocalDate from, LocalDate to) {
        LocalDate f = from != null ? from : LocalDate.of(1900, 1, 1);
        LocalDate t = to != null ? to : LocalDate.of(2999, 12, 31);
        return fineRepository.search(status, partyId, typeId, f, t).stream().map(FineResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public FineSummary summary(UUID partyId) {
        List<AssociateFine> all = fineRepository.search(null, partyId, null, LocalDate.of(1900, 1, 1), LocalDate.of(2999, 12, 31));
        long[] count = new long[3];
        BigDecimal[] total = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        Set<UUID> withPending = new HashSet<>();
        for (AssociateFine f : all) {
            int i = f.getStatus().ordinal();
            count[i]++;
            total[i] = total[i].add(f.getAmount());
            if (f.getStatus() == AssociateFine.Status.PENDING) withPending.add(f.getThirdParty().getId());
        }
        return new FineSummary(count[0], total[0], count[1], total[1], count[2], total[2], withPending.size());
    }

    /** Cobra multas pendientes: un comprobante de ingreso por asociado. */
    @Transactional
    public PayFinesResponse pay(PayFinesRequest r) {
        List<AssociateFine> fines = pending(r.fineIds(), null);
        String cashAccount = r.cashAccountCode() != null && !r.cashAccountCode().isBlank() ? r.cashAccountCode().trim() : AssociationChart.CASH;
        if (!cashAccount.startsWith("11")) {
            throw new BusinessRuleException("La cuenta de cobro debe ser de caja o bancos (grupo 11)");
        }
        ensureIncomeAccount();

        Map<UUID, List<AssociateFine>> byParty = new LinkedHashMap<>();
        fines.forEach(f -> byParty.computeIfAbsent(f.getThirdParty().getId(), k -> new ArrayList<>()).add(f));
        List<String> receipts = new ArrayList<>();
        BigDecimal grand = BigDecimal.ZERO;
        for (List<AssociateFine> list : byParty.values()) {
            ThirdParty party = list.getFirst().getThirdParty();
            String number = nextReceiptNumber();
            BigDecimal total = list.stream().map(AssociateFine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<JournalEntryRequest> entries = new ArrayList<>();
            entries.add(JournalEntryRequest.builder()
                    .accountCode(cashAccount)
                    .thirdPartyId(party.getId())
                    .referenceNumber(number)
                    .description("Pago de multas " + number)
                    .debit(total)
                    .build());
            for (AssociateFine f : list) {
                entries.add(JournalEntryRequest.builder()
                        .accountCode(AssociationChart.FINES)
                        .thirdPartyId(party.getId())
                        .referenceNumber(number)
                        .description(fineLabel(f))
                        .credit(f.getAmount())
                        .build());
            }
            JournalVoucherResponse voucher = voucherService.createAndPostVoucher(JournalVoucherRequest.builder()
                    .voucherNumber(number)
                    .voucherType(VoucherType.INGRESO)
                    .issueDate(r.paymentDate())
                    .description("Pago de multas · " + party.getFullName())
                    .entries(entries)
                    .build());
            markPaid(list, number, voucherRepository.findById(voucher.getId()).orElse(null), r.paymentDate());
            receipts.add(number);
            grand = grand.add(total);
        }
        return new PayFinesResponse(receipts, fines.size(), grand);
    }

    @Transactional
    public FineResponse voidFine(UUID id, VoidFineRequest r) {
        AssociateFine f = fineRepository.findAllWithDetails(List.of(id)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("La multa no existe"));
        if (f.getStatus() != AssociateFine.Status.PENDING) {
            throw new BusinessRuleException("Solo se pueden condonar multas pendientes. Si ya se pagó, anule el comprobante "
                    + f.getReceiptNumber() + " en Asientos y Comprobantes.");
        }
        f.setStatus(AssociateFine.Status.VOIDED);
        f.setVoidReason(r.reason().trim());
        return FineResponse.of(fineRepository.save(f));
    }

    // ================================================================== integración con el recaudo de cuotas

    /**
     * Multas pendientes del asociado que se incluyen en un recibo de cuota. Valida que existan, estén pendientes
     * y sean del mismo asociado.
     */
    @Transactional(readOnly = true)
    public List<AssociateFine> pending(Collection<UUID> ids, UUID partyId) {
        List<AssociateFine> fines = fineRepository.findAllWithDetails(new LinkedHashSet<>(ids));
        if (fines.size() != new HashSet<>(ids).size()) {
            throw new BusinessRuleException("Alguna de las multas seleccionadas no existe");
        }
        for (AssociateFine f : fines) {
            if (f.getStatus() != AssociateFine.Status.PENDING) {
                throw new BusinessRuleException("La multa de " + f.getThirdParty().getFullName() + " del " + f.getFineDate().format(DAY)
                        + " ya no está pendiente");
            }
            if (partyId != null && !f.getThirdParty().getId().equals(partyId)) {
                throw new BusinessRuleException("La multa del " + f.getFineDate().format(DAY) + " pertenece a otro asociado");
            }
        }
        return fines;
    }

    /** Garantiza la cuenta de ingreso 429510 (y sus padres) antes de contabilizar multas. */
    @Transactional
    public void ensureIncomeAccount() {
        provisioning.ensure(AssociationChart.FINES, null);
    }

    @Transactional
    public void markPaid(List<AssociateFine> fines, String receiptNumber, JournalVoucher voucher, LocalDate paidDate) {
        for (AssociateFine f : fines) {
            f.setStatus(AssociateFine.Status.PAID);
            f.setReceiptNumber(receiptNumber);
            f.setVoucher(voucher);
            f.setPaidDate(paidDate);
        }
        fineRepository.saveAll(fines);
    }

    public static String fineLabel(AssociateFine f) {
        return "Multa: " + f.getFineType().getName() + " (" + f.getFineDate().format(DAY) + ")";
    }

    private String nextReceiptNumber() {
        long n = fineRepository.count() + 1;
        String number;
        do {
            number = String.format("MUL-%05d", n++);
        } while (voucherRepository.existsByVoucherNumber(number));
        return number;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
