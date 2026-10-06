package com.app.accounting.ai.service.impl;

import com.app.accounting.ai.dto.ChatMessageDto;
import com.app.accounting.ai.dto.CopilotChatRequest;
import com.app.accounting.ai.dto.CopilotChatResponse;
import com.app.accounting.ai.service.CopilotService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CopilotServiceImpl implements CopilotService {

    private final ObjectMapper objectMapper;

    @Value("${groq.api-key:}")
    private String groqApiKey;

    @Value("${groq.api-url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqApiUrl;

    @Value("${groq.primary-model:qwen/qwen3.8-27b}")
    private String primaryModel;

    @Value("${groq.fallback-model:openai/gpt-oss-120b}")
    private String fallbackModel;

    private static final String SYSTEM_PROMPT = """
            Eres Konta AI Copilot, el asistente inteligente, contable y tributario de Konta ERP (Konta Cloud Accounting) para Colombia.
            Tu especialidad es la gestión de Asociaciones, Cooperativas y Entidades Sin Ánimo de Lucro (ESAL).
            
            CONOCIMIENTO OPERATIVO Y CONTABLE DE KONTA ERP:
            1. Cuotas Mensuales de Asociados:
               - Cuota periódica establecida: $50.000 COP por asociado.
               - Se registran mediante Comprobante de Ingreso (CP-ING) con causación y recaudo simultáneo en partida doble:
                 * Débito: 110505 (Caja General) o 111005 (Bancos).
                 * Crédito: 417010 (Cuotas de sostenimiento ordinarias), 2380 (Fondos sociales) y 3115 (Aportes sociales de capital).
            2. Préstamos a Asociados:
               - Módulo especializado en Cartera para otorgar créditos a asociados con plan de amortización fija (sistema francés).
               - La plata o fondos que se prestan provienen directamente de la Cuenta de Ahorros de los Asociados (PUC 111005 / 11100501 - Bancolombia Cta Ahorros Asociados), conformada por los aportes y fondos sociales de los miembros.
               - Cuenta PUC obligatoria de cartera: 136505 (Cuentas por cobrar - Préstamos a asociados).
               - Desembolso del crédito: Débito 136505 (Cartera de Préstamos) vs Crédito 111005 / 11100501 (Salida de la Cuenta de Ahorros de los Asociados).
               - Cobro de cuotas: Cada cuota recibida ingresa de vuelta a la Cuenta de Ahorros de los Asociados (Débito 111005), amortiza capital a la cuenta 136505 (Crédito) y acredita intereses corrientes a la 421005 (Ingresos financieros).
               - En el recaudo mensual se puede cobrar la cuota estatutaria ($50.000) más la cuota del préstamo activa del asociado.
            3. Cartera y Aging (Antigüedad de Saldos):
               - Módulo de Cartera con clasificación automática en 5 bandas de mora:
                 * Al día / Corriente (0 días)
                 * 1 a 30 días
                 * 31 a 60 días
                 * 61 a 90 días
                 * Más de 90 días
               - Visualización de saldos adeudados de préstamos y cuotas, generación de extractos y estados de cuenta.
            4. Documento Soporte Electrónico DIAN:
               - Emisión de documentos soporte para adquisiciones a proveedores no obligados a facturar ante la DIAN.
               - Liquidación de Retención en la Fuente (PUC 2365) y ReteICA (PUC 2368).
               - Soporta pago inmediato con egreso o causación a crédito en Cuentas por Pagar (PUC 2335).
            5. Contabilidad NIIF y Partida Doble:
               - Catálogo de cuentas basado en el PUC de Colombia.
               - Balance de Comprobación discriminado por Tercero (NIT / Cédula) con regla de oro de sumas iguales auditadas (Débitos = Créditos).
               - Libro Diario, Asientos contables manuales y automáticos, Estado de Resultados (P&G).
            6. Directorio de Terceros:
               - Registro unificado de Asociados y Proveedores.
            7. NOTA CLAVE: Konta ERP para asociaciones NO maneja ventas de mercancías comerciales porque el modelo opera bajo aportes, cuotas y servicios a asociados.
            
            INSTRUCCIONES DE RESPUESTA:
            - Responde siempre de manera conversacional, en español formal, amable, claro y directo.
            - Sé conciso y pedagógico, indicando las cuentas contables del PUC (por ejemplo: **136505**, **110505**, **417010**, **421005**) y los pasos a seguir en la plataforma.
            """;

    @Override
    public CopilotChatResponse askCopilot(CopilotChatRequest request) {
        long startTime = System.currentTimeMillis();

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));

        if (request.getHistory() != null) {
            int size = request.getHistory().size();
            int startIndex = Math.max(0, size - 6);
            for (int i = startIndex; i < size; i++) {
                ChatMessageDto msg = request.getHistory().get(i);
                String role = "user".equalsIgnoreCase(msg.getSender()) ? "user" : "assistant";
                messages.add(Map.of("role", role, "content", msg.getText() != null ? msg.getText() : ""));
            }
        }

        messages.add(Map.of("role", "user", "content", request.getPrompt()));

        String responseText = callGroqApi(messages, primaryModel);
        if (responseText == null && fallbackModel != null && !fallbackModel.isBlank()) {
            log.info("Reintentando llamada a Groq con modelo alternativo: {}", fallbackModel);
            responseText = callGroqApi(messages, fallbackModel);
        }

        long elapsedMs = System.currentTimeMillis() - startTime;
        String workDuration = String.format(Locale.US, "Asistente Konta (Backend) · %.1fs", elapsedMs / 1000.0);

        if (responseText != null && !responseText.isBlank()) {
            return CopilotChatResponse.builder()
                    .text(responseText)
                    .workDuration(workDuration)
                    .build();
        }

        // Fallback inteligente
        String smartFallback = generateSmartFallback(request.getPrompt());
        return CopilotChatResponse.builder()
                .text(smartFallback)
                .workDuration(String.format(Locale.US, "Konta Copilot · %.1fs", elapsedMs / 1000.0))
                .build();
    }

    private String callGroqApi(List<Map<String, String>> messages, String model) {
        if (groqApiKey == null || groqApiKey.isBlank()) {
            log.warn("Groq API Key no configurada en el backend.");
            return null;
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("temperature", 0.35);
            body.put("max_tokens", 900);

            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(groqApiUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + groqApiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode contentNode = root.at("/choices/0/message/content");
                if (!contentNode.isMissingNode() && contentNode.isTextual()) {
                    return contentNode.asText().trim();
                }
            } else {
                log.warn("Error en llamada a Groq API [Status: {}]: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Excepción al comunicarse con Groq Cloud API: {}", e.getMessage());
        }
        return null;
    }

    private String generateSmartFallback(String prompt) {
        String q = prompt != null ? prompt.toLowerCase() : "";

        if (q.contains("prestamo") || q.contains("préstamo") || q.contains("credito") || q.contains("crédito")) {
            return "En Konta ERP los préstamos a asociados se registran bajo la cuenta PUC **136505 (Cuentas por cobrar - Préstamos a asociados)**. Puedes consultar el plan de amortización con cuota fija y registrar los abonos mensuales discriminando capital e intereses (cuenta **421005**).";
        }
        if (q.contains("cartera") || q.contains("aging") || q.contains("cobrar") || q.contains("mora")) {
            return "Con el módulo de Cartera de Konta clasificas automáticamente las deudas de cuotas y préstamos en 5 bandas de vencimiento (Al día, 1-30, 31-60, 61-90 y +90 días) con extractos por asociado.";
        }
        if (q.contains("soporte") || q.contains("documento") || q.contains("egreso") || q.contains("dian")) {
            return "El Documento Soporte Electrónico permite deducir costos de adquisiciones a proveedores no obligados a facturar ante la DIAN, aplicando retención en la fuente y generando egreso o causación en la cuenta 2335.";
        }
        if (q.contains("niif") || q.contains("balance") || q.contains("puc") || q.contains("reporte")) {
            return "Konta genera Balances de Comprobación discriminados por tercero (Cédula/NIT) bajo NIIF, con control en tiempo real de partida doble (Débitos = Créditos).";
        }

        return "Konta ERP integra de forma nativa la gestión de cuotas de asociados ($50.000), préstamos (PUC 136505), cartera por bandas de vencimiento y contabilidad NIIF con partida doble.";
    }
}
