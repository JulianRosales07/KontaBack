package com.app.accounting.closing.service.impl;

import com.app.accounting.closing.dto.response.AnnualClosingResponse;
import com.app.accounting.closing.service.AccountingPeriodService;
import com.app.accounting.closing.service.AnnualClosingService;
import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.common.exception.ResourceNotFoundException;
import com.app.accounting.common.util.BigDecimalUtils;
import com.app.accounting.puc.entity.Account;
import com.app.accounting.puc.entity.AccountNature;
import com.app.accounting.puc.repository.AccountRepository;
import com.app.accounting.voucher.entity.JournalEntry;
import com.app.accounting.voucher.entity.JournalVoucher;
import com.app.accounting.voucher.entity.VoucherStatus;
import com.app.accounting.voucher.entity.VoucherType;
import com.app.accounting.voucher.exception.UnbalancedVoucherException;
import com.app.accounting.voucher.repository.AccountBalanceProjection;
import com.app.accounting.voucher.repository.JournalEntryRepository;
import com.app.accounting.voucher.repository.JournalVoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnnualClosingServiceImpl implements AnnualClosingService {

    private final JournalVoucherRepository voucherRepository;
    private final JournalEntryRepository entryRepository;
    private final AccountRepository accountRepository;
    private final AccountingPeriodService periodService;

    @Override
    @Transactional
    public AnnualClosingResponse executeYearEndClosing(int year) {
        String voucherNumber = "CIERRE-" + year;
        if (voucherRepository.existsByVoucherNumber(voucherNumber)) {
            throw new BusinessRuleException("El cierre contable para el ejercicio " + year + " ya fue ejecutado previamente (" + voucherNumber + ").");
        }

        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        // 1. Obtener saldos del año de todas las cuentas con movimiento
        List<AccountBalanceProjection> balances = entryRepository.findAccountBalancesBetweenDates(startDate, endDate);

        // 2. Obtener cuentas clave del PUC para el cierre
        Account account5905 = findClosingAccount("5905", "590505", "GANANCIAS Y PERDIDAS", AccountNature.DEBIT);
        Account account3605 = findClosingAccount("3605", "360505", "UTILIDAD DEL EJERCICIO", AccountNature.CREDIT);
        Account account3610 = findClosingAccount("3610", "361005", "PERDIDA DEL EJERCICIO", AccountNature.DEBIT);

        JournalVoucher closingVoucher = JournalVoucher.builder()
                .voucherNumber(voucherNumber)
                .voucherType(VoucherType.NOTA_CONTABLE)
                .issueDate(endDate)
                .description("Cierre contable anual del ejercicio " + year + " - Cancelación de cuentas de resultado y traslado a patrimonio")
                .status(VoucherStatus.POSTED)
                .entries(new ArrayList<>())
                .build();

        BigDecimal totalRevenues = BigDecimal.ZERO;
        BigDecimal totalExpensesAndCosts = BigDecimal.ZERO;

        for (AccountBalanceProjection b : balances) {
            String code = b.getAccountCode();
            if (code == null || code.length() < 1) {
                continue;
            }

            char accountClass = code.charAt(0);
            if (accountClass != '4' && accountClass != '5' && accountClass != '6' && accountClass != '7') {
                continue;
            }

            Account account = accountRepository.findById(b.getAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada: " + b.getAccountCode()));

            BigDecimal debit = b.getTotalDebit() != null ? b.getTotalDebit() : BigDecimal.ZERO;
            BigDecimal credit = b.getTotalCredit() != null ? b.getTotalCredit() : BigDecimal.ZERO;

            if (accountClass == '4') {
                // Clase 4: Ingresos (Naturaleza Crédito)
                BigDecimal netCredit = credit.subtract(debit);
                if (netCredit.compareTo(BigDecimal.ZERO) > 0) {
                    // Para cancelar el saldo crédito de la 4, debitamos la cuenta y acreditamos 5905
                    closingVoucher.addEntry(JournalEntry.builder()
                            .account(account)
                            .description("Cancelación saldo anual " + account.getCode() + " - " + account.getName())
                            .debit(netCredit)
                            .credit(BigDecimal.ZERO)
                            .build());

                    closingVoucher.addEntry(JournalEntry.builder()
                            .account(account5905)
                            .description("Traslado de ingresos por cierre anual - " + account.getCode())
                            .debit(BigDecimal.ZERO)
                            .credit(netCredit)
                            .build());

                    totalRevenues = totalRevenues.add(netCredit);
                } else if (netCredit.compareTo(BigDecimal.ZERO) < 0) {
                    BigDecimal netDebit = netCredit.abs();
                    closingVoucher.addEntry(JournalEntry.builder()
                            .account(account)
                            .description("Cancelación saldo débito " + account.getCode())
                            .debit(BigDecimal.ZERO)
                            .credit(netDebit)
                            .build());

                    closingVoucher.addEntry(JournalEntry.builder()
                            .account(account5905)
                            .description("Traslado ajuste ingresos por cierre anual - " + account.getCode())
                            .debit(netDebit)
                            .credit(BigDecimal.ZERO)
                            .build());

                    totalRevenues = totalRevenues.subtract(netDebit);
                }
            } else {
                // Clases 5, 6, 7: Gastos y Costos (Naturaleza Débito)
                BigDecimal netDebit = debit.subtract(credit);
                if (netDebit.compareTo(BigDecimal.ZERO) > 0) {
                    // Para cancelar el saldo débito de 5/6/7, acreditamos la cuenta y debitamos 5905
                    closingVoucher.addEntry(JournalEntry.builder()
                            .account(account)
                            .description("Cancelación saldo anual " + account.getCode() + " - " + account.getName())
                            .debit(BigDecimal.ZERO)
                            .credit(netDebit)
                            .build());

                    closingVoucher.addEntry(JournalEntry.builder()
                            .account(account5905)
                            .description("Traslado gastos/costos por cierre anual - " + account.getCode())
                            .debit(netDebit)
                            .credit(BigDecimal.ZERO)
                            .build());

                    totalExpensesAndCosts = totalExpensesAndCosts.add(netDebit);
                } else if (netDebit.compareTo(BigDecimal.ZERO) < 0) {
                    BigDecimal netCredit = netDebit.abs();
                    closingVoucher.addEntry(JournalEntry.builder()
                            .account(account)
                            .description("Cancelación saldo crédito en gasto " + account.getCode())
                            .debit(netCredit)
                            .credit(BigDecimal.ZERO)
                            .build());

                    closingVoucher.addEntry(JournalEntry.builder()
                            .account(account5905)
                            .description("Traslado ajuste gastos por cierre anual - " + account.getCode())
                            .debit(BigDecimal.ZERO)
                            .credit(netCredit)
                            .build());

                    totalExpensesAndCosts = totalExpensesAndCosts.subtract(netCredit);
                }
            }
        }

        // 3. Determinar el resultado neto del ejercicio (Ingresos - Gastos y Costos)
        BigDecimal netResult = totalRevenues.subtract(totalExpensesAndCosts);
        String resultType;
        String destinationAccountCode;

        if (netResult.compareTo(BigDecimal.ZERO) > 0) {
            // UTILIDAD DEL EJERCICIO: Se debita la 5905 y se acredita la 3605 (Utilidad del Ejercicio)
            resultType = "UTILIDAD";
            destinationAccountCode = account3605.getCode();

            closingVoucher.addEntry(JournalEntry.builder()
                    .account(account5905)
                    .description("Cancelación saldo cuenta 5905 por utilidad del ejercicio " + year)
                    .debit(netResult)
                    .credit(BigDecimal.ZERO)
                    .build());

            closingVoucher.addEntry(JournalEntry.builder()
                    .account(account3605)
                    .description("Traslado utilidad neta del ejercicio " + year + " al patrimonio")
                    .debit(BigDecimal.ZERO)
                    .credit(netResult)
                    .build());

        } else if (netResult.compareTo(BigDecimal.ZERO) < 0) {
            // PÉRDIDA DEL EJERCICIO: Se acredita la 5905 y se debita la 3610 (Pérdida del Ejercicio)
            resultType = "PERDIDA";
            destinationAccountCode = account3610.getCode();
            BigDecimal absoluteLoss = netResult.abs();

            closingVoucher.addEntry(JournalEntry.builder()
                    .account(account3610)
                    .description("Traslado pérdida neta del ejercicio " + year + " al patrimonio")
                    .debit(absoluteLoss)
                    .credit(BigDecimal.ZERO)
                    .build());

            closingVoucher.addEntry(JournalEntry.builder()
                    .account(account5905)
                    .description("Cancelación saldo cuenta 5905 por pérdida del ejercicio " + year)
                    .debit(BigDecimal.ZERO)
                    .credit(absoluteLoss)
                    .build());

        } else {
            resultType = "EQUILIBRIO";
            destinationAccountCode = "N/A";
        }

        // 4. Validar partida doble
        validateDoubleEntry(closingVoucher);

        // 5. Guardar comprobante de cierre contable
        JournalVoucher savedVoucher = voucherRepository.save(closingVoucher);

        // 6. Cerrar todos los 12 meses del ejercicio contable
        for (int m = 1; m <= 12; m++) {
            periodService.closePeriod(year, m, "CIERRE_ANUAL_SISTEMA");
        }

        log.info("Cierre contable anual del año {} ejecutado con éxito. Comprobante: {}. Resultado: {} ({})",
                year, voucherNumber, netResult, resultType);

        return AnnualClosingResponse.builder()
                .year(year)
                .voucherNumber(savedVoucher.getVoucherNumber())
                .voucherId(savedVoucher.getId())
                .totalRevenues(totalRevenues)
                .totalExpensesAndCosts(totalExpensesAndCosts)
                .netResult(netResult)
                .resultType(resultType)
                .destinationAccountCode(destinationAccountCode)
                .message("Cierre contable anual del año " + year + " completado exitosamente.")
                .build();
    }

    private Account findClosingAccount(String primaryCode, String fallbackCode, String defaultName, AccountNature nature) {
        return accountRepository.findByCode(primaryCode)
                .or(() -> accountRepository.findByCode(fallbackCode))
                .orElseGet(() -> accountRepository.save(Account.builder()
                        .code(primaryCode)
                        .name(defaultName)
                        .level(3)
                        .nature(nature)
                        .requiresThirdParty(false)
                        .isActive(true)
                        .build()));
    }

    private void validateDoubleEntry(JournalVoucher voucher) {
        if (voucher.getEntries() == null || voucher.getEntries().isEmpty()) {
            return;
        }

        BigDecimal totalDebit = voucher.getEntries().stream()
                .map(JournalEntry::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredit = voucher.getEntries().stream()
                .map(JournalEntry::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (!BigDecimalUtils.areEqual(totalDebit, totalCredit)) {
            throw new UnbalancedVoucherException(totalDebit, totalCredit);
        }
    }
}
