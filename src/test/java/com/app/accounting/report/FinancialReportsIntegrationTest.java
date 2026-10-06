package com.app.accounting.report;

import com.app.accounting.party.entity.PersonType;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.party.service.ThirdPartyService;
import com.app.accounting.puc.entity.Account;
import com.app.accounting.puc.entity.AccountNature;
import com.app.accounting.puc.service.AccountService;
import com.app.accounting.report.dto.response.*;
import com.app.accounting.report.service.impl.ReportServiceImpl;
import com.app.accounting.voucher.entity.JournalEntry;
import com.app.accounting.voucher.entity.JournalVoucher;
import com.app.accounting.voucher.entity.VoucherStatus;
import com.app.accounting.voucher.entity.VoucherType;
import com.app.accounting.voucher.repository.AccountBalanceProjection;
import com.app.accounting.voucher.repository.JournalEntryRepository;
import com.app.accounting.voucher.repository.PartyAccountBalanceProjection;
import com.app.accounting.voucher.repository.PartyBalanceProjection;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialReportsIntegrationTest {

    @Mock
    private JournalEntryRepository journalEntryRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private ThirdPartyService thirdPartyService;

    @InjectMocks
    private ReportServiceImpl reportService;

    @Test
    @DisplayName("Debe generar Auxiliar por Tercero y Cuenta (Kárdex de Cartera) con saldos acumulados")
    void shouldGeneratePartyAuxiliaryCorrectly() {
        UUID accountId = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        LocalDate startDate = LocalDate.of(2026, 9, 1);
        LocalDate endDate = LocalDate.of(2026, 9, 30);

        Account clientAccount = Account.builder()
                .id(accountId)
                .code("130505")
                .name("CLIENTES NACIONALES")
                .nature(AccountNature.DEBIT)
                .build();

        ThirdParty party = ThirdParty.builder()
                .id(partyId)
                .identificationNumber("901234567")
                .fullName("SERVICIOS ANDINOS S.A.S.")
                .type(PersonType.JURIDICA)
                .build();

        when(accountService.getAccountEntityById(accountId)).thenReturn(clientAccount);
        when(thirdPartyService.getThirdPartyEntityById(partyId)).thenReturn(party);

        // Saldo previo al 1 de septiembre: Débito 500k, Crédito 100k -> Saldo Inicial = 400k
        PartyBalanceProjection priorProjection = new PartyBalanceProjection() {
            @Override public BigDecimal getTotalDebit() { return new BigDecimal("500000.0000"); }
            @Override public BigDecimal getTotalCredit() { return new BigDecimal("100000.0000"); }
        };
        when(journalEntryRepository.findPartyBalanceBeforeDate(accountId, partyId, startDate))
                .thenReturn(priorProjection);

        JournalVoucher voucher1 = JournalVoucher.builder()
                .voucherNumber("CV-FAC-101")
                .issueDate(LocalDate.of(2026, 9, 5))
                .status(VoucherStatus.POSTED)
                .voucherType(VoucherType.NOTA_CONTABLE)
                .build();

        JournalEntry entry1 = JournalEntry.builder()
                .voucher(voucher1)
                .account(clientAccount)
                .thirdParty(party)
                .referenceNumber("FAC-101")
                .description("Venta a crédito")
                .debit(new BigDecimal("600000.0000"))
                .credit(BigDecimal.ZERO)
                .build();

        JournalVoucher voucher2 = JournalVoucher.builder()
                .voucherNumber("RC-RC-501")
                .issueDate(LocalDate.of(2026, 9, 15))
                .status(VoucherStatus.POSTED)
                .voucherType(VoucherType.INGRESO)
                .build();

        JournalEntry entry2 = JournalEntry.builder()
                .voucher(voucher2)
                .account(clientAccount)
                .thirdParty(party)
                .referenceNumber("RC-501")
                .description("Abono en efectivo")
                .debit(BigDecimal.ZERO)
                .credit(new BigDecimal("250000.0000"))
                .build();

        when(journalEntryRepository.findPartyAuxiliaryEntries(accountId, partyId, startDate, endDate))
                .thenReturn(List.of(entry1, entry2));

        PartyAuxiliaryReportResponse report = reportService.generatePartyAuxiliary(accountId, partyId, startDate, endDate);

        assertThat(report).isNotNull();
        assertThat(report.getAccountCode()).isEqualTo("130505");
        assertThat(report.getPartyIdentification()).isEqualTo("901234567");
        assertThat(report.getInitialBalance()).isEqualByComparingTo(new BigDecimal("400000.0000"));
        assertThat(report.getTotalDebit()).isEqualByComparingTo(new BigDecimal("600000.0000"));
        assertThat(report.getTotalCredit()).isEqualByComparingTo(new BigDecimal("250000.0000"));
        // Final = 400k + 600k - 250k = 750k
        assertThat(report.getFinalBalance()).isEqualByComparingTo(new BigDecimal("750000.0000"));

        assertThat(report.getEntries()).hasSize(2);
        assertThat(report.getEntries().get(0).getRunningBalance()).isEqualByComparingTo(new BigDecimal("1000000.0000")); // 400k + 600k
        assertThat(report.getEntries().get(1).getRunningBalance()).isEqualByComparingTo(new BigDecimal("750000.0000"));  // 1000k - 250k
    }

    @Test
    @DisplayName("Debe generar Estado de Resultados (P&G) y calcular la utilidad exacta del periodo")
    void shouldGenerateIncomeStatementCorrectly() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 9, 30);

        AccountBalanceProjection revenue = mockProjection(UUID.randomUUID(), "413505", "VENTA DE MERCANCIAS", "CREDIT", new BigDecimal("100000.00"), new BigDecimal("1000000.00"));
        AccountBalanceProjection expense = mockProjection(UUID.randomUUID(), "513505", "SERVICIOS GENERALES", "DEBIT", new BigDecimal("200000.00"), new BigDecimal("20000.00"));
        AccountBalanceProjection cost = mockProjection(UUID.randomUUID(), "613505", "COSTO DE VENTAS", "DEBIT", new BigDecimal("300000.00"), BigDecimal.ZERO);

        when(journalEntryRepository.findAccountBalancesBetweenDates(startDate, endDate))
                .thenReturn(List.of(revenue, expense, cost));

        IncomeStatementResponse pg = reportService.generateIncomeStatement(startDate, endDate);

        assertThat(pg).isNotNull();
        // Ingresos (Clase 4): Créditos (1,000,000) - Débitos (100,000) = 900,000
        assertThat(pg.getTotalRevenues()).isEqualByComparingTo(new BigDecimal("900000.0000"));

        // Costos (Clase 6): Débitos (300,000) - Créditos (0) = 300,000
        assertThat(pg.getTotalCosts()).isEqualByComparingTo(new BigDecimal("300000.0000"));

        // Utilidad Bruta: Ingresos (900,000) - Costos (300,000) = 600,000
        assertThat(pg.getGrossProfit()).isEqualByComparingTo(new BigDecimal("600000.0000"));

        // Gastos (Clase 5): Débitos (200,000) - Créditos (20,000) = 180,000
        assertThat(pg.getTotalExpenses()).isEqualByComparingTo(new BigDecimal("180000.0000"));

        // Utilidad Neta del periodo: 600,000 - 180,000 = 420,000
        assertThat(pg.getNetOperatingIncome()).isEqualByComparingTo(new BigDecimal("420000.0000"));
    }

    @Test
    @DisplayName("Debe generar Balance General y verificar la igualdad patrimonial Activo = Pasivo + Patrimonio + Utilidad")
    void shouldGenerateBalanceSheetCorrectly() {
        LocalDate asOfDate = LocalDate.of(2026, 9, 30);

        // Clase 1: Activos (Neto = 800k + 400k = 1,200,000)
        AccountBalanceProjection cash = mockProjection(UUID.randomUUID(), "110505", "CAJA GENERAL", "DEBIT", new BigDecimal("1000000.00"), new BigDecimal("200000.00"));
        AccountBalanceProjection clients = mockProjection(UUID.randomUUID(), "130505", "CLIENTES", "DEBIT", new BigDecimal("500000.00"), new BigDecimal("100000.00"));

        // Clase 2: Pasivos (Neto = 700k - 200k = 500,000)
        AccountBalanceProjection suppliers = mockProjection(UUID.randomUUID(), "220505", "PROVEEDORES", "CREDIT", new BigDecimal("200000.00"), new BigDecimal("700000.00"));

        // Clase 3: Patrimonio (Neto = 300,000)
        AccountBalanceProjection equity = mockProjection(UUID.randomUUID(), "310505", "CAPITAL SOCIAL", "CREDIT", BigDecimal.ZERO, new BigDecimal("300000.00"));

        // Clase 4 y 5: Resultado del Ejercicio (Ingresos 600k - Gastos 200k = 400k)
        AccountBalanceProjection revenue = mockProjection(UUID.randomUUID(), "413505", "INGRESOS", "CREDIT", BigDecimal.ZERO, new BigDecimal("600000.00"));
        AccountBalanceProjection expense = mockProjection(UUID.randomUUID(), "513505", "GASTOS", "DEBIT", new BigDecimal("200000.00"), BigDecimal.ZERO);

        when(journalEntryRepository.findAccountBalancesUpToDate(asOfDate))
                .thenReturn(List.of(cash, clients, suppliers, equity, revenue, expense));

        BalanceSheetResponse bs = reportService.generateBalanceSheet(asOfDate);

        assertThat(bs).isNotNull();
        assertThat(bs.getTotalAssets()).isEqualByComparingTo(new BigDecimal("1200000.0000"));
        assertThat(bs.getTotalLiabilities()).isEqualByComparingTo(new BigDecimal("500000.0000"));
        assertThat(bs.getTotalEquity()).isEqualByComparingTo(new BigDecimal("300000.0000"));
        assertThat(bs.getPeriodIncome()).isEqualByComparingTo(new BigDecimal("400000.0000"));

        // Total Pasivo + Patrimonio + Utilidad = 500,000 + 300,000 + 400,000 = 1,200,000
        assertThat(bs.getTotalLiabilitiesAndEquity()).isEqualByComparingTo(new BigDecimal("1200000.0000"));
        assertThat(bs.isBalanced()).isTrue(); // Ecuación patrimonial verificada
    }

    @Test
    @DisplayName("Debe generar Balance de Prueba a nivel de Terceros con saldos iniciales, movimientos y finales")
    void shouldGeneratePartyTrialBalanceCorrectly() {
        LocalDate startDate = LocalDate.of(2026, 9, 1);
        LocalDate endDate = LocalDate.of(2026, 9, 30);
        UUID accId = UUID.randomUUID();
        UUID partyAId = UUID.randomUUID();
        UUID partyBId = UUID.randomUUID();

        // Saldo anterior de Party A en cuenta 130505: Débito 500k, Crédito 100k -> Inicial = 400k
        PartyAccountBalanceProjection priorA = mockPartyProjection(accId, "130505", "CLIENTES", "DEBIT",
                partyAId, "901111111", "CLIENTE ALPHA", new BigDecimal("500000.00"), new BigDecimal("100000.00"));

        when(journalEntryRepository.findPartyAccountBalancesBeforeDate(startDate))
                .thenReturn(List.of(priorA));

        // Periodo:
        // Party A: Débito 200k, Crédito 50k -> Final = 400k + 200k - 50k = 550k
        PartyAccountBalanceProjection periodA = mockPartyProjection(accId, "130505", "CLIENTES", "DEBIT",
                partyAId, "901111111", "CLIENTE ALPHA", new BigDecimal("200000.00"), new BigDecimal("50000.00"));
        // Party B: Débito 300k, Crédito 0 -> Inicial = 0, Final = 300k
        PartyAccountBalanceProjection periodB = mockPartyProjection(accId, "130505", "CLIENTES", "DEBIT",
                partyBId, "902222222", "CLIENTE BETA", new BigDecimal("300000.00"), BigDecimal.ZERO);

        when(journalEntryRepository.findPartyAccountBalancesBetweenDates(startDate, endDate))
                .thenReturn(List.of(periodA, periodB));

        PartyTrialBalanceResponse report = reportService.generatePartyTrialBalance(startDate, endDate, null);

        assertThat(report).isNotNull();
        assertThat(report.getTotalInitialDebit()).isEqualByComparingTo(new BigDecimal("400000.0000"));
        assertThat(report.getTotalPeriodDebit()).isEqualByComparingTo(new BigDecimal("500000.0000"));
        assertThat(report.getTotalPeriodCredit()).isEqualByComparingTo(new BigDecimal("50000.0000"));
        assertThat(report.getTotalFinalDebit()).isEqualByComparingTo(new BigDecimal("850000.0000"));

        assertThat(report.getItems()).hasSize(2);
        PartyTrialBalanceItemResponse itemA = report.getItems().get(0);
        assertThat(itemA.getPartyIdentification()).isEqualTo("901111111");
        assertThat(itemA.getInitialBalance()).isEqualByComparingTo(new BigDecimal("400000.0000"));
        assertThat(itemA.getFinalBalance()).isEqualByComparingTo(new BigDecimal("550000.0000"));

        PartyTrialBalanceItemResponse itemB = report.getItems().get(1);
        assertThat(itemB.getPartyIdentification()).isEqualTo("902222222");
        assertThat(itemB.getInitialBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(itemB.getFinalBalance()).isEqualByComparingTo(new BigDecimal("300000.0000"));
    }

    @Test
    @DisplayName("Debe generar Estado de Resultados a nivel de Terceros discriminando ingresos, costos y gastos por tercero")
    void shouldGeneratePartyIncomeStatementCorrectly() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 9, 30);
        UUID clientAId = UUID.randomUUID();
        UUID supplierBId = UUID.randomUUID();

        // Ingresos generados con Cliente A: 1,000,000
        PartyAccountBalanceProjection revA = mockPartyProjection(UUID.randomUUID(), "413505", "VENTAS", "CREDIT",
                clientAId, "901111111", "CLIENTE ALPHA", BigDecimal.ZERO, new BigDecimal("1000000.00"));

        // Costos con Proveedor B: 300,000 y Gastos con Proveedor B: 100,000
        PartyAccountBalanceProjection costB = mockPartyProjection(UUID.randomUUID(), "613505", "COSTOS", "DEBIT",
                supplierBId, "902222222", "PROVEEDOR BETA", new BigDecimal("300000.00"), BigDecimal.ZERO);
        PartyAccountBalanceProjection expB = mockPartyProjection(UUID.randomUUID(), "513505", "SERVICIOS", "DEBIT",
                supplierBId, "902222222", "PROVEEDOR BETA", new BigDecimal("100000.00"), BigDecimal.ZERO);

        when(journalEntryRepository.findPartyAccountBalancesBetweenDates(startDate, endDate))
                .thenReturn(List.of(revA, costB, expB));

        PartyIncomeStatementResponse report = reportService.generatePartyIncomeStatement(startDate, endDate, null);

        assertThat(report).isNotNull();
        assertThat(report.getTotalRevenues()).isEqualByComparingTo(new BigDecimal("1000000.0000"));
        assertThat(report.getTotalCosts()).isEqualByComparingTo(new BigDecimal("300000.0000"));
        assertThat(report.getTotalExpenses()).isEqualByComparingTo(new BigDecimal("100000.0000"));
        assertThat(report.getGrossProfit()).isEqualByComparingTo(new BigDecimal("700000.0000"));
        assertThat(report.getNetOperatingIncome()).isEqualByComparingTo(new BigDecimal("600000.0000"));

        assertThat(report.getParties()).hasSize(2);
        PartyIncomeItemResponse p1 = report.getParties().get(0);
        assertThat(p1.getPartyIdentification()).isEqualTo("901111111");
        assertThat(p1.getTotalRevenues()).isEqualByComparingTo(new BigDecimal("1000000.0000"));
        assertThat(p1.getNetResult()).isEqualByComparingTo(new BigDecimal("1000000.0000"));

        PartyIncomeItemResponse p2 = report.getParties().get(1);
        assertThat(p2.getPartyIdentification()).isEqualTo("902222222");
        assertThat(p2.getTotalCosts()).isEqualByComparingTo(new BigDecimal("300000.0000"));
        assertThat(p2.getTotalExpenses()).isEqualByComparingTo(new BigDecimal("100000.0000"));
        assertThat(p2.getNetResult()).isEqualByComparingTo(new BigDecimal("-400000.0000"));
    }

    @Test
    @DisplayName("Debe generar Balance General a nivel de Terceros y verificar igualdad patrimonial")
    void shouldGeneratePartyBalanceSheetCorrectly() {
        LocalDate asOfDate = LocalDate.of(2026, 9, 30);
        UUID partyAId = UUID.randomUUID();
        UUID partyBId = UUID.randomUUID();
        UUID partyCId = UUID.randomUUID();

        // Activo: Cliente A debe 600k
        PartyAccountBalanceProjection assetA = mockPartyProjection(UUID.randomUUID(), "130505", "CLIENTES", "DEBIT",
                partyAId, "901111111", "CLIENTE ALPHA", new BigDecimal("600000.00"), BigDecimal.ZERO);

        // Pasivo: Proveedor B se le adeudan 200k
        PartyAccountBalanceProjection liabB = mockPartyProjection(UUID.randomUUID(), "220505", "PROVEEDORES", "CREDIT",
                partyBId, "902222222", "PROVEEDOR BETA", BigDecimal.ZERO, new BigDecimal("200000.00"));

        // Patrimonio: Socio C aportó 100k
        PartyAccountBalanceProjection eqC = mockPartyProjection(UUID.randomUUID(), "310505", "CAPITAL", "CREDIT",
                partyCId, "903333333", "SOCIO GAMMA", BigDecimal.ZERO, new BigDecimal("100000.00"));

        // Resultado del periodo: Ingresos 400k con Cliente A, Gastos 100k con Proveedor B -> Utilidad 300k
        PartyAccountBalanceProjection revA = mockPartyProjection(UUID.randomUUID(), "413505", "VENTAS", "CREDIT",
                partyAId, "901111111", "CLIENTE ALPHA", BigDecimal.ZERO, new BigDecimal("400000.00"));
        PartyAccountBalanceProjection expB = mockPartyProjection(UUID.randomUUID(), "513505", "GASTOS", "DEBIT",
                partyBId, "902222222", "PROVEEDOR BETA", new BigDecimal("100000.00"), BigDecimal.ZERO);

        when(journalEntryRepository.findPartyAccountBalancesUpToDate(asOfDate))
                .thenReturn(List.of(assetA, liabB, eqC, revA, expB));

        PartyBalanceSheetResponse report = reportService.generatePartyBalanceSheet(asOfDate, null);

        assertThat(report).isNotNull();
        assertThat(report.getTotalAssets()).isEqualByComparingTo(new BigDecimal("600000.0000"));
        assertThat(report.getTotalLiabilities()).isEqualByComparingTo(new BigDecimal("200000.0000"));
        assertThat(report.getTotalEquity()).isEqualByComparingTo(new BigDecimal("100000.0000"));
        assertThat(report.getPeriodIncome()).isEqualByComparingTo(new BigDecimal("300000.0000"));

        // 200k + 100k + 300k = 600k = Total Activos
        assertThat(report.getTotalLiabilitiesAndEquity()).isEqualByComparingTo(new BigDecimal("600000.0000"));
        assertThat(report.isBalanced()).isTrue();
        assertThat(report.getAssetItems()).hasSize(1);
        assertThat(report.getAssetItems().get(0).getPartyIdentification()).isEqualTo("901111111");
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

    private PartyAccountBalanceProjection mockPartyProjection(
            UUID accId, String code, String name, String nature,
            UUID partyId, String partyNit, String partyName,
            BigDecimal debit, BigDecimal credit) {
        return new PartyAccountBalanceProjection() {
            @Override public UUID getAccountId() { return accId; }
            @Override public String getAccountCode() { return code; }
            @Override public String getAccountName() { return name; }
            @Override public String getAccountNature() { return nature; }
            @Override public UUID getPartyId() { return partyId; }
            @Override public String getPartyIdentification() { return partyNit; }
            @Override public String getPartyName() { return partyName; }
            @Override public BigDecimal getTotalDebit() { return debit; }
            @Override public BigDecimal getTotalCredit() { return credit; }
        };
    }
}

