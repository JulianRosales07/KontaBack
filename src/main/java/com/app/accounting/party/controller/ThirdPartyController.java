package com.app.accounting.party.controller;

import com.app.accounting.party.dto.request.ThirdPartyRequest;
import com.app.accounting.party.dto.response.ThirdPartyResponse;
import com.app.accounting.party.service.ThirdPartyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/parties")
@RequiredArgsConstructor
@Tag(name = "Terceros", description = "Gestión de asociados, clientes y proveedores")
@PreAuthorize("hasAuthority('ASSOCIATES_VIEW')")
public class ThirdPartyController {

    private final ThirdPartyService thirdPartyService;

    @PreAuthorize("hasAuthority('ASSOCIATES_CREATE')")
    @PostMapping
    @Operation(summary = "Crear un nuevo tercero")
    public ResponseEntity<ThirdPartyResponse> createThirdParty(@Valid @RequestBody ThirdPartyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(thirdPartyService.createThirdParty(request));
    }

    @PreAuthorize("hasAuthority('ASSOCIATES_UPDATE')")
    @PutMapping("/{id}")
    @Operation(summary = "Actualizar información de un tercero")
    public ResponseEntity<ThirdPartyResponse> updateThirdParty(@PathVariable UUID id, @Valid @RequestBody ThirdPartyRequest request) {
        return ResponseEntity.ok(thirdPartyService.updateThirdParty(id, request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener tercero por UUID")
    public ResponseEntity<ThirdPartyResponse> getThirdPartyById(@PathVariable UUID id) {
        return ResponseEntity.ok(thirdPartyService.getThirdPartyById(id));
    }

    @GetMapping("/identification/{identificationNumber}")
    @Operation(summary = "Obtener tercero por número de identificación (NIT/Cédula)")
    public ResponseEntity<ThirdPartyResponse> getThirdPartyByIdentification(@PathVariable String identificationNumber) {
        return ResponseEntity.ok(thirdPartyService.getThirdPartyByIdentification(identificationNumber));
    }

    @GetMapping
    @Operation(summary = "Listar y buscar terceros paginados")
    public ResponseEntity<Page<ThirdPartyResponse>> getAllThirdParties(
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(thirdPartyService.getAllThirdParties(query, pageable));
    }
}
