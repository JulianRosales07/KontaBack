package com.app.accounting.common.service.impl;

import com.app.accounting.common.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    @Value("${resend.api-key:}")
    private String resendApiKey;

    @Value("${resend.from-email:Konta ERP <onboarding@resend.dev>}")
    private String resendFromEmail;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        String resetLink = frontendUrl + "/?resetToken=" + resetToken;

        log.info("=================================================================");
        log.info("[KONTA ERP - SOLICITUD DE RECUPERACIÓN DE CONTRASEÑA]");
        log.info("Destinatario: {}", toEmail);
        log.info("Token de Recuperación: {}", resetToken);
        log.info("Enlace de restablecimiento: {}", resetLink);
        log.info("=================================================================");

        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.warn("RESEND_API_KEY no está configurada aún en application.yaml o variable de entorno.");
            return;
        }

        try {
            String htmlContent = new StringBuilder()
                    .append("<div style='font-family: Arial, sans-serif; max-width: 580px; margin: 0 auto; padding: 28px; border: 1px solid #e2e8f0; border-radius: 14px; background: #ffffff;'>")
                    .append("<div style='text-align: center; margin-bottom: 24px;'>")
                    .append("<h1 style='color: #0f172a; margin: 0; font-size: 26px; font-weight: 800;'>KONTA ERP</h1>")
                    .append("<p style='color: #64748b; font-size: 13px; margin-top: 4px;'>Plataforma Contable en la Nube</p>")
                    .append("</div>")
                    .append("<p style='color: #334155; font-size: 15px; line-height: 1.6;'>Hola,</p>")
                    .append("<p style='color: #334155; font-size: 14px; line-height: 1.6;'>Hemos recibido una solicitud para restablecer la contraseña de acceso a tu cuenta.</p>")
                    .append("<div style='background-color: #f8fafc; border: 1px solid #cbd5e1; border-radius: 10px; padding: 18px; margin: 24px 0; text-align: center;'>")
                    .append("<p style='margin: 0 0 8px 0; font-size: 11px; color: #64748b; text-transform: uppercase; letter-spacing: 0.08em; font-weight: 700;'>Tu código de verificación:</p>")
                    .append("<span style='font-family: monospace; font-size: 22px; font-weight: bold; color: #0f172a; letter-spacing: 3px;'>").append(resetToken).append("</span>")
                    .append("</div>")
                    .append("<p style='color: #64748b; font-size: 13px; line-height: 1.6;'>Este código es de uso único y tiene una validez de <strong>15 minutos</strong>. Si no solicitaste este cambio, puedes ignorar este mensaje con total seguridad.</p>")
                    .append("<hr style='border: 0; border-top: 1px solid #e2e8f0; margin: 24px 0;' />")
                    .append("<p style='color: #94a3b8; font-size: 11px; text-align: center; margin: 0;'>© Konta ERP. Todos los derechos reservados.</p>")
                    .append("</div>")
                    .toString();

            String escapedHtml = htmlContent.replace("\"", "\\\"");
            String requestBody = "{\"from\":\"" + resendFromEmail + "\",\"to\":[\"" + toEmail + "\"],\"subject\":\"Código de recuperación de contraseña - Konta ERP\",\"html\":\"" + escapedHtml + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + resendApiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Correo enviado exitosamente vía Resend a {}. Respuesta: {}", toEmail, response.body());
            } else {
                log.error("Error al enviar correo vía Resend. Código HTTP: {}. Respuesta: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Excepción al comunicarse con la API de Resend: {}", e.getMessage(), e);
        }
    }

    @Override
    public void sendAssociateReceiptEmailWithPdf(String toEmail, String receiptNumber, String associateName, java.math.BigDecimal totalAmount, byte[] pdfBytes) {
        if (toEmail == null || toEmail.isBlank()) {
            throw new com.app.accounting.common.exception.BusinessRuleException("El asociado no tiene un correo electrónico configurado.");
        }
        if (resendApiKey == null || resendApiKey.isBlank()) {
            throw new com.app.accounting.common.exception.BusinessRuleException("El servicio de correo automático (RESEND_API_KEY) no está configurado aún en el servidor. Puede descargar el PDF y enviarlo.");
        }

        try {
            String base64Pdf = java.util.Base64.getEncoder().encodeToString(pdfBytes);
            String formattedTotal = java.text.NumberFormat.getCurrencyInstance(new java.util.Locale("es", "CO")).format(totalAmount);
            String htmlContent = "<div style='font-family: Arial, sans-serif; max-width: 580px; margin: 0 auto; padding: 24px; border: 1px solid #e2e8f0; border-radius: 12px; background: #ffffff;'>"
                    + "<div style='text-align: center; margin-bottom: 20px;'>"
                    + "<h2 style='color: #1e3a8a; margin: 0;'>Comprobante Oficial de Recaudo</h2>"
                    + "<p style='color: #64748b; font-size: 13px; margin-top: 4px;'>Konta ERP</p>"
                    + "</div>"
                    + "<p style='color: #334155; font-size: 14px;'>Estimado(a) <strong>" + associateName + "</strong>,</p>"
                    + "<p style='color: #334155; font-size: 14px;'>Le confirmamos la recepción y contabilización de su pago:</p>"
                    + "<div style='background-color: #f8fafc; border: 1px solid #cbd5e1; border-radius: 8px; padding: 16px; margin: 20px 0;'>"
                    + "<p style='margin: 4px 0; font-size: 14px; color: #1e293b;'><strong>Recibo N°:</strong> " + receiptNumber + "</p>"
                    + "<p style='margin: 4px 0; font-size: 14px; color: #1e293b;'><strong>Valor Total:</strong> " + formattedTotal + " COP</p>"
                    + "</div>"
                    + "<p style='color: #334155; font-size: 14px;'>Adjunto a este correo encontrará su <strong>comprobante oficial en formato PDF</strong>.</p>"
                    + "<hr style='border: 0; border-top: 1px solid #e2e8f0; margin: 24px 0;'/>"
                    + "<p style='color: #94a3b8; font-size: 11px; text-align: center;'>Mensaje generado automáticamente. Por favor no responder a este correo.</p>"
                    + "</div>";

            String escapedHtml = htmlContent.replace("\"", "\\\"");
            String filename = "Recibo_" + receiptNumber + ".pdf";

            String requestBody = "{"
                    + "\"from\":\"" + resendFromEmail + "\","
                    + "\"to\":[\"" + toEmail + "\"],"
                    + "\"subject\":\"Comprobante de Recaudo " + receiptNumber + " - " + associateName + "\","
                    + "\"html\":\"" + escapedHtml + "\","
                    + "\"attachments\":[{\"filename\":\"" + filename + "\",\"content\":\"" + base64Pdf + "\"}]"
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + resendApiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Recibo {} enviado exitosamente con PDF adjunto a {}. Respuesta: {}", receiptNumber, toEmail, response.body());
            } else {
                log.error("Error al enviar recibo vía Resend: Código {} - {}", response.statusCode(), response.body());
                throw new com.app.accounting.common.exception.BusinessRuleException("Error del proveedor de correo (" + response.statusCode() + "): " + response.body());
            }
        } catch (com.app.accounting.common.exception.BusinessRuleException bre) {
            throw bre;
        } catch (Exception e) {
            log.error("Excepción al enviar correo con PDF de recibo: {}", e.getMessage(), e);
            throw new com.app.accounting.common.exception.BusinessRuleException("No se pudo enviar el correo: " + e.getMessage());
        }
    }
}
