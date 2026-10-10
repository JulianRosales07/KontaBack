package com.app.accounting.security;

import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.common.exception.DuplicateResourceException;
import com.app.accounting.common.exception.ResourceNotFoundException;
import com.app.accounting.security.dto.request.CreateUserRequest;
import com.app.accounting.security.dto.response.UserResponse;
import com.app.accounting.security.entity.Role;
import com.app.accounting.security.entity.User;
import com.app.accounting.security.repository.RoleRepository;
import com.app.accounting.security.repository.UserRepository;
import com.app.accounting.security.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private Role superAdmin;
    private Role administrador;
    private User sampleUser;

    @BeforeEach
    void setUp() {
        superAdmin = Role.builder().code(Role.SUPER_ADMIN).name("Super").build();
        administrador = Role.builder().code(Role.ADMINISTRADOR).name("Admin").build();
        sampleUser = User.builder()
                .id(UUID.randomUUID())
                .username("contador_titular")
                .email("contador@konta.com")
                .fullName("Carlos Ruiz - Contador")
                .password("encoded_pass")
                .roles(new HashSet<>(Set.of(administrador)))
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("Debe listar todos los usuarios del sistema con sus roles")
    void shouldListAllUsers() {
        when(userRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(sampleUser));

        List<UserResponse> result = userService.getAllUsers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("contador_titular");
        assertThat(result.get(0).getRoles()).containsExactly(Role.ADMINISTRADOR);
    }

    @Test
    @DisplayName("Debe crear un usuario con los roles indicados")
    void shouldCreateUserSuccessfully() {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("auxiliar1")
                .email("auxiliar1@konta.com")
                .password("auxiliar123")
                .roles(List.of(Role.ADMINISTRADOR))
                .build();

        when(userRepository.existsByUsername("auxiliar1")).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase("auxiliar1@konta.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("auxiliar123")).thenReturn("encoded_aux");
        when(roleRepository.findByCodeIn(anyCollection())).thenReturn(List.of(administrador));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.createUser(request);

        assertThat(response.getRoles()).containsExactly(Role.ADMINISTRADOR);
    }

    @Test
    @DisplayName("Debe rechazar roles inexistentes")
    void shouldRejectUnknownRole() {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("x1").email("x1@konta.com").password("secret1").roles(List.of("ROLE_FANTASMA")).build();
        when(userRepository.existsByUsername("x1")).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase("x1@konta.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret1")).thenReturn("e");
        when(roleRepository.findByCodeIn(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ROLE_FANTASMA");
    }

    @Test
    @DisplayName("Debe rechazar la creación si el username ya está registrado")
    void shouldRejectWhenUsernameExists() {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("contador_titular")
                .email("otro@konta.com")
                .password("password123")
                .build();

        when(userRepository.existsByUsername("contador_titular")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("ya está registrado");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Debe alternar el estado activo/inactivo del usuario")
    void shouldToggleUserStatus() {
        UUID userId = sampleUser.getId();
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        userService.toggleUserStatus(userId, false);

        assertThat(sampleUser.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("No permite desactivar ni quitar el rol al último SUPER_ADMIN activo")
    void protectsLastSuperAdmin() {
        sampleUser.getRoles().clear();
        sampleUser.getRoles().add(superAdmin);
        UUID userId = sampleUser.getId();
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(roleRepository.countEnabledUsersWithRole(Role.SUPER_ADMIN)).thenReturn(1L);
        when(roleRepository.findByCodeIn(anyCollection())).thenReturn(List.of(administrador));

        assertThatThrownBy(() -> userService.toggleUserStatus(userId, false))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> userService.assignRoles(userId, List.of(Role.ADMINISTRADOR)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(sampleUser.isEnabled()).isTrue();
        assertThat(sampleUser.getRoleCodes()).containsExactly(Role.SUPER_ADMIN);
    }

    @Test
    @DisplayName("Debe aprobar a un usuario asignándole roles y activando su cuenta")
    void shouldApproveUser() {
        sampleUser.getRoles().clear();
        sampleUser.setEnabled(false);
        UUID userId = sampleUser.getId();
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(roleRepository.findByCodeIn(anyCollection())).thenReturn(List.of(administrador));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.approveUser(userId, List.of(Role.ADMINISTRADOR));

        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getRoles()).containsExactly(Role.ADMINISTRADOR);
    }
}
