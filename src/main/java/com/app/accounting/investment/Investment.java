package com.app.accounting.investment;

import com.app.accounting.common.entity.AuditableEntity;
import com.app.accounting.puc.entity.Account;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Registro mínimo de una inversión (CDT u otro título). El saldo contable vive en la cuenta PUC asociada. */
@Entity
@Table(name = "investments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Investment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_name", nullable = false, length = 150)
    private String entityName;

    @Column(name = "instrument_type", nullable = false, length = 30)
    @Builder.Default
    private String instrumentType = "CDT";

    @Column(name = "instrument_number", length = 60)
    private String instrumentNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal principal;

    @Column(name = "accrued_interest", nullable = false, precision = 18, scale = 4)
    @Builder.Default
    private BigDecimal accruedInterest = BigDecimal.ZERO;

    @Column(name = "annual_rate", precision = 9, scale = 6)
    private BigDecimal annualRate;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    /** ACTIVE | REDEEMED */
    @Column(nullable = false, length = 15)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(length = 500)
    private String notes;
}
