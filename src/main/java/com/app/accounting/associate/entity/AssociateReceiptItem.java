package com.app.accounting.associate.entity;

import com.app.accounting.puc.entity.Account;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "associate_receipt_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssociateReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    private AssociateReceipt receipt;

    @Column(name = "concept_name", nullable = false, length = 150)
    private String conceptName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuotaCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;
}
