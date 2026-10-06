package com.app.accounting.associate.service;

import com.app.accounting.associate.dto.request.CreateAssociateReceiptRequest;
import com.app.accounting.associate.dto.response.AssociateReceiptResponse;
import com.app.accounting.associate.dto.response.AssociateStatementResponse;
import com.app.accounting.associate.dto.response.QuotaTemplateResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AssociateService {
    List<QuotaTemplateResponse> getActiveTemplates();
    QuotaTemplateResponse getTemplateById(UUID id);
    AssociateReceiptResponse registerReceipt(CreateAssociateReceiptRequest request);
    Page<AssociateReceiptResponse> getAllReceipts(Pageable pageable);
    AssociateReceiptResponse getReceiptById(UUID id);
    AssociateStatementResponse getAssociateStatement(UUID thirdPartyId);

    byte[] generateAssociateReceiptPdf(UUID id);
    byte[] exportAssociateStatement(UUID thirdPartyId, String format);
    List<AssociateReceiptResponse> getReceiptsByAssociate(UUID thirdPartyId);
}
