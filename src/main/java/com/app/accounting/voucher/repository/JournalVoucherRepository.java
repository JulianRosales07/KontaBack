package com.app.accounting.voucher.repository;

import com.app.accounting.voucher.entity.JournalVoucher;
import com.app.accounting.voucher.entity.VoucherStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JournalVoucherRepository extends JpaRepository<JournalVoucher, UUID>, JpaSpecificationExecutor<JournalVoucher> {
    Optional<JournalVoucher> findByVoucherNumber(String voucherNumber);
    boolean existsByVoucherNumber(String voucherNumber);

    @Query("SELECT v FROM JournalVoucher v LEFT JOIN FETCH v.entries WHERE v.id = :id")
    Optional<JournalVoucher> findByIdWithEntries(@Param("id") UUID id);

    @Query("SELECT v FROM JournalVoucher v LEFT JOIN FETCH v.entries WHERE v.voucherNumber = :voucherNumber")
    Optional<JournalVoucher> findByVoucherNumberWithEntries(@Param("voucherNumber") String voucherNumber);

    Page<JournalVoucher> findByStatus(VoucherStatus status, Pageable pageable);

    Page<JournalVoucher> findByIssueDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);
}
