package com.app.accounting.fine;

import com.app.accounting.fine.FineDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fines")
@RequiredArgsConstructor
@Tag(name = "Multas", description = "Multas de asociados (inasistencia y otras), cobro y condonación")
public class FineController {

    private final FineService service;

    @GetMapping("/types")
    @PreAuthorize("hasAnyAuthority('FINES_VIEW', 'COLLECTIONS_CREATE')")
    @Operation(summary = "Tipos de multa y su valor")
    public ResponseEntity<List<FineTypeResponse>> types() {
        return ResponseEntity.ok(service.types());
    }

    @PostMapping("/types")
    @PreAuthorize("hasAuthority('FINES_MANAGE')")
    @Operation(summary = "Crear un tipo de multa")
    public ResponseEntity<FineTypeResponse> createType(@Valid @RequestBody FineTypeRequest request) {
        return ResponseEntity.ok(service.createType(request));
    }

    @PutMapping("/types/{id}")
    @PreAuthorize("hasAuthority('FINES_MANAGE')")
    @Operation(summary = "Editar un tipo de multa (el nuevo valor aplica a las multas nuevas)")
    public ResponseEntity<FineTypeResponse> updateType(@PathVariable UUID id, @Valid @RequestBody FineTypeRequest request) {
        return ResponseEntity.ok(service.updateType(id, request));
    }

    @DeleteMapping("/types/{id}")
    @PreAuthorize("hasAuthority('FINES_MANAGE')")
    @Operation(summary = "Eliminar un tipo de multa (si ya tiene multas, se desactiva)")
    public ResponseEntity<Void> deleteType(@PathVariable UUID id) {
        service.deleteType(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('FINES_VIEW', 'COLLECTIONS_CREATE')")
    @Operation(summary = "Buscar multas por estado, asociado, tipo y fechas")
    public ResponseEntity<List<FineResponse>> search(
            @RequestParam(required = false) AssociateFine.Status status,
            @RequestParam(required = false) UUID thirdPartyId,
            @RequestParam(required = false) UUID fineTypeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(service.search(status, thirdPartyId, fineTypeId, from, to));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('FINES_VIEW')")
    @Operation(summary = "Totales de multas pendientes, pagadas y condonadas")
    public ResponseEntity<FineSummary> summary(@RequestParam(required = false) UUID thirdPartyId) {
        return ResponseEntity.ok(service.summary(thirdPartyId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FINES_MANAGE')")
    @Operation(summary = "Registrar una multa a uno o varios asociados (p. ej. inasistentes a una reunión)")
    public ResponseEntity<RegisterFinesResponse> register(@Valid @RequestBody RegisterFinesRequest request) {
        return ResponseEntity.ok(service.register(request));
    }

    @PostMapping("/pay")
    @PreAuthorize("hasAuthority('FINES_MANAGE')")
    @Operation(summary = "Cobrar multas pendientes (un comprobante de ingreso por asociado, crédito 429510)")
    public ResponseEntity<PayFinesResponse> pay(@Valid @RequestBody PayFinesRequest request) {
        return ResponseEntity.ok(service.pay(request));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('FINES_MANAGE')")
    @Operation(summary = "Condonar una multa pendiente indicando el motivo")
    public ResponseEntity<FineResponse> voidFine(@PathVariable UUID id, @Valid @RequestBody VoidFineRequest request) {
        return ResponseEntity.ok(service.voidFine(id, request));
    }
}
