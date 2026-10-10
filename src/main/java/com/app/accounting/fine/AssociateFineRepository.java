package com.app.accounting.fine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AssociateFineRepository extends JpaRepository<AssociateFine, UUID> {

    @Query("SELECT f FROM AssociateFine f JOIN FETCH f.thirdParty t JOIN FETCH f.fineType ft " +
            "WHERE (:status IS NULL OR f.status = :status) " +
            "AND (:partyId IS NULL OR t.id = :partyId) " +
            "AND (:typeId IS NULL OR ft.id = :typeId) " +
            "AND f.fineDate BETWEEN :from AND :to " +
            "ORDER BY f.fineDate DESC, t.fullName ASC")
    List<AssociateFine> search(@Param("status") AssociateFine.Status status, @Param("partyId") UUID partyId,
                               @Param("typeId") UUID typeId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT f FROM AssociateFine f JOIN FETCH f.thirdParty JOIN FETCH f.fineType WHERE f.id IN :ids")
    List<AssociateFine> findAllWithDetails(@Param("ids") Collection<UUID> ids);

    boolean existsByThirdPartyIdAndFineTypeIdAndFineDateAndStatusNot(UUID partyId, UUID typeId, LocalDate date,
                                                                      AssociateFine.Status status);

    boolean existsByFineTypeId(UUID typeId);

    List<AssociateFine> findByVoucherId(UUID voucherId);
}
