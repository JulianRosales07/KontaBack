package com.app.accounting.security.google;

/**
 * Verifica un ID token emitido por Google Identity Services y devuelve la identidad validada.
 * Lanza {@link org.springframework.security.authentication.BadCredentialsException} si el token
 * no es válido, expiró, el correo no está verificado o no pertenece a este cliente OAuth.
 */
public interface GoogleTokenVerifier {

    GoogleIdentity verify(String idToken);

    record GoogleIdentity(String email, String fullName, String picture) {
    }
}
