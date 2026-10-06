package com.app.accounting.closing;

import com.app.accounting.closing.dto.response.AnnualClosingResponse;
import com.app.accounting.closing.service.AccountingPeriodService;
import com.app.accounting.closing.service.impl.AnnualClosingServiceImpl;
import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.puc.entity.Account;
import com.app.accounting.puc.entity.AccountNature;
import com.app.accounting.puc.repository.AccountRepository;
import com.app.accounting.voucher.entity.JournalEntry;
import com.app.accounting.voucher.entity.JournalVoucher;
import com.app.accounting.voucher.entity.VoucherStatus;
import com.app.accounting.voucher.repository.AccountBalanceProjection;
import com.app.accounting.voucher.repository.JournalEntryRepository;
import com.app.accounting.voucher.repository.JournalVoucherRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class AnnualClosingServiceTest {

    @Mock
    private JournalVoucherRepository voucherRepository;

    @Mock
    private JournalEntryRepository entryRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountingPeriodService periodService;

    @InjectMocks
    private AnnualClosingServiceImpl annualClosingService;

    private Account acc5905;
    private Account acc3605;
    private Account acc3610;
    private Account acc4135;
    private Account acc5105;
    private Account acc6135;

    @BeforeEach
    void setUp() {
        acc5905 = Account.builder().id(UUID.randomUUID()).code("5905").name("GANANCIAS Y PERDIDAS").nature(AccountNature.DEBIT).build();
        acc3605 = Account.builder().id(UUID.randomUUID()).code("3605").name("UTILIDAD DEL EJERCICIO").nature(AccountNature.CREDIT).build();
        acc3610 = Account.builder().id(UUID.randomUUID()).code("3610").name("PERDIDA DEL EJERCICIO").nature(AccountNature.DEBIT).build();

        acc4135 = Account.builder().id(UUID.randomUUID()).code("413505").name("COMERCIO AL POR MAYOR Y MENOR").nature(AccountNature.CREDIT).build();
        acc5105 = Account.builder().id(UUID.randomUUID()).code("510506").name("SUELDOS Y SALARIOS").nature(AccountNature.DEBIT).build();
        acc6135 = Account.builder().id(UUID.randomUUID()).code("613505").name("COSTO DE VENTAS").nature(AccountNature.DEBIT).build();

        lenient().when(accountRepository.findByCode("5905")).thenReturn(Optional.of(acc5905));
        lenient().when(accountRepository.findByCode("3605")).thenReturn(Optional.of(acc3605));
        lenient().when(accountRepository.findByCode("3610")).thenReturn(Optional.of(acc3610));
    }

    @Test
    @DisplayName("Debe ejecutar cierre anual con Utilidad neta (Ingresos > Gastos + Costos) y trasladar a cuenta 3605")
    void shouldExecuteAnnualClosingWithNetProfit() {
        int year = 2025;
        when(voucherRepository.existsByVoucherNumber("CIERRE-" + year)).thenReturn(false);

        // Simulamos saldos del año:
        // Ingresos (413505): Crédito $100.000.000
        // Gastos (510506): Débito $40.000.000
        // Costos (613505): Débito $35.000.000
        // Utilidad esperada: $100M - ($40M + $35M) = $25.000.000
        AccountBalanceProjection p4 = mockBalance(acc4135.getId(), "413505", "COMERCIO", BigDecimal.ZERO, new BigDecimal("100000000.0000"));
        AccountBalanceProjection p5 = mockBalance(acc5105.getId(), "510506", "SUELDOS", new BigDecimal("40000000.0000"), BigDecimal.ZERO);
        AccountBalanceProjection p6 = mockBalance(acc6135.getId(), "613505", "COSTOS", new BigDecimal("35000000.0000"), BigDecimal.ZERO);

        when(entryRepository.findAccountBalancesBetweenDates(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)))
                .thenReturn(List.of(p4, p5, p6));

        when(accountRepository.findById(acc4135.getId())).thenReturn(Optional.of(acc4135));
        when(accountRepository.findById(acc5105.getId())).thenReturn(Optional.of(acc5105));
        when(accountRepository.findById(acc6135.getId())).thenReturn(Optional.of(acc6135));

        when(voucherRepository.save(any(JournalVoucher.class))).thenAnswer(invocation -> {
            JournalVoucher v = invocation.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        // Ejecutar cierre
        AnnualClosingResponse response = annualClosingService.executeYearEndClosing(year);

        // Verificaciones del resultado
        assertThat(response).isNotNull();
        assertThat(response.getYear()).isEqualTo(year);
        assertThat(response.getResultType()).isEqualTo("UTILIDAD");
        assertThat(response.getDestinationAccountCode()).isEqualTo("3605");
        assertThat(response.getTotalRevenues()).isEqualByComparingTo("100000000.0000");
        assertThat(response.getTotalExpensesAndCosts()).isEqualByComparingTo("75000000.0000");
        assertThat(response.getNetResult()).isEqualByComparingTo("25000000.0000");

        // Capturar comprobante generado para verificar partida doble estricta
        ArgumentCaptor<JournalVoucher> voucherCaptor = ArgumentCaptor.forClass(JournalVoucher.class);
        verify(voucherRepository).save(voucherCaptor.capture());

        JournalVoucher savedVoucher = voucherCaptor.getValue();
        assertThat(savedVoucher.getStatus()).isEqualTo(VoucherStatus.POSTED);
        assertThat(savedVoucher.getVoucherNumber()).isEqualTo("CIERRE-2025");
        assertThat(savedVoucher.getIssueDate()).isEqualTo(LocalDate.of(2025, 12, 31));

        // Validación de Partida Doble en el comprobante de cierre
        BigDecimal sumDebits = savedVoucher.getEntries().stream().map(JournalEntry::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumCredits = savedVoucher.getEntries().stream().map(JournalEntry::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumDebits).isEqualByComparingTo(sumCredits);

        // Verificar que los 12 periodos mensuales fueron cerrados
        verify(periodService, times(12)).closePeriod(eq(year), anyInt(), eq("CIERRE_ANUAL_SISTEMA"));
    }

    @Test
    @DisplayName("Debe ejecutar cierre anual con Pérdida neta (Gastos + Costos > Ingresos) y trasladar a cuenta 3610")
    void shouldExecuteAnnualClosingWithNetLoss() {
        int year = 2024;
        when(voucherRepository.existsByVoucherNumber("CIERRE-" + year)).thenReturn(false);

        // Ingresos: $50.000.000
        // Gastos: $70.000.000
        // Pérdida esperada: -$20.000.000
        AccountBalanceProjection p4 = mockBalance(acc4135.getId(), "413505", "COMERCIO", BigDecimal.ZERO, new BigDecimal("50000000.0000"));
        AccountBalanceProjection p5 = mockBalance(acc5105.getId(), "510506", "SUELDOS", new BigDecimal("70000000.0000"), BigDecimal.ZERO);

        when(entryRepository.findAccountBalancesBetweenDates(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)))
                .thenReturn(List.of(p4, p5));

        when(accountRepository.findById(acc4135.getId())).thenReturn(Optional.of(acc4135));
        when(accountRepository.findById(acc5105.getId())).thenReturn(Optional.of(acc5105));

        when(voucherRepository.save(any(JournalVoucher.class))).thenAnswer(invocation -> {
            JournalVoucher v = invocation.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        AnnualClosingResponse response = annualClosingService.executeYearEndClosing(year);

        assertThat(response).isNotNull();
        assertThat(response.getResultType()).isEqualTo("PERDIDA");
        assertThat(response.getDestinationAccountCode()).isEqualTo("3610");
        assertThat(response.getNetResult()).isEqualByComparingTo("-20000000.0000");

        ArgumentCaptor<JournalVoucher> voucherCaptor = ArgumentCaptor.forClass(JournalVoucher.class);
        verify(voucherRepository).save(voucherCaptor.capture());

        JournalVoucher savedVoucher = voucherCaptor.getValue();
        BigDecimal sumDebits = savedVoucher.getEntries().stream().map(JournalEntry::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumCredits = savedVoucher.getEntries().stream().map(JournalEntry::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumDebits).isEqualByComparingTo(sumCredits);
    }

    @Test
    @DisplayName("Debe rechazar la ejecución si el cierre anual del año ya fue procesado")
    void shouldRejectDuplicateAnnualClosing() {
        int year = 2025;
        when(voucherRepository.existsByVoucherNumber("CIERRE-" + year)).thenReturn(true);

        assertThatThrownBy(() -> annualClosingService.executeYearEndClosing(year))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya fue ejecutado previamente");

        verify(voucherRepository, never()).save(any());
    }

    private AccountBalanceProjection mockBalance(UUID id, String code, String name, BigDecimal debit, BigDecimal credit) {
        AccountBalanceProjection p = mock(AccountBalanceProjection.class);
        when(p.getAccountId()).thenReturn(id);
        when(p.getAccountCode()).thenReturn(code);
        when(p.getAccountName()).thenReturn(name);
        when(p.getTotalDebit()).thenReturn(debit);
        when(p.getTotalCredit()).thenReturn(credit);
        return p;
    }
}
