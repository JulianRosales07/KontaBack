package com.app.accounting.security.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private UUID id;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private String avatarUrl;
    /** Rol principal (presentación). Puede ser null si el usuario aún no tiene rol asignado. */
    private String role;
    private List<String> roles;
    private List<String> permissions;
    private boolean enabled;
    private java.time.OffsetDateTime lastLogin;
    private java.time.Instant createdAt;
}
