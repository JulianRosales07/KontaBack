package com.app.accounting.loan.repository;

import com.app.accounting.loan.entity.LoanPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LoanPaymentRepository extends JpaRepository<LoanPayment, UUID> {

    @Query("SELECT p FROM LoanPayment p JOIN FETCH p.installment JOIN FETCH p.thirdParty JOIN FETCH p.loan " +
            "LEFT JOIN FETCH p.voucher LEFT JOIN FETCH p.cashAccount WHERE p.loan.id = :loanId ORDER BY p.paymentDate DESC, p.receiptNumber DESC")
    List<LoanPayment> findByLoanIdWithDetails(@Param("loanId") UUID loanId);

    @Query("SELECT p FROM LoanPayment p JOIN FETCH p.installment JOIN FETCH p.thirdParty JOIN FETCH p.loan " +
            "LEFT JOIN FETCH p.voucher LEFT JOIN FETCH p.cashAccount " +
            "WHERE p.paymentDate BETWEEN :from AND :to ORDER BY p.paymentDate ASC, p.receiptNumber ASC")
    List<LoanPayment> findByPaymentDateBetweenWithDetails(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT p FROM LoanPayment p JOIN FETCH p.installment JOIN FETCH p.thirdParty JOIN FETCH p.loan " +
            "LEFT JOIN FETCH p.voucher LEFT JOIN FETCH p.cashAccount WHERE p.receiptNumber = :receipt ORDER BY p.installment.installmentNumber ASC")
    List<LoanPayment> findByReceiptNumberWithDetails(@Param("receipt") String receiptNumber);

    @Query("SELECT COUNT(DISTINCT p.receiptNumber) FROM LoanPayment p")
    long countDistinctReceipts();

    boolean existsByReceiptNumber(String receiptNumber);
}
