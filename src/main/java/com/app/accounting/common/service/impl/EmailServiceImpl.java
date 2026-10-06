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
}
