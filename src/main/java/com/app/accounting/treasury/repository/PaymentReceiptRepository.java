package com.app.accounting.treasury.repository;

import com.app.accounting.treasury.entity.PaymentReceipt;
import com.app.accounting.treasury.entity.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, UUID>, JpaSpecificationExecutor<PaymentReceipt> {

    Optional<PaymentReceipt> findByReceiptNumber(String receiptNumber);

    boolean existsByReceiptNumber(String receiptNumber);

    List<PaymentReceipt> findByAppliedToDocumentId(UUID documentId);

    List<PaymentReceipt> findByPaymentType(PaymentType paymentType);

    @Query("SELECT r FROM PaymentReceipt r " +
            "LEFT JOIN FETCH r.appliedToDocument d " +
            "LEFT JOIN FETCH d.thirdParty " +
            "JOIN FETCH r.cashOrBankAccount " +
            "LEFT JOIN FETCH r.voucher " +
            "WHERE r.id = :id")
    Optional<PaymentReceipt> findByIdWithDetails(@Param("id") UUID id);
}
