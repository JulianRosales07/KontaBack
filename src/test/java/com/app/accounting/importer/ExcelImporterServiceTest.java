package com.app.accounting.importer;

import com.app.accounting.puc.entity.Account;
import com.app.accounting.puc.entity.AccountNature;
import com.app.accounting.puc.service.AccountService;
import com.app.accounting.importer.dto.response.ImportResultResponse;
import com.app.accounting.importer.service.impl.ExcelImporterServiceImpl;
import com.app.accounting.party.entity.PersonType;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.party.service.ThirdPartyService;
import com.app.accounting.voucher.dto.request.JournalVoucherRequest;
import com.app.accounting.voucher.dto.response.JournalVoucherResponse;
import com.app.accounting.voucher.entity.VoucherStatus;
import com.app.accounting.voucher.entity.VoucherType;
import com.app.accounting.voucher.service.VoucherService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcelImporterServiceTest {

    @Mock
    private AccountService accountService;

    @Mock
    private ThirdPartyService thirdPartyService;

    @Mock
    private VoucherService voucherService;

    @InjectMocks
    private ExcelImporterServiceImpl excelImporterService;

    private Account cashAccount;
    private Account clientAccount;

    @BeforeEach
    void setUp() {
        cashAccount = Account.builder()
                .id(UUID.randomUUID())
                .code("110505")
                .name("CAJA GENERAL")
                .level(4)
                .nature(AccountNature.DEBIT)
                .build();

        clientAccount = Account.builder()
                .id(UUID.randomUUID())
                .code("130505")
                .name("CLIENTES NACIONALES")
                .level(4)
                .nature(AccountNature.DEBIT)
                .build();
    }

    @Test
    @DisplayName("Debe importar y asentar correctamente un archivo Excel cuadrado")
    void shouldImportBalancedExcelFileSuccessfully() throws IOException {
        when(accountService.getAccountEntityByCode("110505")).thenReturn(cashAccount);
        when(accountService.getAccountEntityByCode("130505")).thenReturn(clientAccount);

        ThirdParty thirdParty = ThirdParty.builder()
                .id(UUID.randomUUID())
                .identificationNumber("901234567")
                .fullName("DISTRIBUIDORA")
                .type(PersonType.JURIDICA)
                .build();

        when(thirdPartyService.getOrCreateThirdParty("901234567", "DISTRIBUIDORA", PersonType.NATURAL))
                .thenReturn(thirdParty);

        UUID voucherId = UUID.randomUUID();
        when(voucherService.createAndPostVoucher(any(JournalVoucherRequest.class))).thenReturn(
                JournalVoucherResponse.builder()
                        .id(voucherId)
                        .voucherNumber("RC-IMP-001")
                        .status(VoucherStatus.POSTED)
                        .build()
        );

        byte[] excelBytes = createSampleExcel(
                new Object[][]{
                        {"110505", "", "", "RB-8402", "Ingreso a caja", 150000.0, 0.0},
                        {"130505", "901234567", "DISTRIBUIDORA", "RB-8402", "Abono cliente", 0.0, 150000.0}
                }
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "comprobante.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        ImportResultResponse response = excelImporterService.importVoucherFromExcel(
                file,
                "RC-IMP-001",
                VoucherType.INGRESO,
                LocalDate.now(),
                "Importación de prueba"
        );

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getVoucherNumber()).isEqualTo("RC-IMP-001");
        assertThat(response.getTotalRowsProcessed()).isEqualTo(2);
        assertThat(response.getTotalDebit()).isEqualByComparingTo(new BigDecimal("150000.0000"));
        assertThat(response.getTotalCredit()).isEqualByComparingTo(new BigDecimal("150000.0000"));
        assertThat(response.getErrors()).isEmpty();

        verify(voucherService).createAndPostVoucher(any(JournalVoucherRequest.class));
    }

    @Test
    @DisplayName("Debe rechazar la importación si el archivo Excel está descuadrado")
    void shouldRejectUnbalancedExcelFile() throws IOException {
        when(accountService.getAccountEntityByCode("110505")).thenReturn(cashAccount);
        when(accountService.getAccountEntityByCode("130505")).thenReturn(clientAccount);

        byte[] excelBytes = createSampleExcel(
                new Object[][]{
                        {"110505", "", "", "RB-8402", "Ingreso a caja", 200000.0, 0.0},
                        {"130505", "901234567", "DISTRIBUIDORA", "RB-8402", "Abono cliente", 0.0, 100000.0}
                }
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "descuadrado.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        ImportResultResponse response = excelImporterService.importVoucherFromExcel(
                file,
                "RC-IMP-FAIL",
                VoucherType.INGRESO,
                LocalDate.now(),
                "Importación descuadrada"
        );

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrors()).isNotEmpty();
        assertThat(response.getErrors().get(0).getMessage()).contains("El lote completo está descuadrado");
    }

    private byte[] createSampleExcel(Object[][] rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Asientos");

            // Header row
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Cuenta");
            header.createCell(1).setCellValue("Tercero");
            header.createCell(2).setCellValue("Nombre");
            header.createCell(3).setCellValue("Referencia");
            header.createCell(4).setCellValue("Descripcion");
            header.createCell(5).setCellValue("Debito");
            header.createCell(6).setCellValue("Credito");

            // Data rows
            for (int i = 0; i < rows.length; i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue((String) rows[i][0]);
                row.createCell(1).setCellValue((String) rows[i][1]);
                row.createCell(2).setCellValue((String) rows[i][2]);
                row.createCell(3).setCellValue((String) rows[i][3]);
                row.createCell(4).setCellValue((String) rows[i][4]);
                row.createCell(5).setCellValue((Double) rows[i][5]);
                row.createCell(6).setCellValue((Double) rows[i][6]);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }
}
