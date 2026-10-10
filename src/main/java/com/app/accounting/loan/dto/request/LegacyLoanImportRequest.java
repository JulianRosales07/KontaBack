package com.app.accounting.loan.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Importación única de préstamos que existían solo en el navegador (localStorage). Su plan NO se recalcula. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegacyLoanImportRequest {

    @NotEmpty(message = "No hay préstamos para importar")
    @Valid
    @Builder.Default
    private List<LegacyLoan> loans = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LegacyLoan {
        @NotBlank
        private String loanNumber;
        @NotBlank
        private String associateIdentification;
        @NotBlank
        private String associateName;
        @NotNull
        private BigDecimal principalAmount;
        @NotNull
        private BigDecimal monthlyRate;
        @NotNull
        private Integer termMonths;
        private String amortizationType;
        @NotNull
        private LocalDate disbursementDate;
        @NotNull
        private LocalDate firstPaymentDate;
        private String notes;
        @NotEmpty
        @Valid
        @Builder.Default
        private List<LegacyInstallment> installments = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LegacyInstallment {
        @NotNull
        private Integer number;
        @NotNull
        private LocalDate dueDate;
        @NotNull
        private BigDecimal principal;
        @NotNull
        private BigDecimal interest;
        @NotNull
        private BigDecimal total;
        @NotNull
        private BigDecimal remainingBalance;
        /** PENDING | PARTIAL | PAID */
        private String status;
        private BigDecimal paidAmount;
        private LocalDate paidDate;
    }
}
