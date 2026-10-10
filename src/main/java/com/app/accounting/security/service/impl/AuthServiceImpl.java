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
import com.app.accounting.security.entity.User;
import com.app.accounting.security.jwt.JwtService;
import com.app.accounting.security.repository.UserRepository;
import com.app.accounting.security.google.GoogleTokenVerifier;
import com.app.accounting.security.service.AuthService;
import com.app.accounting.security.service.SecurityMapper;
import com.app.accounting.security.service.SuperAdminBootstrapService;
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
    private final GoogleTokenVerifier googleTokenVerifier;
    private final SuperAdminBootstrapService superAdminBootstrapService;

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

        return issueToken(user, null);
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("El nombre de usuario ya está registrado");
        }
        if (userRepository.findByEmailIgnoreCase(request.getEmail()).isPresent()) {
            throw new DuplicateResourceException("El correo electrónico ya está registrado");
        }

        // Registro público: la cuenta se crea SIN roles (sin acceso) hasta que un SUPER_ADMIN la apruebe.
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail().trim().toLowerCase())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .enabled(true)
                .build();

        User savedUser = userRepository.save(user);
        superAdminBootstrapService.assignIfEligible(savedUser);
        return issueToken(savedUser, null);
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

        return SecurityMapper.toUserResponse(user);
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
        // La identidad se toma del ID token verificado por Google, nunca del cuerpo enviado por el cliente.
        GoogleTokenVerifier.GoogleIdentity identity = googleTokenVerifier.verify(request.getIdToken());
        String email = identity.email();
        String fullName = identity.fullName() != null ? identity.fullName() : request.getFullName();
        String picture = identity.picture() != null ? identity.picture() : request.getPicture();

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);

        if (user == null) {
            // Cuenta nueva: se crea SIN roles (sin acceso) hasta aprobación de un SUPER_ADMIN.
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
                    .fullName(fullName != null && !fullName.isBlank() ? fullName : username)
                    .avatarUrl(picture)
                    .enabled(true)
                    .lastLogin(java.time.OffsetDateTime.now())
                    .build();

            user = userRepository.save(user);
            superAdminBootstrapService.assignIfEligible(user);
        } else {
            if (!user.isEnabled()) {
                throw new org.springframework.security.authentication.DisabledException("La cuenta está desactivada");
            }
            if (picture != null && !picture.isBlank()) {
                user.setAvatarUrl(picture);
            }
            if (fullName != null && !fullName.isBlank() && (user.getFullName() == null || user.getFullName().isBlank())) {
                user.setFullName(fullName);
            }
            user.setLastLogin(java.time.OffsetDateTime.now());
            user = userRepository.save(user);
        }

        return issueToken(user, picture);
    }

    private AuthResponse issueToken(User user, String fallbackPicture) {
        String token = jwtService.generateToken(user, Map.of("roles", user.getRoleCodes()));
        return SecurityMapper.toAuthResponse(user, token, jwtService.getExpirationMs(), fallbackPicture);
    }
}
