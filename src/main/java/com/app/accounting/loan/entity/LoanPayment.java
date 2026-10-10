package com.app.accounting.loan.entity;

import com.app.accounting.common.entity.AuditableEntity;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.puc.entity.Account;
import com.app.accounting.voucher.entity.JournalVoucher;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Abono aplicado a una cuota concreta de un préstamo de un asociado. */
@Entity
@Table(name = "loan_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanPayment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "receipt_number", nullable = false, length = 50)
    private String receiptNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "installment_id", nullable = false)
    private LoanInstallment installment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "third_party_id", nullable = false)
    private ThirdParty thirdParty;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "principal_applied", nullable = false, precision = 18, scale = 2)
    private BigDecimal principalApplied;

    @Column(name = "interest_applied", nullable = false, precision = 18, scale = 2)
    private BigDecimal interestApplied;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_account_id")
    private Account cashAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id")
    private JournalVoucher voucher;

    /** Número de comprobante externo (consignación, transferencia, recibo físico). */
    @Column(length = 100)
    private String reference;

    @Column(length = 500)
    private String notes;
}
