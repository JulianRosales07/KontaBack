package com.app.accounting.report;

import com.app.accounting.puc.dto.response.AccountResponse;
import com.app.accounting.puc.entity.AccountNature;
import com.app.accounting.puc.service.AccountService;
import com.app.accounting.report.dto.response.TrialBalanceItemResponse;
import com.app.accounting.report.dto.response.TrialBalanceResponse;
import com.app.accounting.report.service.impl.ReportServiceImpl;
import com.app.accounting.voucher.repository.AccountBalanceProjection;
import com.app.accounting.voucher.repository.JournalEntryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrialBalanceReportTest {

    @Mock
    private JournalEntryRepository journalEntryRepository;

    @Mock
    private AccountService accountService;

    @InjectMocks
    private ReportServiceImpl reportService;

    @Test
    @DisplayName("Debe calcular correctamente el Balance de Prueba respetando naturalezas DEBIT y CREDIT")
    void shouldCalculateTrialBalanceCorrectly() {
        LocalDate startDate = LocalDate.of(2026, 2, 1);
        LocalDate endDate = LocalDate.of(2026, 2, 28);

        UUID cashId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();

        // Proyección de saldos anteriores (antes del 1 de feb)
        AccountBalanceProjection priorCash = mockProjection(cashId, "110505", "CAJA GENERAL", "DEBIT", new BigDecimal("1000.00"), new BigDecimal("200.00"));
        AccountBalanceProjection priorSupplier = mockProjection(supplierId, "220505", "PROVEEDORES", "CREDIT", new BigDecimal("300.00"), new BigDecimal("1500.00"));

        // Proyección de movimientos del periodo (febrero)
        AccountBalanceProjection periodCash = mockProjection(cashId, "110505", "CAJA GENERAL", "DEBIT", new BigDecimal("500.00"), new BigDecimal("100.00"));
        AccountBalanceProjection periodSupplier = mockProjection(supplierId, "220505", "PROVEEDORES", "CREDIT", new BigDecimal("400.00"), new BigDecimal("800.00"));

        when(journalEntryRepository.findAccountBalancesBeforeDate(startDate)).thenReturn(List.of(priorCash, priorSupplier));
        when(journalEntryRepository.findAccountBalancesBetweenDates(startDate, endDate)).thenReturn(List.of(periodCash, periodSupplier));
        when(accountService.getAllActiveAccounts()).thenReturn(List.of(
                AccountResponse.builder().id(cashId).code("110505").name("CAJA GENERAL").nature(AccountNature.DEBIT).build(),
                AccountResponse.builder().id(supplierId).code("220505").name("PROVEEDORES").nature(AccountNature.CREDIT).build()
        ));

        TrialBalanceResponse report = reportService.generateTrialBalance(startDate, endDate);

        assertThat(report).isNotNull();
        assertThat(report.getItems()).hasSize(2);

        // 1. Verificación Cuenta Débito (Caja):
        // Saldo inicial = 1000 - 200 = 800
        // Movimientos = +500 (deb) - 100 (cred)
        // Saldo final = 800 + 500 - 100 = 1200
        TrialBalanceItemResponse cashItem = report.getItems().stream()
                .filter(i -> i.getAccountCode().equals("110505"))
                .findFirst()
                .orElseThrow();

        assertThat(cashItem.getInitialBalance()).isEqualByComparingTo(new BigDecimal("800.0000"));
        assertThat(cashItem.getPeriodDebit()).isEqualByComparingTo(new BigDecimal("500.0000"));
        assertThat(cashItem.getPeriodCredit()).isEqualByComparingTo(new BigDecimal("100.0000"));
        assertThat(cashItem.getFinalBalance()).isEqualByComparingTo(new BigDecimal("1200.0000"));

        // 2. Verificación Cuenta Crédito (Proveedores):
        // Saldo inicial = 1500 - 300 = 1200
        // Movimientos = -400 (deb) + 800 (cred)
        // Saldo final = 1200 + 800 - 400 = 1600
        TrialBalanceItemResponse supplierItem = report.getItems().stream()
                .filter(i -> i.getAccountCode().equals("220505"))
                .findFirst()
                .orElseThrow();

        assertThat(supplierItem.getInitialBalance()).isEqualByComparingTo(new BigDecimal("1200.0000"));
        assertThat(supplierItem.getPeriodDebit()).isEqualByComparingTo(new BigDecimal("400.0000"));
        assertThat(supplierItem.getPeriodCredit()).isEqualByComparingTo(new BigDecimal("800.0000"));
        assertThat(supplierItem.getFinalBalance()).isEqualByComparingTo(new BigDecimal("1600.0000"));
    }

    private AccountBalanceProjection mockProjection(UUID id, String code, String name, String nature, BigDecimal debit, BigDecimal credit) {
        return new AccountBalanceProjection() {
            @Override public UUID getAccountId() { return id; }
            @Override public String getAccountCode() { return code; }
            @Override public String getAccountName() { return name; }
            @Override public String getAccountNature() { return nature; }
            @Override public BigDecimal getTotalDebit() { return debit; }
            @Override public BigDecimal getTotalCredit() { return credit; }
        };
    }
}
