package com.app.accounting.maintenance.service;

import com.app.accounting.maintenance.dto.DatabaseCleanupRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class DatabaseCleanupService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public Map<String, Integer> performCleanup(DatabaseCleanupRequest request) {
        Map<String, Integer> summary = new LinkedHashMap<>();

        // 1. Recaudos de asociados
        if (request.isCleanAssociateReceipts()) {
            int items = jdbcTemplate.update("DELETE FROM associate_receipt_items");
            int receipts = jdbcTemplate.update("DELETE FROM associate_receipts");
            summary.put("Recaudos y recibos de asociados", receipts);
            summary.put("Detalles de conceptos recaudados", items);
        }

        // 2. Préstamos y Cartera
        if (request.isCleanLoans()) {
            int payments = jdbcTemplate.update("DELETE FROM loan_payments");
            int installments = jdbcTemplate.update("DELETE FROM loan_installments");
            int loans = jdbcTemplate.update("DELETE FROM loans");
            summary.put("Abonos a préstamos", payments);
            summary.put("Cuotas de préstamos", installments);
            summary.put("Préstamos de cartera", loans);
        }

        // 3. Documentos de deuda y tesorería (cartera general)
        if (request.isCleanDebtAndTreasury()) {
            int treasury = jdbcTemplate.update("DELETE FROM payment_receipts");
            int debt = jdbcTemplate.update("DELETE FROM debt_documents");
            summary.put("Recibos y comprobantes de tesorería", treasury);
            summary.put("Documentos de cartera (CXC/CXP)", debt);
        }

        // 4. Comprobantes contables y asientos
        if (request.isCleanJournalVouchers()) {
            // Desenlazar comprobantes en tablas que no fueron seleccionadas para borrado
            try {
                jdbcTemplate.update("UPDATE associate_receipts SET voucher_id = NULL WHERE voucher_id IS NOT NULL");
            } catch (Exception ignored) {}
            try {
                jdbcTemplate.update("UPDATE loans SET disbursement_voucher_id = NULL WHERE disbursement_voucher_id IS NOT NULL");
            } catch (Exception ignored) {}
            try {
                jdbcTemplate.update("UPDATE loan_payments SET voucher_id = NULL WHERE voucher_id IS NOT NULL");
            } catch (Exception ignored) {}
            try {
                jdbcTemplate.update("UPDATE payment_receipts SET voucher_id = NULL WHERE voucher_id IS NOT NULL");
            } catch (Exception ignored) {}
            try {
                jdbcTemplate.update("UPDATE debt_documents SET voucher_id = NULL WHERE voucher_id IS NOT NULL");
            } catch (Exception ignored) {}

            int entries = jdbcTemplate.update("DELETE FROM journal_entries");
            int vouchers = jdbcTemplate.update("DELETE FROM journal_vouchers");
            summary.put("Asientos contables", entries);
            summary.put("Comprobantes contables", vouchers);
        }

        // 5. Plantillas de cuotas
        if (request.isCleanQuotaTemplates()) {
            try {
                jdbcTemplate.update("UPDATE associate_receipts SET template_id = NULL WHERE template_id IS NOT NULL");
            } catch (Exception ignored) {}
            int templateItems = jdbcTemplate.update("DELETE FROM quota_template_items");
            int templates = jdbcTemplate.update("DELETE FROM quota_templates");
            summary.put("Conceptos de plantillas de cuota", templateItems);
            summary.put("Plantillas de cuotas", templates);
        }

        // 6. Asociados y Terceros
        if (request.isCleanThirdParties()) {
            // Desenlazar referencias residuales que bloquearían la eliminación de terceros
            try { jdbcTemplate.update("DELETE FROM associate_receipt_items"); } catch (Exception ignored) {}
            try { jdbcTemplate.update("DELETE FROM associate_receipts"); } catch (Exception ignored) {}
            try { jdbcTemplate.update("DELETE FROM loan_payments"); } catch (Exception ignored) {}
            try { jdbcTemplate.update("DELETE FROM loan_installments"); } catch (Exception ignored) {}
            try { jdbcTemplate.update("DELETE FROM loans"); } catch (Exception ignored) {}
            try { jdbcTemplate.update("DELETE FROM payment_receipts"); } catch (Exception ignored) {}
            try { jdbcTemplate.update("DELETE FROM debt_documents"); } catch (Exception ignored) {}
            try { jdbcTemplate.update("DELETE FROM journal_entries"); } catch (Exception ignored) {}
            try { jdbcTemplate.update("DELETE FROM journal_vouchers"); } catch (Exception ignored) {}

            int parties = jdbcTemplate.update("DELETE FROM third_parties");
            summary.put("Terceros y asociados eliminados", parties);
        }

        log.info("Limpieza de base de datos ejecutada con éxito. Resumen: {}", summary);
        return summary;
    }
}
