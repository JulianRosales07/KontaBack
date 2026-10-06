package com.app.accounting.tax.service;

import com.app.accounting.tax.dto.request.TaxCalculationRequest;
import com.app.accounting.tax.dto.request.TaxTemplateRequest;
import com.app.accounting.tax.dto.response.TaxCalculationResult;
import com.app.accounting.tax.dto.response.TaxTemplateResponse;
import com.app.accounting.tax.entity.TaxTemplate;

import java.util.List;
import java.util.UUID;

public interface TaxEngineService {

    List<TaxTemplateResponse> getAllActiveTemplates();

    TaxTemplateResponse getTemplateById(UUID id);

    TaxTemplateResponse createTemplate(TaxTemplateRequest request);

    TaxCalculationResult calculateTaxes(TaxCalculationRequest request);

    List<TaxTemplate> resolveTemplates(List<UUID> templateIds);

    byte[] generateWithholdingCertificatePdf(UUID partyId, int year);
}
