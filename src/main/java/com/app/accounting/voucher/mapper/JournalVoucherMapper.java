package com.app.accounting.voucher.mapper;

import com.app.accounting.voucher.dto.response.JournalEntryResponse;
import com.app.accounting.voucher.dto.response.JournalVoucherResponse;
import com.app.accounting.voucher.entity.JournalEntry;
import com.app.accounting.voucher.entity.JournalVoucher;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface JournalVoucherMapper {

    @Mapping(source = "account.id", target = "accountId")
    @Mapping(source = "account.code", target = "accountCode")
    @Mapping(source = "account.name", target = "accountName")
    @Mapping(source = "thirdParty.id", target = "thirdPartyId")
    @Mapping(source = "thirdParty.identificationNumber", target = "thirdPartyIdentification")
    @Mapping(source = "thirdParty.fullName", target = "thirdPartyName")
    JournalEntryResponse toEntryResponse(JournalEntry entry);

    List<JournalEntryResponse> toEntryResponseList(List<JournalEntry> entries);

    default JournalVoucherResponse toVoucherResponse(JournalVoucher voucher) {
        if (voucher == null) {
            return null;
        }

        List<JournalEntryResponse> entryResponses = toEntryResponseList(voucher.getEntries());

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        if (voucher.getEntries() != null) {
            for (JournalEntry entry : voucher.getEntries()) {
                if (entry.getDebit() != null) {
                    totalDebit = totalDebit.add(entry.getDebit());
                }
                if (entry.getCredit() != null) {
                    totalCredit = totalCredit.add(entry.getCredit());
                }
            }
        }

        return JournalVoucherResponse.builder()
                .id(voucher.getId())
                .voucherNumber(voucher.getVoucherNumber())
                .voucherType(voucher.getVoucherType())
                .issueDate(voucher.getIssueDate())
                .description(voucher.getDescription())
                .status(voucher.getStatus())
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .createdAt(voucher.getCreatedAt())
                .updatedAt(voucher.getUpdatedAt())
                .createdBy(voucher.getCreatedBy())
                .entries(entryResponses)
                .build();
    }
}
