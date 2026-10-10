package com.app.accounting.report.service;

import com.app.accounting.report.dto.response.FinancialReportResponse;
import com.app.accounting.report.dto.response.FinancialReportResponse.Column;
import com.app.accounting.report.dto.response.FinancialReportResponse.Row;
import com.app.accounting.report.dto.response.FinancialReportResponse.SummaryItem;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Exporta un {@link FinancialReportResponse} a PDF (documento tabular real, no captura de pantalla) y a Excel.
 */
@Service
public class ReportExportService {

    private static final Locale ES_CO = Locale.of("es", "CO");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", ES_CO);

    private static final Color INK = new Color(15, 23, 42);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color HEADER_BG = new Color(30, 41, 59);
    private static final Color GROUP_BG = new Color(241, 245, 249);
    private static final Color SUBTOTAL_BG = new Color(226, 232, 240);
    private static final Color BORDER = new Color(203, 213, 225);

    // ------------------------------------------------------------------ PDF

    public byte[] toPdf(FinancialReportResponse report) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            boolean landscape = report.getColumns().size() > 6;
            Document doc = new Document(landscape ? PageSize.A4.rotate() : PageSize.A4, 28, 28, 30, 36);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new Footer(report));
            doc.open();

            Font org = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, INK);
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, INK);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED);
            Font smallBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, INK);

            PdfPTable header = new PdfPTable(new float[]{3f, 2f});
            header.setWidthPercentage(100);
            PdfPCell left = new PdfPCell();
            left.setBorder(Rectangle.NO_BORDER);
            left.addElement(new Paragraph(nz(report.getOrganizationName()).toUpperCase(ES_CO), org));
            if (report.getOrganizationNit() != null && !report.getOrganizationNit().isBlank()) {
                left.addElement(new Paragraph("NIT " + report.getOrganizationNit(), small));
            }
            left.addElement(new Paragraph(report.getTitle(), title));
            if (report.getSubtitle() != null) {
                left.addElement(new Paragraph(report.getSubtitle(), small));
            }
            header.addCell(left);
            PdfPCell right = new PdfPCell();
            right.setBorder(Rectangle.NO_BORDER);
            right.setHorizontalAlignment(Element.ALIGN_RIGHT);
            Paragraph period = new Paragraph("Periodo: " + fmtDate(report.getPeriodFrom()) + " - " + fmtDate(report.getPeriodTo()), smallBold);
            period.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(period);
            Paragraph gen = new Paragraph("Generado: " + report.getGeneratedAt().format(DATE_TIME), small);
            gen.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(gen);
            Paragraph by = new Paragraph("Usuario: " + nz(report.getGeneratedBy()), small);
            by.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(by);
            for (Map.Entry<String, String> fl : report.getFilters().entrySet()) {
                Paragraph p = new Paragraph(fl.getKey() + ": " + fl.getValue(), small);
                p.setAlignment(Element.ALIGN_RIGHT);
                right.addElement(p);
            }
            header.addCell(right);
            doc.add(header);
            doc.add(new Paragraph(" ", small));

            // Resumen
            if (!report.getSummary().isEmpty()) {
                PdfPTable summary = new PdfPTable(report.getSummary().size());
                summary.setWidthPercentage(100);
                for (SummaryItem s : report.getSummary()) {
                    PdfPCell c = new PdfPCell();
                    c.setBorderColor(BORDER);
                    c.setPadding(6);
                    c.addElement(new Paragraph(s.getLabel(), small));
                    Paragraph v = new Paragraph("NUMBER".equals(s.getType()) ? number(s.getValue()) : money(s.getValue()),
                            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, INK));
                    c.addElement(v);
                    summary.addCell(c);
                }
                doc.add(summary);
                doc.add(new Paragraph(" ", small));
            }

            // Detalle
            List<Column> cols = report.getColumns();
            PdfPTable table = new PdfPTable(cols.size());
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            table.setWidths(widths(cols));
            Font th = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, Color.WHITE);
            Font td = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, INK);
            Font tdBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, INK);
            for (Column c : cols) {
                PdfPCell cell = new PdfPCell(new Phrase(c.getLabel(), th));
                cell.setBackgroundColor(HEADER_BG);
                cell.setPadding(4);
                cell.setHorizontalAlignment(isNumeric(c) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                table.addCell(cell);
            }
            if (report.getRows().isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase("Sin movimientos para los filtros seleccionados", td));
                empty.setColspan(cols.size());
                empty.setPadding(8);
                empty.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(empty);
            }
            for (Row row : report.getRows()) {
                boolean strong = !"DETAIL".equals(row.getRowType());
                Color bg = "GROUP".equals(row.getRowType()) ? GROUP_BG : "SUBTOTAL".equals(row.getRowType()) ? SUBTOTAL_BG : null;
                for (Column c : cols) {
                    PdfPCell cell = new PdfPCell(new Phrase(format(c, row.getCells().get(c.getKey())), strong ? tdBold : td));
                    cell.setPadding(3.5f);
                    cell.setBorderColor(BORDER);
                    cell.setHorizontalAlignment(isNumeric(c) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                    if (bg != null) {
                        cell.setBackgroundColor(bg);
                    }
                    table.addCell(cell);
                }
            }
            if (!report.getTotals().isEmpty()) {
                for (int i = 0; i < cols.size(); i++) {
                    Column c = cols.get(i);
                    Object v = report.getTotals().get(c.getKey());
                    String text = i == 0 ? "TOTALES" : v != null ? format(c, v) : "";
                    PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE)));
                    cell.setBackgroundColor(HEADER_BG);
                    cell.setPadding(4);
                    cell.setHorizontalAlignment(isNumeric(c) && i > 0 ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                    table.addCell(cell);
                }
            }
            doc.add(table);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF del reporte: " + e.getMessage(), e);
        }
    }

    private static final class Footer extends PdfPageEventHelper {
        private final FinancialReportResponse report;

        private Footer(FinancialReportResponse report) {
            this.report = report;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Font f = FontFactory.getFont(FontFactory.HELVETICA, 7, MUTED);
            Phrase p = new Phrase(report.getTitle() + " · " + nz(report.getOrganizationName()) + " · Página " + writer.getPageNumber(), f);
            com.lowagie.text.pdf.ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER, p,
                    (document.left() + document.right()) / 2, document.bottom() - 18, 0);
        }
    }

    // ------------------------------------------------------------------ Excel

    public byte[] toExcel(FinancialReportResponse report) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(safeSheetName(report.getCode()));
            DataFormat df = wb.createDataFormat();

            org.apache.poi.ss.usermodel.Font bold = wb.createFont();
            bold.setBold(true);
            org.apache.poi.ss.usermodel.Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            org.apache.poi.ss.usermodel.Font white = wb.createFont();
            white.setBold(true);
            white.setColor(IndexedColors.WHITE.getIndex());

            CellStyle titleStyle = wb.createCellStyle();
            titleStyle.setFont(titleFont);
            CellStyle boldStyle = wb.createCellStyle();
            boldStyle.setFont(bold);
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(white);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_80_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle moneyStyle = wb.createCellStyle();
            moneyStyle.setDataFormat(df.getFormat("$#,##0;[Red]-$#,##0"));
            CellStyle moneyBold = wb.createCellStyle();
            moneyBold.cloneStyleFrom(moneyStyle);
            moneyBold.setFont(bold);
            moneyBold.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            moneyBold.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle textBold = wb.createCellStyle();
            textBold.setFont(bold);
            textBold.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            textBold.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(df.getFormat("dd/mm/yyyy"));
            CellStyle numberStyle = wb.createCellStyle();
            numberStyle.setDataFormat(df.getFormat("#,##0"));

            int r = 0;
            set(sheet.createRow(r++), 0, nz(report.getOrganizationName()).toUpperCase(ES_CO), boldStyle);
            set(sheet.createRow(r++), 0, report.getTitle(), titleStyle);
            if (report.getSubtitle() != null) {
                set(sheet.createRow(r++), 0, report.getSubtitle(), null);
            }
            set(sheet.createRow(r++), 0, "Periodo: " + fmtDate(report.getPeriodFrom()) + " - " + fmtDate(report.getPeriodTo()), boldStyle);
            set(sheet.createRow(r++), 0, "Generado: " + report.getGeneratedAt().format(DATE_TIME) + " · Usuario: " + nz(report.getGeneratedBy()), null);
            for (Map.Entry<String, String> fl : report.getFilters().entrySet()) {
                set(sheet.createRow(r++), 0, fl.getKey() + ": " + fl.getValue(), null);
            }
            r++;
            for (SummaryItem s : report.getSummary()) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r++);
                set(row, 0, s.getLabel(), boldStyle);
                Cell v = row.createCell(1);
                v.setCellValue(s.getValue() != null ? s.getValue().doubleValue() : 0);
                v.setCellStyle("NUMBER".equals(s.getType()) ? numberStyle : moneyStyle);
            }
            if (!report.getSummary().isEmpty()) {
                r++;
            }

            List<Column> cols = report.getColumns();
            org.apache.poi.ss.usermodel.Row head = sheet.createRow(r++);
            for (int i = 0; i < cols.size(); i++) {
                set(head, i, cols.get(i).getLabel(), headerStyle);
            }
            int firstDataRow = r;
            for (Row row : report.getRows()) {
                org.apache.poi.ss.usermodel.Row x = sheet.createRow(r++);
                boolean strong = !"DETAIL".equals(row.getRowType());
                for (int i = 0; i < cols.size(); i++) {
                    Column c = cols.get(i);
                    Object v = row.getCells().get(c.getKey());
                    Cell cell = x.createCell(i);
                    writeValue(cell, c, v, strong ? (isNumeric(c) ? moneyBold : textBold) : null, moneyStyle, dateStyle, numberStyle);
                }
            }
            if (!report.getTotals().isEmpty()) {
                org.apache.poi.ss.usermodel.Row tot = sheet.createRow(r);
                set(tot, 0, "TOTALES", textBold);
                for (int i = 1; i < cols.size(); i++) {
                    Object v = report.getTotals().get(cols.get(i).getKey());
                    Cell cell = tot.createCell(i);
                    cell.setCellStyle(textBold);
                    if (v instanceof BigDecimal bd) {
                        cell.setCellValue(bd.doubleValue());
                        cell.setCellStyle(moneyBold);
                    }
                }
            }
            if (r > firstDataRow) {
                sheet.setAutoFilter(new CellRangeAddress(firstDataRow - 1, r - 1, 0, cols.size() - 1));
            }
            sheet.createFreezePane(0, firstDataRow);
            for (int i = 0; i < cols.size(); i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.min(Math.max(sheet.getColumnWidth(i), 3000), 14000));
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el Excel del reporte: " + e.getMessage(), e);
        }
    }

    private static void writeValue(Cell cell, Column c, Object v, CellStyle strongStyle, CellStyle money, CellStyle date,
                                   CellStyle number) {
        if (v == null) {
            if (strongStyle != null) cell.setCellStyle(strongStyle);
            return;
        }
        switch (c.getType()) {
            case "MONEY" -> {
                cell.setCellValue(new BigDecimal(v.toString()).doubleValue());
                cell.setCellStyle(strongStyle != null ? strongStyle : money);
            }
            case "NUMBER", "PERCENT" -> {
                try {
                    cell.setCellValue(new BigDecimal(v.toString()).doubleValue());
                    cell.setCellStyle(strongStyle != null ? strongStyle : number);
                } catch (NumberFormatException e) {
                    cell.setCellValue(v.toString());
                }
            }
            case "DATE" -> {
                if (v instanceof LocalDate d) {
                    cell.setCellValue(d);
                    cell.setCellStyle(date);
                } else {
                    cell.setCellValue(v.toString());
                }
            }
            default -> {
                cell.setCellValue(v.toString());
                if (strongStyle != null) cell.setCellStyle(strongStyle);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static float[] widths(List<Column> cols) {
        float[] w = new float[cols.size()];
        for (int i = 0; i < cols.size(); i++) {
            Column c = cols.get(i);
            w[i] = switch (c.getType()) {
                case "MONEY" -> 1.3f;
                case "DATE" -> 1.0f;
                case "NUMBER", "PERCENT" -> 0.8f;
                default -> c.getKey().equals("concept") || c.getKey().equals("party") || c.getKey().equals("account") || c.getKey().equals("detail") ? 2.4f : 1.2f;
            };
        }
        return w;
    }

    private static boolean isNumeric(Column c) {
        return "MONEY".equals(c.getType()) || "NUMBER".equals(c.getType()) || "PERCENT".equals(c.getType());
    }

    static String format(Column c, Object v) {
        if (v == null) {
            return "";
        }
        return switch (c.getType()) {
            case "MONEY" -> money(new BigDecimal(v.toString()));
            case "DATE" -> v instanceof LocalDate d ? d.format(DATE) : v.toString();
            case "PERCENT" -> v.toString().matches("-?\\d+(\\.\\d+)?") ? new BigDecimal(v.toString()).stripTrailingZeros().toPlainString() + " %" : v.toString();
            case "NUMBER" -> v.toString().matches("-?\\d+(\\.\\d+)?") ? number(new BigDecimal(v.toString())) : v.toString();
            default -> v.toString();
        };
    }

    /** Formato es-CO: $1.250.000 (negativos con signo antes del símbolo). */
    static String money(BigDecimal value) {
        if (value == null) {
            return "$0";
        }
        DecimalFormat f = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(ES_CO));
        String s = f.format(value.abs().setScale(0, java.math.RoundingMode.HALF_UP));
        return (value.signum() < 0 ? "-$" : "$") + s;
    }

    static String number(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(ES_CO)).format(value);
    }

    private static String fmtDate(LocalDate d) {
        return d == null ? "" : d.format(DATE);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static void set(org.apache.poi.ss.usermodel.Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        if (style != null) {
            c.setCellStyle(style);
        }
    }

    private static String safeSheetName(String code) {
        String s = code == null ? "Reporte" : code.replaceAll("[\\\\/?*\\[\\]:]", "_");
        return s.length() > 31 ? s.substring(0, 31) : s;
    }
}
