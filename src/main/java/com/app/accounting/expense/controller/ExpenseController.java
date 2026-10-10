package com.app.accounting.expense.controller;

import com.app.accounting.billing.dto.response.DebtDocumentResponse;
import com.app.accounting.billing.entity.DocumentStatus;
import com.app.accounting.expense.dto.request.EquivalentDocumentRequest;
import com.app.accounting.expense.dto.request.PurchaseInvoiceRequest;
import com.app.accounting.expense.dto.response.EquivalentDocumentResponse;
import com.app.accounting.expense.dto.response.PurchaseInvoiceResponse;
import com.app.accounting.expense.service.ExpenseService;
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
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
@Tag(name = "Compras y Cuentas por Pagar (Gastos)", description = "Registro de compras y facturas de proveedores con retenciones y causación automática")
@PreAuthorize("hasAuthority('ACCOUNTING_VIEW')")
public class ExpenseController {

    private final ExpenseService expenseService;

    @PreAuthorize("hasAuthority('ACCOUNTING_CREATE')")
    @PostMapping("/invoices")
    @Operation(summary = "Registrar factura de proveedor (compra/gasto) con liquidación de impuestos y causación contable automática")
    public ResponseEntity<PurchaseInvoiceResponse> createPurchaseInvoice(@Valid @RequestBody PurchaseInvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(expenseService.createPurchaseInvoice(request));
    }

    @PreAuthorize("hasAuthority('ACCOUNTING_CREATE')")
    @PostMapping("/equivalent-documents")
    @Operation(summary = "Crear Documento Equivalente / Soporte (como comprobante contable de causación o recibo de egreso directo)")
    public ResponseEntity<EquivalentDocumentResponse> createEquivalentDocument(@Valid @RequestBody EquivalentDocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(expenseService.createEquivalentDocument(request));
    }

    @GetMapping("/payables")
    @Operation(summary = "Listar cuentas por pagar (proveedores) filtradas por tercero y estado")
    public ResponseEntity<Page<DebtDocumentResponse>> getPayables(
            @RequestParam(required = false) UUID thirdPartyId,
            @RequestParam(required = false) DocumentStatus status,
            @org.springdoc.core.annotations.ParameterObject @PageableDefault(size = 20, sort = "dueDate") Pageable pageable
    ) {
        return ResponseEntity.ok(expenseService.getPayables(thirdPartyId, status, pageable));
    }
}

