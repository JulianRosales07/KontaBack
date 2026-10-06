package com.app.accounting.puc.repository;

import com.app.accounting.puc.entity.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByCode(String code);
    boolean existsByCode(String code);
    List<Account> findByParentId(UUID parentId);
    List<Account> findByIsActiveTrueOrderByCodeAsc();

    @Query("SELECT a FROM Account a LEFT JOIN FETCH a.parent WHERE a.isActive = true ORDER BY a.code ASC")
    List<Account> findAllWithParent();

    Page<Account> findByIsActiveTrue(Pageable pageable);
}
