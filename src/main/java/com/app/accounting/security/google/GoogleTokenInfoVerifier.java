package com.app.accounting.security.google;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Valida el ID token contra el endpoint oficial tokeninfo de Google (que verifica firma y expiración)
 * y comprueba emisor, audiencia (client id) y correo verificado.
 */
@Slf4j
@Component
public class GoogleTokenInfoVerifier implements GoogleTokenVerifier {

    private static final String TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token={token}";

    private final RestClient restClient = RestClient.create();

    @Value("${app.security.google-client-id:}")
    private String clientId;

    @Override
    @SuppressWarnings("unchecked")
    public GoogleIdentity verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new BadCredentialsException("Token de Google ausente");
        }
        Map<String, Object> claims;
        try {
            claims = restClient.get().uri(TOKEN_INFO_URL, idToken).retrieve().body(Map.class);
        } catch (RestClientException e) {
            log.warn("Token de Google rechazado: {}", e.getMessage());
            throw new BadCredentialsException("Token de Google inválido o expirado");
        }
        if (claims == null) {
            throw new BadCredentialsException("Token de Google inválido");
        }
        String aud = String.valueOf(claims.get("aud"));
        if (clientId != null && !clientId.isBlank() && !clientId.equals(aud)) {
            throw new BadCredentialsException("El token de Google no pertenece a esta aplicación");
        }
        String iss = String.valueOf(claims.get("iss"));
        if (!"accounts.google.com".equals(iss) && !"https://accounts.google.com".equals(iss)) {
            throw new BadCredentialsException("Emisor de token de Google inválido");
        }
        if (!"true".equalsIgnoreCase(String.valueOf(claims.get("email_verified")))) {
            throw new BadCredentialsException("El correo de Google no está verificado");
        }
        Object email = claims.get("email");
        if (email == null) {
            throw new BadCredentialsException("El token de Google no contiene correo");
        }
        return new GoogleIdentity(
                email.toString().trim().toLowerCase(),
                claims.get("name") != null ? claims.get("name").toString() : null,
                claims.get("picture") != null ? claims.get("picture").toString() : null
        );
    }
}
