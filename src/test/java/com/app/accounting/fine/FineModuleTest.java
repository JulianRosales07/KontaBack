package com.app.accounting.fine;

import com.app.accounting.associate.dto.request.CreateAssociateReceiptRequest;
import com.app.accounting.associate.dto.request.ReceiptItemRequest;
import com.app.accounting.associate.entity.QuotaCategory;
import com.app.accounting.associate.service.AssociateService;
import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.fine.FineDtos.*;
import com.app.accounting.party.entity.PersonType;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.party.repository.ThirdPartyRepository;
import com.app.accounting.report.dto.response.FinancialReportResponse;
import com.app.accounting.report.service.FinancialReportService;
import com.app.accounting.voucher.dto.response.JournalVoucherResponse;
import com.app.accounting.voucher.service.VoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FineModuleTest {

    @Autowired private FineService fineService;
    @Autowired private AssociateService associateService;
    @Autowired private VoucherService voucherService;
    @Autowired private ThirdPartyRepository thirdPartyRepository;
    @Autowired private FinancialReportService reportService;

    private ThirdParty ana;
    private ThirdParty luis;
    private ThirdParty marta;
    private FineTypeResponse absence;
    private final LocalDate meeting = LocalDate.now().withDayOfMonth(1);

    @BeforeEach
    void setUp() {
        ana = associate("8101", "ANA MULTAS");
        luis = associate("8102", "LUIS MULTAS");
        marta = associate("8103", "MARTA MULTAS");
        absence = fineService.types().stream().filter(t -> t.name().equals("Inasistencia a reunión")).findFirst().orElseThrow();
    }

    private ThirdParty associate(String id, String name) {
        return thirdPartyRepository.save(ThirdParty.builder().identificationNumber(id).fullName(name)
                .associate(true).idType("CC").type(PersonType.NATURAL).build());
    }

    @Test
    @DisplayName("La multa base es inasistencia a reunión por $5.000 y se pueden crear y editar más tipos")
    void fineTypes() {
        assertThat(absence.amount()).isEqualByComparingTo("5000");
        assertThat(absence.active()).isTrue();

        FineTypeResponse late = fineService.createType(new FineTypeRequest("Llegada tarde", "Más de 15 minutos", new BigDecimal("2000"), true));
        assertThat(fineService.types()).extracting(FineTypeResponse::name).contains("Inasistencia a reunión", "Llegada tarde");
        assertThatThrownBy(() -> fineService.createType(new FineTypeRequest("llegada tarde", null, new BigDecimal("1000"), true)))
                .isInstanceOf(BusinessRuleException.class);

        FineTypeResponse updated = fineService.updateType(late.id(), new FineTypeRequest("Llegada tarde", null, new BigDecimal("3000"), true));
        assertThat(updated.amount()).isEqualByComparingTo("3000");

        // Un tipo sin uso se elimina; uno usado solo se desactiva
        fineService.register(new RegisterFinesRequest(late.id(), meeting, null, null, List.of(ana.getId())));
        fineService.deleteType(late.id());
        assertThat(fineService.types()).filteredOn(t -> t.id().equals(late.id())).singleElement()
                .satisfies(t -> assertThat(t.active()).isFalse());
    }

    @Test
    @DisplayName("Registro masivo de inasistentes: $5.000 cada uno, sin duplicar la misma reunión")
    void registerAbsentees() {
        RegisterFinesResponse r = fineService.register(new RegisterFinesRequest(absence.id(), meeting, "Asamblea ordinaria",
                null, List.of(ana.getId(), luis.getId(), marta.getId())));
        assertThat(r.created()).isEqualTo(3);
        assertThat(r.total()).isEqualByComparingTo("15000");

        RegisterFinesResponse again = fineService.register(new RegisterFinesRequest(absence.id(), meeting, null, null, List.of(ana.getId())));
        assertThat(again.created()).isZero();
        assertThat(again.skipped()).hasSize(1);

        List<FineResponse> pending = fineService.search(AssociateFine.Status.PENDING, null, absence.id(), meeting, meeting);
        assertThat(pending).hasSize(3).allSatisfy(f -> assertThat(f.amount()).isEqualByComparingTo("5000"));
        assertThat(fineService.summary(null).pendingTotal()).isGreaterThanOrEqualTo(new BigDecimal("15000"));
    }

    @Test
    @DisplayName("Cobro: un ingreso por asociado a 429510; aparece en la columna MULTAS de la planilla; al anular vuelve a pendiente")
    void payFines() {
        fineService.register(new RegisterFinesRequest(absence.id(), meeting, null, null, List.of(ana.getId())));
        fineService.register(new RegisterFinesRequest(absence.id(), meeting.plusDays(7), null, null, List.of(ana.getId())));
        List<FineResponse> anaFines = fineService.search(AssociateFine.Status.PENDING, ana.getId(), null, null, null);
        assertThat(anaFines).hasSize(2);

        PayFinesResponse paid = fineService.pay(new PayFinesRequest(anaFines.stream().map(FineResponse::id).toList(), meeting.plusDays(8), "110505"));
        assertThat(paid.receipts()).hasSize(1);
        assertThat(paid.total()).isEqualByComparingTo("10000");
        List<FineResponse> after = fineService.search(AssociateFine.Status.PAID, ana.getId(), null, null, null);
        assertThat(after).hasSize(2).allSatisfy(f -> assertThat(f.receiptNumber()).isEqualTo(paid.receipts().getFirst()));

        FinancialReportResponse planilla = reportService.generate("PLANILLA_RECAUDOS",
                new FinancialReportService.Filters(meeting, meeting.plusDays(8), null, null, ana.getId(), null));
        assertThat(planilla.getTotals().get("multas")).isEqualByComparingTo("10000");

        // No se puede condonar una multa pagada
        assertThatThrownBy(() -> fineService.voidFine(after.getFirst().id(), new VoidFineRequest("error")))
                .isInstanceOf(BusinessRuleException.class);

        // Anular el comprobante devuelve las multas a pendiente
        JournalVoucherResponse voucher = voucherService.getVoucherByNumber(paid.receipts().getFirst());
        voucherService.reverseVoucher(voucher.getId(), "Pago registrado por error");
        assertThat(fineService.search(AssociateFine.Status.PENDING, ana.getId(), null, null, null)).hasSize(2);
    }

    @Test
    @DisplayName("Condonación de una multa pendiente con motivo")
    void voidFine() {
        fineService.register(new RegisterFinesRequest(absence.id(), meeting, null, null, List.of(luis.getId())));
        FineResponse fine = fineService.search(AssociateFine.Status.PENDING, luis.getId(), null, null, null).getFirst();
        FineResponse voided = fineService.voidFine(fine.id(), new VoidFineRequest("Calamidad doméstica"));
        assertThat(voided.status()).isEqualTo("VOIDED");
        assertThat(voided.voidReason()).isEqualTo("Calamidad doméstica");
        // Condonada, se puede volver a imponer la misma multa
        assertThat(fineService.register(new RegisterFinesRequest(absence.id(), meeting, null, null, List.of(luis.getId()))).created()).isEqualTo(1);
    }

    @Test
    @DisplayName("La multa se cobra dentro del recibo de cuota mensual y queda pagada con ese recibo")
    void payInsideMonthlyReceipt() {
        fineService.register(new RegisterFinesRequest(absence.id(), meeting, null, null, List.of(marta.getId(), luis.getId())));
        FineResponse martaFine = fineService.search(AssociateFine.Status.PENDING, marta.getId(), null, null, null).getFirst();
        FineResponse luisFine = fineService.search(AssociateFine.Status.PENDING, luis.getId(), null, null, null).getFirst();

        var receipt = associateService.registerReceipt(CreateAssociateReceiptRequest.builder()
                .thirdPartyId(marta.getId())
                .paymentDate(meeting.plusDays(2))
                .cashAccountCode("110505")
                .totalAmount(new BigDecimal("15000"))
                .items(List.of(ReceiptItemRequest.builder().conceptName("Aportes").category(QuotaCategory.EQUITY_SAVING)
                        .accountCode("311505").amount(new BigDecimal("10000")).build()))
                .fineIds(List.of(martaFine.id()))
                .build());
        FineResponse paid = fineService.search(AssociateFine.Status.PAID, marta.getId(), null, null, null).getFirst();
        assertThat(paid.receiptNumber()).isEqualTo(receipt.getReceiptNumber());

        // La multa de otro asociado no se puede incluir
        assertThatThrownBy(() -> associateService.registerReceipt(CreateAssociateReceiptRequest.builder()
                .thirdPartyId(marta.getId())
                .paymentDate(meeting.plusDays(2))
                .cashAccountCode("110505")
                .totalAmount(new BigDecimal("5000"))
                .items(List.of())
                .fineIds(List.of(luisFine.id()))
                .build())).isInstanceOf(BusinessRuleException.class);
    }
}
