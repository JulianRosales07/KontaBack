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

    /**
     * Débitos y créditos asentados por cuenta en un rango de fechas: [código, débitos, créditos].
     * Con {@code excludeClosing} se omite el comprobante de cierre anual (CIERRE-año) para presentar resultados.
     */
    @Query("SELECT a.code, COALESCE(SUM(e.debit), 0), COALESCE(SUM(e.credit), 0) FROM JournalEntry e " +
            "JOIN e.voucher v JOIN e.account a " +
            "WHERE v.status = com.app.accounting.voucher.entity.VoucherStatus.POSTED " +
            "AND v.issueDate BETWEEN :from AND :to " +
            "AND (:excludeClosing = false OR v.voucherNumber NOT LIKE 'CIERRE-%') " +
            "GROUP BY a.code")
    List<Object[]> sumsByAccount(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                 @Param("excludeClosing") boolean excludeClosing);

    /** Débitos y créditos por cuenta y tercero para las cuentas que empiezan por el prefijo: [código, terceroId, débitos, créditos]. */
    @Query("SELECT a.code, t.id, COALESCE(SUM(e.debit), 0), COALESCE(SUM(e.credit), 0) FROM JournalEntry e " +
            "JOIN e.voucher v JOIN e.account a LEFT JOIN e.thirdParty t " +
            "WHERE a.code LIKE CONCAT(:prefix, '%') " +
            "AND v.status = com.app.accounting.voucher.entity.VoucherStatus.POSTED " +
            "AND v.issueDate BETWEEN :from AND :to " +
            "GROUP BY a.code, t.id")
    List<Object[]> sumsByAccountAndParty(@Param("prefix") String prefix, @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);

    /** Asientos de comprobantes de ingreso del periodo (para la planilla de recaudos). */
    @Query("SELECT e FROM JournalEntry e " +
            "JOIN FETCH e.voucher v " +
            "JOIN FETCH e.account a " +
            "LEFT JOIN FETCH e.thirdParty t " +
            "WHERE v.voucherType = com.app.accounting.voucher.entity.VoucherType.INGRESO " +
            "AND v.status = com.app.accounting.voucher.entity.VoucherStatus.POSTED " +
            "AND v.issueDate BETWEEN :from AND :to " +
            "ORDER BY v.issueDate ASC, v.voucherNumber ASC")
    List<JournalEntry> findPostedIncomeVoucherEntries(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Todos los asientos de un tercero hasta una fecha (estado de cuenta del asociado). */
    @Query("SELECT e FROM JournalEntry e " +
            "JOIN FETCH e.voucher v " +
            "JOIN FETCH e.account a " +
            "JOIN FETCH e.thirdParty t " +
            "WHERE t.id = :partyId " +
            "AND v.status = com.app.accounting.voucher.entity.VoucherStatus.POSTED " +
            "AND v.issueDate BETWEEN :from AND :to " +
            "ORDER BY v.issueDate ASC, v.voucherNumber ASC")
    List<JournalEntry> findPostedByParty(@Param("partyId") UUID partyId, @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);
}
