package com.app.accounting.security.dto.response;

import com.app.accounting.security.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private Role role;
    private boolean enabled;
    private java.time.OffsetDateTime lastLogin;
}
