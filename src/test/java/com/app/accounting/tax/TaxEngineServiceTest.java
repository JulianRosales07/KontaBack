package com.app.accounting.tax;

import com.app.accounting.common.util.BigDecimalUtils;
import com.app.accounting.puc.entity.AccountNature;
import com.app.accounting.tax.dto.request.TaxCalculationRequest;
import com.app.accounting.tax.dto.response.TaxCalculationResult;
import com.app.accounting.tax.dto.response.TaxItemDetail;
import com.app.accounting.tax.entity.TaxTemplate;
import com.app.accounting.tax.entity.TaxType;
import com.app.accounting.tax.repository.TaxTemplateRepository;
import com.app.accounting.tax.service.impl.TaxEngineServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxEngineServiceTest {

    @Mock
    private TaxTemplateRepository taxTemplateRepository;

    @Mock
    private com.app.accounting.party.service.ThirdPartyService thirdPartyService;

    @Mock
    private com.app.accounting.voucher.repository.JournalEntryRepository entryRepository;

    @Mock
    private com.app.accounting.common.document.PdfExportService pdfExportService;

    @InjectMocks
    private TaxEngineServiceImpl taxEngineService;

    private TaxTemplate iva19;
    private TaxTemplate retefuente25;
    private TaxTemplate reteIca0966;

    @BeforeEach
    void setUp() {
        iva19 = TaxTemplate.builder()
                .id(UUID.randomUUID())
                .name("IVA General 19%")
                .taxType(TaxType.IVA)
                .ratePercentage(new BigDecimal("19.0000"))
                .salesAccountCode("240805")
                .purchaseAccountCode("240810")
                .isActive(true)
                .build();

        retefuente25 = TaxTemplate.builder()
                .id(UUID.randomUUID())
                .name("Retefuente Compras 2.5%")
                .taxType(TaxType.RETEFUENTE)
                .ratePercentage(new BigDecimal("2.5000"))
                .salesAccountCode("135515")
                .purchaseAccountCode("236540")
                .isActive(true)
                .build();

        reteIca0966 = TaxTemplate.builder()
                .id(UUID.randomUUID())
                .name("ReteICA Bogotá 0.966%")
                .taxType(TaxType.RETEICA)
                .ratePercentage(new BigDecimal("0.9660"))
                .salesAccountCode("135518")
                .purchaseAccountCode("236805")
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("Debe liquidar impuestos para una Venta a Crédito correctamente")
    void shouldCalculateTaxesForSaleCorrectly() {
        when(taxTemplateRepository.findAllById(anyList())).thenReturn(List.of(iva19, retefuente25, reteIca0966));

        BigDecimal subtotal = new BigDecimal("1000000.0000"); // 1 millón
        TaxCalculationRequest request = TaxCalculationRequest.builder()
                .subtotal(subtotal)
                .isSale(true)
                .taxTemplateIds(List.of(iva19.getId(), retefuente25.getId(), reteIca0966.getId()))
                .build();

        TaxCalculationResult result = taxEngineService.calculateTaxes(request);

        assertThat(result).isNotNull();
        assertThat(result.getSubtotal()).isEqualByComparingTo(subtotal);

        // IVA 19% = 190,000
        assertThat(result.getTaxTotal()).isEqualByComparingTo(new BigDecimal("190000.0000"));

        // Retefuente 2.5% = 25,000 + ReteICA 0.966% = 9,660 -> 34,660
        assertThat(result.getRetentionTotal()).isEqualByComparingTo(new BigDecimal("34660.0000"));

        // Total neto a cobrar = 1,000,000 + 190,000 - 34,660 = 1,155,340
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1155340.0000"));

        // Cuentas y naturalezas en Venta:
        // IVA -> Crédito 240805
        TaxItemDetail ivaItem = result.getItems().stream()
                .filter(i -> i.getTaxType() == TaxType.IVA)
                .findFirst().orElseThrow();
        assertThat(ivaItem.getAccountCode()).isEqualTo("240805");
        assertThat(ivaItem.getAccountNature()).isEqualTo(AccountNature.CREDIT);

        // Retefuente a favor -> Débito 135515
        TaxItemDetail rfItem = result.getItems().stream()
                .filter(i -> i.getTaxType() == TaxType.RETEFUENTE)
                .findFirst().orElseThrow();
        assertThat(rfItem.getAccountCode()).isEqualTo("135515");
        assertThat(rfItem.getAccountNature()).isEqualTo(AccountNature.DEBIT);
    }

    @Test
    @DisplayName("Debe liquidar impuestos para una Compra de Proveedor correctamente")
    void shouldCalculateTaxesForPurchaseCorrectly() {
        when(taxTemplateRepository.findAllById(anyList())).thenReturn(List.of(iva19, retefuente25, reteIca0966));

        BigDecimal subtotal = new BigDecimal("2000000.0000"); // 2 millones
        TaxCalculationRequest request = TaxCalculationRequest.builder()
                .subtotal(subtotal)
                .isSale(false)
                .taxTemplateIds(List.of(iva19.getId(), retefuente25.getId(), reteIca0966.getId()))
                .build();

        TaxCalculationResult result = taxEngineService.calculateTaxes(request);

        assertThat(result).isNotNull();
        // IVA Descontable 19% = 380,000
        assertThat(result.getTaxTotal()).isEqualByComparingTo(new BigDecimal("380000.0000"));

        // Retefuente 2.5% = 50,000 + ReteICA 0.966% = 19,320 -> 69,320
        assertThat(result.getRetentionTotal()).isEqualByComparingTo(new BigDecimal("69320.0000"));

        // Total a pagar proveedor = 2,000,000 + 380,000 - 69,320 = 2,310,680
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("2310680.0000"));

        // Cuentas y naturalezas en Compra:
        // IVA Descontable -> Débito 240810
        TaxItemDetail ivaItem = result.getItems().stream()
                .filter(i -> i.getTaxType() == TaxType.IVA)
                .findFirst().orElseThrow();
        assertThat(ivaItem.getAccountCode()).isEqualTo("240810");
        assertThat(ivaItem.getAccountNature()).isEqualTo(AccountNature.DEBIT);

        // Retefuente por pagar -> Crédito 236540
        TaxItemDetail rfItem = result.getItems().stream()
                .filter(i -> i.getTaxType() == TaxType.RETEFUENTE)
                .findFirst().orElseThrow();
        assertThat(rfItem.getAccountCode()).isEqualTo("236540");
        assertThat(rfItem.getAccountNature()).isEqualTo(AccountNature.CREDIT);
    }
}
