package com.app.accounting.maintenance.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Service
public class RenderKeepAliveService {

    @Value("${app.keepalive.enabled:true}")
    private boolean enabled;

    @Value("${app.keepalive.url:https://kontaback.onrender.com/api/v1/security/public-key}")
    private String targetUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * Se ejecuta cada 12 minutos (720.000 ms) para evitar que Render suspenda la instancia por inactividad (límite de 15 min).
     */
    @Scheduled(fixedDelay = 720000, initialDelay = 120000)
    public void pingServer() {
        if (!enabled || targetUrl == null || targetUrl.isBlank() || targetUrl.contains("localhost")) {
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Konta-Internal-KeepAlive/1.0")
                    .GET()
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            log.info("[RenderKeepAlive] Self-ping exitoso hacia {} (Status: {})", targetUrl, response.statusCode());
        } catch (Exception e) {
            log.warn("[RenderKeepAlive] No se pudo completar el self-ping: {}", e.getMessage());
        }
    }
}
