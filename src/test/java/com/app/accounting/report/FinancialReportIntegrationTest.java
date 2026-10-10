package com.app.accounting.report;

import com.app.accounting.associate.dto.request.CreateAssociateReceiptRequest;
import com.app.accounting.associate.dto.request.ReceiptItemRequest;
import com.app.accounting.associate.dto.response.AssociateSummaryResponse;
import com.app.accounting.associate.entity.QuotaCategory;
import com.app.accounting.associate.service.AssociateDirectoryService;
import com.app.accounting.associate.service.AssociateService;
import com.app.accounting.loan.dto.request.CreateLoanRequest;
import com.app.accounting.loan.dto.request.LoanPaymentRequest;
import com.app.accounting.loan.dto.response.LoanResponse;
import com.app.accounting.loan.entity.PaymentMethod;
import com.app.accounting.loan.service.LoanService;
import com.app.accounting.party.entity.PersonType;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.party.repository.ThirdPartyRepository;
import com.app.accounting.report.dto.response.FinancialReportResponse;
import com.app.accounting.report.service.FinancialReportService;
import com.app.accounting.report.service.ReportExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FinancialReportIntegrationTest {

    @Autowired private LoanService loanService;
    @Autowired private AssociateService associateService;
    @Autowired private AssociateDirectoryService directoryService;
    @Autowired private ThirdPartyRepository thirdPartyRepository;
    @Autowired private FinancialReportService reportService;
    @Autowired private ReportExportService exportService;

    private ThirdParty juan;
    private ThirdParty juanCarlos;
    private final LocalDate today = LocalDate.now();

    private static BigDecimal $(long v) {
        return BigDecimal.valueOf(v);
    }

    @BeforeEach
    void setUp() {
        juan = thirdPartyRepository.save(ThirdParty.builder().identificationNumber("7001").fullName("Juan Pérez")
                .email("juan.perez@correo.com").phone("3001112233").associate(true).idType("CC").type(PersonType.NATURAL).build());
        juanCarlos = thirdPartyRepository.save(ThirdParty.builder().identificationNumber("7002").fullName("Juan Carlos López")
                .associate(true).idType("CC").type(PersonType.NATURAL).build());
        thirdPartyRepository.save(ThirdParty.builder().identificationNumber("7003").fullName("María Gómez")
                .associate(true).idType("CE").associateCode("ASO-0099").type(PersonType.NATURAL).build());

        LoanResponse loan = loanService.createLoan(CreateLoanRequest.builder()
                .thirdPartyId(juan.getId()).principalAmount($(1_000_000)).monthlyRate(new BigDecimal("2")).termMonths(10)
                .disbursementDate(today).firstPaymentDate(today.plusMonths(1)).build());
        loanService.registerPayment(loan.getId(), LoanPaymentRequest.builder()
                .paymentDate(today).paymentMethod(PaymentMethod.CASH)
                .allocations(List.of(LoanPaymentRequest.Allocation.builder()
                        .installmentId(loan.getInstallments().getFirst().getId()).amount($(120_000)).build()))
                .build());

        // Recibo con aportes y ahorros por separado (usa códigos PUC en lugar de UUID)
        associateService.registerReceipt(CreateAssociateReceiptRequest.builder()
                .thirdPartyId(juanCarlos.getId())
                .paymentDate(today)
                .cashAccountCode("110505")
                .totalAmount($(80_000))
                .items(List.of(
                        ReceiptItemRequest.builder().conceptName("Aportes ordinarios").category(QuotaCategory.EQUITY_SAVING)
                                .accountCode("311505").amount($(50_000)).build(),
                        ReceiptItemRequest.builder().conceptName("Ahorro voluntario").category(QuotaCategory.SAVINGS)
                                .accountCode("311510").amount($(30_000)).build()))
                .build());
    }

    @Test
    @DisplayName("Reporte de caja: saldo final = saldo inicial + ingresos − egresos")
    void cashReport() {
        FinancialReportResponse r = reportService.generate("CAJA",
                new FinancialReportService.Filters(today, today, "1105", null, null, null));

        BigDecimal opening = value(r, "Saldo inicial");
        BigDecimal in = value(r, "Total ingresos");
        BigDecimal out = value(r, "Total egresos");
        BigDecimal end = value(r, "Saldo final");
        assertThat(end).isEqualByComparingTo(opening.add(in).subtract(out));
        // desembolso 1.000.000 (egreso) + abono 120.000 y recibo 80.000 (ingresos)
        assertThat(in).isEqualByComparingTo($(200_000));
        assertThat(out).isEqualByComparingTo($(1_000_000));
        assertThat(r.getGeneratedAt()).isNotNull();
        assertThat(r.getRows()).isNotEmpty();
    }

    @Test
    @DisplayName("Intereses, cuentas por cobrar y recaudos usan los mismos datos del préstamo")
    void consistencyAcrossReports() {
        FinancialReportService.Filters f = new FinancialReportService.Filters(today, today, null, null, null, null);
        assertThat(reportService.generate("INTERESES", f).getTotals().get("interest")).isEqualByComparingTo($(20_000));
        assertThat(reportService.generate("CUENTAS_POR_COBRAR", f).getTotals().get("balance")).isEqualByComparingTo($(900_000));
        FinancialReportResponse collections = reportService.generate("RECAUDOS", f);
        assertThat(collections.getTotals().get("principal")).isEqualByComparingTo($(100_000));
        assertThat(collections.getTotals().get("interest")).isEqualByComparingTo($(20_000));
        assertThat(collections.getTotals().get("total")).isEqualByComparingTo($(200_000));
    }

    @Test
    @DisplayName("Cuentas por pagar: aportes y ahorros separados")
    void payablesKeepConceptsSeparate() {
        AssociateSummaryResponse row = directoryService.payables(null).stream()
                .filter(a -> a.getId().equals(juanCarlos.getId())).findFirst().orElseThrow();
        assertThat(row.getContributions()).isEqualByComparingTo($(50_000));
        assertThat(row.getSavings()).isEqualByComparingTo($(30_000));
        assertThat(row.getPayableBalance()).isEqualByComparingTo($(80_000));

        FinancialReportResponse r = reportService.generate("CUENTAS_POR_PAGAR",
                new FinancialReportService.Filters(today, today, null, null, null, null));
        assertThat(r.getTotals().get("contributions")).isEqualByComparingTo($(50_000));
        assertThat(r.getTotals().get("savings")).isEqualByComparingTo($(30_000));
    }

    @Test
    @DisplayName("Búsqueda global de asociados: 'Juan' encuentra a Juan Pérez y Juan Carlos López; sin tildes; por código/correo/teléfono")
    void directorySearch() {
        List<String> juanes = directoryService.search(new AssociateDirectoryService.Filters("Juan", null, null, true, null, null, null))
                .stream().map(AssociateSummaryResponse::getFullName).toList();
        assertThat(juanes).contains("Juan Pérez", "Juan Carlos López").doesNotContain("María Gómez");

        assertThat(names("perez")).containsExactly("Juan Pérez");
        assertThat(names("juan lopez")).containsExactly("Juan Carlos López");
        assertThat(names("ASO-0099")).containsExactly("María Gómez");
        assertThat(names("juan.perez@correo")).containsExactly("Juan Pérez");
        assertThat(names("3001112233")).containsExactly("Juan Pérez");

        assertThat(directoryService.search(new AssociateDirectoryService.Filters(null, null, null, true, true, null, null)))
                .extracting(AssociateSummaryResponse::getFullName).contains("Juan Pérez").doesNotContain("María Gómez");
        assertThat(directoryService.search(new AssociateDirectoryService.Filters(null, null, "CE", true, null, null, null)))
                .extracting(AssociateSummaryResponse::getFullName).containsExactly("María Gómez");
        assertThat(directoryService.search(new AssociateDirectoryService.Filters(null, null, null, true, null, true, null)))
                .extracting(AssociateSummaryResponse::getFullName).containsExactly("Juan Carlos López");
    }

    @Test
    @DisplayName("Exportación: PDF real y Excel válido para todos los reportes")
    void exportsAllReports() {
        FinancialReportService.Filters f = new FinancialReportService.Filters(today.minusMonths(1), today, null, null, null, null);
        for (String code : FinancialReportService.CATALOG.keySet()) {
            FinancialReportResponse r = reportService.generate(code, f);
            byte[] pdf = exportService.toPdf(r);
            byte[] xlsx = exportService.toExcel(r);
            assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).as(code).isEqualTo("%PDF");
            assertThat(xlsx[0]).as(code).isEqualTo((byte) 'P'); // zip
            assertThat(xlsx[1]).as(code).isEqualTo((byte) 'K');
        }
    }

    private List<String> names(String q) {
        return directoryService.search(new AssociateDirectoryService.Filters(q, null, null, true, null, null, null))
                .stream().map(AssociateSummaryResponse::getFullName).toList();
    }

    private static BigDecimal value(FinancialReportResponse r, String label) {
        return r.getSummary().stream().filter(s -> s.getLabel().equals(label)).findFirst().orElseThrow().getValue();
    }
}
