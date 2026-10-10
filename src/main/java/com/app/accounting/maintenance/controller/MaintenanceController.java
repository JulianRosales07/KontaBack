package com.app.accounting.maintenance.controller;

import com.app.accounting.maintenance.dto.DatabaseCleanupRequest;
import com.app.accounting.maintenance.dto.DatabaseCleanupResponse;
import com.app.accounting.maintenance.service.DatabaseCleanupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class MaintenanceController {

    private final DatabaseCleanupService cleanupService;

    @PostMapping("/database-cleanup")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMINISTRADOR')")
    public ResponseEntity<DatabaseCleanupResponse> cleanupDatabase(@Valid @RequestBody DatabaseCleanupRequest request) {
        Map<String, Integer> summary = cleanupService.performCleanup(request);
        return ResponseEntity.ok(DatabaseCleanupResponse.builder()
                .message("Limpieza de base de datos realizada con éxito.")
                .deletedCounts(summary)
                .build());
    }
}
