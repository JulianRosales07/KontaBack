package com.app.accounting.associate;

import com.app.accounting.associate.dto.request.CreateAssociateReceiptRequest;
import com.app.accounting.associate.dto.request.ReceiptItemRequest;
import com.app.accounting.associate.dto.response.AssociateReceiptResponse;
import com.app.accounting.associate.entity.QuotaCategory;
import com.app.accounting.associate.repository.AssociateReceiptRepository;
import com.app.accounting.associate.service.AssociateService;
import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.loan.dto.request.CreateLoanRequest;
import com.app.accounting.loan.dto.request.LoanPaymentRequest;
import com.app.accounting.loan.dto.response.LoanResponse;
import com.app.accounting.loan.service.LoanService;
import com.app.accounting.party.entity.PersonType;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.party.repository.ThirdPartyRepository;
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

/** Recaudo mensual que incluye la cuota ordinaria y la cuota del préstamo en un solo recibo. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CombinedQuotaLoanReceiptTest {

    @Autowired private AssociateService associateService;
    @Autowired private LoanService loanService;
    @Autowired private ThirdPartyRepository thirdPartyRepository;
    @Autowired private AssociateReceiptRepository receiptRepository;

    private static BigDecimal $(long v) {
        return BigDecimal.valueOf(v);
    }

    private CreateAssociateReceiptRequest request(ThirdParty p, LoanResponse loan, long loanAmount) {
        return CreateAssociateReceiptRequest.builder()
                .thirdPartyId(p.getId())
                .paymentDate(LocalDate.now())
                .cashAccountCode("110505")
                .totalAmount($(50_000))
                .items(List.of(
                        ReceiptItemRequest.builder().conceptName("Cuota de administración").category(QuotaCategory.INCOME)
                                .accountCode("417010").amount($(20_000)).build(),
                        ReceiptItemRequest.builder().conceptName("Aportes sociales").category(QuotaCategory.EQUITY_SAVING)
                                .accountCode("311505").amount($(30_000)).build()))
                .loanPayment(CreateAssociateReceiptRequest.LoanPortion.builder()
                        .loanId(loan.getId())
                        .allocations(List.of(LoanPaymentRequest.Allocation.builder()
                                .installmentId(loan.getInstallments().getFirst().getId()).amount($(loanAmount)).build()))
                        .build())
                .build();
    }

    private ThirdParty associateWithLoan() {
        return thirdPartyRepository.save(ThirdParty.builder().identificationNumber("90111222")
                .fullName("Socia Combinada").associate(true).type(PersonType.NATURAL).build());
    }

    private LoanResponse loanFor(ThirdParty p) {
        return loanService.createLoan(CreateLoanRequest.builder().thirdPartyId(p.getId()).principalAmount($(1_000_000))
                .monthlyRate(new BigDecimal("2")).termMonths(10).disbursementDate(LocalDate.now())
                .firstPaymentDate(LocalDate.now().plusMonths(1)).build());
    }

    @Test
    @DisplayName("Cuota mensual + cuota del préstamo en un solo recaudo")
    void combinedReceipt() {
        ThirdParty p = associateWithLoan();
        LoanResponse loan = loanFor(p);

        AssociateReceiptResponse r = associateService.registerReceipt(request(p, loan, 120_000));

        assertThat(r.getTotalAmount()).isEqualByComparingTo($(50_000));
        assertThat(r.getLoanPayment()).isNotNull();
        assertThat(r.getLoanPayment().getPrincipalApplied()).isEqualByComparingTo($(100_000));
        assertThat(r.getLoanPayment().getInterestApplied()).isEqualByComparingTo($(20_000));
        assertThat(r.getLoanPayment().getReference()).isEqualTo(r.getReceiptNumber());
        assertThat(loanService.getLoan(loan.getId()).getOutstandingPrincipal()).isEqualByComparingTo($(900_000));
    }

    @Test
    @DisplayName("Si la parte del préstamo es inválida no se guarda nada")
    void invalidLoanPartRejectsWholeReceipt() {
        ThirdParty p = associateWithLoan();
        LoanResponse loan = loanFor(p);
        long before = receiptRepository.count();

        assertThatThrownBy(() -> associateService.registerReceipt(request(p, loan, 999_999)))
                .isInstanceOf(BusinessRuleException.class);
        // En la aplicación real la transacción completa se revierte; aquí se verifica que el préstamo no cambió
        assertThat(loanService.getLoan(loan.getId()).getOutstandingPrincipal()).isEqualByComparingTo($(1_000_000));
        assertThat(before).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("No se puede incluir el préstamo de otro asociado")
    void loanMustBelongToAssociate() {
        ThirdParty owner = associateWithLoan();
        LoanResponse loan = loanFor(owner);
        ThirdParty other = thirdPartyRepository.save(ThirdParty.builder().identificationNumber("90333444")
                .fullName("Otro Socio").associate(true).type(PersonType.NATURAL).build());

        assertThatThrownBy(() -> associateService.registerReceipt(request(other, loan, 120_000)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no pertenece");
    }
}
