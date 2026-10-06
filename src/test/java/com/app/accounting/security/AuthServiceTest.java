package com.app.accounting.security;

import com.app.accounting.common.exception.DuplicateResourceException;
import com.app.accounting.security.dto.request.LoginRequest;
import com.app.accounting.security.dto.request.RegisterRequest;
import com.app.accounting.security.dto.response.AuthResponse;
import com.app.accounting.security.entity.Role;
import com.app.accounting.security.entity.User;
import com.app.accounting.security.jwt.JwtService;
import com.app.accounting.security.repository.UserRepository;
import com.app.accounting.security.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthServiceImpl authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(UUID.randomUUID())
                .username("contador1")
                .email("contador1@contable.com")
                .password("encoded_pass")
                .role(Role.ROLE_CONTADOR)
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("Debe registrar exitosamente un nuevo usuario y retornar token JWT")
    void shouldRegisterNewUserSuccessfully() {
        RegisterRequest request = RegisterRequest.builder()
                .username("contador1")
                .email("contador1@contable.com")
                .password("password123")
                .role(Role.ROLE_CONTADOR)
                .build();

        when(userRepository.existsByUsername("contador1")).thenReturn(false);
        when(userRepository.existsByEmail("contador1@contable.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded_pass");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(jwtService.generateToken(any(User.class), any())).thenReturn("mock.jwt.token");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("mock.jwt.token");
        assertThat(response.getUsername()).isEqualTo("contador1");
        assertThat(response.getRole()).isEqualTo(Role.ROLE_CONTADOR.name());

        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Debe rechazar el registro si el nombre de usuario ya existe")
    void shouldRejectRegisterWhenUsernameAlreadyExists() {
        RegisterRequest request = RegisterRequest.builder()
                .username("contador1")
                .email("nuevo@contable.com")
                .password("password123")
                .role(Role.ROLE_CONTADOR)
                .build();

        when(userRepository.existsByUsername("contador1")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("El nombre de usuario ya está registrado");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Debe iniciar sesión y retornar token JWT válido")
    void shouldLoginSuccessfullyAndReturnJwt() {
        LoginRequest request = LoginRequest.builder()
                .username("contador1")
                .password("password123")
                .build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("contador1", "password123"));
        when(userRepository.findByUsername("contador1")).thenReturn(Optional.of(sampleUser));
        when(jwtService.generateToken(eq(sampleUser), any())).thenReturn("mock.jwt.token");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("mock.jwt.token");
        assertThat(response.getUsername()).isEqualTo("contador1");
        assertThat(response.getRole()).isEqualTo(Role.ROLE_CONTADOR.name());
    }
}
