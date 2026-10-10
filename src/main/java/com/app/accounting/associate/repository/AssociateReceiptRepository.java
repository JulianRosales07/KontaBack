package com.app.accounting.associate.repository;

import com.app.accounting.associate.entity.AssociateReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssociateReceiptRepository extends JpaRepository<AssociateReceipt, UUID> {
    Optional<AssociateReceipt> findByReceiptNumber(String receiptNumber);
    List<AssociateReceipt> findByThirdPartyIdOrderByPaymentDateDesc(UUID thirdPartyId);
    Page<AssociateReceipt> findAllByOrderByPaymentDateDesc(Pageable pageable);

    @Query("SELECT COUNT(r) FROM AssociateReceipt r")
    long countAllReceipts();

    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM AssociateReceiptItem i WHERE i.receipt.thirdParty.id = :thirdPartyId AND i.category = :category")
    BigDecimal sumTotalByCategoryAndThirdParty(@Param("thirdPartyId") UUID thirdPartyId, @Param("category") com.app.accounting.associate.entity.QuotaCategory category);

    /** Agregado por asociado y categoría: [thirdPartyId, category, suma, nº recibos, última fecha]. */
    @Query("SELECT r.thirdParty.id, i.category, COALESCE(SUM(i.amount), 0), COUNT(DISTINCT r.id), MAX(r.paymentDate) " +
            "FROM AssociateReceipt r JOIN r.items i GROUP BY r.thirdParty.id, i.category")
    List<Object[]> aggregateByThirdPartyAndCategory();

    @Query("SELECT DISTINCT r FROM AssociateReceipt r JOIN FETCH r.thirdParty LEFT JOIN FETCH r.items i LEFT JOIN FETCH i.account " +
            "LEFT JOIN FETCH r.cashAccount LEFT JOIN FETCH r.voucher WHERE r.paymentDate BETWEEN :from AND :to ORDER BY r.paymentDate ASC")
    List<AssociateReceipt> findByPaymentDateBetweenWithDetails(@Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);
}
