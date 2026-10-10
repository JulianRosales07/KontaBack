package com.app.accounting.importer.controller;

import com.app.accounting.importer.dto.response.ImportResultResponse;
import com.app.accounting.importer.service.ExcelImporterService;
import com.app.accounting.voucher.entity.VoucherType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/importers/excel")
@RequiredArgsConstructor
@Tag(name = "Importador Excel", description = "Importación masiva de comprobantes y asientos contables vía .xlsx")
@PreAuthorize("hasAuthority('ACCOUNTING_CREATE')")
public class ExcelImporterController {

    private final ExcelImporterService excelImporterService;

    @PostMapping(value = "/voucher", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importar comprobante contable desde archivo Excel .xlsx con validación atómica")
    public ResponseEntity<ImportResultResponse> importVoucher(
            @Parameter(description = "Archivo .xlsx con asientos contables")
            @RequestPart("file") MultipartFile file,
            @RequestParam("voucherNumber") String voucherNumber,
            @RequestParam(value = "voucherType", defaultValue = "NOTA_CONTABLE") VoucherType voucherType,
            @RequestParam(value = "issueDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDate,
            @RequestParam(value = "description", required = false) String description
    ) {
        ImportResultResponse response = excelImporterService.importVoucherFromExcel(
                file,
                voucherNumber,
                voucherType,
                issueDate != null ? issueDate : LocalDate.now(),
                description
        );

        if (!response.isSuccess()) {
            return ResponseEntity.unprocessableEntity().body(response);
        }

        return ResponseEntity.ok(response);
    }
}
