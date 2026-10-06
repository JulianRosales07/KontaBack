package com.app.accounting.voucher;

import com.app.accounting.puc.entity.Account;
import com.app.accounting.puc.entity.AccountNature;
import com.app.accounting.puc.service.AccountService;
import com.app.accounting.party.entity.PersonType;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.party.service.ThirdPartyService;
import com.app.accounting.voucher.dto.request.JournalEntryRequest;
import com.app.accounting.voucher.dto.request.JournalVoucherRequest;
import com.app.accounting.voucher.dto.response.JournalVoucherResponse;
import com.app.accounting.voucher.entity.JournalEntry;
import com.app.accounting.voucher.entity.JournalVoucher;
import com.app.accounting.voucher.entity.VoucherStatus;
import com.app.accounting.voucher.entity.VoucherType;
import com.app.accounting.voucher.exception.ImmutableVoucherException;
import com.app.accounting.voucher.exception.UnbalancedVoucherException;
import com.app.accounting.voucher.mapper.JournalVoucherMapper;
import com.app.accounting.voucher.repository.JournalVoucherRepository;
import com.app.accounting.voucher.service.impl.VoucherServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    @Mock
    private JournalVoucherRepository voucherRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private ThirdPartyService thirdPartyService;

    @Mock
    private com.app.accounting.closing.service.AccountingPeriodService accountingPeriodService;

    @Spy
    private JournalVoucherMapper voucherMapper = Mappers.getMapper(JournalVoucherMapper.class);

    @InjectMocks
    private VoucherServiceImpl voucherService;

    private Account cashAccount;
    private Account clientAccount;
    private ThirdParty customer;

    @BeforeEach
    void setUp() {
        cashAccount = Account.builder()
                .id(UUID.randomUUID())
                .code("110505")
                .name("CAJA GENERAL")
                .level(4)
                .nature(AccountNature.DEBIT)
                .requiresThirdParty(false)
                .isActive(true)
                .build();

        clientAccount = Account.builder()
                .id(UUID.randomUUID())
                .code("130505")
                .name("CLIENTES NACIONALES")
                .level(4)
                .nature(AccountNature.DEBIT)
                .requiresThirdParty(true)
                .isActive(true)
                .build();

        customer = ThirdParty.builder()
                .id(UUID.randomUUID())
                .identificationNumber("901234567")
                .fullName("CLIENTE EJEMPLO S.A.S.")
                .type(PersonType.JURIDICA)
                .build();
    }

    @Test
    @DisplayName("Debe guardar exitosamente un comprobante con partida doble cuadrada")
    void shouldCreateAndPostBalancedVoucherSuccessfully() {
        when(accountService.getAccountEntityByCode("110505")).thenReturn(cashAccount);
        when(accountService.getAccountEntityByCode("130505")).thenReturn(clientAccount);
        when(thirdPartyService.getThirdPartyEntityByIdentification("901234567")).thenReturn(customer);
        when(voucherRepository.existsByVoucherNumber("RC-001")).thenReturn(false);
        when(voucherRepository.save(any(JournalVoucher.class))).thenAnswer(invocation -> {
            JournalVoucher v = invocation.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        JournalVoucherRequest request = JournalVoucherRequest.builder()
                .voucherNumber("RC-001")
                .voucherType(VoucherType.INGRESO)
                .issueDate(LocalDate.now())
                .description("Recibo de caja pago cliente RB-8402")
                .entries(List.of(
                        JournalEntryRequest.builder()
                                .accountCode("110505")
                                .referenceNumber("RB-8402")
                                .description("Ingreso a caja")
                                .debit(new BigDecimal("1500000.0000"))
                                .credit(BigDecimal.ZERO)
                                .build(),
                        JournalEntryRequest.builder()
                                .accountCode("130505")
                                .thirdPartyIdentification("901234567")
                                .referenceNumber("RB-8402")
                                .description("Abono factura cliente")
                                .debit(BigDecimal.ZERO)
                                .credit(new BigDecimal("1500000.0000"))
                                .build()
                ))
                .build();

        JournalVoucherResponse response = voucherService.createAndPostVoucher(request);

        assertThat(response).isNotNull();
        assertThat(response.getVoucherNumber()).isEqualTo("RC-001");
        assertThat(response.getStatus()).isEqualTo(VoucherStatus.POSTED);
        assertThat(response.getTotalDebit()).isEqualByComparingTo(new BigDecimal("1500000.0000"));
        assertThat(response.getTotalCredit()).isEqualByComparingTo(new BigDecimal("1500000.0000"));

        verify(voucherRepository, times(1)).save(any(JournalVoucher.class));
    }

    @Test
    @DisplayName("Debe lanzar UnbalancedVoucherException cuando débitos no coinciden con créditos")
    void shouldThrowUnbalancedVoucherExceptionWhenDebitsDoNotMatchCredits() {
        when(accountService.getAccountEntityByCode("110505")).thenReturn(cashAccount);
        when(accountService.getAccountEntityByCode("130505")).thenReturn(clientAccount);
        when(thirdPartyService.getThirdPartyEntityByIdentification("901234567")).thenReturn(customer);
        when(voucherRepository.existsByVoucherNumber("RC-DESCUADRADO")).thenReturn(false);

        JournalVoucherRequest request = JournalVoucherRequest.builder()
                .voucherNumber("RC-DESCUADRADO")
                .voucherType(VoucherType.INGRESO)
                .issueDate(LocalDate.now())
                .description("Comprobante con descuadre")
                .entries(List.of(
                        JournalEntryRequest.builder()
                                .accountCode("110505")
                                .debit(new BigDecimal("2000000.0000"))
                                .credit(BigDecimal.ZERO)
                                .build(),
                        JournalEntryRequest.builder()
                                .accountCode("130505")
                                .thirdPartyIdentification("901234567")
                                .debit(BigDecimal.ZERO)
                                .credit(new BigDecimal("1800000.0000"))
                                .build()
                ))
                .build();

        assertThatThrownBy(() -> voucherService.createAndPostVoucher(request))
                .isInstanceOf(UnbalancedVoucherException.class)
                .hasMessageContaining("no cumple con la regla de partida doble");

        verify(voucherRepository, never()).save(any(JournalVoucher.class));
    }

    @Test
    @DisplayName("Debe revertir un comprobante POSTED creando la contrapartida y anulando el original")
    void shouldReversePostedVoucherCorrectly() {
        UUID voucherId = UUID.randomUUID();

        JournalVoucher original = JournalVoucher.builder()
                .id(voucherId)
                .voucherNumber("RC-ORIGINAL")
                .voucherType(VoucherType.INGRESO)
                .issueDate(LocalDate.now())
                .description("Pago cliente")
                .status(VoucherStatus.POSTED)
                .build();

        JournalEntry entry1 = JournalEntry.builder()
                .account(cashAccount)
                .debit(new BigDecimal("500000.0000"))
                .credit(BigDecimal.ZERO)
                .build();

        JournalEntry entry2 = JournalEntry.builder()
                .account(clientAccount)
                .thirdParty(customer)
                .debit(BigDecimal.ZERO)
                .credit(new BigDecimal("500000.0000"))
                .build();

        original.addEntry(entry1);
        original.addEntry(entry2);

        when(voucherRepository.findByIdWithEntries(voucherId)).thenReturn(Optional.of(original));
        when(voucherRepository.existsByVoucherNumber("REV-RC-ORIGINAL")).thenReturn(false);
        when(voucherRepository.save(any(JournalVoucher.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JournalVoucherResponse reversal = voucherService.reverseVoucher(voucherId, "Error en concepto");

        assertThat(reversal).isNotNull();
        assertThat(reversal.getVoucherNumber()).isEqualTo("REV-RC-ORIGINAL");
        assertThat(reversal.getStatus()).isEqualTo(VoucherStatus.POSTED);
        assertThat(original.getStatus()).isEqualTo(VoucherStatus.VOIDED);
    }

    @Test
    @DisplayName("Debe impedir la eliminación de un comprobante asentado (POSTED)")
    void shouldPreventDeletingPostedVoucher() {
        UUID voucherId = UUID.randomUUID();
        JournalVoucher postedVoucher = JournalVoucher.builder()
                .id(voucherId)
                .voucherNumber("RC-CERRADO")
                .status(VoucherStatus.POSTED)
                .build();

        when(voucherRepository.findById(voucherId)).thenReturn(Optional.of(postedVoucher));

        assertThatThrownBy(() -> voucherService.deleteDraftVoucher(voucherId))
                .isInstanceOf(ImmutableVoucherException.class)
                .hasMessageContaining("inmutable");

        verify(voucherRepository, never()).delete(any(JournalVoucher.class));
    }
}
