package com.app.accounting;

import com.app.accounting.associate.dto.request.CreateAssociateReceiptRequest;
import com.app.accounting.associate.dto.request.ReceiptItemRequest;
import com.app.accounting.associate.dto.response.AssociateReceiptResponse;
import com.app.accounting.associate.dto.response.AssociateStatementResponse;
import com.app.accounting.associate.dto.response.QuotaTemplateResponse;
import com.app.accounting.associate.service.AssociateService;
import com.app.accounting.party.entity.PersonType;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.party.repository.ThirdPartyRepository;
import com.app.accounting.puc.entity.Account;
import com.app.accounting.puc.repository.AccountRepository;
import com.app.accounting.voucher.dto.response.JournalVoucherResponse;
import com.app.accounting.voucher.service.VoucherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AssociateReceiptIntegrationTest {

    @Autowired
    private AssociateService associateService;

    @Autowired
    private ThirdPartyRepository thirdPartyRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private VoucherService voucherService;

    @Test
    void testAssociatePaymentWorkflow() {
        // 1. Obtener la plantilla activa de $50.000
        List<QuotaTemplateResponse> templates = associateService.getActiveTemplates();
        assertFalse(templates.isEmpty(), "Debe existir al menos una plantilla de cuota activa");
        QuotaTemplateResponse template = templates.getFirst();
        assertEquals(new BigDecimal("50000.0000"), template.getTotalAmount());
        assertEquals(7, template.getItems().size(), "La plantilla debe contener los 7 conceptos discriminados");

        // 2. Obtener o crear un asociado de prueba
        ThirdParty associate = thirdPartyRepository.findByIdentificationNumber("1020304050")
                .orElseGet(() -> thirdPartyRepository.save(ThirdParty.builder()
                        .identificationNumber("1020304050")
                        .dv("1")
                        .fullName("JUAN CARLOS PEREZ GOMEZ (ASOCIADO)")
                        .email("juan.asociado@email.com")
                        .type(PersonType.NATURAL)
                        .build()));

        // 3. Obtener la cuenta de Caja General (110505)
        Account cashAccount = accountRepository.findByCode("110505")
                .orElseThrow(() -> new RuntimeException("Cuenta 110505 no encontrada"));

        // 4. Mapear los items de la plantilla a la solicitud de recibo
        List<ReceiptItemRequest> receiptItems = template.getItems().stream()
                .map(item -> ReceiptItemRequest.builder()
                        .conceptName(item.getConceptName())
                        .category(item.getCategory())
                        .accountId(item.getAccountId())
                        .amount(item.getAmount())
                        .build())
                .toList();

        CreateAssociateReceiptRequest request = CreateAssociateReceiptRequest.builder()
                .thirdPartyId(associate.getId())
                .templateId(template.getId())
                .paymentDate(LocalDate.now())
                .cashAccountId(cashAccount.getId())
                .totalAmount(new BigDecimal("50000.0000"))
                .items(receiptItems)
                .notes("Pago mensual de cuota ordinaria y aporte extraordinario de prueba")
                .build();

        // 5. Registrar el recibo
        AssociateReceiptResponse receipt = associateService.registerReceipt(request);

        assertNotNull(receipt.getId());
        assertTrue(receipt.getReceiptNumber().startsWith("RC-ASOC-"));
        assertEquals(new BigDecimal("50000.0000"), receipt.getTotalAmount());
        assertNotNull(receipt.getVoucherId());

        // 6. Verificar el asiento contable (JournalVoucher) generado
        JournalVoucherResponse voucher = voucherService.getVoucherById(receipt.getVoucherId());
        assertEquals(8, voucher.getEntries().size(), "El comprobante debe tener 1 línea débito (caja) + 7 líneas crédito");
        assertEquals(0, voucher.getTotalDebit().compareTo(voucher.getTotalCredit()), "Las sumas deben ser iguales");
        assertEquals(0, voucher.getTotalDebit().compareTo(new BigDecimal("50000.0000")));

        // 7. Verificar el estado de cuenta del asociado
        AssociateStatementResponse statement = associateService.getAssociateStatement(associate.getId());
        assertEquals(0, statement.getTotalAdministrationFees().compareTo(new BigDecimal("6250.0000")), "Cuota administración debe ser $6.250");
        assertEquals(0, statement.getTotalEquitySavings().compareTo(new BigDecimal("30000.0000")), "Aportes totales deben ser $30.000 (10k + 20k)");
        assertEquals(0, statement.getTotalSocialFunds().compareTo(new BigDecimal("13750.0000")), "Fondos sociales deben ser $13.750 (5250 + 5500 + 2000 + 1000)");
        assertEquals(0, statement.getTotalPaidOverall().compareTo(new BigDecimal("50000.0000")));

        // 8. Generar PDF oficial del recibo de caja de asociado
        byte[] pdf = associateService.generateAssociateReceiptPdf(receipt.getId());
        assertNotNull(pdf);
        assertTrue(pdf.length > 0, "El PDF del recibo debe generarse con contenido en bytes");

        System.out.println(">>> Test exitoso! Recibo generado: " + receipt.getReceiptNumber() + " | Comprobante: " + voucher.getVoucherNumber() + " | PDF bytes: " + pdf.length);
    }
}
