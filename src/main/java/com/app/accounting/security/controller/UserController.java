package com.app.accounting.security.controller;

import com.app.accounting.security.dto.request.ChangePasswordRequest;
import com.app.accounting.security.dto.request.CreateUserRequest;
import com.app.accounting.security.dto.request.UpdateUserRequest;
import com.app.accounting.security.dto.response.UserResponse;
import com.app.accounting.security.service.UserService;
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
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Endpoints de administración y gestión de usuarios del sistema")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_CONTADOR')")
    @Operation(summary = "Listar todos los usuarios del sistema")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_CONTADOR')")
    @Operation(summary = "Obtener un usuario por su ID")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Crear un nuevo usuario con rol asignado")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Actualizar datos y rol de un usuario")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Activar o desactivar un usuario")
    public ResponseEntity<UserResponse> toggleStatus(
            @PathVariable UUID id,
            @RequestParam boolean enabled
    ) {
        return ResponseEntity.ok(userService.toggleUserStatus(id, enabled));
    }

    @PatchMapping("/{id}/password")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Cambiar la contraseña de un usuario")
    public ResponseEntity<Void> changePassword(
            @PathVariable UUID id,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userService.changePassword(id, request);
        return ResponseEntity.noContent().build();
    }
}
