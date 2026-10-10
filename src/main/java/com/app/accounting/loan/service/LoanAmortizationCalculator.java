package com.app.accounting.loan.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Única fuente de verdad del cálculo de préstamos de la asociación: amortización bajo saldo de capital
 * (abono constante a capital, interés sobre el saldo pendiente).
 *
 * <pre>
 * capitalCuota  = principal / numeroCuotas            (la última cuota absorbe el residuo del redondeo)
 * interesCuota  = saldoInicial * tasaPeriodo
 * valorCuota    = capitalCuota + interesCuota
 * saldoFinal    = saldoInicial - capitalCuota
 * </pre>
 *
 * Los valores se redondean a pesos enteros (COP no maneja centavos en la práctica) con HALF_UP.
 */
@Component
public class LoanAmortizationCalculator {

    public static final int MONEY_SCALE = 0;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public record ScheduleLine(
            int number,
            LocalDate dueDate,
            BigDecimal openingBalance,
            BigDecimal principal,
            BigDecimal interest,
            BigDecimal total,
            BigDecimal closingBalance
    ) {
    }

    public record Schedule(
            List<ScheduleLine> lines,
            BigDecimal totalPrincipal,
            BigDecimal totalInterest,
            BigDecimal totalPayable,
            BigDecimal firstInstallment
    ) {
    }

    /** Aplicación de un pago a una cuota: primero interés pendiente, luego capital. */
    public record Allocation(BigDecimal interest, BigDecimal principal) {
        public BigDecimal total() {
            return interest.add(principal);
        }
    }

    /**
     * @param principal          valor desembolsado
     * @param monthlyRatePercent tasa del periodo en porcentaje (2 = 2% mensual)
     * @param termMonths         número de cuotas
     * @param firstPaymentDate   vencimiento de la primera cuota; las siguientes vencen mes a mes
     */
    public Schedule buildSchedule(BigDecimal principal, BigDecimal monthlyRatePercent, int termMonths, LocalDate firstPaymentDate) {
        if (principal == null || principal.signum() <= 0) {
            throw new IllegalArgumentException("El valor del préstamo debe ser mayor a cero");
        }
        if (monthlyRatePercent == null || monthlyRatePercent.signum() < 0) {
            throw new IllegalArgumentException("La tasa de interés no puede ser negativa");
        }
        if (termMonths <= 0) {
            throw new IllegalArgumentException("El número de cuotas debe ser mayor a cero");
        }
        if (firstPaymentDate == null) {
            throw new IllegalArgumentException("La fecha de la primera cuota es obligatoria");
        }

        BigDecimal amount = money(principal);
        BigDecimal rate = monthlyRatePercent.divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP);
        BigDecimal fixedPrincipal = amount.divide(BigDecimal.valueOf(termMonths), MONEY_SCALE, RoundingMode.HALF_UP);

        List<ScheduleLine> lines = new ArrayList<>(termMonths);
        BigDecimal balance = amount;
        BigDecimal totalInterest = BigDecimal.ZERO;

        for (int i = 1; i <= termMonths; i++) {
            BigDecimal opening = balance;
            BigDecimal capital = (i == termMonths) ? opening : fixedPrincipal.min(opening);
            BigDecimal interest = money(opening.multiply(rate));
            BigDecimal closing = opening.subtract(capital);

            lines.add(new ScheduleLine(i, firstPaymentDate.plusMonths(i - 1L), opening, capital, interest,
                    capital.add(interest), closing));

            totalInterest = totalInterest.add(interest);
            balance = closing;
        }

        return new Schedule(lines, amount, totalInterest, amount.add(totalInterest), lines.getFirst().total());
    }

    /**
     * Distribuye un pago sobre lo pendiente de una cuota. Primero cubre interés y luego capital.
     *
     * @throws IllegalArgumentException si el pago excede lo pendiente de la cuota
     */
    public Allocation allocate(BigDecimal payment, BigDecimal interestPending, BigDecimal principalPending) {
        BigDecimal amount = money(payment);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("El valor del abono debe ser mayor a cero");
        }
        BigDecimal pending = interestPending.add(principalPending);
        if (amount.compareTo(pending) > 0) {
            throw new IllegalArgumentException("El abono ($" + amount.toPlainString()
                    + ") excede el saldo pendiente de la cuota ($" + pending.toPlainString() + ")");
        }
        BigDecimal toInterest = amount.min(interestPending);
        BigDecimal toPrincipal = amount.subtract(toInterest);
        return new Allocation(toInterest, toPrincipal);
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
