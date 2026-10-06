package com.app.accounting.party.repository;

import com.app.accounting.party.entity.ThirdParty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ThirdPartyRepository extends JpaRepository<ThirdParty, UUID> {
    Optional<ThirdParty> findByIdentificationNumber(String identificationNumber);
    boolean existsByIdentificationNumber(String identificationNumber);

    @Query("SELECT t FROM ThirdParty t WHERE " +
            "LOWER(t.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "t.identificationNumber LIKE CONCAT('%', :query, '%')")
    Page<ThirdParty> searchThirdParties(@Param("query") String query, Pageable pageable);
}
