package com.app.accounting.security;

import com.app.accounting.security.entity.Role;
import com.app.accounting.security.entity.User;
import com.app.accounting.security.jwt.JwtService;
import com.app.accounting.security.repository.RoleRepository;
import com.app.accounting.security.repository.UserRepository;
import com.app.accounting.security.service.SuperAdminBootstrapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de roles: la autorización proviene de user_roles → role_permissions en base de datos.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RoleAuthorizationIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SuperAdminBootstrapService bootstrapService;

    private MockMvc mvc;
    private String superAdminToken;
    private String adminToken;
    private String noRoleToken;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        superAdminToken = tokenFor("sa_test", Role.SUPER_ADMIN);
        adminToken = tokenFor("admin_test", Role.ADMINISTRADOR);
        noRoleToken = tokenFor("pending_test", null);
    }

    private String tokenFor(String username, String roleCode) {
        Set<Role> roles = new HashSet<>();
        if (roleCode != null) {
            roles.add(roleRepository.findByCode(roleCode).orElseThrow());
        }
        User user = userRepository.save(User.builder()
                .username(username)
                .email(username + "@test.com")
                .password("x")
                .roles(roles)
                .enabled(true)
                .build());
        return "Bearer " + jwtService.generateToken(user, Map.of());
    }

    @Test
    @DisplayName("SUPER_ADMIN accede a la gestión de usuarios, roles y permisos")
    void superAdminCanManageAccess() throws Exception {
        mvc.perform(get("/api/v1/users").header("Authorization", superAdminToken)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/roles").header("Authorization", superAdminToken)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/permissions").header("Authorization", superAdminToken)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMINISTRADOR recibe 403 en usuarios, roles y permisos")
    void administradorGetsForbiddenOnAccessManagement() throws Exception {
        mvc.perform(get("/api/v1/users").header("Authorization", adminToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/roles").header("Authorization", adminToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/permissions").header("Authorization", adminToken)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"hacker\",\"email\":\"h@x.com\",\"password\":\"secret1\",\"roles\":[\"SUPER_ADMIN\"]}"))
                .andExpect(status().isForbidden());
        // Un cuerpo inválido tampoco debe revelar errores de validación: 403 antes de leerlo
        mvc.perform(post("/api/v1/users").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMINISTRADOR accede a préstamos, recaudos, reportes, contabilidad y asociados")
    void administradorCanUseFunctionalModules() throws Exception {
        mvc.perform(get("/api/v1/loans").header("Authorization", adminToken)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/associates/receipts").header("Authorization", adminToken)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/reports/trial-balance").header("Authorization", adminToken)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/vouchers").header("Authorization", adminToken)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/parties").header("Authorization", adminToken)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Usuario sin rol (pendiente de aprobación) recibe 403; anónimo recibe 401")
    void noRoleAndAnonymous() throws Exception {
        mvc.perform(get("/api/v1/loans").header("Authorization", noRoleToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", noRoleToken)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/loans")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/reports/trial-balance")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Bootstrap: el correo inicial recibe SUPER_ADMIN solo si no existe ningún SUPER_ADMIN")
    void bootstrapOnlyWhenNoSuperAdminExists() {
        User initial = userRepository.save(User.builder()
                .username("julian").email("juliandrosalesp@gmail.com").password("x").enabled(true).build());

        // Ya existe un SUPER_ADMIN (sa_test) → no se otorga
        bootstrapService.assignIfEligible(initial);
        assertThat(initial.getRoleCodes()).isEmpty();

        // Sin SUPER_ADMIN activo → se otorga por única vez
        User sa = userRepository.findByUsername("sa_test").orElseThrow();
        sa.setEnabled(false);
        userRepository.saveAndFlush(sa);
        bootstrapService.assignIfEligible(initial);
        assertThat(initial.getRoleCodes()).containsExactly(Role.SUPER_ADMIN);
    }
}
