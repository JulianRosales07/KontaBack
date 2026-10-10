package com.app.accounting.security.service;

import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.common.exception.DuplicateResourceException;
import com.app.accounting.common.exception.ResourceNotFoundException;
import com.app.accounting.security.dto.request.RoleRequest;
import com.app.accounting.security.dto.response.PermissionResponse;
import com.app.accounting.security.dto.response.RoleResponse;
import com.app.accounting.security.entity.Permission;
import com.app.accounting.security.entity.Role;
import com.app.accounting.security.repository.PermissionRepository;
import com.app.accounting.security.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Transactional(readOnly = true)
    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAllByOrderByModuleAscCodeAsc().stream()
                .map(p -> PermissionResponse.builder()
                        .id(p.getId())
                        .code(p.getCode())
                        .name(p.getName())
                        .module(p.getModule())
                        .description(p.getDescription())
                        .build())
                .toList();
    }

    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        if (roleRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Ya existe un rol con código " + request.getCode());
        }
        Role role = Role.builder()
                .code(request.getCode())
                .name(request.getName())
                .description(request.getDescription())
                .permissions(resolvePermissions(request.getPermissions()))
                .build();
        return toResponse(roleRepository.save(role));
    }

    @Transactional
    public RoleResponse updateRole(UUID id, RoleRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rol no encontrado con ID: " + id));
        if (Role.SUPER_ADMIN.equals(role.getCode())) {
            // SUPER_ADMIN conserva siempre todos los permisos para evitar bloquear la gestión de accesos.
            throw new BusinessRuleException("El rol SUPER_ADMIN no se puede modificar");
        }
        if (!role.getCode().equals(request.getCode())) {
            throw new BusinessRuleException("El código de un rol no se puede cambiar");
        }
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.getPermissions().clear();
        role.getPermissions().addAll(resolvePermissions(request.getPermissions()));
        return toResponse(roleRepository.save(role));
    }

    private Set<Permission> resolvePermissions(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return new HashSet<>();
        }
        Set<String> wanted = new HashSet<>(codes);
        List<Permission> found = permissionRepository.findByCodeIn(wanted);
        if (found.size() != wanted.size()) {
            Set<String> missing = new HashSet<>(wanted);
            found.forEach(p -> missing.remove(p.getCode()));
            throw new ResourceNotFoundException("Permiso(s) inexistente(s): " + String.join(", ", missing));
        }
        return new HashSet<>(found);
    }

    private RoleResponse toResponse(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .system(role.isSystem())
                .permissions(role.getPermissions().stream().map(Permission::getCode).sorted().toList())
                .userCount(roleRepository.countEnabledUsersWithRole(role.getCode()))
                .build();
    }
}
