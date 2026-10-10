package com.app.accounting.security.entity;

import com.app.accounting.common.entity.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends AuditableEntity implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(length = 30)
    private String phone;

    @Column(name = "last_login")
    private java.time.OffsetDateTime lastLogin;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "password_reset_code", length = 10)
    private String passwordResetCode;

    @Column(name = "password_reset_expires_at")
    private java.time.Instant passwordResetExpiresAt;

    /**
     * Autoridades efectivas: "ROLE_<código>" por cada rol asignado + cada permiso granular de esos roles.
     * La autorización depende exclusivamente de los roles/permisos persistidos.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (Role r : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + r.getCode()));
            r.getPermissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p.getCode())));
        }
        return authorities;
    }

    public List<String> getRoleCodes() {
        return roles.stream().map(Role::getCode).sorted().toList();
    }

    public List<String> getPermissionCodes() {
        return roles.stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getCode)
                .distinct()
                .sorted()
                .toList();
    }

    /** Rol principal para presentación (SUPER_ADMIN tiene prioridad). */
    public String getPrimaryRoleCode() {
        List<String> codes = getRoleCodes();
        if (codes.contains(Role.SUPER_ADMIN)) return Role.SUPER_ADMIN;
        return codes.isEmpty() ? null : codes.get(0);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
