package com.app.accounting.security.controller;

import com.app.accounting.security.dto.request.RoleRequest;
import com.app.accounting.security.dto.response.PermissionResponse;
import com.app.accounting.security.dto.response.RoleResponse;
import com.app.accounting.security.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Roles y Permisos", description = "Gestión de roles y catálogo de permisos granulares")
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ROLES_VIEW')")
    @Operation(summary = "Listar roles con sus permisos")
    public ResponseEntity<List<RoleResponse>> getRoles() {
        return ResponseEntity.ok(roleService.getAllRoles());
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('ROLES_MANAGE')")
    @Operation(summary = "Crear un rol con permisos")
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody RoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.createRole(request));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ROLES_MANAGE')")
    @Operation(summary = "Actualizar nombre, descripción y permisos de un rol")
    public ResponseEntity<RoleResponse> updateRole(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        return ResponseEntity.ok(roleService.updateRole(id, request));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('PERMISSIONS_VIEW')")
    @Operation(summary = "Catálogo de permisos granulares")
    public ResponseEntity<List<PermissionResponse>> getPermissions() {
        return ResponseEntity.ok(roleService.getAllPermissions());
    }
}
