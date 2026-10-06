package com.app.accounting.puc.config;

import com.app.accounting.puc.dto.response.PucImportResultResponse;
import com.app.accounting.puc.repository.AccountRepository;
import com.app.accounting.puc.service.AccountService;
import com.app.accounting.security.entity.Role;
import com.app.accounting.security.entity.User;
import com.app.accounting.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.File;

@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class PucDatabaseInitializer implements CommandLineRunner {

    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // Garantizar usuario administrador inicial: admin / admin123
        userRepository.findByUsername("admin").ifPresentOrElse(
                admin -> {
                    admin.setPassword(passwordEncoder.encode("admin123"));
                    userRepository.save(admin);
                },
                () -> {
                    User admin = User.builder()
                            .username("admin")
                            .email("admin@contable.com")
                            .password(passwordEncoder.encode("admin123"))
                            .role(Role.ROLE_ADMIN)
                            .enabled(true)
                            .build();
                    userRepository.save(admin);
                }
        );

        // Cargar catálogo PUC desde Excel si la base de datos no lo tiene
        File pucFile = new File("Docs/PUC_Cuentas_Comerciantes.xlsx");
        if (pucFile.exists() && accountRepository.count() < 100) {
            log.info("Iniciando carga automática del catálogo PUC desde Docs/PUC_Cuentas_Comerciantes.xlsx...");
            try {
                PucImportResultResponse result = accountService.importDefaultPuc();
                log.info("Catálogo PUC cargado con éxito: {}", result.getMessage());
            } catch (Exception e) {
                log.warn("No se pudo completar la carga automática del PUC: {}", e.getMessage());
            }
        }
    }
}
