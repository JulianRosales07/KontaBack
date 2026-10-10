package com.app.accounting.importer.monthly;

import com.app.accounting.common.exception.BusinessRuleException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/api/v1/importers/monthly-report")
@RequiredArgsConstructor
@Tag(name = "Importador del informe mensual", description = "Carga el informe mensual de la asociación (Excel) como apertura y comprobantes del mes, con conciliación")
@PreAuthorize("hasAuthority('ACCOUNTING_CREATE')")
public class MonthlyReportImportController {

    private final MonthlyReportImportService importService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importar el informe mensual. Con dryRun=true (por defecto) simula, concilia y no guarda nada")
    public ResponseEntity<MonthlyReportImportResponse> importReport(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean dryRun
    ) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Adjunte el archivo .xlsx del informe mensual");
        }
        try (InputStream in = file.getInputStream()) {
            MonthlyReportImportResponse response = importService.importReport(in, file.getOriginalFilename(), dryRun);
            return response.getErrors().isEmpty() ? ResponseEntity.ok(response) : ResponseEntity.status(422).body(response);
        } catch (IOException e) {
            throw new BusinessRuleException("No se pudo leer el archivo: " + e.getMessage());
        }
    }
}
