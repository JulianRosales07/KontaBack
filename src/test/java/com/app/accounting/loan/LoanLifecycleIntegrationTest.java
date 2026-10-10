package com.app.accounting.loan;

import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.loan.dto.request.CreateLoanRequest;
import com.app.accounting.loan.dto.request.LegacyLoanImportRequest;
import com.app.accounting.loan.dto.request.LoanPaymentRequest;
import com.app.accounting.loan.dto.response.LegacyImportResultResponse;
import com.app.accounting.loan.dto.response.LoanPaymentResponse;
import com.app.accounting.loan.dto.response.LoanResponse;
import com.app.accounting.loan.dto.response.LoanScheduleLineResponse;
import com.app.accounting.loan.entity.LoanStatus;
import com.app.accounting.loan.entity.PaymentMethod;
import com.app.accounting.loan.service.LoanService;
import com.app.accounting.party.entity.PersonType;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.party.repository.ThirdPartyRepository;
import com.app.accounting.voucher.dto.response.JournalEntryResponse;
import com.app.accounting.voucher.dto.response.JournalVoucherResponse;
import com.app.accounting.voucher.service.VoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LoanLifecycleIntegrationTest {

    @Autowired
    private LoanService loanService;

    @Autowired
    private ThirdPartyRepository thirdPartyRepository;

    @Autowired
    private VoucherService voucherService;

    private ThirdParty juan;

    private static BigDecimal $(long v) {
        return BigDecimal.valueOf(v);
    }

    @BeforeEach
    void setUp() {
        juan = thirdPartyRepository.save(ThirdParty.builder()
                .identificationNumber("80123456")
                .fullName("JUAN PÉREZ")
                .type(PersonType.NATURAL)
                .build());
    }

    private LoanResponse createStandardLoan() {
        return loanService.createLoan(CreateLoanRequest.builder()
                .thirdPartyId(juan.getId())
                .principalAmount($(1_000_000))
                .monthlyRate(new BigDecimal("2"))
                .termMonths(10)
                .disbursementDate(LocalDate.now().minusDays(5))
                .firstPaymentDate(LocalDate.now().plusDays(25))
                .build());
    }

    private LoanPaymentResponse pay(LoanResponse loan, int installmentIndex, long amount) {
        LoanScheduleLineResponse inst = loan.getInstallments().get(installmentIndex);
        return loanService.registerPayment(loan.getId(), LoanPaymentRequest.builder()
                .paymentDate(LocalDate.now())
                .paymentMethod(PaymentMethod.CASH)
                .reference("REC-FISICO-001")
                .allocations(List.of(LoanPaymentRequest.Allocation.builder()
                        .installmentId(inst.getId()).amount($(amount)).build()))
                .build());
    }

    @Test
    @DisplayName("Crear préstamo persiste el plan bajo saldo de capital y contabiliza el desembolso")
    void createLoanPersistsScheduleAndDisbursementVoucher() {
        LoanResponse loan = createStandardLoan();

        assertThat(loan.getLoanNumber()).startsWith("PR-");
        assertThat(loan.getOutstandingPrincipal()).isEqualByComparingTo($(1_000_000));
        assertThat(loan.getInstallments()).hasSize(10);
        assertThat(loan.getInstallments().get(0).getTotal()).isEqualByComparingTo($(120_000));
        assertThat(loan.getInstallments().get(1).getInterest()).isEqualByComparingTo($(18_000));
        assertThat(loan.getTotalInterest()).isEqualByComparingTo($(110_000));
        assertThat(loan.getPortfolioStatus()).isEqualTo("AL_DIA");
        assertThat(thirdPartyRepository.findById(juan.getId()).orElseThrow().isAssociate()).isTrue();

        JournalVoucherResponse voucher = voucherService.getVoucherByNumber(loan.getDisbursementVoucherNumber());
        assertThat(line(voucher, "136505").getDebit()).isEqualByComparingTo($(1_000_000));
        assertThat(line(voucher, "110505").getCredit()).isEqualByComparingTo($(1_000_000));
    }

    @Test
    @DisplayName("Prueba de recaudo: pagar cuota 1 ($120.000) → capital 100.000, interés 20.000, saldo 900.000, cuota PAGADA")
    void payFirstInstallment() {
        LoanResponse loan = createStandardLoan();

        LoanPaymentResponse receipt = pay(loan, 0, 120_000);

        assertThat(receipt.getTotalAmount()).isEqualByComparingTo($(120_000));
        assertThat(receipt.getPrincipalApplied()).isEqualByComparingTo($(100_000));
        assertThat(receipt.getInterestApplied()).isEqualByComparingTo($(20_000));
        assertThat(receipt.getLoanOutstandingAfter()).isEqualByComparingTo($(900_000));
        assertThat(receipt.getLines()).singleElement().satisfies(l -> {
            assertThat(l.getInstallmentNumber()).isEqualTo(1);
            assertThat(l.getInstallmentStatus()).isEqualTo("PAID");
        });

        LoanResponse after = loanService.getLoan(loan.getId());
        assertThat(after.getPaidPrincipal()).isEqualByComparingTo($(100_000));
        assertThat(after.getPaidInterest()).isEqualByComparingTo($(20_000));
        assertThat(after.getOutstandingPrincipal()).isEqualByComparingTo($(900_000));
        assertThat(after.getInstallments().get(0).getStatus()).isEqualTo("PAID");
        assertThat(after.getPaidInstallments()).isEqualTo(1);
        assertThat(after.getNextInstallmentNumber()).isEqualTo(2);

        // Asiento cuadrado: solo el capital reduce la cartera; el interés va al ingreso
        JournalVoucherResponse voucher = voucherService.getVoucherByNumber(receipt.getVoucherNumber());
        assertThat(line(voucher, "110505").getDebit()).isEqualByComparingTo($(120_000));
        assertThat(line(voucher, "136505").getCredit()).isEqualByComparingTo($(100_000));
        assertThat(line(voucher, "421005").getCredit()).isEqualByComparingTo($(20_000));
        BigDecimal debits = voucher.getEntries().stream().map(JournalEntryResponse::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = voucher.getEntries().stream().map(JournalEntryResponse::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(debits).isEqualByComparingTo(credits);
    }

    @Test
    @DisplayName("Prueba de cuentas por cobrar: el saldo pasa de $1.000.000 a $900.000 tras pagar la primera cuota")
    void receivableReflectsNewBalance() {
        LoanResponse loan = createStandardLoan();
        assertThat(receivableFor(loan).getOutstandingPrincipal()).isEqualByComparingTo($(1_000_000));

        pay(loan, 0, 120_000);

        LoanResponse receivable = receivableFor(loan);
        assertThat(receivable.getOutstandingPrincipal()).isEqualByComparingTo($(900_000));
        assertThat(receivable.getPaidInstallments()).isEqualTo(1);
        assertThat(receivable.getPendingInstallments()).isEqualTo(9);
    }

    @Test
    @DisplayName("Abono parcial: cubre interés primero y deja la cuota PARCIAL")
    void partialPayment() {
        LoanResponse loan = createStandardLoan();
        LoanPaymentResponse receipt = pay(loan, 0, 50_000);
        assertThat(receipt.getInterestApplied()).isEqualByComparingTo($(20_000));
        assertThat(receipt.getPrincipalApplied()).isEqualByComparingTo($(30_000));
        assertThat(receipt.getLoanOutstandingAfter()).isEqualByComparingTo($(970_000));
        assertThat(receipt.getLines().getFirst().getInstallmentStatus()).isEqualTo("PARTIAL");

        // Completar la cuota 1 con lo pendiente (70.000) la deja PAGADA
        LoanResponse refreshed = loanService.getLoan(loan.getId());
        assertThat(refreshed.getInstallments().get(0).getPending()).isEqualByComparingTo($(70_000));
        LoanPaymentResponse second = pay(refreshed, 0, 70_000);
        assertThat(second.getLines().getFirst().getInstallmentStatus()).isEqualTo("PAID");
        assertThat(second.getLoanOutstandingAfter()).isEqualByComparingTo($(900_000));
    }

    @Test
    @DisplayName("Reglas: no se paga una cuota posterior con una anterior pendiente ni se excede lo pendiente")
    void paymentRules() {
        LoanResponse loan = createStandardLoan();
        assertThatThrownBy(() -> pay(loan, 1, 118_000))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cuotas anteriores");
        assertThatThrownBy(() -> pay(loan, 0, 120_001))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("excede");
    }

    @Test
    @DisplayName("Pagar todas las cuotas deja el préstamo PAGADO con saldo cero")
    void fullRepayment() {
        LoanResponse loan = createStandardLoan();
        LoanPaymentRequest req = LoanPaymentRequest.builder()
                .paymentDate(LocalDate.now())
                .paymentMethod(PaymentMethod.TRANSFER)
                .allocations(loan.getInstallments().stream()
                        .map(i -> LoanPaymentRequest.Allocation.builder().installmentId(i.getId()).amount(i.getTotal()).build())
                        .toList())
                .build();
        LoanPaymentResponse receipt = loanService.registerPayment(loan.getId(), req);
        assertThat(receipt.getTotalAmount()).isEqualByComparingTo($(1_110_000));
        assertThat(receipt.getCashAccountCode()).isEqualTo("111005");

        LoanResponse after = loanService.getLoan(loan.getId());
        assertThat(after.getStatus()).isEqualTo(LoanStatus.PAID.name());
        assertThat(after.getOutstandingPrincipal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(after.getPortfolioStatus()).isEqualTo("PAGADO");
    }

    @Test
    @DisplayName("Importación histórica conserva el plan original sin recalcular y es idempotente")
    void legacyImport() {
        LegacyLoanImportRequest request = LegacyLoanImportRequest.builder()
                .loans(List.of(LegacyLoanImportRequest.LegacyLoan.builder()
                        .loanNumber("CR-ASOC-00999")
                        .associateIdentification("52189423")
                        .associateName("MARÍA RODRÍGUEZ")
                        .principalAmount($(300_000))
                        .monthlyRate(new BigDecimal("1.5"))
                        .termMonths(2)
                        .amortizationType("FRENCH")
                        .disbursementDate(LocalDate.of(2026, 8, 1))
                        .firstPaymentDate(LocalDate.of(2026, 9, 1))
                        .installments(List.of(
                                LegacyLoanImportRequest.LegacyInstallment.builder().number(1).dueDate(LocalDate.of(2026, 9, 1))
                                        .principal($(148_877)).interest($(4_500)).total($(153_377)).remainingBalance($(151_123))
                                        .status("PAID").paidDate(LocalDate.of(2026, 9, 1)).build(),
                                LegacyLoanImportRequest.LegacyInstallment.builder().number(2).dueDate(LocalDate.of(2026, 10, 1))
                                        .principal($(151_123)).interest($(2_267)).total($(153_390)).remainingBalance($(0))
                                        .status("PENDING").build()))
                        .build()))
                .build();

        LegacyImportResultResponse first = loanService.importLegacy(request);
        LegacyImportResultResponse second = loanService.importLegacy(request);

        assertThat(first.getImported()).isEqualTo(1);
        assertThat(second.getImported()).isZero();
        assertThat(second.getSkipped()).isEqualTo(1);

        LoanResponse imported = loanService.getLoans(null, null, true).stream()
                .filter(l -> l.getLoanNumber().equals("CR-ASOC-00999")).findFirst().orElseThrow();
        assertThat(imported.isLegacy()).isTrue();
        assertThat(imported.getAmortizationType()).isEqualTo("FRENCH");
        assertThat(imported.getInstallments().get(0).getInterest()).isEqualByComparingTo($(4_500));
        assertThat(imported.getOutstandingPrincipal()).isEqualByComparingTo($(151_123));
    }

    private LoanResponse receivableFor(LoanResponse loan) {
        return loanService.getLoans(LoanStatus.ACTIVE, null, false).stream()
                .filter(l -> l.getId().equals(loan.getId())).findFirst().orElseThrow();
    }

    private static JournalEntryResponse line(JournalVoucherResponse voucher, String accountCode) {
        return voucher.getEntries().stream()
                .filter(e -> accountCode.equals(e.getAccountCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No hay línea para la cuenta " + accountCode));
    }
}
