package com.app.accounting.importer.service;

import com.app.accounting.importer.dto.response.ImportResultResponse;
import com.app.accounting.voucher.entity.VoucherType;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

public interface ExcelImporterService {
    ImportResultResponse importVoucherFromExcel(
            MultipartFile file,
            String voucherNumber,
            VoucherType voucherType,
            LocalDate issueDate,
            String description
    );
}
