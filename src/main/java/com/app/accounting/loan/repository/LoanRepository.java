package com.app.accounting.loan.repository;

import com.app.accounting.loan.entity.Loan;
import com.app.accounting.loan.entity.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoanRepository extends JpaRepository<Loan, UUID> {

    @Query("SELECT DISTINCT l FROM Loan l JOIN FETCH l.thirdParty LEFT JOIN FETCH l.installments ORDER BY l.disbursementDate DESC, l.loanNumber DESC")
    List<Loan> findAllWithDetails();

    @Query("SELECT DISTINCT l FROM Loan l JOIN FETCH l.thirdParty LEFT JOIN FETCH l.installments WHERE l.status IN :statuses ORDER BY l.disbursementDate DESC, l.loanNumber DESC")
    List<Loan> findByStatusInWithDetails(@Param("statuses") Collection<LoanStatus> statuses);

    @Query("SELECT DISTINCT l FROM Loan l JOIN FETCH l.thirdParty LEFT JOIN FETCH l.installments WHERE l.thirdParty.id = :partyId ORDER BY l.disbursementDate DESC")
    List<Loan> findByThirdPartyWithDetails(@Param("partyId") UUID partyId);

    @Query("SELECT l FROM Loan l JOIN FETCH l.thirdParty LEFT JOIN FETCH l.installments WHERE l.id = :id")
    Optional<Loan> findByIdWithDetails(@Param("id") UUID id);

    boolean existsByLoanNumber(String loanNumber);

    long countByThirdPartyIdAndStatus(UUID thirdPartyId, LoanStatus status);
}
