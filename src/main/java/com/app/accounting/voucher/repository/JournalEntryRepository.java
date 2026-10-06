package com.app.accounting.voucher.repository;

import com.app.accounting.voucher.entity.JournalEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    List<JournalEntry> findByVoucherId(UUID voucherId);

    @Query("SELECT " +
            "e.account.id as accountId, " +
            "e.account.code as accountCode, " +
            "e.account.name as accountName, " +
            "STR(e.account.nature) as accountNature, " +
            "COALESCE(SUM(e.debit), 0) as totalDebit, " +
            "COALESCE(SUM(e.credit), 0) as totalCredit " +
            "FROM JournalEntry e " +
            "WHERE e.voucher.status = com.app.accounting.voucher.entity.VoucherStatus.POSTED " +
            "AND e.voucher.issueDate < :startDate " +
            "GROUP BY e.account.id, e.account.code, e.account.name, e.account.nature")
    List<AccountBalanceProjection> findAccountBalancesBeforeDate(@Param("startDate") LocalDate startDate);

    @Query("SELECT " +
            "e.account.id as accountId, " +
            "e.account.code as accountCode, " +
            "e.account.name as accountName, " +
            "STR(e.account.nature) as accountNature, " +
            "COALESCE(SUM(e.debit), 0) as totalDebit, " +
            "COALESCE(SUM(e.credit), 0) as totalCredit " +
            "FROM JournalEntry e " +
            "WHERE e.voucher.status = com.app.accounting.voucher.entity.VoucherStatus.POSTED " +
            "AND e.voucher.issueDate BETWEEN :startDate AND :endDate " +
            "GROUP BY e.account.id, e.account.code, e.account.name, e.account.nature")
    List<AccountBalanceProjection> findAccountBalancesBetweenDates(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT " +
            "e.account.id as accountId, " +
            "e.account.code as accountCode, " +
            "e.account.name as accountName, " +
            "STR(e.account.nature) as accountNature, " +
            "COALESCE(SUM(e.debit), 0) as totalDebit, " +
            "COALESCE(SUM(e.credit), 0) as totalCredit " +
            "FROM JournalEntry e " +
            "WHERE e.voucher.status = com.app.accounting.voucher.entity.VoucherStatus.POSTED " +
            "AND e.voucher.issueDate <= :asOfDate " +
            "GROUP BY e.account.id, e.account.code, e.account.name, e.account.nature")
    List<AccountBalanceProjection> findAccountBalancesUpToDate(@Param("asOfDate") LocalDate asOfDate);

    @Query("SELECT " +
            "e.account.id as accountId, " +
            "e.account.code as accountCode, " +
            "e.account.name as accountName, " +
            "STR(e.account.nature) as accountNature, " +
            "t.id as partyId, " +
            "COALESCE(t.identificationNumber, 'SIN_NIT') as partyIdentification, " +
            "COALESCE(t.fullName, 'VARIOS / SIN TERCERO') as partyName, " +
            "COALESCE(SUM(e.debit), 0) as totalDebit, " +
            "COALESCE(SUM(e.credit), 0) as totalCredit " +
            "FROM JournalEntry e " +
            "JOIN e.voucher v " +
            "JOIN e.account a " +
            "LEFT JOIN e.thirdParty t " +
            "WHERE v.status = com.app.accounting.voucher.entity.VoucherStatus.POSTED " +
            "AND v.issueDate < :startDate " +
            "GROUP BY e.account.id, e.account.code, e.account.name, e.account.nature, t.id, t.identificationNumber, t.fullName")
    List<PartyAccountBalanceProjection> findPartyAccountBalancesBeforeDate(@Param("startDate") LocalDate startDate);

    @Query("SELECT " +
            "e.account.id as accountId, " +
            "e.account.code as accountCode, " +
            "e.account.name as accountName, " +
            "STR(e.account.nature) as accountNature, " +
            "t.id as partyId, " +
            "COALESCE(t.identificationNumber, 'SIN_NIT') as partyIdentification, " +
            "COALESCE(t.fullName, 'VARIOS / SIN TERCERO') as partyName, " +
            "COALESCE(SUM(e.debit), 0) as totalDebit, " +
            "COALESCE(SUM(e.credit), 0) as totalCredit " +
            "FROM JournalEntry e " +
            "JOIN e.voucher v " +
            "JOIN e.account a " +
            "JOIN e.thirdParty t " +
            "WHERE v.status = com.app.accounting.voucher.entity.VoucherStatus.POSTED " +
            "AND v.issueDate < :startDate " +
            "AND t.id = :partyId " +
            "GROUP BY e.account.id, e.account.code, e.account.name, e.account.nature, t.id, t.identificationNumber, t.fullName")
    List<PartyAccountBalanceProjection> findPartyAccountBalancesBeforeDateAndParty(
            @Param("startDate") LocalDate startDate,
            @Param("partyId") UUID partyId
    );

    @Query("SELECT " +
            "e.account.id as accountId, " +
            "e.account.code as accountCode, " +
            "e.account.name as accountName, " +
            "STR(e.account.nature) as accountNature, " +
            "t.id as partyId, " +
            "COALESCE(t.identificationNumber, 'SIN_NIT') as partyIdentification, " +
            "COALESCE(t.fullName, 'VARIOS / SIN TERCERO') as partyName, " +
            "COALESCE(SUM(e.debit), 0) as totalDebit, " +
            "COALESCE(SUM(e.credit), 0) as totalCredit " +
            "FROM JournalEntry e " +
            "JOIN e.voucher v " +
            "JOIN e.account a " +
            "LEFT JOIN e.thirdParty t " +
            "WHERE v.status = com.app.accounting.voucher.entity.VoucherStatus.POSTED " +
            "AND v.issueDate BETWEEN :startDate AND :endDate " +
            "GROUP BY e.account.id, e.account.code, e.account.name, e.account.nature, t.id, t.identificationNumber, t.fullName")
    List<PartyAccountBalanceProjection> findPartyAccountBalancesBetweenDates(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT " +
            "e.account.id as accountId, " +
            "e.account.code as accountCode, " +
            "e.account.name as accountName, " +
            "STR(e.account.nature) as accountNature, " +
            "t.id as partyId, " +
            "COALESCE(t.identificationNumber, 'SIN_NIT') as partyIdentification, " +
            "COALESCE(t.fullName, 'VARIOS / SIN TERCERO') as partyName, " +
            "COALESCE(SUM(e.debit), 0) as totalDebit, " +
            "COALESCE(SUM(e.credit), 0) as totalCredit " +
            "FROM JournalEntry e " +
            "JOIN e.voucher v " +
            "JOIN e.account a " +
            "JOIN e.thirdParty t " +
            "WHERE v.status = com.app.accounting.voucher.entity.VoucherStatus.POSTED " +
            "AND v.issueDate BETWEEN :startDate AND :endDate " +
            "AND t.id = :partyId " +
            "GROUP BY e.account.id, e.account.code, e.account.name, e.account.nature, t.id, t.identificationNumber, t.fullName")
    List<PartyAccountBalanceProjection> findPartyAccountBalancesBetweenDatesAndParty(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("partyId") UUID partyId
    );

    @Query("SELECT " +
            "e.account.id as accountId, " +
            "e.account.code as accountCode, " +
            "e.account.name as accountName, " +
            "STR(e.account.nature) as accountNature, " +
            "t.id as partyId, " +
            "COALESCE(t.identificationNumber, 'SIN_NIT') as partyIdentification, " +
            "COALESCE(t.fullName, 'VARIOS / SIN TERCERO') as partyName, " +
            "COALESCE(SUM(e.debit), 0) as totalDebit, " +
            "COALESCE(SUM(e.credit), 0) as totalCredit " +
            "FROM JournalEntry e " +
            "JOIN e.voucher v " +
            "JOIN e.account a " +
            "LEFT JOIN e.thirdParty t " +
            "WHERE v.status = com.app.accounting.voucher.entity.VoucherStatus.POSTED " +
            "AND v.issueDate <= :asOfDate " +
            "GROUP BY e.account.id, e.account.code, e.account.name, e.account.nature, t.id, t.identificationNumber, t.fullName")
    List<PartyAccountBalanceProjection> findPartyAccountBalancesUpToDate(@Param("asOfDate") LocalDate asOfDate);

    @Query("SELECT " +
            "e.account.id as accountId, " +
            "e.account.code as accountCode, " +
            "e.account.name as accountName, " +
            "STR(e.account.nature) as accountNature, " +
            "t.id as partyId, " +
            "COALESCE(t.identificationNumber, 'SIN_NIT') as partyIdentification, " +
            "COALESCE(t.fullName, 'VARIOS / SIN TERCERO') as partyName, " +
            "COALESCE(SUM(e.debit), 0) as totalDebit, " +
            "COALESCE(SUM(e.credit), 0) as totalCredit " +
            "FROM JournalEntry e " +
            "JOIN e.voucher v " +
            "JOIN e.account a " +
            "JOIN e.thirdParty t " +
            "WHERE v.status = com.app.accounting.voucher.entity.VoucherStatus.POSTED " +
            "AND v.issueDate <= :asOfDate " +
            "AND t.id = :partyId " +
            "GROUP BY e.account.id, e.account.code, e.account.name, e.account.nature, t.id, t.identificationNumber, t.fullName")
    List<PartyAccountBalanceProjection> findPartyAccountBalancesUpToDateAndParty(
            @Param("asOfDate") LocalDate asOfDate,
            @Param("partyId") UUID partyId
    );


    @Query("SELECT e FROM JournalEntry e " +
            "JOIN FETCH e.voucher v " +
            "JOIN FETCH e.account a " +
            "LEFT JOIN FETCH e.thirdParty t " +
            "WHERE a.code = :accountCode " +
            "AND v.status = com.app.accounting.voucher.entity.VoucherStatus.POSTED " +
            "AND v.issueDate BETWEEN :startDate AND :endDate " +
            "ORDER BY v.issueDate ASC, v.voucherNumber ASC")
    Page<JournalEntry> findLedgerEntries(
            @Param("accountCode") String accountCode,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable
    );

    @Query("SELECT e FROM JournalEntry e " +
            "JOIN FETCH e.voucher v " +
            "JOIN FETCH e.account a " +
            "JOIN FETCH e.thirdParty t " +
            "WHERE a.id = :accountId " +
            "AND t.id = :partyId " +
            "AND v.status = com.app.accounting.voucher.entity.VoucherStatus.POSTED " +
            "AND v.issueDate BETWEEN :startDate AND :endDate " +
            "ORDER BY v.issueDate ASC, v.voucherNumber ASC")
    List<JournalEntry> findPartyAuxiliaryEntries(
            @Param("accountId") UUID accountId,
            @Param("partyId") UUID partyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT " +
            "COALESCE(SUM(e.debit), 0) as totalDebit, " +
            "COALESCE(SUM(e.credit), 0) as totalCredit " +
            "FROM JournalEntry e " +
            "WHERE e.account.id = :accountId " +
            "AND e.thirdParty.id = :partyId " +
            "AND e.voucher.status = com.app.accounting.voucher.entity.VoucherStatus.POSTED " +
            "AND e.voucher.issueDate < :startDate")
    PartyBalanceProjection findPartyBalanceBeforeDate(
            @Param("accountId") UUID accountId,
            @Param("partyId") UUID partyId,
            @Param("startDate") LocalDate startDate
    );
}
