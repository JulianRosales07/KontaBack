package com.app.accounting.closing;

import com.app.accounting.closing.dto.response.AccountingPeriodResponse;
import com.app.accounting.closing.entity.AccountingPeriod;
import com.app.accounting.closing.entity.PeriodStatus;
import com.app.accounting.closing.repository.AccountingPeriodRepository;
import com.app.accounting.closing.service.impl.AccountingPeriodServiceImpl;
import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.puc.entity.Account;
import com.app.accounting.puc.entity.AccountNature;
import com.app.accounting.voucher.dto.response.JournalVoucherResponse;
import com.app.accounting.voucher.entity.JournalEntry;
import com.app.accounting.voucher.entity.JournalVoucher;
import com.app.accounting.voucher.entity.VoucherStatus;
import com.app.accounting.voucher.entity.VoucherType;
import com.app.accounting.voucher.mapper.JournalVoucherMapper;
import com.app.accounting.voucher.repository.JournalVoucherRepository;
import com.app.accounting.voucher.service.impl.VoucherServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountingPeriodAndReversalTest {

    @Mock
    private AccountingPeriodRepository periodRepository;

    @InjectMocks
    private AccountingPeriodServiceImpl periodService;

    @Mock
    private JournalVoucherRepository voucherRepository;

    @Spy
    private JournalVoucherMapper voucherMapper = Mappers.getMapper(JournalVoucherMapper.class);

    @Mock
    private com.app.accounting.fine.AssociateFineRepository fineRepository;

    private VoucherServiceImpl voucherService;

    @BeforeEach
    void setUp() {
        voucherService = new VoucherServiceImpl(
                voucherRepository,
                null,
                null,
                voucherMapper,
                periodService,
                fineRepository
        );
    }

    @Test
    @DisplayName("Debe abrir y cerrar un periodo contable mensual correctamente")
    void shouldOpenAndClosePeriod() {
        when(periodRepository.findByYearAndMonth(2025, 3)).thenReturn(Optional.empty());
        when(periodRepository.save(any(AccountingPeriod.class))).thenAnswer(i -> {
            AccountingPeriod p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        AccountingPeriodResponse opened = periodService.openPeriod(2025, 3);
        assertThat(opened.getStatus()).isEqualTo(PeriodStatus.OPEN);

        AccountingPeriodResponse closed = periodService.closePeriod(2025, 3, "admin");
        assertThat(closed.getStatus()).isEqualTo(PeriodStatus.CLOSED);
        assertThat(closed.getClosedBy()).isEqualTo("admin");
    }

    @Test
    @DisplayName("Debe lanzar BusinessRuleException al validar un periodo contable CERRADO")
    void shouldThrowExceptionWhenPeriodIsClosed() {
        AccountingPeriod closedPeriod = AccountingPeriod.builder()
                .year(2025)
                .month(1)
                .status(PeriodStatus.CLOSED)
                .build();

        when(periodRepository.findByYearAndMonth(2025, 1)).thenReturn(Optional.of(closedPeriod));

        assertThatThrownBy(() -> periodService.validatePeriodIsOpen(LocalDate.of(2025, 1, 15)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("se encuentra CERRADO");
    }

    @Test
    @DisplayName("Debe revertir un comprobante generando una NOTA_CONTABLE con valores Débito/Crédito invertidos y marcar VOIDED")
    void shouldReversePostedVoucherSuccessfully() {
        UUID voucherId = UUID.randomUUID();
        Account cash = Account.builder().id(UUID.randomUUID()).code("110505").name("CAJA").nature(AccountNature.DEBIT).build();
        Account income = Account.builder().id(UUID.randomUUID()).code("413505").name("VENTAS").nature(AccountNature.CREDIT).build();
        ThirdParty party = ThirdParty.builder().id(UUID.randomUUID()).fullName("CLIENTE DEMO").build();

        JournalVoucher original = JournalVoucher.builder()
                .id(voucherId)
                .voucherNumber("RC-2025-001")
                .voucherType(VoucherType.INGRESO)
                .issueDate(LocalDate.now())
                .description("Venta de contado")
                .status(VoucherStatus.POSTED)
                .entries(new ArrayList<>())
                .build();

        JournalEntry entry1 = JournalEntry.builder()
                .account(cash)
                .thirdParty(party)
                .debit(new BigDecimal("50000.0000"))
                .credit(BigDecimal.ZERO)
                .build();

        JournalEntry entry2 = JournalEntry.builder()
                .account(income)
                .thirdParty(party)
                .debit(BigDecimal.ZERO)
                .credit(new BigDecimal("50000.0000"))
                .build();

        original.addEntry(entry1);
        original.addEntry(entry2);

        when(voucherRepository.findByIdWithEntries(voucherId)).thenReturn(Optional.of(original));
        when(voucherRepository.existsByVoucherNumber("REV-RC-2025-001")).thenReturn(false);
        when(voucherRepository.save(any(JournalVoucher.class))).thenAnswer(i -> {
            JournalVoucher v = i.getArgument(0);
            if (v.getId() == null) v.setId(UUID.randomUUID());
            return v;
        });

        // Ejecutar reversión
        JournalVoucherResponse response = voucherService.reverseVoucher(voucherId, "Error en registro inicial");

        assertThat(response).isNotNull();
        assertThat(response.getVoucherType()).isEqualTo(VoucherType.NOTA_CONTABLE);
        assertThat(response.getVoucherNumber()).isEqualTo("REV-RC-2025-001");
        assertThat(response.getStatus()).isEqualTo(VoucherStatus.POSTED);

        // Verificar que el original ahora está VOIDED y tiene nota cruzada
        assertThat(original.getStatus()).isEqualTo(VoucherStatus.VOIDED);
        assertThat(original.getDescription()).contains("[REVERTIDO POR COMPROBANTE REV-RC-2025-001]");

        // Capturar los vouchers guardados
        ArgumentCaptor<JournalVoucher> captor = ArgumentCaptor.forClass(JournalVoucher.class);
        verify(voucherRepository, times(2)).save(captor.capture());

        JournalVoucher reversalVoucher = captor.getAllValues().get(1);

        // Verificar que las entradas se invirtieron exactamente:
        // Cash original (Debit 50.000, Credit 0) -> Contrapartida (Debit 0, Credit 50.000)
        // Income original (Debit 0, Credit 50.000) -> Contrapartida (Debit 50.000, Credit 0)
        JournalEntry rev1 = reversalVoucher.getEntries().get(0);
        assertThat(rev1.getDebit()).isEqualByComparingTo("0.0000");
        assertThat(rev1.getCredit()).isEqualByComparingTo("50000.0000");

        JournalEntry rev2 = reversalVoucher.getEntries().get(1);
        assertThat(rev2.getDebit()).isEqualByComparingTo("50000.0000");
        assertThat(rev2.getCredit()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("Debe impedir revertir un comprobante si su estado no es POSTED")
    void shouldRejectReversingNonPostedVoucher() {
        UUID voucherId = UUID.randomUUID();
        JournalVoucher draftVoucher = JournalVoucher.builder()
                .id(voucherId)
                .voucherNumber("DRF-001")
                .status(VoucherStatus.DRAFT)
                .build();

        when(voucherRepository.findByIdWithEntries(voucherId)).thenReturn(Optional.of(draftVoucher));

        assertThatThrownBy(() -> voucherService.reverseVoucher(voucherId, "Intento de reversión"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Solo se pueden revertir comprobantes en estado asentado");
    }
}
