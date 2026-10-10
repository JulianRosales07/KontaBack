package com.app.accounting.associate.controller;

import com.app.accounting.associate.dto.request.AssociateImportRequest;
import com.app.accounting.associate.dto.response.AssociateSummaryResponse;
import com.app.accounting.associate.service.AssociateDirectoryService;
import com.app.accounting.loan.dto.response.LegacyImportResultResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/associates")
@RequiredArgsConstructor
@Tag(name = "Directorio de Asociados", description = "Búsqueda global de asociados y consolidado de aportes y ahorros")
public class AssociateDirectoryController {

    private final AssociateDirectoryService directoryService;

    @GetMapping("/directory")
    @PreAuthorize("hasAnyAuthority('ASSOCIATES_VIEW', 'COLLECTIONS_VIEW', 'LOANS_VIEW')")
    @Operation(summary = "Buscar asociados por nombre, documento, correo, teléfono o código, con filtros")
    public ResponseEntity<List<AssociateSummaryResponse>> directory(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String idType,
            @RequestParam(required = false) Boolean associatesOnly,
            @RequestParam(required = false) Boolean hasLoan,
            @RequestParam(required = false) Boolean hasSavings,
            @RequestParam(required = false) Boolean hasContributions
    ) {
        return ResponseEntity.ok(directoryService.search(new AssociateDirectoryService.Filters(
                q, status, idType, associatesOnly, hasLoan, hasSavings, hasContributions)));
    }

    @GetMapping("/payables")
    @PreAuthorize("hasAuthority('PAYABLES_VIEW')")
    @Operation(summary = "Cuentas por pagar a asociados: aportes y ahorros acumulados (separados)")
    public ResponseEntity<List<AssociateSummaryResponse>> payables(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(directoryService.payables(q));
    }

    @PostMapping("/directory/import")
    @PreAuthorize("hasAuthority('ASSOCIATES_CREATE')")
    @Operation(summary = "Importar una única vez los terceros guardados en el navegador (por número de documento)")
    public ResponseEntity<LegacyImportResultResponse> importAssociates(@Valid @RequestBody AssociateImportRequest request) {
        return ResponseEntity.ok(directoryService.importAssociates(request));
    }
}
