package com.app.accounting.treasury;

import com.app.accounting.billing.entity.DebtDocument;
import com.app.accounting.billing.entity.DebtType;
import com.app.accounting.billing.entity.DocumentStatus;
import com.app.accounting.billing.service.BillingService;
import com.app.accounting.party.entity.PersonType;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.puc.entity.Account;
import com.app.accounting.puc.entity.AccountNature;
import com.app.accounting.puc.service.AccountService;
import com.app.accounting.treasury.dto.request.PaymentReceiptRequest;
import com.app.accounting.treasury.dto.response.PaymentReceiptResponse;
import com.app.accounting.treasury.entity.PaymentReceipt;
import com.app.accounting.treasury.entity.PaymentType;
import com.app.accounting.treasury.repository.PaymentReceiptRepository;
import com.app.accounting.treasury.service.impl.TreasuryServiceImpl;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TreasuryServiceTest {

    @Mock
    private PaymentReceiptRepository paymentReceiptRepository;

    @Mock
    private BillingService billingService;

    @Mock
    private AccountService accountService;

    @Mock
    private VoucherService voucherService;

    @Mock
    private JournalVoucherRepository voucherRepository;

    @Mock
    private com.app.accounting.common.document.PdfExportService pdfExportService;

    @InjectMocks
    private TreasuryServiceImpl treasuryService;

    private ThirdParty thirdParty;
    private Account cashAccount;

    @BeforeEach
    void setUp() {
        thirdParty = ThirdParty.builder()
                .id(UUID.randomUUID())
                .identificationNumber("900123456")
                .fullName("COMERCIALIZADORA DEL VALLE S.A.S.")
                .type(PersonType.JURIDICA)
                .build();

        cashAccount = Account.builder()
                .id(UUID.randomUUID())
                .code("110505")
                .name("CAJA GENERAL")
                .nature(AccountNature.DEBIT)
                .build();
    }

    @Test
    @DisplayName("Debe procesar Recibo de Caja (CASH_IN), amortizar cartera y generar comprobante de Ingreso")
    void shouldProcessCashReceiptSuccessfully() {
        UUID docId = UUID.randomUUID();
        DebtDocument receivable = DebtDocument.builder()
                .id(docId)
                .type(DebtType.RECEIVABLE)
                .documentNumber("FAC-1001")
                .thirdParty(thirdParty)
                .totalAmount(new BigDecimal("1000000.0000"))
                .remainingBalance(new BigDecimal("1000000.0000"))
                .status(DocumentStatus.PENDING)
                .build();

        when(paymentReceiptRepository.existsByReceiptNumber("RC-8402")).thenReturn(false);
        when(billingService.getDebtDocumentEntity(docId)).thenReturn(receivable);
        when(accountService.getAccountEntityByCode("110505")).thenReturn(cashAccount);

        when(paymentReceiptRepository.save(any(PaymentReceipt.class))).thenAnswer(inv -> {
            PaymentReceipt r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });

        UUID voucherId = UUID.randomUUID();
        JournalVoucherResponse mockVoucherResp = JournalVoucherResponse.builder()
                .id(voucherId)
                .voucherNumber("RC-VOUCHER-RC-8402")
                .voucherType(VoucherType.INGRESO)
                .status(VoucherStatus.POSTED)
                .totalDebit(new BigDecimal("400000.0000"))
                .totalCredit(new BigDecimal("400000.0000"))
                .build();

        when(voucherService.createAndPostVoucher(any(JournalVoucherRequest.class))).thenReturn(mockVoucherResp);
        when(voucherRepository.findById(voucherId)).thenReturn(Optional.empty());

        PaymentReceiptRequest request = PaymentReceiptRequest.builder()
                .paymentType(PaymentType.CASH_IN)
                .receiptNumber("RC-8402")
                .paymentDate(LocalDate.of(2026, 9, 15))
                .debtDocumentId(docId)
                .cashAccountCode("110505")
                .amountPaid(new BigDecimal("400000.0000"))
                .build();

        PaymentReceiptResponse response = treasuryService.processPaymentReceipt(request);

        assertThat(response).isNotNull();
        assertThat(response.getReceiptNumber()).isEqualTo("RC-8402");
        assertThat(response.getPaymentType()).isEqualTo(PaymentType.CASH_IN);
        assertThat(response.getAmountPaid()).isEqualByComparingTo(new BigDecimal("400000.0000"));

        // Verificar que se llamó a amortizar saldo
        verify(billingService).updateRemainingBalance(receivable, new BigDecimal("400000.0000"));

        // Verificar el comprobante contable de INGRESO
        ArgumentCaptor<JournalVoucherRequest> captor = ArgumentCaptor.forClass(JournalVoucherRequest.class);
        verify(voucherService).createAndPostVoucher(captor.capture());
        JournalVoucherRequest sentVoucher = captor.getValue();

        assertThat(sentVoucher.getVoucherType()).isEqualTo(VoucherType.INGRESO);
        assertThat(sentVoucher.getEntries()).hasSize(2);
        // Débito 110505 (Caja), Crédito 130505 (Clientes)
        assertThat(sentVoucher.getEntries().get(0).getAccountCode()).isEqualTo("110505");
        assertThat(sentVoucher.getEntries().get(0).getDebit()).isEqualByComparingTo(new BigDecimal("400000.0000"));

        assertThat(sentVoucher.getEntries().get(1).getAccountCode()).isEqualTo("130505");
        assertThat(sentVoucher.getEntries().get(1).getCredit()).isEqualByComparingTo(new BigDecimal("400000.0000"));
    }

    @Test
    @DisplayName("Debe procesar Comprobante de Egreso (CASH_OUT), amortizar proveedor y generar comprobante de Egreso")
    void shouldProcessCashOutSuccessfully() {
        UUID docId = UUID.randomUUID();
        DebtDocument payable = DebtDocument.builder()
                .id(docId)
                .type(DebtType.PAYABLE)
                .documentNumber("COMP-540")
                .thirdParty(thirdParty)
                .totalAmount(new BigDecimal("2330000.0000"))
                .remainingBalance(new BigDecimal("2330000.0000"))
                .status(DocumentStatus.PENDING)
                .build();

        when(paymentReceiptRepository.existsByReceiptNumber("CE-1050")).thenReturn(false);
        when(billingService.getDebtDocumentEntity(docId)).thenReturn(payable);
        when(accountService.getAccountEntityByCode("110505")).thenReturn(cashAccount);

        when(paymentReceiptRepository.save(any(PaymentReceipt.class))).thenAnswer(inv -> {
            PaymentReceipt r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });

        UUID voucherId = UUID.randomUUID();
        JournalVoucherResponse mockVoucherResp = JournalVoucherResponse.builder()
                .id(voucherId)
                .voucherNumber("CE-VOUCHER-CE-1050")
                .voucherType(VoucherType.EGRESO)
                .status(VoucherStatus.POSTED)
                .totalDebit(new BigDecimal("1000000.0000"))
                .totalCredit(new BigDecimal("1000000.0000"))
                .build();

        when(voucherService.createAndPostVoucher(any(JournalVoucherRequest.class))).thenReturn(mockVoucherResp);
        when(voucherRepository.findById(voucherId)).thenReturn(Optional.empty());

        PaymentReceiptRequest request = PaymentReceiptRequest.builder()
                .paymentType(PaymentType.CASH_OUT)
                .receiptNumber("CE-1050")
                .paymentDate(LocalDate.of(2026, 9, 20))
                .debtDocumentId(docId)
                .cashAccountCode("110505")
                .amountPaid(new BigDecimal("1000000.0000"))
                .build();

        PaymentReceiptResponse response = treasuryService.processPaymentReceipt(request);

        assertThat(response).isNotNull();
        assertThat(response.getReceiptNumber()).isEqualTo("CE-1050");
        assertThat(response.getPaymentType()).isEqualTo(PaymentType.CASH_OUT);

        // Verificar amortización de proveedor
        verify(billingService).updateRemainingBalance(payable, new BigDecimal("1000000.0000"));

        // Verificar comprobante contable de EGRESO
        ArgumentCaptor<JournalVoucherRequest> captor = ArgumentCaptor.forClass(JournalVoucherRequest.class);
        verify(voucherService).createAndPostVoucher(captor.capture());
        JournalVoucherRequest sentVoucher = captor.getValue();

        assertThat(sentVoucher.getVoucherType()).isEqualTo(VoucherType.EGRESO);
        assertThat(sentVoucher.getEntries()).hasSize(2);
        // Débito 220505 (Proveedores), Crédito 110505 (Caja)
        assertThat(sentVoucher.getEntries().get(0).getAccountCode()).isEqualTo("220505");
        assertThat(sentVoucher.getEntries().get(0).getDebit()).isEqualByComparingTo(new BigDecimal("1000000.0000"));

        assertThat(sentVoucher.getEntries().get(1).getAccountCode()).isEqualTo("110505");
        assertThat(sentVoucher.getEntries().get(1).getCredit()).isEqualByComparingTo(new BigDecimal("1000000.0000"));
    }
}
