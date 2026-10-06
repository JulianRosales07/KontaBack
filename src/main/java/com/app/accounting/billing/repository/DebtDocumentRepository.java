package com.app.accounting.billing.repository;

import com.app.accounting.billing.entity.DebtDocument;
import com.app.accounting.billing.entity.DebtType;
import com.app.accounting.billing.entity.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DebtDocumentRepository extends JpaRepository<DebtDocument, UUID>, JpaSpecificationExecutor<DebtDocument> {

    Optional<DebtDocument> findByDocumentNumber(String documentNumber);

    boolean existsByDocumentNumber(String documentNumber);

    List<DebtDocument> findByThirdPartyId(UUID thirdPartyId);

    List<DebtDocument> findByTypeAndStatus(DebtType type, DocumentStatus status);

    @Query("SELECT d FROM DebtDocument d " +
            "JOIN FETCH d.thirdParty t " +
            "LEFT JOIN FETCH d.voucher v " +
            "WHERE d.id = :id")
    Optional<DebtDocument> findByIdWithDetails(@Param("id") UUID id);

    @Query("SELECT d FROM DebtDocument d " +
            "JOIN FETCH d.thirdParty t " +
            "LEFT JOIN FETCH d.voucher v " +
            "WHERE d.documentNumber = :documentNumber")
    Optional<DebtDocument> findByDocumentNumberWithDetails(@Param("documentNumber") String documentNumber);

    @Query("SELECT d FROM DebtDocument d " +
            "JOIN FETCH d.thirdParty t " +
            "LEFT JOIN FETCH d.voucher v " +
            "WHERE d.type = com.app.accounting.billing.entity.DebtType.RECEIVABLE " +
            "AND d.status IN (com.app.accounting.billing.entity.DocumentStatus.PENDING, com.app.accounting.billing.entity.DocumentStatus.PARTIALLY_PAID) " +
            "ORDER BY d.dueDate ASC")
    List<DebtDocument> findPendingReceivablesWithDetails();

    @Query("SELECT d FROM DebtDocument d " +
            "JOIN FETCH d.thirdParty t " +
            "LEFT JOIN FETCH d.voucher v " +
            "WHERE d.type = com.app.accounting.billing.entity.DebtType.RECEIVABLE " +
            "AND d.status IN (com.app.accounting.billing.entity.DocumentStatus.PENDING, com.app.accounting.billing.entity.DocumentStatus.PARTIALLY_PAID) " +
            "AND t.id = :thirdPartyId " +
            "ORDER BY d.dueDate ASC")
    List<DebtDocument> findPendingReceivablesByThirdPartyWithDetails(@Param("thirdPartyId") UUID thirdPartyId);

    @Query("SELECT d FROM DebtDocument d " +
            "JOIN FETCH d.thirdParty t " +
            "LEFT JOIN FETCH d.voucher v " +
            "WHERE d.type = com.app.accounting.billing.entity.DebtType.RECEIVABLE " +
            "AND t.id = :thirdPartyId " +
            "ORDER BY d.issueDate DESC")
    List<DebtDocument> findReceivablesByThirdPartyWithDetails(@Param("thirdPartyId") UUID thirdPartyId);
}

