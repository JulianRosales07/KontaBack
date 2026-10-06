package com.app.accounting.billing.entity;

import com.app.accounting.common.entity.AuditableEntity;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.voucher.entity.JournalVoucher;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "debt_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtDocument extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DebtType type; // RECEIVABLE (Cobrar) o PAYABLE (Pagar)

    @Column(name = "document_number", nullable = false, unique = true, length = 50)
    private String documentNumber; // Ej: FAC-1001 o COMP-540

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "third_party_id", nullable = false)
    private ThirdParty thirdParty;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(precision = 18, scale = 4, nullable = false)
    private BigDecimal subtotal;

    @Column(name = "tax_total", precision = 18, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "retention_total", precision = 18, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal retentionTotal = BigDecimal.ZERO;

    @Column(name = "total_amount", precision = 18, scale = 4, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "remaining_balance", precision = 18, scale = 4, nullable = false)
    private BigDecimal remainingBalance; // Saldo pendiente

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.PENDING; // PENDING, PARTIALLY_PAID, PAID, CANCELLED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id")
    private JournalVoucher voucher; // Comprobante contable de causación asociado
}
