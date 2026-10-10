package com.app.accounting.security.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    @Builder.Default
    private String tokenType = "Bearer";
    private String username;
    private String email;
    private String fullName;
    /** Rol principal (presentación). null = usuario pendiente de aprobación. */
    private String role;
    private List<String> roles;
    private List<String> permissions;
    private String picture;
    private String avatarUrl;
    private long expiresIn;
}
