package com.app.accounting.loan.service.impl;

import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.common.exception.ResourceNotFoundException;
import com.app.accounting.loan.dto.request.CreateLoanRequest;
import com.app.accounting.loan.dto.request.LegacyLoanImportRequest;
import com.app.accounting.loan.dto.request.LoanPaymentRequest;
import com.app.accounting.loan.dto.request.LoanPreviewRequest;
import com.app.accounting.loan.dto.response.LegacyImportResultResponse;
import com.app.accounting.loan.dto.response.LoanPaymentResponse;
import com.app.accounting.loan.dto.response.LoanPreviewResponse;
import com.app.accounting.loan.dto.response.LoanResponse;
import com.app.accounting.loan.dto.response.LoanScheduleLineResponse;
import com.app.accounting.loan.entity.*;
import com.app.accounting.loan.repository.LoanPaymentRepository;
import com.app.accounting.loan.repository.LoanRepository;
import com.app.accounting.loan.service.LoanAmortizationCalculator;
import com.app.accounting.loan.service.LoanService;
import com.app.accounting.party.entity.PersonType;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;

import static com.app.accounting.loan.service.LoanAmortizationCalculator.money;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanServiceImpl implements LoanService {

    static final String DEFAULT_LOAN_ACCOUNT = "136505";
    static final String DEFAULT_INTEREST_ACCOUNT = "421005";
    static final String DEFAULT_CASH_ACCOUNT = "110505";
    static final String DEFAULT_BANK_ACCOUNT = "111005";

    private final LoanAmortizationCalculator calculator;
    private final LoanRepository loanRepository;
    private final LoanPaymentRepository paymentRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final AccountRepository accountRepository;
    private final VoucherService voucherService;
    private final JournalVoucherRepository journalVoucherRepository;

    // ------------------------------------------------------------------ preview / create

    @Override
    public LoanPreviewResponse preview(LoanPreviewRequest request) {
        LoanAmortizationCalculator.Schedule schedule = calculator.buildSchedule(
                request.getPrincipalAmount(), request.getMonthlyRate(), request.getTermMonths(), request.getFirstPaymentDate());
        List<LoanScheduleLineResponse> lines = schedule.lines().stream()
                .map(l -> LoanScheduleLineResponse.builder()
                        .number(l.number())
                        .dueDate(l.dueDate())
                        .openingBalance(l.openingBalance())
                        .principal(l.principal())
                        .interest(l.interest())
                        .total(l.total())
                        .closingBalance(l.closingBalance())
                        .principalPaid(BigDecimal.ZERO)
                        .interestPaid(BigDecimal.ZERO)
                        .pending(l.total())
                        .status(InstallmentStatus.PENDING.name())
                        .build())
                .toList();
        return LoanPreviewResponse.builder()
                .principalAmount(schedule.totalPrincipal())
                .monthlyRate(request.getMonthlyRate())
                .termMonths(request.getTermMonths())
                .principalPerInstallment(lines.getFirst().getPrincipal())
                .firstInstallment(schedule.firstInstallment())
                .totalInterest(schedule.totalInterest())
                .totalPayable(schedule.totalPayable())
                .schedule(lines)
                .build();
    }

    @Override
    @Transactional
    public LoanResponse createLoan(CreateLoanRequest request) {
        if (request.getFirstPaymentDate().isBefore(request.getDisbursementDate())) {
            throw new BusinessRuleException("La primera cuota no puede vencer antes de la fecha de desembolso");
        }
        ThirdParty associate = thirdPartyRepository.findById(request.getThirdPartyId())
                .orElseThrow(() -> new ResourceNotFoundException("Asociado no encontrado con ID: " + request.getThirdPartyId()));
        if ("INACTIVE".equalsIgnoreCase(associate.getStatus())) {
            throw new BusinessRuleException("El asociado " + associate.getFullName() + " está inactivo");
        }
        markAsAssociate(associate);

        Account loanAccount = requireAccount(orDefault(request.getLoanAccountCode(), DEFAULT_LOAN_ACCOUNT));
        Account interestAccount = requireAccount(orDefault(request.getInterestAccountCode(), DEFAULT_INTEREST_ACCOUNT));
        Account disbursementAccount = requireAccount(orDefault(request.getDisbursementAccountCode(), DEFAULT_CASH_ACCOUNT));

        LoanAmortizationCalculator.Schedule schedule = calculator.buildSchedule(
                request.getPrincipalAmount(), request.getMonthlyRate(), request.getTermMonths(), request.getFirstPaymentDate());

        Loan loan = Loan.builder()
                .loanNumber(nextNumber("PR-", loanRepository::existsByLoanNumber, loanRepository.count()))
                .thirdParty(associate)
                .principalAmount(schedule.totalPrincipal())
                .monthlyRate(request.getMonthlyRate())
                .termMonths(request.getTermMonths())
                .amortizationType(AmortizationType.CONSTANT_PRINCIPAL)
                .disbursementDate(request.getDisbursementDate())
                .firstPaymentDate(request.getFirstPaymentDate())
                .status(LoanStatus.ACTIVE)
                .outstandingPrincipal(schedule.totalPrincipal())
                .loanAccount(loanAccount)
                .interestAccount(interestAccount)
                .disbursementAccount(disbursementAccount)
                .notes(request.getNotes())
                .build();

        for (LoanAmortizationCalculator.ScheduleLine line : schedule.lines()) {
            loan.addInstallment(LoanInstallment.builder()
                    .installmentNumber(line.number())
                    .dueDate(line.dueDate())
                    .openingBalance(line.openingBalance())
                    .principalAmount(line.principal())
                    .interestAmount(line.interest())
                    .totalAmount(line.total())
                    .closingBalance(line.closingBalance())
                    .build());
        }

        // Asiento de desembolso: Dr Cartera (tercero) / Cr Caja-Banco
        String voucherNumber = nextNumber("CE-PR-", journalVoucherRepository::existsByVoucherNumber, loanRepository.count());
        JournalVoucherResponse voucher = voucherService.createAndPostVoucher(JournalVoucherRequest.builder()
                .voucherNumber(voucherNumber)
                .voucherType(VoucherType.EGRESO)
                .issueDate(request.getDisbursementDate())
                .description("Desembolso préstamo " + loan.getLoanNumber() + " - " + associate.getFullName())
                .status(VoucherStatus.POSTED)
                .entries(List.of(
                        entry(loanAccount, associate, loan.getLoanNumber(), "Préstamo " + loan.getLoanNumber() + " a " + associate.getFullName(),
                                loan.getPrincipalAmount(), BigDecimal.ZERO),
                        entry(disbursementAccount, associate, loan.getLoanNumber(), "Desembolso préstamo " + loan.getLoanNumber(),
                                BigDecimal.ZERO, loan.getPrincipalAmount())))
                .build());
        loan.setDisbursementVoucher(journalVoucherRepository.findById(voucher.getId()).orElse(null));

        Loan saved = loanRepository.save(loan);
        log.info("Préstamo {} creado para {} por ${}", saved.getLoanNumber(), associate.getFullName(), saved.getPrincipalAmount());
        return toResponse(saved, true);
    }

    // ------------------------------------------------------------------ queries

    @Override
    @Transactional(readOnly = true)
    public List<LoanResponse> getLoans(LoanStatus status, UUID thirdPartyId, boolean includeInstallments) {
        List<Loan> loans;
        if (thirdPartyId != null) {
            loans = loanRepository.findByThirdPartyWithDetails(thirdPartyId);
            if (status != null) {
                loans = loans.stream().filter(l -> l.getStatus() == status).toList();
            }
        } else if (status != null) {
            loans = loanRepository.findByStatusInWithDetails(List.of(status));
        } else {
            loans = loanRepository.findAllWithDetails();
        }
        return loans.stream().map(l -> toResponse(l, includeInstallments)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LoanResponse getLoan(UUID id) {
        return toResponse(findLoan(id), true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoanPaymentResponse> getPayments(UUID loanId) {
        findLoan(loanId);
        Map<String, List<LoanPayment>> byReceipt = new LinkedHashMap<>();
        for (LoanPayment p : paymentRepository.findByLoanIdWithDetails(loanId)) {
            byReceipt.computeIfAbsent(p.getReceiptNumber(), k -> new ArrayList<>()).add(p);
        }
        return byReceipt.values().stream().map(this::toPaymentResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoanPaymentResponse> getPaymentsBetween(LocalDate from, LocalDate to) {
        Map<String, List<LoanPayment>> byReceipt = new LinkedHashMap<>();
        List<LoanPayment> payments = new ArrayList<>(paymentRepository.findByPaymentDateBetweenWithDetails(from, to));
        Collections.reverse(payments); // más recientes primero
        for (LoanPayment p : payments) {
            byReceipt.computeIfAbsent(p.getReceiptNumber(), k -> new ArrayList<>()).add(p);
        }
        return byReceipt.values().stream().map(this::toPaymentResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LoanPaymentResponse getReceipt(String receiptNumber) {
        List<LoanPayment> payments = paymentRepository.findByReceiptNumberWithDetails(receiptNumber);
        if (payments.isEmpty()) {
            throw new ResourceNotFoundException("Recibo de abono no encontrado: " + receiptNumber);
        }
        return toPaymentResponse(payments);
    }

    // ------------------------------------------------------------------ payments

    @Override
    @Transactional
    public LoanPaymentResponse registerPayment(UUID loanId, LoanPaymentRequest request) {
        Loan loan = findLoan(loanId);
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new BusinessRuleException("El préstamo " + loan.getLoanNumber() + " no está activo (" + loan.getStatus() + ")");
        }
        if (request.getPaymentDate().isBefore(loan.getDisbursementDate())) {
            throw new BusinessRuleException("La fecha del abono no puede ser anterior al desembolso");
        }

        Map<UUID, BigDecimal> requested = new HashMap<>();
        for (LoanPaymentRequest.Allocation a : request.getAllocations()) {
            if (requested.put(a.getInstallmentId(), a.getAmount()) != null) {
                throw new BusinessRuleException("Una cuota aparece repetida en el abono");
            }
        }
        Set<UUID> loanInstallmentIds = new HashSet<>();
        loan.getInstallments().forEach(i -> loanInstallmentIds.add(i.getId()));
        if (!loanInstallmentIds.containsAll(requested.keySet())) {
            throw new BusinessRuleException("Una o más cuotas no pertenecen al préstamo " + loan.getLoanNumber());
        }

        // Las cuotas se pagan en orden: no se puede abonar a una cuota si una anterior queda pendiente.
        record Applied(LoanInstallment installment, LoanAmortizationCalculator.Allocation allocation) {
        }
        List<Applied> applied = new ArrayList<>();
        boolean gapFound = false;
        for (LoanInstallment inst : loan.getInstallments()) {
            if (inst.getStatus() == InstallmentStatus.PAID) {
                continue;
            }
            BigDecimal amount = requested.get(inst.getId());
            if (amount == null) {
                gapFound = true;
                continue;
            }
            if (gapFound) {
                throw new BusinessRuleException("Debe pagar primero las cuotas anteriores a la cuota " + inst.getInstallmentNumber());
            }
            LoanAmortizationCalculator.Allocation allocation;
            try {
                allocation = calculator.allocate(amount, inst.getInterestPending(), inst.getPrincipalPending());
            } catch (IllegalArgumentException e) {
                throw new BusinessRuleException("Cuota " + inst.getInstallmentNumber() + ": " + e.getMessage());
            }
            applied.add(new Applied(inst, allocation));
            boolean fullyPaid = allocation.total().compareTo(inst.getTotalPending()) == 0;
            if (!fullyPaid) {
                gapFound = true; // un abono parcial no deja avanzar a cuotas posteriores
            }
        }
        if (applied.size() != requested.size()) {
            throw new BusinessRuleException("Una o más cuotas seleccionadas ya están pagadas");
        }

        BigDecimal totalPrincipal = applied.stream().map(a -> a.allocation().principal()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalInterest = applied.stream().map(a -> a.allocation().interest()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = totalPrincipal.add(totalInterest);

        Account cashAccount = requireAccount(orDefault(request.getCashAccountCode(),
                request.getPaymentMethod() == PaymentMethod.CASH ? DEFAULT_CASH_ACCOUNT : DEFAULT_BANK_ACCOUNT));
        Account loanAccount = loan.getLoanAccount() != null ? loan.getLoanAccount() : requireAccount(DEFAULT_LOAN_ACCOUNT);
        Account interestAccount = loan.getInterestAccount() != null ? loan.getInterestAccount() : requireAccount(DEFAULT_INTEREST_ACCOUNT);
        ThirdParty associate = loan.getThirdParty();

        long sequence = paymentRepository.countDistinctReceipts();
        String receiptNumber = nextNumber("RC-PR-", paymentRepository::existsByReceiptNumber, sequence);
        String voucherNumber = nextNumber("CI-PR-", journalVoucherRepository::existsByVoucherNumber, sequence);

        // Asiento: Dr Caja/Banco (total) / Cr Cartera (capital) / Cr Ingreso por intereses (interés)
        List<JournalEntryRequest> entries = new ArrayList<>();
        entries.add(entry(cashAccount, associate, receiptNumber,
                "Abono préstamo " + loan.getLoanNumber() + " - " + associate.getFullName(), total, BigDecimal.ZERO));
        if (totalPrincipal.signum() > 0) {
            entries.add(entry(loanAccount, associate, receiptNumber,
                    "Abono a capital préstamo " + loan.getLoanNumber(), BigDecimal.ZERO, totalPrincipal));
        }
        if (totalInterest.signum() > 0) {
            entries.add(entry(interestAccount, associate, receiptNumber,
                    "Intereses préstamo " + loan.getLoanNumber(), BigDecimal.ZERO, totalInterest));
        }
        JournalVoucherResponse posted = voucherService.createAndPostVoucher(JournalVoucherRequest.builder()
                .voucherNumber(voucherNumber)
                .voucherType(VoucherType.INGRESO)
                .issueDate(request.getPaymentDate())
                .description("Recaudo préstamo " + loan.getLoanNumber() + " - " + associate.getFullName() + " [" + receiptNumber + "]")
                .status(VoucherStatus.POSTED)
                .entries(entries)
                .build());
        JournalVoucher voucher = journalVoucherRepository.findById(posted.getId()).orElse(null);

        List<LoanPayment> payments = new ArrayList<>();
        for (Applied a : applied) {
            LoanInstallment inst = a.installment();
            inst.setInterestPaid(inst.getInterestPaid().add(a.allocation().interest()));
            inst.setPrincipalPaid(inst.getPrincipalPaid().add(a.allocation().principal()));
            boolean paid = inst.getTotalPending().signum() == 0;
            inst.setStatus(paid ? InstallmentStatus.PAID : InstallmentStatus.PARTIAL);
            inst.setPaidDate(request.getPaymentDate());

            payments.add(LoanPayment.builder()
                    .receiptNumber(receiptNumber)
                    .loan(loan)
                    .installment(inst)
                    .thirdParty(associate)
                    .paymentDate(request.getPaymentDate())
                    .amount(a.allocation().total())
                    .principalApplied(a.allocation().principal())
                    .interestApplied(a.allocation().interest())
                    .paymentMethod(request.getPaymentMethod())
                    .cashAccount(cashAccount)
                    .voucher(voucher)
                    .reference(request.getReference())
                    .notes(request.getNotes())
                    .build());
        }

        // Solo el capital disminuye el saldo del préstamo; el interés es ingreso.
        loan.setOutstandingPrincipal(loan.getOutstandingPrincipal().subtract(totalPrincipal));
        if (loan.getInstallments().stream().allMatch(i -> i.getStatus() == InstallmentStatus.PAID)) {
            loan.setStatus(LoanStatus.PAID);
        }
        loanRepository.save(loan);
        paymentRepository.saveAll(payments);

        log.info("Abono {} aplicado a préstamo {}: capital ${} interés ${}", receiptNumber, loan.getLoanNumber(), totalPrincipal, totalInterest);
        return toPaymentResponse(payments);
    }

    // ------------------------------------------------------------------ legacy import

    @Override
    @Transactional
    public LegacyImportResultResponse importLegacy(LegacyLoanImportRequest request) {
        LegacyImportResultResponse result = LegacyImportResultResponse.builder().build();
        Account loanAccount = accountRepository.findByCode(DEFAULT_LOAN_ACCOUNT).orElse(null);
        Account interestAccount = accountRepository.findByCode(DEFAULT_INTEREST_ACCOUNT).orElse(null);

        for (LegacyLoanImportRequest.LegacyLoan legacy : request.getLoans()) {
            if (loanRepository.existsByLoanNumber(legacy.getLoanNumber())) {
                result.setSkipped(result.getSkipped() + 1);
                result.getMessages().add(legacy.getLoanNumber() + ": ya existía, se omitió");
                continue;
            }
            ThirdParty associate = thirdPartyRepository.findByIdentificationNumber(legacy.getAssociateIdentification().trim())
                    .orElseGet(() -> thirdPartyRepository.save(ThirdParty.builder()
                            .identificationNumber(legacy.getAssociateIdentification().trim())
                            .fullName(legacy.getAssociateName().trim())
                            .type(PersonType.NATURAL)
                            .build()));
            markAsAssociate(associate);

            BigDecimal principal = money(legacy.getPrincipalAmount());
            AmortizationType type = "FRENCH".equalsIgnoreCase(legacy.getAmortizationType())
                    ? AmortizationType.FRENCH : AmortizationType.CONSTANT_PRINCIPAL;

            Loan loan = Loan.builder()
                    .loanNumber(legacy.getLoanNumber())
                    .thirdParty(associate)
                    .principalAmount(principal)
                    .monthlyRate(legacy.getMonthlyRate())
                    .termMonths(legacy.getTermMonths())
                    .amortizationType(type)
                    .disbursementDate(legacy.getDisbursementDate())
                    .firstPaymentDate(legacy.getFirstPaymentDate())
                    .status(LoanStatus.ACTIVE)
                    .outstandingPrincipal(principal)
                    .loanAccount(loanAccount)
                    .interestAccount(interestAccount)
                    .legacy(true)
                    .notes(legacy.getNotes())
                    .build();

            // El plan se conserva tal cual; solo se reconstruye lo pagado por cuota a partir de su estado.
            BigDecimal opening = principal;
            BigDecimal paidPrincipal = BigDecimal.ZERO;
            List<LegacyLoanImportRequest.LegacyInstallment> ordered = new ArrayList<>(legacy.getInstallments());
            ordered.sort(Comparator.comparing(LegacyLoanImportRequest.LegacyInstallment::getNumber));
            for (LegacyLoanImportRequest.LegacyInstallment li : ordered) {
                BigDecimal p = money(li.getPrincipal());
                BigDecimal in = money(li.getInterest());
                BigDecimal pPaid = BigDecimal.ZERO;
                BigDecimal iPaid = BigDecimal.ZERO;
                InstallmentStatus st = InstallmentStatus.PENDING;
                if ("PAID".equalsIgnoreCase(li.getStatus())) {
                    pPaid = p;
                    iPaid = in;
                    st = InstallmentStatus.PAID;
                } else if ("PARTIAL".equalsIgnoreCase(li.getStatus()) && li.getPaidAmount() != null) {
                    BigDecimal paid = money(li.getPaidAmount()).min(p.add(in));
                    iPaid = paid.min(in);
                    pPaid = paid.subtract(iPaid);
                    st = paid.signum() > 0 ? InstallmentStatus.PARTIAL : InstallmentStatus.PENDING;
                }
                paidPrincipal = paidPrincipal.add(pPaid);
                loan.addInstallment(LoanInstallment.builder()
                        .installmentNumber(li.getNumber())
                        .dueDate(li.getDueDate())
                        .openingBalance(opening)
                        .principalAmount(p)
                        .interestAmount(in)
                        .totalAmount(money(li.getTotal()))
                        .closingBalance(money(li.getRemainingBalance()))
                        .principalPaid(pPaid)
                        .interestPaid(iPaid)
                        .status(st)
                        .paidDate(st == InstallmentStatus.PENDING ? null : li.getPaidDate())
                        .build());
                opening = money(li.getRemainingBalance());
            }
            loan.setOutstandingPrincipal(principal.subtract(paidPrincipal));
            if (loan.getInstallments().stream().allMatch(i -> i.getStatus() == InstallmentStatus.PAID)) {
                loan.setStatus(LoanStatus.PAID);
            }
            loanRepository.save(loan);
            result.setImported(result.getImported() + 1);
            result.getMessages().add(legacy.getLoanNumber() + ": importado (" + associate.getFullName() + ")");
        }
        return result;
    }

    // ------------------------------------------------------------------ helpers

    private Loan findLoan(UUID id) {
        return loanRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Préstamo no encontrado con ID: " + id));
    }

    private void markAsAssociate(ThirdParty party) {
        if (!party.isAssociate()) {
            party.setAssociate(true);
            if (party.getClassification() == null) {
                party.setClassification("Asociado");
            }
            thirdPartyRepository.save(party);
        }
    }

    private Account requireAccount(String code) {
        return accountRepository.findByCode(code.trim())
                .orElseThrow(() -> new ResourceNotFoundException("La cuenta PUC " + code + " no existe. Créela en el Plan de Cuentas antes de continuar."));
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String nextNumber(String prefix, Predicate<String> exists, long current) {
        long n = current + 1;
        String candidate = String.format("%s%05d", prefix, n);
        while (exists.test(candidate)) {
            n++;
            candidate = String.format("%s%05d", prefix, n);
        }
        return candidate;
    }

    private static JournalEntryRequest entry(Account account, ThirdParty party, String reference, String description,
                                             BigDecimal debit, BigDecimal credit) {
        return JournalEntryRequest.builder()
                .accountId(account.getId())
                .accountCode(account.getCode())
                .thirdPartyId(party.getId())
                .referenceNumber(reference)
                .description(description)
                .debit(debit)
                .credit(credit)
                .build();
    }

    LoanResponse toResponse(Loan loan, boolean includeInstallments) {
        LocalDate today = LocalDate.now();
        BigDecimal paidPrincipal = BigDecimal.ZERO;
        BigDecimal paidInterest = BigDecimal.ZERO;
        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal overdueAmount = BigDecimal.ZERO;
        int paid = 0;
        int overdue = 0;
        LoanInstallment next = null;
        List<LoanScheduleLineResponse> lines = new ArrayList<>();

        for (LoanInstallment i : loan.getInstallments()) {
            paidPrincipal = paidPrincipal.add(i.getPrincipalPaid());
            paidInterest = paidInterest.add(i.getInterestPaid());
            totalInterest = totalInterest.add(i.getInterestAmount());
            boolean isPaid = i.getStatus() == InstallmentStatus.PAID;
            boolean isOverdue = !isPaid && i.getDueDate().isBefore(today);
            if (isPaid) {
                paid++;
            } else if (next == null) {
                next = i;
            }
            if (isOverdue) {
                overdue++;
                overdueAmount = overdueAmount.add(i.getTotalPending());
            }
            if (includeInstallments) {
                lines.add(LoanScheduleLineResponse.builder()
                        .id(i.getId())
                        .number(i.getInstallmentNumber())
                        .dueDate(i.getDueDate())
                        .openingBalance(i.getOpeningBalance())
                        .principal(i.getPrincipalAmount())
                        .interest(i.getInterestAmount())
                        .total(i.getTotalAmount())
                        .closingBalance(i.getClosingBalance())
                        .principalPaid(i.getPrincipalPaid())
                        .interestPaid(i.getInterestPaid())
                        .pending(i.getTotalPending())
                        .status(i.getStatus().name())
                        .paidDate(i.getPaidDate())
                        .overdue(isOverdue)
                        .daysOverdue(isOverdue ? ChronoUnit.DAYS.between(i.getDueDate(), today) : 0)
                        .build());
            }
        }

        String portfolioStatus = switch (loan.getStatus()) {
            case CANCELLED -> "ANULADO";
            case PAID -> "PAGADO";
            case ACTIVE -> overdue > 0 ? "EN_MORA" : "AL_DIA";
        };
        ThirdParty party = loan.getThirdParty();
        int count = loan.getInstallments().size();

        return LoanResponse.builder()
                .id(loan.getId())
                .loanNumber(loan.getLoanNumber())
                .thirdPartyId(party.getId())
                .thirdPartyName(party.getFullName())
                .thirdPartyIdNumber(party.getIdentificationNumber())
                .principalAmount(loan.getPrincipalAmount())
                .monthlyRate(loan.getMonthlyRate())
                .termMonths(loan.getTermMonths())
                .amortizationType(loan.getAmortizationType().name())
                .disbursementDate(loan.getDisbursementDate())
                .firstPaymentDate(loan.getFirstPaymentDate())
                .status(loan.getStatus().name())
                .portfolioStatus(portfolioStatus)
                .outstandingPrincipal(loan.getOutstandingPrincipal())
                .paidPrincipal(paidPrincipal)
                .paidInterest(paidInterest)
                .totalInterest(totalInterest)
                .totalPayable(loan.getPrincipalAmount().add(totalInterest))
                .firstInstallmentAmount(count > 0 ? loan.getInstallments().getFirst().getTotalAmount() : BigDecimal.ZERO)
                .paidInstallments(paid)
                .pendingInstallments(count - paid)
                .overdueInstallments(overdue)
                .overdueAmount(overdueAmount)
                .nextDueDate(next != null ? next.getDueDate() : null)
                .nextDueAmount(next != null ? next.getTotalPending() : null)
                .nextInstallmentNumber(next != null ? next.getInstallmentNumber() : null)
                .loanAccountCode(loan.getLoanAccount() != null ? loan.getLoanAccount().getCode() : null)
                .loanAccountName(loan.getLoanAccount() != null ? loan.getLoanAccount().getName() : null)
                .interestAccountCode(loan.getInterestAccount() != null ? loan.getInterestAccount().getCode() : null)
                .interestAccountName(loan.getInterestAccount() != null ? loan.getInterestAccount().getName() : null)
                .disbursementAccountCode(loan.getDisbursementAccount() != null ? loan.getDisbursementAccount().getCode() : null)
                .disbursementVoucherNumber(loan.getDisbursementVoucher() != null ? loan.getDisbursementVoucher().getVoucherNumber() : null)
                .legacy(loan.isLegacy())
                .notes(loan.getNotes())
                .installments(includeInstallments ? lines : null)
                .build();
    }

    private LoanPaymentResponse toPaymentResponse(List<LoanPayment> payments) {
        LoanPayment first = payments.getFirst();
        Loan loan = first.getLoan();
        ThirdParty party = first.getThirdParty();
        BigDecimal principal = BigDecimal.ZERO;
        BigDecimal interest = BigDecimal.ZERO;
        List<LoanPaymentResponse.Line> lines = new ArrayList<>();
        List<LoanPayment> sorted = new ArrayList<>(payments);
        sorted.sort(Comparator.comparing(p -> p.getInstallment().getInstallmentNumber()));
        for (LoanPayment p : sorted) {
            principal = principal.add(p.getPrincipalApplied());
            interest = interest.add(p.getInterestApplied());
            lines.add(LoanPaymentResponse.Line.builder()
                    .installmentId(p.getInstallment().getId())
                    .installmentNumber(p.getInstallment().getInstallmentNumber())
                    .dueDate(p.getInstallment().getDueDate())
                    .amount(p.getAmount())
                    .principal(p.getPrincipalApplied())
                    .interest(p.getInterestApplied())
                    .installmentStatus(p.getInstallment().getStatus().name())
                    .build());
        }
        return LoanPaymentResponse.builder()
                .receiptNumber(first.getReceiptNumber())
                .paymentDate(first.getPaymentDate())
                .loanId(loan.getId())
                .loanNumber(loan.getLoanNumber())
                .thirdPartyId(party.getId())
                .thirdPartyName(party.getFullName())
                .thirdPartyIdNumber(party.getIdentificationNumber())
                .paymentMethod(first.getPaymentMethod().name())
                .cashAccountCode(first.getCashAccount() != null ? first.getCashAccount().getCode() : null)
                .cashAccountName(first.getCashAccount() != null ? first.getCashAccount().getName() : null)
                .voucherId(first.getVoucher() != null ? first.getVoucher().getId() : null)
                .voucherNumber(first.getVoucher() != null ? first.getVoucher().getVoucherNumber() : null)
                .reference(first.getReference())
                .notes(first.getNotes())
                .totalAmount(principal.add(interest))
                .principalApplied(principal)
                .interestApplied(interest)
                .loanOutstandingAfter(loan.getOutstandingPrincipal())
                .createdBy(first.getCreatedBy())
                .lines(lines)
                .build();
    }
}
