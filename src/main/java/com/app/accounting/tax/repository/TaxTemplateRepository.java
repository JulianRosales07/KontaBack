package com.app.accounting.tax.repository;

import com.app.accounting.tax.entity.TaxTemplate;
import com.app.accounting.tax.entity.TaxType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaxTemplateRepository extends JpaRepository<TaxTemplate, UUID> {

    List<TaxTemplate> findByIsActiveTrue();

    List<TaxTemplate> findByTaxTypeAndIsActiveTrue(TaxType taxType);
}
