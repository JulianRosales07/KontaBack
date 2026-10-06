package com.app.accounting.expense;

import com.app.accounting.billing.entity.DebtDocument;
import com.app.accounting.billing.entity.DebtType;
import com.app.accounting.billing.entity.DocumentStatus;
import com.app.accounting.billing.repository.DebtDocumentRepository;
import com.app.accounting.expense.dto.request.EquivalentDocumentRequest;
import com.app.accounting.expense.dto.request.PurchaseInvoiceRequest;
import com.app.accounting.expense.dto.response.EquivalentDocumentResponse;
import com.app.accounting.expense.dto.response.PurchaseInvoiceResponse;
import com.app.accounting.expense.service.impl.ExpenseServiceImpl;
import com.app.accounting.party.entity.PersonType;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.party.service.ThirdPartyService;
import com.app.accounting.puc.entity.Account;
import com.app.accounting.puc.entity.AccountNature;
import com.app.accounting.puc.service.AccountService;
import com.app.accounting.tax.dto.response.TaxCalculationResult;
import com.app.accounting.tax.dto.response.TaxItemDetail;
import com.app.accounting.tax.entity.TaxType;
import com.app.accounting.tax.service.TaxEngineService;
import com.app.accounting.treasury.entity.PaymentReceipt;
import com.app.accounting.treasury.repository.PaymentReceiptRepository;
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
class ExpenseServiceTest {

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
    private AccountService accountService;

    @Mock
    private PaymentReceiptRepository paymentReceiptRepository;

    @InjectMocks
    private ExpenseServiceImpl expenseService;


    private ThirdParty supplier;

    @BeforeEach
    void setUp() {
        supplier = ThirdParty.builder()
                .id(UUID.randomUUID())
                .identificationNumber("800123456")
                .fullName("DISTRIBUCIONES NACIONALES S.A.S.")
                .type(PersonType.JURIDICA)
                .build();
    }

    @Test
    @DisplayName("Debe registrar factura de compra, crear DebtDocument PAYABLE y causar asiento balanceado")
    void shouldCreatePurchaseInvoiceSuccessfully() {
        PurchaseInvoiceRequest request = PurchaseInvoiceRequest.builder()
                .documentNumber("COMP-540")
                .thirdPartyId(supplier.getId())
                .issueDate(LocalDate.of(2026, 9, 2))
                .dueDate(LocalDate.of(2026, 10, 2))
                .expenseAccountCode("513505")
                .subtotal(new BigDecimal("2000000.0000"))
                .taxTemplateIds(List.of(UUID.randomUUID()))
                .build();

        when(debtDocumentRepository.existsByDocumentNumber("COMP-540")).thenReturn(false);
        when(thirdPartyService.getThirdPartyEntityById(supplier.getId())).thenReturn(supplier);

        TaxCalculationResult taxResult = TaxCalculationResult.builder()
                .subtotal(new BigDecimal("2000000.0000"))
                .taxTotal(new BigDecimal("380000.0000"))    // IVA 19%
                .retentionTotal(new BigDecimal("50000.0000")) // Retefuente 2.5%
                .totalAmount(new BigDecimal("2330000.0000"))  // 2,000,000 + 380,000 - 50,000
                .items(List.of(
                        TaxItemDetail.builder()
                                .name("IVA Descontable 19%")
                                .taxType(TaxType.IVA)
                                .ratePercentage(new BigDecimal("19.0000"))
                                .taxAmount(new BigDecimal("380000.0000"))
                                .accountCode("240810")
                                .accountNature(AccountNature.DEBIT)
                                .isRetention(false)
                                .build(),
                        TaxItemDetail.builder()
                                .name("Retefuente Compras 2.5%")
                                .taxType(TaxType.RETEFUENTE)
                                .ratePercentage(new BigDecimal("2.5000"))
                                .taxAmount(new BigDecimal("50000.0000"))
                                .accountCode("236540")
                                .accountNature(AccountNature.CREDIT)
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
                .voucherNumber("CP-COMP-540")
                .voucherType(VoucherType.NOTA_CONTABLE)
                .status(VoucherStatus.POSTED)
                .totalDebit(new BigDecimal("2380000.0000"))
                .totalCredit(new BigDecimal("2380000.0000"))
                .build();

        when(voucherService.createAndPostVoucher(any(JournalVoucherRequest.class))).thenReturn(mockVoucherResp);
        when(voucherRepository.findById(voucherId)).thenReturn(Optional.empty());

        PurchaseInvoiceResponse response = expenseService.createPurchaseInvoice(request);

        assertThat(response).isNotNull();
        assertThat(response.getDebtDocument().getDocumentNumber()).isEqualTo("COMP-540");
        assertThat(response.getDebtDocument().getType()).isEqualTo(DebtType.PAYABLE);
        assertThat(response.getDebtDocument().getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(response.getDebtDocument().getTotalAmount()).isEqualByComparingTo(new BigDecimal("2330000.0000"));

        // Verificar el comprobante de causación
        ArgumentCaptor<JournalVoucherRequest> captor = ArgumentCaptor.forClass(JournalVoucherRequest.class);
        verify(voucherService).createAndPostVoucher(captor.capture());
        JournalVoucherRequest sentVoucher = captor.getValue();

        assertThat(sentVoucher.getVoucherNumber()).isEqualTo("CP-COMP-540");
        assertThat(sentVoucher.getEntries()).hasSize(4); // Gasto 513505 (deb), IVA 240810 (deb), Retefuente 236540 (cred), Proveedores 220505 (cred)

        BigDecimal totalDebits = sentVoucher.getEntries().stream()
                .map(e -> e.getDebit() != null ? e.getDebit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = sentVoucher.getEntries().stream()
                .map(e -> e.getCredit() != null ? e.getCredit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(totalDebits).isEqualByComparingTo(new BigDecimal("2380000.0000"));
        assertThat(totalCredits).isEqualByComparingTo(new BigDecimal("2380000.0000"));
        assertThat(totalDebits).isEqualByComparingTo(totalCredits); // Partida doble balanceada
    }

    @Test
    @DisplayName("Debe crear Documento Equivalente a crédito como comprobante de causación (NOTA_CONTABLE)")
    void shouldCreateEquivalentDocumentAsAccrualVoucherSuccessfully() {
        EquivalentDocumentRequest request = EquivalentDocumentRequest.builder()
                .documentNumber("DS-001")
                .thirdPartyId(supplier.getId())
                .issueDate(LocalDate.of(2026, 9, 10))
                .dueDate(LocalDate.of(2026, 9, 25))
                .expenseAccountCode("513505")
                .subtotal(new BigDecimal("500000.0000"))
                .taxTemplateIds(List.of(UUID.randomUUID()))
                .description("Cuenta de cobro por servicios técnicos")
                .payImmediately(false)
                .build();

        when(debtDocumentRepository.existsByDocumentNumber("DS-001")).thenReturn(false);
        when(thirdPartyService.getThirdPartyEntityById(supplier.getId())).thenReturn(supplier);

        TaxCalculationResult taxResult = TaxCalculationResult.builder()
                .subtotal(new BigDecimal("500000.0000"))
                .taxTotal(BigDecimal.ZERO)
                .retentionTotal(new BigDecimal("20000.0000")) // Retefuente 4%
                .totalAmount(new BigDecimal("480000.0000"))   // 500,000 - 20,000
                .items(List.of(
                        TaxItemDetail.builder()
                                .name("Retefuente Servicios 4%")
                                .taxType(TaxType.RETEFUENTE)
                                .ratePercentage(new BigDecimal("4.0000"))
                                .taxAmount(new BigDecimal("20000.0000"))
                                .accountCode("236540")
                                .accountNature(AccountNature.CREDIT)
                                .isRetention(true)
                                .build()
                ))
                .build();

        when(taxEngineService.calculateTaxes(any())).thenReturn(taxResult);
        when(debtDocumentRepository.save(any(DebtDocument.class))).thenAnswer(inv -> {
            DebtDocument doc = inv.getArgument(0);
            if (doc.getId() == null) doc.setId(UUID.randomUUID());
            return doc;
        });

        UUID voucherId = UUID.randomUUID();
        JournalVoucherResponse mockVoucherResp = JournalVoucherResponse.builder()
                .id(voucherId)
                .voucherNumber("CP-DS-001")
                .voucherType(VoucherType.NOTA_CONTABLE)
                .status(VoucherStatus.POSTED)
                .totalDebit(new BigDecimal("500000.0000"))
                .totalCredit(new BigDecimal("500000.0000"))
                .build();

        when(voucherService.createAndPostVoucher(any(JournalVoucherRequest.class))).thenReturn(mockVoucherResp);
        when(voucherRepository.findById(voucherId)).thenReturn(Optional.empty());

        EquivalentDocumentResponse response = expenseService.createEquivalentDocument(request);

        assertThat(response).isNotNull();
        assertThat(response.getDocumentType()).isEqualTo("DOCUMENTO_EQUIVALENTE");
        assertThat(response.isPaidImmediately()).isFalse();
        assertThat(response.getDebtDocument().getDocumentNumber()).isEqualTo("DS-001");
        assertThat(response.getDebtDocument().getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(response.getDebtDocument().getTotalAmount()).isEqualByComparingTo(new BigDecimal("480000.0000"));
        assertThat(response.getDebtDocument().getRemainingBalance()).isEqualByComparingTo(new BigDecimal("480000.0000"));
        assertThat(response.getPaymentReceipt()).isNull();

        ArgumentCaptor<JournalVoucherRequest> captor = ArgumentCaptor.forClass(JournalVoucherRequest.class);
        verify(voucherService).createAndPostVoucher(captor.capture());
        JournalVoucherRequest sentVoucher = captor.getValue();

        assertThat(sentVoucher.getVoucherNumber()).isEqualTo("CP-DS-001");
        assertThat(sentVoucher.getVoucherType()).isEqualTo(VoucherType.NOTA_CONTABLE);
        assertThat(sentVoucher.getEntries()).hasSize(3); // Gasto 513505 (deb 500k), Rete 236540 (cred 20k), CXP 220505 (cred 480k)
    }

    @Test
    @DisplayName("Debe crear Documento Equivalente de contado como recibo / comprobante de egreso directo (EGRESO)")
    void shouldCreateEquivalentDocumentAsImmediateCashReceiptSuccessfully() {
        EquivalentDocumentRequest request = EquivalentDocumentRequest.builder()
                .documentNumber("DS-002")
                .thirdPartyId(supplier.getId())
                .issueDate(LocalDate.of(2026, 9, 12))
                .expenseAccountCode("513505")
                .subtotal(new BigDecimal("300000.0000"))
                .description("Pago de transporte a no obligado a facturar")
                .payImmediately(true)
                .cashAccountCode("110505")
                .build();

        when(debtDocumentRepository.existsByDocumentNumber("DS-002")).thenReturn(false);
        when(thirdPartyService.getThirdPartyEntityById(supplier.getId())).thenReturn(supplier);

        TaxCalculationResult taxResult = TaxCalculationResult.builder()
                .subtotal(new BigDecimal("300000.0000"))
                .taxTotal(BigDecimal.ZERO)
                .retentionTotal(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("300000.0000"))
                .items(List.of())
                .build();

        when(taxEngineService.calculateTaxes(any())).thenReturn(taxResult);
        when(debtDocumentRepository.save(any(DebtDocument.class))).thenAnswer(inv -> {
            DebtDocument doc = inv.getArgument(0);
            if (doc.getId() == null) doc.setId(UUID.randomUUID());
            return doc;
        });

        Account cashAccount = Account.builder()
                .id(UUID.randomUUID())
                .code("110505")
                .name("CAJA GENERAL")
                .build();
        when(accountService.getAccountEntityByCode("110505")).thenReturn(cashAccount);

        UUID voucherId = UUID.randomUUID();
        JournalVoucherResponse mockVoucherResp = JournalVoucherResponse.builder()
                .id(voucherId)
                .voucherNumber("CE-DS-002")
                .voucherType(VoucherType.EGRESO)
                .status(VoucherStatus.POSTED)
                .totalDebit(new BigDecimal("300000.0000"))
                .totalCredit(new BigDecimal("300000.0000"))
                .build();

        when(voucherService.createAndPostVoucher(any(JournalVoucherRequest.class))).thenReturn(mockVoucherResp);
        when(voucherRepository.findById(voucherId)).thenReturn(Optional.empty());

        when(paymentReceiptRepository.save(any(PaymentReceipt.class))).thenAnswer(inv -> {
            PaymentReceipt r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });

        EquivalentDocumentResponse response = expenseService.createEquivalentDocument(request);

        assertThat(response).isNotNull();
        assertThat(response.isPaidImmediately()).isTrue();
        assertThat(response.getDebtDocument().getDocumentNumber()).isEqualTo("DS-002");
        assertThat(response.getDebtDocument().getStatus()).isEqualTo(DocumentStatus.PAID);
        assertThat(response.getDebtDocument().getRemainingBalance()).isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(response.getPaymentReceipt()).isNotNull();
        assertThat(response.getPaymentReceipt().getReceiptNumber()).isEqualTo("REC-DS-002");
        assertThat(response.getPaymentReceipt().getAmountPaid()).isEqualByComparingTo(new BigDecimal("300000.0000"));

        ArgumentCaptor<JournalVoucherRequest> captor = ArgumentCaptor.forClass(JournalVoucherRequest.class);
        verify(voucherService, atLeastOnce()).createAndPostVoucher(captor.capture());
        JournalVoucherRequest sentVoucher = captor.getValue();

        assertThat(sentVoucher.getVoucherNumber()).isEqualTo("CE-DS-002");
        assertThat(sentVoucher.getVoucherType()).isEqualTo(VoucherType.EGRESO);
        assertThat(sentVoucher.getEntries()).hasSize(2); // Gasto 513505 (deb 300k), Caja 110505 (cred 300k)
    }
}

