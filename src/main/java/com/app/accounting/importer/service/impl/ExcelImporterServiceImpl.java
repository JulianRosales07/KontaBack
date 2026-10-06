package com.app.accounting.importer.service.impl;

import com.app.accounting.puc.service.AccountService;
import com.app.accounting.common.util.BigDecimalUtils;
import com.app.accounting.importer.dto.response.ImportResultResponse;
import com.app.accounting.importer.dto.response.RowErrorDetail;
import com.app.accounting.importer.service.ExcelImporterService;
import com.app.accounting.party.entity.PersonType;
import com.app.accounting.party.service.ThirdPartyService;
import com.app.accounting.voucher.dto.request.JournalEntryRequest;
import com.app.accounting.voucher.dto.request.JournalVoucherRequest;
import com.app.accounting.voucher.dto.response.JournalVoucherResponse;
import com.app.accounting.voucher.entity.VoucherStatus;
import com.app.accounting.voucher.entity.VoucherType;
import com.app.accounting.voucher.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelImporterServiceImpl implements ExcelImporterService {

    private final AccountService accountService;
    private final ThirdPartyService thirdPartyService;
    private final VoucherService voucherService;

    @Override
    @Transactional
    public ImportResultResponse importVoucherFromExcel(
            MultipartFile file,
            String voucherNumber,
            VoucherType voucherType,
            LocalDate issueDate,
            String description
    ) {
        List<RowErrorDetail> errors = new ArrayList<>();
        List<JournalEntryRequest> entries = new ArrayList<>();

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        int processedRows = 0;

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getLastRowNum() < 0) {
                return ImportResultResponse.builder()
                        .success(false)
                        .voucherNumber(voucherNumber)
                        .message("El archivo Excel está vacío o no contiene hojas válidas")
                        .build();
            }

            DataFormatter formatter = new DataFormatter();
            int headerRowNum = -1;
            Map<String, Integer> colMap = new HashMap<>();

            // Buscar fila de encabezados
            for (int r = 0; r <= Math.min(10, sheet.getLastRowNum()); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                Map<String, Integer> tempColMap = detectHeaders(row, formatter);
                if (tempColMap.containsKey("account") && (tempColMap.containsKey("debit") || tempColMap.containsKey("credit"))) {
                    headerRowNum = r;
                    colMap = tempColMap;
                    break;
                }
            }

            // Si no detectó encabezados por nombre, usar posiciones por defecto
            if (headerRowNum == -1) {
                headerRowNum = 0;
                colMap.put("account", 0);
                colMap.put("third_party", 1);
                colMap.put("third_party_name", 2);
                colMap.put("reference", 3);
                colMap.put("description", 4);
                colMap.put("debit", 5);
                colMap.put("credit", 6);
            }

            int lastRow = sheet.getLastRowNum();
            for (int r = headerRowNum + 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String accountCode = getCellValueAsString(row, colMap.get("account"), formatter);
                if (accountCode == null || accountCode.isBlank()) {
                    continue; // Fila vacía
                }

                // Omitir filas de totales calculados en Excel
                String normalizedAccount = accountCode.toUpperCase().trim();
                if (normalizedAccount.contains("TOTAL") || normalizedAccount.contains("SUMA") || normalizedAccount.contains("BALANCE")) {
                    continue;
                }

                processedRows++;
                int displayRow = r + 1;

                // 1. Validar cuenta contable
                try {
                    accountService.getAccountEntityByCode(accountCode.trim());
                } catch (Exception e) {
                    errors.add(RowErrorDetail.builder()
                            .rowNumber(displayRow)
                            .field("accountCode")
                            .message("La cuenta contable no existe en el catálogo PUC: " + accountCode)
                            .rejectedValue(accountCode)
                            .build());
                }

                // 2. Extraer y validar valores Débito y Crédito
                BigDecimal debit = parseNumericCell(row, colMap.get("debit"), formatter);
                BigDecimal credit = parseNumericCell(row, colMap.get("credit"), formatter);

                if (debit.compareTo(BigDecimal.ZERO) < 0) {
                    errors.add(RowErrorDetail.builder()
                            .rowNumber(displayRow)
                            .field("debit")
                            .message("El valor débito no puede ser negativo")
                            .rejectedValue(debit.toString())
                            .build());
                }

                if (credit.compareTo(BigDecimal.ZERO) < 0) {
                    errors.add(RowErrorDetail.builder()
                            .rowNumber(displayRow)
                            .field("credit")
                            .message("El valor crédito no puede ser negativo")
                            .rejectedValue(credit.toString())
                            .build());
                }

                if (BigDecimalUtils.isZero(debit) && BigDecimalUtils.isZero(credit)) {
                    errors.add(RowErrorDetail.builder()
                            .rowNumber(displayRow)
                            .field("amount")
                            .message("La fila debe tener un valor mayor a cero en Débito o Crédito")
                            .rejectedValue("0.0")
                            .build());
                }

                // 3. Extraer tercero y referencia
                String thirdPartyDoc = getCellValueAsString(row, colMap.get("third_party"), formatter);
                String thirdPartyName = getCellValueAsString(row, colMap.get("third_party_name"), formatter);
                String refNumber = getCellValueAsString(row, colMap.get("reference"), formatter);
                String rowDesc = getCellValueAsString(row, colMap.get("description"), formatter);

                if (thirdPartyDoc != null && !thirdPartyDoc.isBlank()) {
                    thirdPartyService.getOrCreateThirdParty(thirdPartyDoc.trim(), thirdPartyName, PersonType.NATURAL);
                }

                totalDebit = totalDebit.add(debit);
                totalCredit = totalCredit.add(credit);

                entries.add(JournalEntryRequest.builder()
                        .accountCode(accountCode.trim())
                        .thirdPartyIdentification(thirdPartyDoc != null && !thirdPartyDoc.isBlank() ? thirdPartyDoc.trim() : null)
                        .referenceNumber(refNumber != null && !refNumber.isBlank() ? refNumber.trim() : null)
                        .description(rowDesc != null && !rowDesc.isBlank() ? rowDesc.trim() : description)
                        .debit(debit)
                        .credit(credit)
                        .build());
            }

        } catch (Exception ex) {
            log.error("Error al procesar el archivo Excel", ex);
            return ImportResultResponse.builder()
                    .success(false)
                    .voucherNumber(voucherNumber)
                    .message("Error leyendo el archivo Excel: " + ex.getMessage())
                    .build();
        }

        totalDebit = BigDecimalUtils.scale(totalDebit);
        totalCredit = BigDecimalUtils.scale(totalCredit);

        // Validación de partida doble para todo el archivo
        if (!BigDecimalUtils.areEqual(totalDebit, totalCredit)) {
            errors.add(RowErrorDetail.builder()
                    .rowNumber(0)
                    .field("balance")
                    .message(String.format("El lote completo está descuadrado. Total Débitos: %s, Total Créditos: %s, Diferencia: %s",
                            totalDebit, totalCredit, totalDebit.subtract(totalCredit).abs()))
                    .rejectedValue("Débitos: " + totalDebit + " vs Créditos: " + totalCredit)
                    .build());
        }

        if (!errors.isEmpty()) {
            return ImportResultResponse.builder()
                    .success(false)
                    .voucherNumber(voucherNumber)
                    .totalRowsProcessed(processedRows)
                    .totalDebit(totalDebit)
                    .totalCredit(totalCredit)
                    .message(String.format("El archivo contiene %d error(es). Se ha cancelado la importación y revertido todos los cambios.", errors.size()))
                    .errors(errors)
                    .build();
        }

        // Persistir comprobante y asientos
        JournalVoucherRequest voucherRequest = JournalVoucherRequest.builder()
                .voucherNumber(voucherNumber)
                .voucherType(voucherType != null ? voucherType : VoucherType.NOTA_CONTABLE)
                .issueDate(issueDate != null ? issueDate : LocalDate.now())
                .description(description != null && !description.isBlank() ? description : "Importación masiva desde Excel")
                .status(VoucherStatus.POSTED)
                .entries(entries)
                .build();

        JournalVoucherResponse savedVoucher = voucherService.createAndPostVoucher(voucherRequest);

        return ImportResultResponse.builder()
                .success(true)
                .voucherId(savedVoucher.getId())
                .voucherNumber(savedVoucher.getVoucherNumber())
                .totalRowsProcessed(processedRows)
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .message("Importación exitosa y comprobante asentado en estado POSTED con partida doble.")
                .errors(List.of())
                .build();
    }

    private Map<String, Integer> detectHeaders(Row row, DataFormatter formatter) {
        Map<String, Integer> map = new HashMap<>();
        for (Cell cell : row) {
            String text = formatter.formatCellValue(cell).toLowerCase().trim();
            if (text.contains("cuenta") || text.contains("codigo") || text.contains("puc")) {
                map.put("account", cell.getColumnIndex());
            } else if (text.contains("tercero") || text.contains("nit") || text.contains("cedula") || text.contains("identificacion")) {
                map.put("third_party", cell.getColumnIndex());
            } else if (text.contains("nombre") || text.contains("razon")) {
                map.put("third_party_name", cell.getColumnIndex());
            } else if (text.contains("ref") || text.contains("doc") || text.contains("recibo")) {
                map.put("reference", cell.getColumnIndex());
            } else if (text.contains("desc") || text.contains("concepto") || text.contains("detalle")) {
                map.put("description", cell.getColumnIndex());
            } else if (text.contains("deb") || text.contains("debe")) {
                map.put("debit", cell.getColumnIndex());
            } else if (text.contains("cred") || text.contains("haber")) {
                map.put("credit", cell.getColumnIndex());
            }
        }
        return map;
    }

    private String getCellValueAsString(Row row, Integer colIndex, DataFormatter formatter) {
        if (colIndex == null || colIndex < 0) return null;
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;
        return formatter.formatCellValue(cell);
    }

    private BigDecimal parseNumericCell(Row row, Integer colIndex, DataFormatter formatter) {
        if (colIndex == null || colIndex < 0) return BigDecimalUtils.ZERO;
        Cell cell = row.getCell(colIndex);
        if (cell == null) return BigDecimalUtils.ZERO;

        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimalUtils.scale(BigDecimal.valueOf(cell.getNumericCellValue()));
        }

        String str = formatter.formatCellValue(cell);
        if (str == null || str.isBlank()) {
            return BigDecimalUtils.ZERO;
        }

        try {
            // Limpiar símbolos de moneda y separadores de miles
            String cleaned = str.replace("$", "").replace(" ", "").replace(",", ".").trim();
            return BigDecimalUtils.scale(new BigDecimal(cleaned));
        } catch (Exception e) {
            return BigDecimalUtils.ZERO;
        }
    }
}
