package com.app.accounting.common.document;

import com.app.accounting.associate.dto.response.AssociateReceiptResponse;
import com.app.accounting.associate.dto.response.AssociateStatementResponse;
import com.app.accounting.associate.entity.QuotaCategory;
import com.app.accounting.billing.dto.response.CustomerStatementResponse;
import com.app.accounting.billing.dto.response.DebtDocumentResponse;
import com.app.accounting.billing.entity.DebtDocument;
import com.app.accounting.billing.entity.DebtType;
import com.app.accounting.billing.entity.DocumentStatus;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.puc.entity.Account;
import com.app.accounting.treasury.entity.PaymentReceipt;
import com.app.accounting.treasury.entity.PaymentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentExportTest {

    private PdfExportService pdfExportService;
    private ExcelExportService excelExportService;

    @BeforeEach
    void setUp() {
        pdfExportService = new PdfExportService();
        excelExportService = new ExcelExportService();
    }

    @Test
    @DisplayName("Debe generar PDF de Recibo de Caja (CASH_IN) de Tesorería correctamente")
    void shouldGeneratePaymentReceiptPdf() {
        ThirdParty party = ThirdParty.builder()
                .id(UUID.randomUUID())
                .fullName("ALIMENTOS DEL NORTE S.A.S.")
                .identificationNumber("900123456")
                .dv("7")
                .phone("3101234567")
                .email("pagos@elnorte.com")
                .build();

        DebtDocument invoice = DebtDocument.builder()
                .id(UUID.randomUUID())
                .documentNumber("FAC-1005")
                .type(DebtType.RECEIVABLE)
                .totalAmount(new BigDecimal("1500000.0000"))
                .remainingBalance(new BigDecimal("500000.0000"))
                .thirdParty(party)
                .build();

        Account cashAcc = Account.builder()
                .code("110505")
                .name("CAJA GENERAL")
                .build();

        PaymentReceipt receipt = PaymentReceipt.builder()
                .id(UUID.randomUUID())
                .receiptNumber("RC-8402")
                .paymentType(PaymentType.CASH_IN)
                .paymentDate(LocalDate.now())
                .appliedToDocument(invoice)
                .cashOrBankAccount(cashAcc)
                .amountPaid(new BigDecimal("1000000.0000"))
                .build();

        byte[] pdfBytes = pdfExportService.generatePaymentReceiptPdf(receipt);

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(100);
        // Validar cabecera PDF (%PDF-)
        assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("Debe generar PDF de Recibo de Asociado (RB) con conceptos discriminados")
    void shouldGenerateAssociateReceiptPdf() {
        var item1 = AssociateReceiptResponse.AssociateReceiptItemResponse.builder()
                .conceptName("Cuota de Administración")
                .category(QuotaCategory.INCOME)
                .accountCode("417010")
                .amount(new BigDecimal("6250.0000"))
                .build();

        var item2 = AssociateReceiptResponse.AssociateReceiptItemResponse.builder()
                .conceptName("Aportes Sociales Ordinarios")
                .category(QuotaCategory.EQUITY_SAVING)
                .accountCode("311505")
                .amount(new BigDecimal("10000.0000"))
                .build();

        byte[] pdfBytes = pdfExportService.generateAssociateReceiptPdf(
                "RB-2025-001",
                LocalDate.now(),
                "CARLOS ANDRES MENDOZA",
                "1018234567",
                "3159988776",
                "carlos@asociacion.org",
                "110505",
                "CAJA GENERAL ASOCIACIÓN",
                "NC-045",
                "Cuota Ordinaria Mensual $50.000",
                new BigDecimal("50000.0000"),
                List.of(item1, item2)
        );

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(100);
        assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("Debe generar PDF y Excel de Estado de Cuenta de Cartera de Cliente")
    void shouldGenerateCustomerStatementPdfAndExcel() {
        var doc = DebtDocumentResponse.builder()
                .documentNumber("FAC-2001")
                .issueDate(LocalDate.now().minusDays(15))
                .dueDate(LocalDate.now().plusDays(15))
                .subtotal(new BigDecimal("2000000.0000"))
                .taxTotal(new BigDecimal("380000.0000"))
                .retentionTotal(new BigDecimal("50000.0000"))
                .totalAmount(new BigDecimal("2330000.0000"))
                .remainingBalance(new BigDecimal("1330000.0000"))
                .status(DocumentStatus.PARTIALLY_PAID)
                .build();

        CustomerStatementResponse stmt = CustomerStatementResponse.builder()
                .thirdPartyId(UUID.randomUUID())
                .fullName("DISTRIBUIDORA DEL VALLE S.A.S.")
                .identificationNumber("800987654")
                .phone("3005556677")
                .email("cartera@delvalle.com")
                .totalInvoiced(new BigDecimal("5000000.0000"))
                .totalPaid(new BigDecimal("3670000.0000"))
                .currentBalance(new BigDecimal("1330000.0000"))
                .totalInvoices(3)
                .pendingInvoices(1)
                .overdueInvoices(0)
                .documents(List.of(doc))
                .build();

        // PDF
        byte[] pdf = pdfExportService.generateCustomerStatementPdf(stmt);
        assertThat(pdf).isNotNull();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");

        // Excel
        byte[] excel = excelExportService.generateCustomerStatementExcel(stmt);
        assertThat(excel).isNotNull();
        assertThat(excel.length).isGreaterThan(500); // Cabecera ZIP / OOXML
    }

    @Test
    @DisplayName("Debe generar Certificado Oficial de Retención en la Fuente en PDF")
    void shouldGenerateWithholdingCertificatePdf() {
        var item1 = new PdfExportService.WithholdingCertificateItem(
                "Retención en la fuente por compras 2.5%",
                "236540",
                new BigDecimal("40000000.0000"),
                new BigDecimal("1000000.0000")
        );

        var item2 = new PdfExportService.WithholdingCertificateItem(
                "ReteICA Servicios Bogotá 0.966%",
                "236805",
                new BigDecimal("10000000.0000"),
                new BigDecimal("96600.0000")
        );

        byte[] pdf = pdfExportService.generateWithholdingCertificatePdf(
                2025,
                "PROVEEDORES Y SUMINISTROS DE COLOMBIA S.A.",
                "900.555.444-1",
                List.of(item1, item2),
                new BigDecimal("1096600.0000")
        );

        assertThat(pdf).isNotNull();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }
}
