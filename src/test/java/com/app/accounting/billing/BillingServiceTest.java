package com.app.accounting.billing;

import com.app.accounting.billing.dto.request.SalesInvoiceRequest;
import com.app.accounting.billing.dto.response.SalesInvoiceResponse;
import com.app.accounting.billing.entity.DebtDocument;
import com.app.accounting.billing.entity.DebtType;
import com.app.accounting.billing.entity.DocumentStatus;
import com.app.accounting.billing.repository.DebtDocumentRepository;
import com.app.accounting.billing.service.impl.BillingServiceImpl;
import com.app.accounting.party.entity.PersonType;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.party.service.ThirdPartyService;
import com.app.accounting.puc.entity.AccountNature;
import com.app.accounting.tax.dto.response.TaxCalculationResult;
import com.app.accounting.tax.dto.response.TaxItemDetail;
import com.app.accounting.tax.entity.TaxType;
import com.app.accounting.tax.service.TaxEngineService;
import com.app.accounting.voucher.dto.request.JournalVoucherRequest;
import com.app.accounting.voucher.dto.response.JournalVoucherResponse;
import com.app.accounting.voucher.entity.VoucherStatus;
import com.app.accounting.voucher.entity.VoucherType;
import com.app.accounting.voucher.repository.JournalVoucherRepository;
import com.app.accounting.voucher.service.VoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private DebtDocumentRepository debtDocumentRepository;

    @Mock
    private ThirdPartyService thirdPartyService;

    @Mock
    private TaxEngineService taxEngineService;

    @Mock
    private VoucherService voucherService;

    @Mock
    private JournalVoucherRepository voucherRepository;

    @Mock
    private com.app.accounting.common.document.PdfExportService pdfExportService;

    @Mock
    private com.app.accounting.common.document.ExcelExportService excelExportService;

    @InjectMocks
    private BillingServiceImpl billingService;

    private ThirdParty client;

    @BeforeEach
    void setUp() {
        client = ThirdParty.builder()
                .id(UUID.randomUUID())
                .identificationNumber("901234567")
                .fullName("ACME CORP S.A.S.")
                .type(PersonType.JURIDICA)
                .build();
    }

    @Test
    @DisplayName("Debe emitir factura de venta, registrar DebtDocument RECEIVABLE y causar asiento balanceado")
    void shouldCreateSalesInvoiceSuccessfully() {
        SalesInvoiceRequest request = SalesInvoiceRequest.builder()
                .documentNumber("FAC-1001")
                .thirdPartyId(client.getId())
                .issueDate(LocalDate.of(2026, 9, 1))
                .dueDate(LocalDate.of(2026, 10, 1))
                .incomeAccountCode("413505")
                .subtotal(new BigDecimal("1000000.0000"))
                .taxTemplateIds(List.of(UUID.randomUUID()))
                .build();

        when(debtDocumentRepository.existsByDocumentNumber("FAC-1001")).thenReturn(false);
        when(thirdPartyService.getThirdPartyEntityById(client.getId())).thenReturn(client);

        TaxCalculationResult taxResult = TaxCalculationResult.builder()
                .subtotal(new BigDecimal("1000000.0000"))
                .taxTotal(new BigDecimal("190000.0000"))
                .retentionTotal(new BigDecimal("25000.0000"))
                .totalAmount(new BigDecimal("1165000.0000"))
                .items(List.of(
                        TaxItemDetail.builder()
                                .name("IVA 19%")
                                .taxType(TaxType.IVA)
                                .ratePercentage(new BigDecimal("19.0000"))
                                .taxAmount(new BigDecimal("190000.0000"))
                                .accountCode("240805")
                                .accountNature(AccountNature.CREDIT)
                                .isRetention(false)
                                .build(),
                        TaxItemDetail.builder()
                                .name("Retefuente 2.5%")
                                .taxType(TaxType.RETEFUENTE)
                                .ratePercentage(new BigDecimal("2.5000"))
                                .taxAmount(new BigDecimal("25000.0000"))
                                .accountCode("135515")
                                .accountNature(AccountNature.DEBIT)
                                .isRetention(true)
                                .build()
                ))
                .build();

        when(taxEngineService.calculateTaxes(any())).thenReturn(taxResult);

        when(debtDocumentRepository.save(any(DebtDocument.class))).thenAnswer(inv -> {
            DebtDocument doc = inv.getArgument(0);
            if (doc.getId() == null) {
                doc.setId(UUID.randomUUID());
            }
            return doc;
        });

        UUID voucherId = UUID.randomUUID();
        JournalVoucherResponse mockVoucherResp = JournalVoucherResponse.builder()
                .id(voucherId)
                .voucherNumber("CV-FAC-1001")
                .voucherType(VoucherType.NOTA_CONTABLE)
                .status(VoucherStatus.POSTED)
                .totalDebit(new BigDecimal("1190000.0000"))
                .totalCredit(new BigDecimal("1190000.0000"))
                .build();

        when(voucherService.createAndPostVoucher(any(JournalVoucherRequest.class))).thenReturn(mockVoucherResp);
        when(voucherRepository.findById(voucherId)).thenReturn(Optional.empty());

        SalesInvoiceResponse response = billingService.createSalesInvoice(request);

        assertThat(response).isNotNull();
        assertThat(response.getDebtDocument().getDocumentNumber()).isEqualTo("FAC-1001");
        assertThat(response.getDebtDocument().getType()).isEqualTo(DebtType.RECEIVABLE);
        assertThat(response.getDebtDocument().getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(response.getDebtDocument().getTotalAmount()).isEqualByComparingTo(new BigDecimal("1165000.0000"));
        assertThat(response.getDebtDocument().getRemainingBalance()).isEqualByComparingTo(new BigDecimal("1165000.0000"));

        // Verificar el asiento contable enviado al VoucherService
        ArgumentCaptor<JournalVoucherRequest> captor = ArgumentCaptor.forClass(JournalVoucherRequest.class);
        verify(voucherService).createAndPostVoucher(captor.capture());
        JournalVoucherRequest sentVoucher = captor.getValue();

        assertThat(sentVoucher.getVoucherNumber()).isEqualTo("CV-FAC-1001");
        assertThat(sentVoucher.getEntries()).hasSize(4); // Clientes 130505 (deb), Retefuente 135515 (deb), Ingresos 413505 (cred), IVA 240805 (cred)

        BigDecimal totalDebits = sentVoucher.getEntries().stream()
                .map(e -> e.getDebit() != null ? e.getDebit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = sentVoucher.getEntries().stream()
                .map(e -> e.getCredit() != null ? e.getCredit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(totalDebits).isEqualByComparingTo(new BigDecimal("1190000.0000"));
        assertThat(totalCredits).isEqualByComparingTo(new BigDecimal("1190000.0000"));
        assertThat(totalDebits).isEqualByComparingTo(totalCredits); // Partida doble balanceada
    }

    @Test
    @DisplayName("Debe generar reporte de cartera por edades (Aging) clasificando en los tramos correctos")
    void shouldGenerateReceivablesAgingReportWithBuckets() {
        LocalDate asOfDate = LocalDate.of(2026, 9, 30);

        // Doc 1: No vencida (Vence en octubre) -> CURRENT
        DebtDocument doc1 = DebtDocument.builder()
                .id(UUID.randomUUID())
                .type(DebtType.RECEIVABLE)
                .documentNumber("FAC-101")
                .thirdParty(client)
                .issueDate(LocalDate.of(2026, 9, 15))
                .dueDate(LocalDate.of(2026, 10, 15))
                .totalAmount(new BigDecimal("100000.0000"))
                .remainingBalance(new BigDecimal("100000.0000"))
                .status(DocumentStatus.PENDING)
                .build();

        // Doc 2: 15 días vencida -> DAYS_1_TO_30
        DebtDocument doc2 = DebtDocument.builder()
                .id(UUID.randomUUID())
                .type(DebtType.RECEIVABLE)
                .documentNumber("FAC-102")
                .thirdParty(client)
                .issueDate(LocalDate.of(2026, 8, 15))
                .dueDate(LocalDate.of(2026, 9, 15))
                .totalAmount(new BigDecimal("200000.0000"))
                .remainingBalance(new BigDecimal("200000.0000"))
                .status(DocumentStatus.PENDING)
                .build();

        // Doc 3: 45 días vencida -> DAYS_31_TO_60
        DebtDocument doc3 = DebtDocument.builder()
                .id(UUID.randomUUID())
                .type(DebtType.RECEIVABLE)
                .documentNumber("FAC-103")
                .thirdParty(client)
                .issueDate(LocalDate.of(2026, 7, 1))
                .dueDate(LocalDate.of(2026, 8, 16))
                .totalAmount(new BigDecimal("300000.0000"))
                .remainingBalance(new BigDecimal("300000.0000"))
                .status(DocumentStatus.PENDING)
                .build();

        // Doc 4: 100 días vencida -> OVER_90
        DebtDocument doc4 = DebtDocument.builder()
                .id(UUID.randomUUID())
                .type(DebtType.RECEIVABLE)
                .documentNumber("FAC-104")
                .thirdParty(client)
                .issueDate(LocalDate.of(2026, 5, 1))
                .dueDate(LocalDate.of(2026, 6, 22))
                .totalAmount(new BigDecimal("400000.0000"))
                .remainingBalance(new BigDecimal("400000.0000"))
                .status(DocumentStatus.PENDING)
                .build();

        when(debtDocumentRepository.findPendingReceivablesWithDetails()).thenReturn(List.of(doc1, doc2, doc3, doc4));

        var report = billingService.getReceivablesAging(asOfDate, null);

        assertThat(report).isNotNull();
        assertThat(report.getAsOfDate()).isEqualTo(asOfDate);
        assertThat(report.getTotalReceivables()).isEqualByComparingTo(new BigDecimal("1000000.0000"));
        assertThat(report.getTotalCurrent()).isEqualByComparingTo(new BigDecimal("100000.0000"));
        assertThat(report.getTotal1To30()).isEqualByComparingTo(new BigDecimal("200000.0000"));
        assertThat(report.getTotal31To60()).isEqualByComparingTo(new BigDecimal("300000.0000"));
        assertThat(report.getTotal61To90()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(report.getTotalOver90()).isEqualByComparingTo(new BigDecimal("400000.0000"));
        assertThat(report.getCustomerCount()).isEqualTo(1);
        assertThat(report.getDocumentCount()).isEqualTo(4);

        var cust = report.getCustomers().get(0);
        assertThat(cust.getIdentificationNumber()).isEqualTo("901234567");
        assertThat(cust.getTotalBalance()).isEqualByComparingTo(new BigDecimal("1000000.0000"));
        assertThat(cust.getCurrentBalance()).isEqualByComparingTo(new BigDecimal("100000.0000"));
        assertThat(cust.getBalance1To30()).isEqualByComparingTo(new BigDecimal("200000.0000"));
        assertThat(cust.getBalance31To60()).isEqualByComparingTo(new BigDecimal("300000.0000"));
        assertThat(cust.getBalanceOver90()).isEqualByComparingTo(new BigDecimal("400000.0000"));
        assertThat(cust.getDocuments()).hasSize(4);
    }

    @Test
    @DisplayName("Debe generar Estado de Cuenta de un cliente con total facturado, cobrado y saldo")
    void shouldGenerateCustomerStatementCorrectly() {
        DebtDocument doc1 = DebtDocument.builder()
                .id(UUID.randomUUID())
                .type(DebtType.RECEIVABLE)
                .documentNumber("FAC-201")
                .thirdParty(client)
                .issueDate(LocalDate.of(2026, 8, 1))
                .dueDate(LocalDate.of(2026, 8, 30))
                .totalAmount(new BigDecimal("500000.0000"))
                .remainingBalance(BigDecimal.ZERO)
                .status(DocumentStatus.PAID)
                .build();

        DebtDocument doc2 = DebtDocument.builder()
                .id(UUID.randomUUID())
                .type(DebtType.RECEIVABLE)
                .documentNumber("FAC-202")
                .thirdParty(client)
                .issueDate(LocalDate.of(2026, 9, 1))
                .dueDate(LocalDate.of(2026, 9, 30))
                .totalAmount(new BigDecimal("300000.0000"))
                .remainingBalance(new BigDecimal("100000.0000"))
                .status(DocumentStatus.PARTIALLY_PAID)
                .build();

        when(thirdPartyService.getThirdPartyEntityById(client.getId())).thenReturn(client);
        when(debtDocumentRepository.findReceivablesByThirdPartyWithDetails(client.getId()))
                .thenReturn(List.of(doc1, doc2));

        var statement = billingService.getCustomerStatement(client.getId());

        assertThat(statement).isNotNull();
        assertThat(statement.getIdentificationNumber()).isEqualTo("901234567");
        assertThat(statement.getTotalInvoiced()).isEqualByComparingTo(new BigDecimal("800000.0000"));
        assertThat(statement.getTotalPaid()).isEqualByComparingTo(new BigDecimal("700000.0000"));
        assertThat(statement.getCurrentBalance()).isEqualByComparingTo(new BigDecimal("100000.0000"));
        assertThat(statement.getTotalInvoices()).isEqualTo(2);
        assertThat(statement.getPendingInvoices()).isEqualTo(1);
    }
}

