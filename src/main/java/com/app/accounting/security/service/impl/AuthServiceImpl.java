package com.app.accounting.security.service.impl;

import com.app.accounting.common.exception.DuplicateResourceException;
import com.app.accounting.common.exception.ResourceNotFoundException;
import com.app.accounting.common.service.EmailService;
import com.app.accounting.security.dto.request.ForgotPasswordRequest;
import com.app.accounting.security.dto.request.GoogleAuthRequest;
import com.app.accounting.security.dto.request.LoginRequest;
import com.app.accounting.security.dto.request.RegisterRequest;
import com.app.accounting.security.dto.request.ResetPasswordRequest;
import com.app.accounting.security.dto.response.AuthResponse;
import com.app.accounting.security.dto.response.MessageResponse;
import com.app.accounting.security.dto.response.UserResponse;
import com.app.accounting.security.entity.Role;
import com.app.accounting.security.entity.User;
import com.app.accounting.security.jwt.JwtService;
import com.app.accounting.security.repository.UserRepository;
import com.app.accounting.security.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = userRepository.findByUsername(request.getUsername())
                .or(() -> userRepository.findByEmail(request.getUsername()))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con username o email: " + request.getUsername()));

        user.setLastLogin(java.time.OffsetDateTime.now());
        userRepository.save(user);

        String token = jwtService.generateToken(user, Map.of("role", user.getRole().name()));

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .expiresIn(jwtService.getExpirationMs())
                .build();
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("El nombre de usuario ya está registrado");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("El correo electrónico ya está registrado");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole() != null ? request.getRole() : Role.ROLE_ADMIN)
                .enabled(true)
                .build();

        User savedUser = userRepository.save(user);
        String token = jwtService.generateToken(savedUser, Map.of("role", savedUser.getRole().name()));

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .role(savedUser.getRole().name())
                .expiresIn(jwtService.getExpirationMs())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResourceNotFoundException("No hay usuario autenticado en la sesión");
        }

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + username));

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .lastLogin(user.getLastLogin())
                .build();
    }

    @Override
    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().trim();
        User user = userRepository.findByEmail(email)
                .or(() -> userRepository.findByUsername(email))
                .orElse(null);

        String resetCode = null;
        if (user != null) {
            // Generar código numérico seguro de 6 dígitos (ej: 492015)
            resetCode = String.format("%06d", new java.security.SecureRandom().nextInt(1000000));
            user.setPasswordResetCode(resetCode);
            user.setPasswordResetExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofMinutes(15)));
            userRepository.save(user);

            emailService.sendPasswordResetEmail(user.getEmail(), resetCode);
        }

        return MessageResponse.builder()
                .success(true)
                .message("Si la cuenta está registrada en Konta ERP, hemos enviado el código de verificación de 6 dígitos a tu correo electrónico.")
                .build();
    }

    @Override
    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        String code = request.getToken().trim();
        User user = userRepository.findByPasswordResetCode(code)
                .orElseThrow(() -> new IllegalArgumentException("El código de verificación de 6 dígitos es incorrecto o no existe."));

        if (user.getPasswordResetExpiresAt() == null || user.getPasswordResetExpiresAt().isBefore(java.time.Instant.now())) {
            throw new IllegalArgumentException("El código de verificación de 6 dígitos ha expirado. Por favor solicita uno nuevo.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordResetCode(null);
        user.setPasswordResetExpiresAt(null);
        userRepository.save(user);

        return MessageResponse.builder()
                .success(true)
                .message("Contraseña actualizada exitosamente. Ya puedes iniciar sesión con tu nueva contraseña.")
                .build();
    }

    @Override
    @Transactional
    public AuthResponse loginWithGoogle(GoogleAuthRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // Crear automáticamente la cuenta del usuario de Google en la base de datos
            String baseUsername = email.contains("@") ? email.split("@")[0] : email;
            String username = baseUsername;
            int counter = 1;
            while (userRepository.existsByUsername(username)) {
                username = baseUsername + counter;
                counter++;
            }

            user = User.builder()
                    .username(username)
                    .email(email)
                    .password(passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                    .fullName(request.getFullName() != null && !request.getFullName().isBlank() ? request.getFullName() : username)
                    .avatarUrl(request.getPicture())
                    .role(Role.ROLE_ADMIN)
                    .enabled(true)
                    .build();

            user = userRepository.save(user);
        } else {
            if (request.getPicture() != null && !request.getPicture().isBlank()) {
                user.setAvatarUrl(request.getPicture());
            }
            if (request.getFullName() != null && !request.getFullName().isBlank() && (user.getFullName() == null || user.getFullName().isBlank())) {
                user.setFullName(request.getFullName());
            }
            user.setLastLogin(java.time.OffsetDateTime.now());
            user = userRepository.save(user);
        }

        String token = jwtService.generateToken(user, Map.of("role", user.getRole().name()));

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .picture(user.getAvatarUrl() != null ? user.getAvatarUrl() : request.getPicture())
                .avatarUrl(user.getAvatarUrl() != null ? user.getAvatarUrl() : request.getPicture())
                .expiresIn(jwtService.getExpirationMs())
                .build();
    }
}
