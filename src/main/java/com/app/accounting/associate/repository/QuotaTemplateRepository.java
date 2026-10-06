package com.app.accounting.associate.repository;

import com.app.accounting.associate.entity.QuotaTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuotaTemplateRepository extends JpaRepository<QuotaTemplate, UUID> {
    List<QuotaTemplate> findByIsActiveTrue();
    Optional<QuotaTemplate> findByName(String name);
}
