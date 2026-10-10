package com.app.accounting.report.repository;

import com.app.accounting.voucher.entity.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Consultas de lectura sobre asientos asentados (POSTED) usadas por los reportes financieros. */
@Repository
public interface ReportEntryRepository extends JpaRepository<JournalEntry, UUID> {

    @Query("SELECT e FROM JournalEntry e " +
            "JOIN FETCH e.voucher v " +
            "JOIN FETCH e.account a " +
            "LEFT JOIN FETCH e.thirdParty t " +
            "WHERE a.code LIKE CONCAT(:prefix, '%') " +
            "AND v.status = com.app.accounting.voucher.entity.VoucherStatus.POSTED " +
            "AND v.issueDate BETWEEN :from AND :to " +
            "ORDER BY v.issueDate ASC, v.voucherNumber ASC")
    List<JournalEntry> findPostedByAccountPrefix(@Param("prefix") String prefix,
                                                 @Param("from") LocalDate from,
                                                 @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(e.debit), 0) - COALESCE(SUM(e.credit), 0) FROM JournalEntry e " +
            "JOIN e.voucher v JOIN e.account a " +
            "WHERE a.code LIKE CONCAT(:prefix, '%') " +
            "AND v.status = com.app.accounting.voucher.entity.VoucherStatus.POSTED " +
            "AND v.issueDate < :before")
    BigDecimal sumDebitMinusCreditBefore(@Param("prefix") String prefix, @Param("before") LocalDate before);
}
