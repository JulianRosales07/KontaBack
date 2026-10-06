package com.app.accounting.closing.repository;

import com.app.accounting.closing.entity.AccountingPeriod;
import com.app.accounting.closing.entity.PeriodStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, UUID> {
    Optional<AccountingPeriod> findByYearAndMonth(Integer year, Integer month);
    List<AccountingPeriod> findByYearOrderByMonthAsc(Integer year);
    boolean existsByYearAndMonthAndStatus(Integer year, Integer month, PeriodStatus status);
}
