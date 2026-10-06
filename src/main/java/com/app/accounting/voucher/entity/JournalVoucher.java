package com.app.accounting.voucher.entity;

import com.app.accounting.common.entity.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "journal_vouchers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalVoucher extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "voucher_number", nullable = false, unique = true, length = 50)
    private String voucherNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "voucher_type", nullable = false, length = 30)
    private VoucherType voucherType;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VoucherStatus status = VoucherStatus.DRAFT;

    @OneToMany(mappedBy = "voucher", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<JournalEntry> entries = new ArrayList<>();

    public void addEntry(JournalEntry entry) {
        entries.add(entry);
        entry.setVoucher(this);
    }

    public void removeEntry(JournalEntry entry) {
        entries.remove(entry);
        entry.setVoucher(null);
    }
}
