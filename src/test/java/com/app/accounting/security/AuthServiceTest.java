package com.app.accounting.security;

import com.app.accounting.common.exception.DuplicateResourceException;
import com.app.accounting.security.dto.request.GoogleAuthRequest;
import com.app.accounting.security.dto.request.LoginRequest;
import com.app.accounting.security.dto.request.RegisterRequest;
import com.app.accounting.security.dto.response.AuthResponse;
import com.app.accounting.security.entity.Permission;
import com.app.accounting.security.entity.Role;
import com.app.accounting.security.entity.User;
import com.app.accounting.security.google.GoogleTokenVerifier;
import com.app.accounting.security.jwt.JwtService;
import com.app.accounting.security.repository.UserRepository;
import com.app.accounting.security.service.SuperAdminBootstrapService;
import com.app.accounting.security.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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

    @Mock
    private GoogleTokenVerifier googleTokenVerifier;

    @Mock
    private SuperAdminBootstrapService superAdminBootstrapService;

    @InjectMocks
    private AuthServiceImpl authService;

    private User adminUser;

    @BeforeEach
    void setUp() {
        Role administrador = Role.builder()
                .code(Role.ADMINISTRADOR)
                .name("Administrador")
                .permissions(Set.of(Permission.builder().code("LOANS_VIEW").name("Ver").module("LOANS").build()))
                .build();
        adminUser = User.builder()
                .id(UUID.randomUUID())
                .username("contador1")
                .email("contador1@contable.com")
                .password("encoded_pass")
                .roles(new HashSet<>(Set.of(administrador)))
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("El registro público crea la cuenta SIN roles (pendiente de aprobación)")
    void shouldRegisterNewUserWithoutRoles() {
        RegisterRequest request = RegisterRequest.builder()
                .username("nuevo")
                .email("Nuevo@Contable.com")
                .password("password123")
                .build();

        when(userRepository.existsByUsername("nuevo")).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase("Nuevo@Contable.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encoded_pass");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any(User.class), any())).thenReturn("mock.jwt.token");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.register(request);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRoles()).isEmpty();
        assertThat(saved.getValue().getEmail()).isEqualTo("nuevo@contable.com");
        assertThat(response.getRole()).isNull();
        assertThat(response.getPermissions()).isEmpty();
        verify(superAdminBootstrapService).assignIfEligible(saved.getValue());
    }

    @Test
    @DisplayName("Debe rechazar el registro si el nombre de usuario ya existe")
    void shouldRejectRegisterWhenUsernameAlreadyExists() {
        RegisterRequest request = RegisterRequest.builder()
                .username("contador1")
                .email("nuevo@contable.com")
                .password("password123")
                .build();

        when(userRepository.existsByUsername("contador1")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("El nombre de usuario ya está registrado");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("El login devuelve token con roles y permisos persistidos")
    void shouldLoginSuccessfullyAndReturnJwt() {
        LoginRequest request = LoginRequest.builder()
                .username("contador1")
                .password("password123")
                .build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("contador1", "password123"));
        when(userRepository.findByUsername("contador1")).thenReturn(Optional.of(adminUser));
        when(jwtService.generateToken(eq(adminUser), any())).thenReturn("mock.jwt.token");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("mock.jwt.token");
        assertThat(response.getRole()).isEqualTo(Role.ADMINISTRADOR);
        assertThat(response.getRoles()).containsExactly(Role.ADMINISTRADOR);
        assertThat(response.getPermissions()).containsExactly("LOANS_VIEW");
    }

    @Test
    @DisplayName("Google: la identidad sale del token verificado, no del correo enviado por el cliente")
    void googleLoginUsesVerifiedIdentity() {
        GoogleAuthRequest request = GoogleAuthRequest.builder()
                .email("juliandrosalesp@gmail.com") // intento de suplantación
                .idToken("token-de-otra-persona")
                .build();
        when(googleTokenVerifier.verify("token-de-otra-persona"))
                .thenReturn(new GoogleTokenVerifier.GoogleIdentity("otra@gmail.com", "Otra Persona", null));
        when(userRepository.findByEmailIgnoreCase("otra@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("otra")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("x");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any(User.class), any())).thenReturn("jwt");

        AuthResponse response = authService.loginWithGoogle(request);

        assertThat(response.getEmail()).isEqualTo("otra@gmail.com");
        assertThat(response.getRoles()).isEmpty();
        verify(userRepository, never()).findByEmailIgnoreCase("juliandrosalesp@gmail.com");
    }

    @Test
    @DisplayName("Google: un token inválido se rechaza")
    void googleLoginRejectsInvalidToken() {
        GoogleAuthRequest request = GoogleAuthRequest.builder().email("a@b.com").idToken("bad").build();
        when(googleTokenVerifier.verify("bad")).thenThrow(new BadCredentialsException("Token de Google inválido"));

        assertThatThrownBy(() -> authService.loginWithGoogle(request)).isInstanceOf(BadCredentialsException.class);
        verify(userRepository, never()).save(any());
    }
}
