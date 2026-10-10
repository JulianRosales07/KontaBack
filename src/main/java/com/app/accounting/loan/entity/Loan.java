package com.app.accounting.loan.entity;

import com.app.accounting.common.entity.AuditableEntity;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.puc.entity.Account;
import com.app.accounting.voucher.entity.JournalVoucher;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "loans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Loan extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "loan_number", nullable = false, unique = true, length = 30)
    private String loanNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "third_party_id", nullable = false)
    private ThirdParty thirdParty;

    @Column(name = "principal_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal principalAmount;

    /** Tasa del periodo en porcentaje (2.000000 = 2% mensual). */
    @Column(name = "monthly_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal monthlyRate;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "amortization_type", nullable = false, length = 30)
    private AmortizationType amortizationType;

    @Column(name = "disbursement_date", nullable = false)
    private LocalDate disbursementDate;

    @Column(name = "first_payment_date", nullable = false)
    private LocalDate firstPaymentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoanStatus status;

    /** Saldo de capital pendiente = principal - capital pagado. Solo se modifica al aplicar abonos. */
    @Column(name = "outstanding_principal", nullable = false, precision = 18, scale = 2)
    private BigDecimal outstandingPrincipal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_account_id")
    private Account loanAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interest_account_id")
    private Account interestAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disbursement_account_id")
    private Account disbursementAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disbursement_voucher_id")
    private JournalVoucher disbursementVoucher;

    /** Préstamo histórico importado tal como estaba (su plan NO se recalcula). */
    @Column(nullable = false)
    @Builder.Default
    private boolean legacy = false;

    @Column(length = 500)
    private String notes;

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("installmentNumber ASC")
    @Builder.Default
    private List<LoanInstallment> installments = new ArrayList<>();

    public void addInstallment(LoanInstallment installment) {
        installments.add(installment);
        installment.setLoan(this);
    }
}
