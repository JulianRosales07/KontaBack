package com.app.accounting.security.config;

import com.app.accounting.security.entity.Role;
import com.app.accounting.security.entity.User;
import com.app.accounting.security.repository.RoleRepository;
import com.app.accounting.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

/**
 * Usuarios de demostración. Solo se ejecuta con {@code app.security.seed-demo-users=true}
 * (desactivado por defecto: sus contraseñas son públicas). Nunca modifica usuarios existentes.
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.security.seed-demo-users", havingValue = "true")
public class UserSeedInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        seedUser("admin", "admin@konta.com", "admin123", "Administrador Principal");
        seedUser("contador", "contador@konta.com", "contador123", "Carlos Ruiz - Contador Titular");
    }

    private void seedUser(String username, String email, String rawPassword, String fullName) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        HashSet<Role> roles = new HashSet<>();
        roleRepository.findByCode(Role.ADMINISTRADOR).ifPresent(roles::add);
        userRepository.save(User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .fullName(fullName)
                .roles(roles)
                .enabled(true)
                .build());
        log.info("Usuario demo creado: {} (ADMINISTRADOR)", username);
    }
}
