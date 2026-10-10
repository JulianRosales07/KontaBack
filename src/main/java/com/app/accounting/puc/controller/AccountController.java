package com.app.accounting.puc.controller;

import com.app.accounting.puc.dto.request.AccountRequest;
import com.app.accounting.puc.dto.response.AccountResponse;
import com.app.accounting.puc.service.AccountService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/accounts", "/api/v1/puc"})
@RequiredArgsConstructor
@Tag(name = "Cuentas Contables (PUC)", description = "Gestión del catálogo de cuentas jerárquico PUC")
@PreAuthorize("hasAuthority('PUC_VIEW')")
public class AccountController {

    private final AccountService accountService;

    @PreAuthorize("hasAuthority('PUC_MANAGE')")
    @PostMapping
    @Operation(summary = "Crear una nueva cuenta contable PUC")
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody AccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(request));
    }

    @PreAuthorize("hasAuthority('PUC_MANAGE')")
    @PutMapping("/{id}")
    @Operation(summary = "Actualizar una cuenta contable existente")
    public ResponseEntity<AccountResponse> updateAccount(@PathVariable UUID id, @Valid @RequestBody AccountRequest request) {
        return ResponseEntity.ok(accountService.updateAccount(id, request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener cuenta contable por su identificador UUID")
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.getAccountById(id));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Obtener cuenta contable por código PUC (ej: 110505)")
    public ResponseEntity<AccountResponse> getAccountByCode(@PathVariable String code) {
        return ResponseEntity.ok(accountService.getAccountByCode(code));
    }

    @GetMapping
    @Operation(summary = "Listar cuentas contables paginadas")
    public ResponseEntity<Page<AccountResponse>> getAllAccounts(
            @org.springdoc.core.annotations.ParameterObject @PageableDefault(size = 20, sort = "code") Pageable pageable
    ) {
        return ResponseEntity.ok(accountService.getAllAccounts(pageable));
    }

    @GetMapping("/active")
    @Operation(summary = "Listar todas las cuentas contables activas ordenadas por código")
    public ResponseEntity<List<AccountResponse>> getAllActiveAccounts() {
        return ResponseEntity.ok(accountService.getAllActiveAccounts());
    }

    @PreAuthorize("hasAuthority('PUC_MANAGE')")
    @PostMapping(value = "/import-puc", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importar catálogo de cuentas PUC desde archivo Excel (.xlsx)")
    public ResponseEntity<com.app.accounting.puc.dto.response.PucImportResultResponse> importPuc(
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file
    ) {
        try {
            return ResponseEntity.ok(accountService.importPucFromExcel(file.getInputStream()));
        } catch (java.io.IOException e) {
            throw new com.app.accounting.common.exception.BusinessRuleException("Error leyendo el archivo: " + e.getMessage(), e);
        }
    }

    @PreAuthorize("hasAuthority('PUC_MANAGE')")
    @PostMapping("/import-default-puc")
    @Operation(summary = "Importar catálogo PUC desde el archivo predeterminado del sistema (Docs/PUC_Cuentas_Comerciantes.xlsx)")
    public ResponseEntity<com.app.accounting.puc.dto.response.PucImportResultResponse> importDefaultPuc() {
        return ResponseEntity.ok(accountService.importDefaultPuc());
    }
}
