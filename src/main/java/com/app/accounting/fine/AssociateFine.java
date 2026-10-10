package com.app.accounting.fine;

import com.app.accounting.common.entity.AuditableEntity;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.voucher.entity.JournalVoucher;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Multa impuesta a un asociado. PENDING no afecta la contabilidad; al pagarse (PAID) queda enlazada al comprobante
 * de ingreso que la registró en 429510. VOIDED = condonada/anulada con motivo.
 */
@Entity
@Table(name = "associate_fines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssociateFine extends AuditableEntity {

    public enum Status { PENDING, PAID, VOIDED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "third_party_id", nullable = false)
    private ThirdParty thirdParty;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fine_type_id", nullable = false)
    private FineType fineType;

    @Column(name = "fine_date", nullable = false)
    private LocalDate fineDate;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(length = 300)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "receipt_number", length = 50)
    private String receiptNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id")
    private JournalVoucher voucher;

    @Column(name = "void_reason", length = 300)
    private String voidReason;
}
