package com.app.accounting.treasury.entity;

import com.app.accounting.billing.entity.DebtDocument;
import com.app.accounting.common.entity.AuditableEntity;
import com.app.accounting.puc.entity.Account;
import com.app.accounting.voucher.entity.JournalVoucher;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "payment_receipts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentReceipt extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 20)
    private PaymentType paymentType; // CASH_IN (Recibo Caja) o CASH_OUT (Egreso)

    @Column(name = "receipt_number", nullable = false, unique = true, length = 50)
    private String receiptNumber; // Ej: RC-8402 o CE-1050

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "debt_document_id")
    private DebtDocument appliedToDocument; // Factura asociada

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_account_id", nullable = false)
    private Account cashOrBankAccount; // Cuenta PUC 110505 o 111005

    @Column(name = "amount_paid", precision = 18, scale = 4, nullable = false)
    private BigDecimal amountPaid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id")
    private JournalVoucher voucher; // Comprobante contable asociado
}
