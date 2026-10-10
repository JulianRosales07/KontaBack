package com.app.accounting.voucher.service;

import com.app.accounting.voucher.dto.request.JournalVoucherRequest;
import com.app.accounting.voucher.dto.response.JournalVoucherResponse;
import com.app.accounting.voucher.entity.VoucherStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface VoucherService {
    JournalVoucherResponse createAndPostVoucher(JournalVoucherRequest request);
    JournalVoucherResponse createDraftVoucher(JournalVoucherRequest request);
    JournalVoucherResponse postVoucher(UUID voucherId);
    JournalVoucherResponse reverseVoucher(UUID voucherId, String reason);
    JournalVoucherResponse getVoucherById(UUID id);
    JournalVoucherResponse getVoucherByNumber(String voucherNumber);
    Page<JournalVoucherResponse> getAllVouchers(LocalDate startDate, LocalDate endDate, VoucherStatus status, Pageable pageable);

    Page<JournalVoucherResponse> getAllVouchers(LocalDate startDate, LocalDate endDate, VoucherStatus status,
                                                com.app.accounting.voucher.entity.VoucherType type, String query, Pageable pageable);
    void deleteDraftVoucher(UUID voucherId);
}
