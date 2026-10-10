package com.app.accounting.common.document;

import com.app.accounting.associate.dto.response.AssociateReceiptResponse;
import com.app.accounting.associate.dto.response.AssociateReceiptResponse.AssociateReceiptItemResponse;
import com.app.accounting.associate.dto.response.AssociateStatementResponse;
import com.app.accounting.billing.dto.response.CustomerStatementResponse;
import com.app.accounting.billing.dto.response.DebtDocumentResponse;
import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.treasury.entity.PaymentReceipt;
import com.app.accounting.treasury.entity.PaymentType;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class PdfExportService {

    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Colors
    private static final Color PRIMARY_COLOR = new Color(30, 58, 138); // Deep Navy
    private static final Color SECONDARY_COLOR = new Color(51, 65, 85); // Slate
    private static final Color BG_LIGHT = new Color(248, 250, 252);
    private static final Color BG_HEADER = new Color(241, 245, 249);
    private static final Color BORDER_COLOR = new Color(203, 213, 225);

    // Fonts
    private static final Font FONT_TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, PRIMARY_COLOR);
    private static final Font FONT_SUBTITLE = FontFactory.getFont(FontFactory.HELVETICA, 9, SECONDARY_COLOR);
    private static final Font FONT_SECTION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, PRIMARY_COLOR);
    private static final Font FONT_HEADER_CELL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
    private static final Font FONT_BODY = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
    private static final Font FONT_BODY_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.BLACK);
    private static final Font FONT_MUTED = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7, Color.GRAY);

    /**
     * Genera el PDF oficial de Recibo de Caja (CASH_IN) o Comprobante de Egreso (CASH_OUT)
     */
    public byte[] generatePaymentReceiptPdf(PaymentReceipt receipt) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            boolean isCashIn = receipt.getPaymentType() == PaymentType.CASH_IN;
            String title = isCashIn ? "RECIBO DE CAJA / INGRESO" : "COMPROBANTE DE EGRESO";

            // 1. Header con datos de la empresa y número de comprobante
            addCompanyHeader(document, title, receipt.getReceiptNumber(), receipt.getPaymentDate());

            // 2. Información del Tercero
            PdfPTable partyTable = new PdfPTable(2);
            partyTable.setWidthPercentage(100);
            partyTable.setSpacingBefore(10f);
            partyTable.setSpacingAfter(10f);

            var doc = receipt.getAppliedToDocument();
            var party = doc != null ? doc.getThirdParty() : null;

            String partyName = party != null ? party.getFullName() : "TERCERO GENERAL";
            String partyDoc = party != null ? party.getIdentificationNumber() + (party.getDv() != null ? "-" + party.getDv() : "") : "N/A";
            String partyPhone = party != null && party.getPhone() != null ? party.getPhone() : "N/A";
            String partyEmail = party != null && party.getEmail() != null ? party.getEmail() : "N/A";

            addInfoCell(partyTable, isCashIn ? "Recibido de:" : "Pagado a:", partyName);
            addInfoCell(partyTable, "NIT / Cédula:", partyDoc);
            addInfoCell(partyTable, "Teléfono:", partyPhone);
            addInfoCell(partyTable, "Correo Electrónico:", partyEmail);
            document.add(partyTable);

            // 3. Detalle de la Transacción / Documento Amortizado
            Paragraph sectionTitle = new Paragraph("DETALLE DE LA APLICACIÓN / ABONO", FONT_SECTION);
            sectionTitle.setSpacingAfter(6f);
            document.add(sectionTitle);

            PdfPTable detailTable = new PdfPTable(6);
            detailTable.setWidthPercentage(100);
            detailTable.setWidths(new float[]{2.5f, 2.0f, 2.0f, 2.0f, 2.0f, 2.0f});

            addHeaderCell(detailTable, "Documento");
            addHeaderCell(detailTable, "Tipo");
            addHeaderCell(detailTable, "Valor Doc.");
            addHeaderCell(detailTable, "Saldo Anterior");
            addHeaderCell(detailTable, "Valor Abonado");
            addHeaderCell(detailTable, "Nuevo Saldo");

            BigDecimal amountPaid = receipt.getAmountPaid() != null ? receipt.getAmountPaid() : BigDecimal.ZERO;
            BigDecimal totalDoc = doc != null ? doc.getTotalAmount() : amountPaid;
            BigDecimal remaining = doc != null ? doc.getRemainingBalance() : BigDecimal.ZERO;
            BigDecimal priorBalance = remaining.add(amountPaid);

            addBodyCell(detailTable, doc != null ? doc.getDocumentNumber() : "PAGO DIRECTO", Element.ALIGN_LEFT);
            addBodyCell(detailTable, doc != null ? doc.getType().name() : "TESORERIA", Element.ALIGN_CENTER);
            addBodyCell(detailTable, formatCurrency(totalDoc), Element.ALIGN_RIGHT);
            addBodyCell(detailTable, formatCurrency(priorBalance), Element.ALIGN_RIGHT);
            addBodyBoldCell(detailTable, formatCurrency(amountPaid), Element.ALIGN_RIGHT);
            addBodyCell(detailTable, formatCurrency(remaining), Element.ALIGN_RIGHT);

            document.add(detailTable);

            // 4. Cuenta Bancaria / Caja y Comprobante Contable
            PdfPTable accountTable = new PdfPTable(2);
            accountTable.setWidthPercentage(100);
            accountTable.setSpacingBefore(10f);
            accountTable.setSpacingAfter(15f);

            String cashAcc = receipt.getCashOrBankAccount() != null
                    ? receipt.getCashOrBankAccount().getCode() + " - " + receipt.getCashOrBankAccount().getName()
                    : "CAJA PRINCIPAL";
            String voucherNum = receipt.getVoucher() != null ? receipt.getVoucher().getVoucherNumber() : "AUTOMATICO";

            addInfoCell(accountTable, "Medio de Pago / Cuenta:", cashAcc);
            addInfoCell(accountTable, "Comprobante Contable:", voucherNum);
            document.add(accountTable);

            // 5. Total Destacado
            PdfPTable totalTable = new PdfPTable(1);
            totalTable.setWidthPercentage(100);
            PdfPCell totalCell = new PdfPCell(new Phrase("TOTAL ABONADO: " + formatCurrency(amountPaid), FONT_SECTION));
            totalCell.setBackgroundColor(BG_LIGHT);
            totalCell.setBorderColor(BORDER_COLOR);
            totalCell.setPadding(8f);
            totalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalTable.addCell(totalCell);
            document.add(totalTable);

            // 6. Firmas
            addSignatureBlock(document, isCashIn ? "Firma del Pagador / Cliente" : "Firma del Beneficiario", "Aprobado / Sello de Contabilidad");

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessRuleException("Error generando PDF de recibo de caja/egreso: " + e.getMessage());
        }
    }

    /**
     * Genera el PDF oficial del Recibo de Caja de Asociado (RB)
     */
    public byte[] generateAssociateReceiptPdf(
            String receiptNumber,
            LocalDate paymentDate,
            String associateName,
            String associateNit,
            String associatePhone,
            String associateEmail,
            String cashAccountCode,
            String cashAccountName,
            String voucherNumber,
            String templateName,
            BigDecimal totalAmount,
            List<AssociateReceiptItemResponse> items
    ) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            // Header
            addCompanyHeader(document, "RECIBO DE CAJA DE ASOCIADO (RB)", receiptNumber, paymentDate);

            // Información Asociado
            PdfPTable partyTable = new PdfPTable(2);
            partyTable.setWidthPercentage(100);
            partyTable.setSpacingBefore(10f);
            partyTable.setSpacingAfter(10f);

            addInfoCell(partyTable, "Asociado / Socio:", associateName);
            addInfoCell(partyTable, "Documento de Identidad:", associateNit);
            addInfoCell(partyTable, "Teléfono:", associatePhone != null ? associatePhone : "N/A");
            addInfoCell(partyTable, "Correo:", associateEmail != null ? associateEmail : "N/A");
            if (templateName != null) {
                addInfoCell(partyTable, "Plantilla Aplicada:", templateName);
                addInfoCell(partyTable, "Comprobante Contable:", voucherNumber != null ? voucherNumber : "ASIENTO AUTOMÁTICO");
            }
            document.add(partyTable);

            // Detalle de Conceptos Recaudados
            Paragraph sectionTitle = new Paragraph("DISCRIMINACIÓN DE CONCEPTOS Y APORTES", FONT_SECTION);
            sectionTitle.setSpacingAfter(6f);
            document.add(sectionTitle);

            PdfPTable itemsTable = new PdfPTable(4);
            itemsTable.setWidthPercentage(100);
            itemsTable.setWidths(new float[]{4.5f, 2.5f, 2.0f, 2.5f});

            addHeaderCell(itemsTable, "Concepto de Cuota / Aporte");
            addHeaderCell(itemsTable, "Categoría");
            addHeaderCell(itemsTable, "Cuenta PUC");
            addHeaderCell(itemsTable, "Valor Abonado");

            for (AssociateReceiptItemResponse item : items) {
                addBodyCell(itemsTable, item.getConceptName(), Element.ALIGN_LEFT);
                addBodyCell(itemsTable, translateCategory(item.getCategory() != null ? item.getCategory().name() : ""), Element.ALIGN_CENTER);
                addBodyCell(itemsTable, item.getAccountCode() != null ? item.getAccountCode() : "N/A", Element.ALIGN_CENTER);
                addBodyBoldCell(itemsTable, formatCurrency(item.getAmount()), Element.ALIGN_RIGHT);
            }

            document.add(itemsTable);

            // Total
            PdfPTable totalTable = new PdfPTable(1);
            totalTable.setWidthPercentage(100);
            totalTable.setSpacingBefore(8f);
            PdfPCell totalCell = new PdfPCell(new Phrase("TOTAL RECAUDADO: " + formatCurrency(totalAmount), FONT_SECTION));
            totalCell.setBackgroundColor(BG_LIGHT);
            totalCell.setBorderColor(BORDER_COLOR);
            totalCell.setPadding(8f);
            totalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalTable.addCell(totalCell);
            document.add(totalTable);

            // Cuenta
            Paragraph accountInfo = new Paragraph("Cuenta Destino: " + cashAccountCode + " - " + cashAccountName, FONT_SUBTITLE);
            accountInfo.setSpacingBefore(6f);
            document.add(accountInfo);

            // Firmas
            addSignatureBlock(document, "Firma del Asociado / Pagador", "Tesorería / Recaudador Autorizado");

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessRuleException("Error generando PDF de recibo de asociado: " + e.getMessage());
        }
    }

    /**
     * Genera el Estado de Cuenta de Cartera en PDF (Clientes / Proveedores)
     */
    public byte[] generateCustomerStatementPdf(CustomerStatementResponse statement) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            addCompanyHeader(document, "ESTADO DE CUENTA DE CARTERA", "EC-" + statement.getIdentificationNumber(), LocalDate.now());

            // Datos Cliente
            PdfPTable partyTable = new PdfPTable(2);
            partyTable.setWidthPercentage(100);
            partyTable.setSpacingBefore(10f);
            partyTable.setSpacingAfter(10f);

            addInfoCell(partyTable, "Cliente / Razón Social:", statement.getFullName());
            addInfoCell(partyTable, "NIT / Identificación:", statement.getIdentificationNumber());
            addInfoCell(partyTable, "Teléfono:", statement.getPhone() != null ? statement.getPhone() : "N/A");
            addInfoCell(partyTable, "Correo:", statement.getEmail() != null ? statement.getEmail() : "N/A");
            document.add(partyTable);

            // Resumen de Cartera (Kpis)
            PdfPTable kpiTable = new PdfPTable(3);
            kpiTable.setWidthPercentage(100);
            kpiTable.setSpacingAfter(12f);

            addKpiCell(kpiTable, "Total Facturado", formatCurrency(statement.getTotalInvoiced()));
            addKpiCell(kpiTable, "Total Pagado / Abonos", formatCurrency(statement.getTotalPaid()));
            addKpiCell(kpiTable, "Saldo Pendiente", formatCurrency(statement.getCurrentBalance()));
            document.add(kpiTable);

            // Facturas
            Paragraph titleDocs = new Paragraph("FACTURAS Y DOCUMENTOS DE COBRO", FONT_SECTION);
            titleDocs.setSpacingAfter(6f);
            document.add(titleDocs);

            PdfPTable docTable = new PdfPTable(6);
            docTable.setWidthPercentage(100);
            docTable.setWidths(new float[]{2.5f, 2.0f, 2.0f, 2.2f, 2.2f, 1.8f});

            addHeaderCell(docTable, "Número");
            addHeaderCell(docTable, "Emisión");
            addHeaderCell(docTable, "Vencimiento");
            addHeaderCell(docTable, "Total Factura");
            addHeaderCell(docTable, "Saldo Actual");
            addHeaderCell(docTable, "Estado");

            if (statement.getDocuments() != null) {
                for (DebtDocumentResponse d : statement.getDocuments()) {
                    addBodyCell(docTable, d.getDocumentNumber(), Element.ALIGN_LEFT);
                    addBodyCell(docTable, d.getIssueDate() != null ? d.getIssueDate().format(DATE_FORMATTER) : "", Element.ALIGN_CENTER);
                    addBodyCell(docTable, d.getDueDate() != null ? d.getDueDate().format(DATE_FORMATTER) : "", Element.ALIGN_CENTER);
                    addBodyCell(docTable, formatCurrency(d.getTotalAmount()), Element.ALIGN_RIGHT);
                    addBodyBoldCell(docTable, formatCurrency(d.getRemainingBalance()), Element.ALIGN_RIGHT);
                    addBodyCell(docTable, d.getStatus() != null ? d.getStatus().name() : "", Element.ALIGN_CENTER);
                }
            }

            document.add(docTable);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessRuleException("Error generando PDF de estado de cuenta de cliente: " + e.getMessage());
        }
    }

    /**
     * Genera el Estado de Cuenta de Asociado en PDF (Aportes y Fondos)
     */
    public byte[] generateAssociateStatementPdf(AssociateStatementResponse statement) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            addCompanyHeader(document, "ESTADO DE CUENTA INDIVIDUAL DE ASOCIADO", "ECA-" + statement.getIdentificationNumber(), LocalDate.now());

            // Datos
            PdfPTable partyTable = new PdfPTable(2);
            partyTable.setWidthPercentage(100);
            partyTable.setSpacingBefore(10f);
            partyTable.setSpacingAfter(10f);

            addInfoCell(partyTable, "Asociado:", statement.getFullName());
            addInfoCell(partyTable, "Cédula / NIT:", statement.getIdentificationNumber());
            addInfoCell(partyTable, "Teléfono:", statement.getPhone() != null ? statement.getPhone() : "N/A");
            addInfoCell(partyTable, "Correo:", statement.getEmail() != null ? statement.getEmail() : "N/A");
            document.add(partyTable);

            // KPIs
            PdfPTable kpiTable = new PdfPTable(4);
            kpiTable.setWidthPercentage(100);
            kpiTable.setSpacingAfter(12f);

            addKpiCell(kpiTable, "Aportes Sociales (Patrimonio)", formatCurrency(statement.getTotalEquitySavings()));
            addKpiCell(kpiTable, "Fondos Sociales", formatCurrency(statement.getTotalSocialFunds()));
            addKpiCell(kpiTable, "Cuotas Admin.", formatCurrency(statement.getTotalAdministrationFees()));
            addKpiCell(kpiTable, "Total Global Aportado", formatCurrency(statement.getTotalPaidOverall()));
            document.add(kpiTable);

            // Histórico de Recibos
            Paragraph titleDocs = new Paragraph("HISTORIAL DE RECAUDOS REGISTRADOS", FONT_SECTION);
            titleDocs.setSpacingAfter(6f);
            document.add(titleDocs);

            PdfPTable recTable = new PdfPTable(4);
            recTable.setWidthPercentage(100);
            recTable.setWidths(new float[]{2.5f, 2.5f, 3.5f, 2.5f});

            addHeaderCell(recTable, "Recibo No.");
            addHeaderCell(recTable, "Fecha de Pago");
            addHeaderCell(recTable, "Cuenta Recaudadora");
            addHeaderCell(recTable, "Valor Total");

            if (statement.getReceiptsHistory() != null) {
                for (AssociateReceiptResponse r : statement.getReceiptsHistory()) {
                    addBodyCell(recTable, r.getReceiptNumber(), Element.ALIGN_LEFT);
                    addBodyCell(recTable, r.getPaymentDate() != null ? r.getPaymentDate().format(DATE_FORMATTER) : "", Element.ALIGN_CENTER);
                    addBodyCell(recTable, r.getCashAccountCode() != null ? r.getCashAccountCode() + " - " + r.getCashAccountName() : "CAJA", Element.ALIGN_LEFT);
                    addBodyBoldCell(recTable, formatCurrency(r.getTotalAmount()), Element.ALIGN_RIGHT);
                }
            }

            document.add(recTable);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessRuleException("Error generando PDF de estado de cuenta de asociado: " + e.getMessage());
        }
    }

    /**
     * Genera el Certificado Oficial de Retenciones en la Fuente (Art. 381 E.T.)
     */
    public byte[] generateWithholdingCertificatePdf(
            int year,
            String partyName,
            String partyIdentification,
            List<WithholdingCertificateItem> items,
            BigDecimal totalWithheld
    ) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            // Header Oficial DIAN / Colombia
            Paragraph title = new Paragraph("CERTIFICADO DE RETENCIÓN EN LA FUENTE", FONT_TITLE);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            Paragraph yearSub = new Paragraph("AÑO GRAVABLE " + year, FONT_SECTION);
            yearSub.setAlignment(Element.ALIGN_CENTER);
            yearSub.setSpacingAfter(12f);
            document.add(yearSub);

            // Cuadro Retenedor y Sujeto Retenido
            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);
            infoTable.setSpacingAfter(15f);

            addInfoCell(infoTable, "AGENTE RETENEDOR:", "KONTA S.A.S.");
            addInfoCell(infoTable, "NIT RETENEDOR:", "901.234.567-8");
            addInfoCell(infoTable, "BENEFICIARIO DEL PAGO:", partyName);
            addInfoCell(infoTable, "NIT / CÉDULA BENEFICIARIO:", partyIdentification);
            addInfoCell(infoTable, "DIRECCIÓN / CIUDAD:", "BOGOTÁ D.C. - COLOMBIA");
            addInfoCell(infoTable, "FECHA DE EXPEDICIÓN:", LocalDate.now().format(DATE_FORMATTER));
            document.add(infoTable);

            // Tabla de Retenciones Practicadas
            Paragraph sectionTitle = new Paragraph("RETENCIONES PRACTICADAS DURANTE EL PERIODO", FONT_SECTION);
            sectionTitle.setSpacingAfter(6f);
            document.add(sectionTitle);

            PdfPTable itemsTable = new PdfPTable(4);
            itemsTable.setWidthPercentage(100);
            itemsTable.setWidths(new float[]{4.5f, 2.0f, 2.5f, 2.5f});

            addHeaderCell(itemsTable, "Concepto de Retención");
            addHeaderCell(itemsTable, "Cuenta PUC");
            addHeaderCell(itemsTable, "Base Gravable");
            addHeaderCell(itemsTable, "Valor Retenido");

            for (WithholdingCertificateItem item : items) {
                addBodyCell(itemsTable, item.conceptName(), Element.ALIGN_LEFT);
                addBodyCell(itemsTable, item.accountCode(), Element.ALIGN_CENTER);
                addBodyCell(itemsTable, formatCurrency(item.taxableBase()), Element.ALIGN_RIGHT);
                addBodyBoldCell(itemsTable, formatCurrency(item.withheldAmount()), Element.ALIGN_RIGHT);
            }

            document.add(itemsTable);

            // Total
            PdfPTable totalTable = new PdfPTable(1);
            totalTable.setWidthPercentage(100);
            totalTable.setSpacingBefore(8f);
            PdfPCell totalCell = new PdfPCell(new Phrase("TOTAL RETENIDO EN EL AÑO " + year + ": " + formatCurrency(totalWithheld), FONT_SECTION));
            totalCell.setBackgroundColor(BG_LIGHT);
            totalCell.setBorderColor(BORDER_COLOR);
            totalCell.setPadding(8f);
            totalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalTable.addCell(totalCell);
            document.add(totalTable);

            // Nota Legal Art. 381 E.T.
            Paragraph legalNote = new Paragraph(
                    "Este certificado se expide en cumplimiento de lo establecido en el Artículo 381 del Estatuto Tributario " +
                    "y no requiere de firma autógrafa conforme a lo estipulado en el Artículo 10 del Decreto 836 de 1991.",
                    FONT_MUTED
            );
            legalNote.setSpacingBefore(15f);
            legalNote.setAlignment(Element.ALIGN_JUSTIFIED);
            document.add(legalNote);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessRuleException("Error generando certificado de retención: " + e.getMessage());
        }
    }

    public record WithholdingCertificateItem(String conceptName, String accountCode, BigDecimal taxableBase, BigDecimal withheldAmount) {}

    // --- Helpers de Formato y Maquetación ---

    private void addCompanyHeader(Document document, String documentTitle, String docNumber, LocalDate date) throws DocumentException {
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{6.2f, 3.8f});

        // Columna Izquierda: Logo y Datos Empresa
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);

        Image logoImg = getLogoImage();
        if (logoImg != null) {
            PdfPTable brandTable = new PdfPTable(2);
            brandTable.setWidthPercentage(100);
            brandTable.setWidths(new float[]{2.0f, 8.0f});

            PdfPCell logoCell = new PdfPCell();
            logoCell.setBorder(Rectangle.NO_BORDER);
            logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            logoCell.setPaddingRight(6f);
            logoImg.scaleToFit(52f, 52f);
            logoCell.addElement(logoImg);
            brandTable.addCell(logoCell);

            PdfPCell textCell = new PdfPCell();
            textCell.setBorder(Rectangle.NO_BORDER);
            textCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            textCell.addElement(new Paragraph("KONTA S.A.S.", FONT_TITLE));
            textCell.addElement(new Paragraph("NIT: 901.234.567-8 | Régimen Común", FONT_SUBTITLE));
            textCell.addElement(new Paragraph("Cra 7 # 71-21 Torre B Of 801, Bogotá D.C.", FONT_SUBTITLE));
            textCell.addElement(new Paragraph("Tel: (601) 745-0000 | info@konta.com", FONT_SUBTITLE));
            brandTable.addCell(textCell);

            leftCell.addElement(brandTable);
        } else {
            leftCell.addElement(new Paragraph("KONTA S.A.S.", FONT_TITLE));
            leftCell.addElement(new Paragraph("NIT: 901.234.567-8 | Régimen Común", FONT_SUBTITLE));
            leftCell.addElement(new Paragraph("Cra 7 # 71-21 Torre B Of 801, Bogotá D.C.", FONT_SUBTITLE));
            leftCell.addElement(new Paragraph("Tel: (601) 745-0000 | info@konta.com", FONT_SUBTITLE));
        }
        headerTable.addCell(leftCell);

        // Columna Derecha: Número y Fecha
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.BOX);
        rightCell.setBorderColor(BORDER_COLOR);
        rightCell.setBackgroundColor(BG_LIGHT);
        rightCell.setPadding(8f);

        Paragraph pTitle = new Paragraph(documentTitle, FONT_SECTION);
        pTitle.setAlignment(Element.ALIGN_CENTER);
        rightCell.addElement(pTitle);

        Paragraph pNum = new Paragraph("No. " + docNumber, FONT_SECTION);
        pNum.setAlignment(Element.ALIGN_CENTER);
        rightCell.addElement(pNum);

        Paragraph pDate = new Paragraph("Fecha: " + (date != null ? date.format(DATE_FORMATTER) : ""), FONT_SUBTITLE);
        pDate.setAlignment(Element.ALIGN_CENTER);
        rightCell.addElement(pDate);

        headerTable.addCell(rightCell);
        document.add(headerTable);
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_HEADER_CELL));
        cell.setBackgroundColor(PRIMARY_COLOR);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(6f);
        cell.setBorderColor(Color.WHITE);
        table.addCell(cell);
    }

    private void addBodyCell(PdfPTable table, String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_BODY));
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5f);
        cell.setBorderColor(BORDER_COLOR);
        table.addCell(cell);
    }

    private void addBodyBoldCell(PdfPTable table, String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_BODY_BOLD));
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5f);
        cell.setBorderColor(BORDER_COLOR);
        table.addCell(cell);
    }

    private void addInfoCell(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.addElement(new Paragraph(label, FONT_BODY_BOLD));
        cell.addElement(new Paragraph(value, FONT_BODY));
        cell.setPadding(3f);
        table.addCell(cell);
    }

    private void addKpiCell(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(BG_LIGHT);
        cell.setBorderColor(BORDER_COLOR);
        cell.setPadding(8f);
        Paragraph pLabel = new Paragraph(label, FONT_SUBTITLE);
        Paragraph pVal = new Paragraph(value, FONT_SECTION);
        cell.addElement(pLabel);
        cell.addElement(pVal);
        table.addCell(cell);
    }

    private void addSignatureBlock(Document document, String sign1, String sign2) throws DocumentException {
        PdfPTable signTable = new PdfPTable(2);
        signTable.setWidthPercentage(100);
        signTable.setSpacingBefore(35f);

        PdfPCell cell1 = new PdfPCell();
        cell1.setBorder(Rectangle.NO_BORDER);
        Paragraph line1 = new Paragraph("________________________________________", FONT_BODY);
        line1.setAlignment(Element.ALIGN_CENTER);
        Paragraph label1 = new Paragraph(sign1, FONT_BODY_BOLD);
        label1.setAlignment(Element.ALIGN_CENTER);
        cell1.addElement(line1);
        cell1.addElement(label1);
        signTable.addCell(cell1);

        PdfPCell cell2 = new PdfPCell();
        cell2.setBorder(Rectangle.NO_BORDER);
        Paragraph line2 = new Paragraph("________________________________________", FONT_BODY);
        line2.setAlignment(Element.ALIGN_CENTER);
        Paragraph label2 = new Paragraph(sign2, FONT_BODY_BOLD);
        label2.setAlignment(Element.ALIGN_CENTER);
        cell2.addElement(line2);
        cell2.addElement(label2);
        signTable.addCell(cell2);

        document.add(signTable);
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "$0.00 COP";
        return CURRENCY_FORMAT.format(amount) + " COP";
    }

    private String translateCategory(String cat) {
        if ("EQUITY_SAVING".equals(cat)) return "Aporte Social (Patrimonio)";
        if ("SOCIAL_FUND".equals(cat)) return "Fondo Social";
        if ("INCOME".equals(cat)) return "Cuota Sostenimiento / Ingreso";
        return cat;
    }

    private Image getLogoImage() {
        try {
            byte[] bytes = null;
            try (java.io.InputStream is = getClass().getResourceAsStream("/konta-logo.jpg")) {
                if (is != null) {
                    bytes = is.readAllBytes();
                }
            }
            if (bytes == null) {
                try (java.io.InputStream is = getClass().getResourceAsStream("/static/konta-logo.jpg")) {
                    if (is != null) {
                        bytes = is.readAllBytes();
                    }
                }
            }
            if (bytes == null) {
                java.nio.file.Path[] candidates = new java.nio.file.Path[]{
                        java.nio.file.Path.of("src/main/resources/konta-logo.jpg"),
                        java.nio.file.Path.of("accounting-service/src/main/resources/konta-logo.jpg"),
                        java.nio.file.Path.of("../accounting-service-web/public/konta-logo.jpg"),
                        java.nio.file.Path.of("accounting-service-web/public/konta-logo.jpg")
                };
                for (var p : candidates) {
                    if (java.nio.file.Files.exists(p)) {
                        bytes = java.nio.file.Files.readAllBytes(p);
                        break;
                    }
                }
            }
            if (bytes != null && bytes.length > 0) {
                return Image.getInstance(bytes);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
