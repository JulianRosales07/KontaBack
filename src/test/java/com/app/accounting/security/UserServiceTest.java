package com.app.accounting.security;

import com.app.accounting.common.exception.DuplicateResourceException;
import com.app.accounting.security.dto.request.CreateUserRequest;
import com.app.accounting.security.dto.request.UpdateUserRequest;
import com.app.accounting.security.dto.response.UserResponse;
import com.app.accounting.security.entity.Role;
import com.app.accounting.security.entity.User;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(UUID.randomUUID())
                .username("contador_titular")
                .email("contador@konta.com")
                .fullName("Carlos Ruiz - Contador")
                .phone("+57 310 123 4567")
                .password("encoded_pass")
                .role(Role.ROLE_CONTADOR)
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("Debe listar todos los usuarios del sistema")
    void shouldListAllUsers() {
        when(userRepository.findAll()).thenReturn(List.of(sampleUser));

        List<UserResponse> result = userService.getAllUsers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("contador_titular");
        assertThat(result.get(0).getFullName()).isEqualTo("Carlos Ruiz - Contador");
    }

    @Test
    @DisplayName("Debe crear un usuario exitosamente")
    void shouldCreateUserSuccessfully() {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("auxiliar1")
                .email("auxiliar1@konta.com")
                .password("auxiliar123")
                .fullName("Laura Gómez")
                .phone("+57 320 000 1111")
                .role(Role.ROLE_AUXILIAR)
                .enabled(true)
                .build();

        when(userRepository.existsByUsername("auxiliar1")).thenReturn(false);
        when(userRepository.existsByEmail("auxiliar1@konta.com")).thenReturn(false);
        when(passwordEncoder.encode("auxiliar123")).thenReturn("encoded_aux");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        UserResponse response = userService.createUser(request);

        assertThat(response).isNotNull();
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Debe rechazar la creación si el username ya está registrado")
    void shouldRejectWhenUsernameExists() {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("contador_titular")
                .email("otro@konta.com")
                .password("password123")
                .role(Role.ROLE_CONTADOR)
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

        UserResponse response = userService.toggleUserStatus(userId, false);

        assertThat(response).isNotNull();
        assertThat(sampleUser.isEnabled()).isFalse();
        verify(userRepository, times(1)).save(sampleUser);
    }
}
