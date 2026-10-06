package com.app.accounting.common.document;

import com.app.accounting.associate.dto.response.AssociateReceiptResponse;
import com.app.accounting.associate.dto.response.AssociateStatementResponse;
import com.app.accounting.billing.dto.response.CustomerStatementResponse;
import com.app.accounting.billing.dto.response.DebtDocumentResponse;
import com.app.accounting.common.exception.BusinessRuleException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class ExcelExportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Genera el Estado de Cuenta de Cartera (Clientes / Proveedores) en Excel (.xlsx)
     */
    public byte[] generateCustomerStatementExcel(CustomerStatementResponse statement) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Estado de Cartera");
            sheet.setDisplayGridlines(true);

            // Estilos
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle subtitleStyle = createSubtitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle bodyStyle = createBodyStyle(workbook);
            CellStyle boldStyle = createBoldStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook, false);
            CellStyle currencyBoldStyle = createCurrencyStyle(workbook, true);

            int rowIdx = 0;

            // Título
            Row titleRow = sheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("DATAICO S.A.S. - ESTADO DE CUENTA DE CARTERA");
            titleCell.setCellStyle(titleStyle);

            Row subRow = sheet.createRow(rowIdx++);
            Cell subCell = subRow.createCell(0);
            subCell.setCellValue("Generado el: " + LocalDate.now().format(DATE_FORMATTER));
            subCell.setCellStyle(subtitleStyle);

            rowIdx++; // Espacio

            // Información del Cliente
            createLabelValueRow(sheet, rowIdx++, "Cliente / Razón Social:", statement.getFullName(), boldStyle, bodyStyle);
            createLabelValueRow(sheet, rowIdx++, "NIT / Cédula:", statement.getIdentificationNumber(), boldStyle, bodyStyle);
            createLabelValueRow(sheet, rowIdx++, "Teléfono:", statement.getPhone() != null ? statement.getPhone() : "N/A", boldStyle, bodyStyle);
            createLabelValueRow(sheet, rowIdx++, "Correo:", statement.getEmail() != null ? statement.getEmail() : "N/A", boldStyle, bodyStyle);

            rowIdx++; // Espacio

            // Resumen Financiero (KPIs)
            Row kpiHeaderRow = sheet.createRow(rowIdx++);
            createHeaderCell(kpiHeaderRow, 0, "Total Facturado", headerStyle);
            createHeaderCell(kpiHeaderRow, 1, "Total Pagado / Abonos", headerStyle);
            createHeaderCell(kpiHeaderRow, 2, "Saldo Actual Pendiente", headerStyle);
            createHeaderCell(kpiHeaderRow, 3, "Total Facturas", headerStyle);

            Row kpiValRow = sheet.createRow(rowIdx++);
            setCurrencyCell(kpiValRow, 0, statement.getTotalInvoiced(), currencyBoldStyle);
            setCurrencyCell(kpiValRow, 1, statement.getTotalPaid(), currencyBoldStyle);
            setCurrencyCell(kpiValRow, 2, statement.getCurrentBalance(), currencyBoldStyle);
            Cell tfCell = kpiValRow.createCell(3);
            tfCell.setCellValue(statement.getTotalInvoices());
            tfCell.setCellStyle(boldStyle);

            rowIdx += 2; // Espacio

            // Tabla de Facturas
            Row tableHeader = sheet.createRow(rowIdx++);
            String[] headers = {"No. Documento", "Fecha Emisión", "Fecha Vencimiento", "Subtotal", "Impuestos", "Retenciones", "Total", "Saldo Pendiente", "Estado"};
            for (int i = 0; i < headers.length; i++) {
                createHeaderCell(tableHeader, i, headers[i], headerStyle);
            }

            if (statement.getDocuments() != null) {
                for (DebtDocumentResponse doc : statement.getDocuments()) {
                    Row r = sheet.createRow(rowIdx++);
                    createCell(r, 0, doc.getDocumentNumber(), bodyStyle);
                    createCell(r, 1, doc.getIssueDate() != null ? doc.getIssueDate().format(DATE_FORMATTER) : "", bodyStyle);
                    createCell(r, 2, doc.getDueDate() != null ? doc.getDueDate().format(DATE_FORMATTER) : "", bodyStyle);
                    setCurrencyCell(r, 3, doc.getSubtotal(), currencyStyle);
                    setCurrencyCell(r, 4, doc.getTaxTotal(), currencyStyle);
                    setCurrencyCell(r, 5, doc.getRetentionTotal(), currencyStyle);
                    setCurrencyCell(r, 6, doc.getTotalAmount(), currencyStyle);
                    setCurrencyCell(r, 7, doc.getRemainingBalance(), currencyBoldStyle);
                    createCell(r, 8, doc.getStatus() != null ? doc.getStatus().name() : "", bodyStyle);
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessRuleException("Error generando Excel de estado de cuenta de cliente: " + e.getMessage());
        }
    }

    /**
     * Genera el Estado de Cuenta de Asociado en Excel (.xlsx)
     */
    public byte[] generateAssociateStatementExcel(AssociateStatementResponse statement) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Estado de Asociado");
            sheet.setDisplayGridlines(true);

            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle subtitleStyle = createSubtitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle bodyStyle = createBodyStyle(workbook);
            CellStyle boldStyle = createBoldStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook, false);
            CellStyle currencyBoldStyle = createCurrencyStyle(workbook, true);

            int rowIdx = 0;

            // Encabezado
            Row titleRow = sheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("ESTADO DE CUENTA INDIVIDUAL DE ASOCIADO - KONTA ERP");
            titleCell.setCellStyle(titleStyle);

            Row subRow = sheet.createRow(rowIdx++);
            Cell subCell = subRow.createCell(0);
            subCell.setCellValue("Generado el: " + LocalDate.now().format(DATE_FORMATTER));
            subCell.setCellStyle(subtitleStyle);

            rowIdx++;

            // Información Asociado
            createLabelValueRow(sheet, rowIdx++, "Asociado:", statement.getFullName(), boldStyle, bodyStyle);
            createLabelValueRow(sheet, rowIdx++, "Cédula / Identificación:", statement.getIdentificationNumber(), boldStyle, bodyStyle);
            createLabelValueRow(sheet, rowIdx++, "Teléfono:", statement.getPhone() != null ? statement.getPhone() : "N/A", boldStyle, bodyStyle);
            createLabelValueRow(sheet, rowIdx++, "Correo Electrónico:", statement.getEmail() != null ? statement.getEmail() : "N/A", boldStyle, bodyStyle);

            rowIdx++;

            // Resumen de Aportes
            Row kpiHeaderRow = sheet.createRow(rowIdx++);
            createHeaderCell(kpiHeaderRow, 0, "Aportes Sociales (Patrimonio)", headerStyle);
            createHeaderCell(kpiHeaderRow, 1, "Fondos Sociales", headerStyle);
            createHeaderCell(kpiHeaderRow, 2, "Cuotas de Administración", headerStyle);
            createHeaderCell(kpiHeaderRow, 3, "Total Global Aportado", headerStyle);

            Row kpiValRow = sheet.createRow(rowIdx++);
            setCurrencyCell(kpiValRow, 0, statement.getTotalEquitySavings(), currencyBoldStyle);
            setCurrencyCell(kpiValRow, 1, statement.getTotalSocialFunds(), currencyBoldStyle);
            setCurrencyCell(kpiValRow, 2, statement.getTotalAdministrationFees(), currencyBoldStyle);
            setCurrencyCell(kpiValRow, 3, statement.getTotalPaidOverall(), currencyBoldStyle);

            rowIdx += 2;

            // Histórico de Recaudos
            Row tableHeader = sheet.createRow(rowIdx++);
            String[] headers = {"Recibo No.", "Fecha de Pago", "Cuenta Destino", "Total Abonado"};
            for (int i = 0; i < headers.length; i++) {
                createHeaderCell(tableHeader, i, headers[i], headerStyle);
            }

            if (statement.getReceiptsHistory() != null) {
                for (AssociateReceiptResponse rec : statement.getReceiptsHistory()) {
                    Row r = sheet.createRow(rowIdx++);
                    createCell(r, 0, rec.getReceiptNumber(), bodyStyle);
                    createCell(r, 1, rec.getPaymentDate() != null ? rec.getPaymentDate().format(DATE_FORMATTER) : "", bodyStyle);
                    createCell(r, 2, rec.getCashAccountCode() != null ? rec.getCashAccountCode() + " - " + rec.getCashAccountName() : "CAJA", bodyStyle);
                    setCurrencyCell(r, 3, rec.getTotalAmount(), currencyBoldStyle);
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessRuleException("Error generando Excel de estado de cuenta de asociado: " + e.getMessage());
        }
    }

    // --- Helpers de Estilos Apache POI ---

    private CellStyle createTitleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setFontHeightInPoints((short) 14);
        font.setBold(true);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        return style;
    }

    private CellStyle createSubtitleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setFontHeightInPoints((short) 9);
        font.setItalic(true);
        font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFont(font);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setFontHeightInPoints((short) 9);
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createBodyStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setFontHeightInPoints((short) 9);
        style.setFont(font);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createBoldStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setFontHeightInPoints((short) 9);
        font.setBold(true);
        style.setFont(font);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createCurrencyStyle(Workbook wb, boolean bold) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setFontHeightInPoints((short) 9);
        font.setBold(bold);
        style.setFont(font);
        DataFormat format = wb.createDataFormat();
        style.setDataFormat(format.getFormat("$#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private void createHeaderCell(Row row, int colIdx, String text, CellStyle style) {
        Cell cell = row.createCell(colIdx);
        cell.setCellValue(text);
        cell.setCellStyle(style);
    }

    private void createCell(Row row, int colIdx, String text, CellStyle style) {
        Cell cell = row.createCell(colIdx);
        cell.setCellValue(text != null ? text : "");
        cell.setCellStyle(style);
    }

    private void setCurrencyCell(Row row, int colIdx, BigDecimal amount, CellStyle style) {
        Cell cell = row.createCell(colIdx);
        if (amount != null) {
            cell.setCellValue(amount.doubleValue());
        } else {
            cell.setCellValue(0.0);
        }
        cell.setCellStyle(style);
    }

    private void createLabelValueRow(Sheet sheet, int rowIdx, String label, String value, CellStyle lblStyle, CellStyle valStyle) {
        Row row = sheet.createRow(rowIdx);
        Cell c1 = row.createCell(0);
        c1.setCellValue(label);
        c1.setCellStyle(lblStyle);

        Cell c2 = row.createCell(1);
        c2.setCellValue(value != null ? value : "");
        c2.setCellStyle(valStyle);
    }
}
