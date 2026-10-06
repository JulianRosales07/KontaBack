package com.app.accounting.associate.entity;

import com.app.accounting.puc.entity.Account;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "quota_template_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuotaTemplateItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private QuotaTemplate template;

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

    @Column(precision = 8, scale = 4)
    private BigDecimal percentage;

    @Column(name = "is_mandatory", nullable = false)
    @Builder.Default
    private Boolean isMandatory = true;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;
}
