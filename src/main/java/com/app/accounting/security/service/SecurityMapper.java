package com.app.accounting.security.service;

import com.app.accounting.security.dto.response.AuthResponse;
import com.app.accounting.security.dto.response.UserResponse;
import com.app.accounting.security.entity.User;

public final class SecurityMapper {

    private SecurityMapper() {
    }

    public static UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getPrimaryRoleCode())
                .roles(user.getRoleCodes())
                .permissions(user.getPermissionCodes())
                .enabled(user.isEnabled())
                .lastLogin(user.getLastLogin())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public static AuthResponse toAuthResponse(User user, String token, long expiresIn, String fallbackPicture) {
        String picture = user.getAvatarUrl() != null ? user.getAvatarUrl() : fallbackPicture;
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getPrimaryRoleCode())
                .roles(user.getRoleCodes())
                .permissions(user.getPermissionCodes())
                .picture(picture)
                .avatarUrl(picture)
                .expiresIn(expiresIn)
                .build();
    }
}
