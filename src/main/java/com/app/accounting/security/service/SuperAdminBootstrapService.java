package com.app.accounting.security.service;

import com.app.accounting.security.entity.Role;
import com.app.accounting.security.entity.User;
import com.app.accounting.security.repository.RoleRepository;
import com.app.accounting.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asignación inicial (bootstrap) del rol SUPER_ADMIN.
 *
 * <p>El correo configurado en {@code app.security.bootstrap-super-admin-email} solo se usa para
 * otorgar SUPER_ADMIN cuando en el sistema todavía NO existe ningún SUPER_ADMIN activo.
 * Una vez asignado, la autorización depende exclusivamente de los roles/permisos persistidos:
 * si otro SUPER_ADMIN existe, este servicio nunca vuelve a otorgar el rol.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminBootstrapService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Value("${app.security.bootstrap-super-admin-email:}")
    private String bootstrapEmail;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStartup() {
        if (bootstrapEmail == null || bootstrapEmail.isBlank()) {
            return;
        }
        userRepository.findByEmailIgnoreCase(bootstrapEmail.trim()).ifPresent(this::assignIfNoSuperAdmin);
    }

    /** Se invoca al crear una cuenta nueva (Google o registro público). */
    @Transactional
    public void assignIfEligible(User user) {
        if (bootstrapEmail == null || bootstrapEmail.isBlank() || user.getEmail() == null) {
            return;
        }
        if (user.getEmail().equalsIgnoreCase(bootstrapEmail.trim())) {
            assignIfNoSuperAdmin(user);
        }
    }

    private void assignIfNoSuperAdmin(User user) {
        if (roleRepository.countEnabledUsersWithRole(Role.SUPER_ADMIN) > 0) {
            return;
        }
        roleRepository.findByCode(Role.SUPER_ADMIN).ifPresent(role -> {
            user.getRoles().add(role);
            userRepository.save(user);
            log.info("Rol SUPER_ADMIN asignado inicialmente a {}", user.getEmail());
        });
    }
}
