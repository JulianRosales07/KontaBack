package com.app.accounting.security.config;

import com.app.accounting.security.entity.Role;
import com.app.accounting.security.entity.User;
import com.app.accounting.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class UserSeedInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        seedUser("admin", "admin@konta.com", "admin123", "Administrador Principal", Role.ROLE_ADMIN);
        seedUser("contador", "contador@konta.com", "contador123", "Carlos Ruiz - Contador Titular", Role.ROLE_CONTADOR);
        seedUser("auxiliar", "auxiliar@konta.com", "auxiliar123", "Laura Gómez - Auxiliar Contable", Role.ROLE_AUXILIAR);
    }

    private void seedUser(String username, String email, String rawPassword, String fullName, Role role) {
        userRepository.findByUsername(username).ifPresentOrElse(
                user -> {
                    // Update full name and ensure password is valid if missing
                    boolean modified = false;
                    if (user.getFullName() == null || !user.getFullName().equals(fullName)) {
                        user.setFullName(fullName);
                        modified = true;
                    }
                    if (user.getRole() != role) {
                        user.setRole(role);
                        modified = true;
                    }
                    if (modified) {
                        userRepository.save(user);
                        log.info("Usuario inicial sincronizado: {} ({})", username, role);
                    }
                },
                () -> {
                    User newUser = User.builder()
                            .username(username)
                            .email(email)
                            .password(passwordEncoder.encode(rawPassword))
                            .fullName(fullName)
                            .role(role)
                            .enabled(true)
                            .build();
                    userRepository.save(newUser);
                    log.info("Nuevo usuario semilla creado: {} con rol {}", username, role);
                }
        );
    }
}
