package com.app.accounting.security.service.impl;

import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.common.exception.DuplicateResourceException;
import com.app.accounting.common.exception.ResourceNotFoundException;
import com.app.accounting.security.dto.request.ChangePasswordRequest;
import com.app.accounting.security.dto.request.CreateUserRequest;
import com.app.accounting.security.dto.request.UpdateUserRequest;
import com.app.accounting.security.dto.response.UserResponse;
import com.app.accounting.security.entity.Role;
import com.app.accounting.security.entity.User;
import com.app.accounting.security.repository.RoleRepository;
import com.app.accounting.security.repository.UserRepository;
import com.app.accounting.security.service.SecurityMapper;
import com.app.accounting.security.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(SecurityMapper::toUserResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        return SecurityMapper.toUserResponse(findUser(id));
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("El nombre de usuario '" + request.getUsername() + "' ya está registrado");
        }
        if (userRepository.findByEmailIgnoreCase(request.getEmail()).isPresent()) {
            throw new DuplicateResourceException("El correo electrónico '" + request.getEmail() + "' ya está registrado");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail().trim().toLowerCase())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(resolveRoles(request.getRoles()))
                .enabled(request.getEnabled() == null || request.getEnabled())
                .build();

        return SecurityMapper.toUserResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = findUser(id);

        if (!user.getEmail().equalsIgnoreCase(request.getEmail())
                && userRepository.findByEmailIgnoreCase(request.getEmail()).isPresent()) {
            throw new DuplicateResourceException("El correo electrónico '" + request.getEmail() + "' ya está en uso por otro usuario");
        }

        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        if (request.getRoles() != null) {
            applyRoles(user, request.getRoles());
        }
        if (request.getEnabled() != null) {
            applyEnabled(user, request.getEnabled());
        }

        return SecurityMapper.toUserResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse toggleUserStatus(UUID id, boolean enabled) {
        User user = findUser(id);
        applyEnabled(user, enabled);
        return SecurityMapper.toUserResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse assignRoles(UUID id, List<String> roleCodes) {
        User user = findUser(id);
        applyRoles(user, roleCodes);
        return SecurityMapper.toUserResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void changePassword(UUID id, ChangePasswordRequest request) {
        User user = findUser(id);
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordResetCode(null);
        user.setPasswordResetExpiresAt(null);
        userRepository.save(user);
    }

    private User findUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));
    }

    private Set<Role> resolveRoles(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return new HashSet<>();
        }
        Set<String> wanted = new HashSet<>(codes);
        List<Role> found = roleRepository.findByCodeIn(wanted);
        if (found.size() != wanted.size()) {
            Set<String> missing = new HashSet<>(wanted);
            found.forEach(r -> missing.remove(r.getCode()));
            throw new ResourceNotFoundException("Rol(es) inexistente(s): " + String.join(", ", missing));
        }
        return new HashSet<>(found);
    }

    private void applyRoles(User user, List<String> codes) {
        Set<Role> newRoles = resolveRoles(codes);
        boolean losesSuperAdmin = user.getRoleCodes().contains(Role.SUPER_ADMIN)
                && newRoles.stream().noneMatch(r -> Role.SUPER_ADMIN.equals(r.getCode()));
        if (losesSuperAdmin && user.isEnabled() && isLastActiveSuperAdmin()) {
            throw new BusinessRuleException("No se puede retirar el rol SUPER_ADMIN al último super administrador activo");
        }
        user.getRoles().clear();
        user.getRoles().addAll(newRoles);
    }

    private void applyEnabled(User user, boolean enabled) {
        if (!enabled && user.isEnabled() && user.getRoleCodes().contains(Role.SUPER_ADMIN) && isLastActiveSuperAdmin()) {
            throw new BusinessRuleException("No se puede desactivar al último super administrador activo");
        }
        user.setEnabled(enabled);
    }

    private boolean isLastActiveSuperAdmin() {
        return roleRepository.countEnabledUsersWithRole(Role.SUPER_ADMIN) <= 1;
    }
}
