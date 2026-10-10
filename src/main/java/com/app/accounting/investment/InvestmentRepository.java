package com.app.accounting.investment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface InvestmentRepository extends JpaRepository<Investment, UUID> {

    @Query("SELECT i FROM Investment i JOIN FETCH i.account ORDER BY i.status ASC, i.entityName ASC")
    List<Investment> findAllWithAccount();

    boolean existsByEntityNameIgnoreCaseAndInstrumentType(String entityName, String instrumentType);
}
