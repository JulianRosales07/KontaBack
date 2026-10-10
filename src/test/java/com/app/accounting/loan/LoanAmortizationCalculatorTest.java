package com.app.accounting.loan;

import com.app.accounting.loan.service.LoanAmortizationCalculator;
import com.app.accounting.loan.service.LoanAmortizationCalculator.Schedule;
import com.app.accounting.loan.service.LoanAmortizationCalculator.ScheduleLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoanAmortizationCalculatorTest {

    private final LoanAmortizationCalculator calculator = new LoanAmortizationCalculator();

    private static BigDecimal $(long v) {
        return BigDecimal.valueOf(v);
    }

    @Test
    @DisplayName("Caso obligatorio: $1.000.000, 10 cuotas, 2% mensual — interés sobre saldo de capital")
    void mandatoryCase() {
        Schedule s = calculator.buildSchedule($(1_000_000), new BigDecimal("2"), 10, LocalDate.of(2026, 11, 1));

        assertThat(s.lines()).hasSize(10);

        ScheduleLine c1 = s.lines().get(0);
        assertThat(c1.openingBalance()).isEqualByComparingTo($(1_000_000));
        assertThat(c1.principal()).isEqualByComparingTo($(100_000));
        assertThat(c1.interest()).isEqualByComparingTo($(20_000));
        assertThat(c1.total()).isEqualByComparingTo($(120_000));
        assertThat(c1.closingBalance()).isEqualByComparingTo($(900_000));

        ScheduleLine c2 = s.lines().get(1);
        assertThat(c2.openingBalance()).isEqualByComparingTo($(900_000));
        assertThat(c2.principal()).isEqualByComparingTo($(100_000));
        assertThat(c2.interest()).isEqualByComparingTo($(18_000));
        assertThat(c2.total()).isEqualByComparingTo($(118_000));
        assertThat(c2.closingBalance()).isEqualByComparingTo($(800_000));

        ScheduleLine c3 = s.lines().get(2);
        assertThat(c3.openingBalance()).isEqualByComparingTo($(800_000));
        assertThat(c3.interest()).isEqualByComparingTo($(16_000));
        assertThat(c3.total()).isEqualByComparingTo($(116_000));
        assertThat(c3.closingBalance()).isEqualByComparingTo($(700_000));

        ScheduleLine c10 = s.lines().get(9);
        assertThat(c10.openingBalance()).isEqualByComparingTo($(100_000));
        assertThat(c10.interest()).isEqualByComparingTo($(2_000));
        assertThat(c10.closingBalance()).isEqualByComparingTo(BigDecimal.ZERO);

        // Σ intereses = 2% × (1.000.000 + 900.000 + ... + 100.000) = 2% × 5.500.000 = 110.000
        assertThat(s.totalInterest()).isEqualByComparingTo($(110_000));
        assertThat(s.totalPayable()).isEqualByComparingTo($(1_110_000));
    }

    @Test
    @DisplayName("El interés NO se calcula sobre el valor original en cuotas posteriores")
    void interestIsNotOnOriginalPrincipal() {
        Schedule s = calculator.buildSchedule($(1_000_000), new BigDecimal("2"), 10, LocalDate.of(2026, 11, 1));
        assertThat(s.lines().stream().map(ScheduleLine::interest).distinct().count()).isEqualTo(10);
    }

    @Test
    @DisplayName("Vencimientos mensuales consecutivos desde la primera cuota")
    void monthlyDueDates() {
        Schedule s = calculator.buildSchedule($(300_000), BigDecimal.ONE, 3, LocalDate.of(2026, 1, 31));
        assertThat(s.lines()).extracting(ScheduleLine::dueDate)
                .containsExactly(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31));
    }

    @Test
    @DisplayName("Capital no divisible: la última cuota absorbe el residuo y el saldo cierra en cero")
    void remainderGoesToLastInstallment() {
        Schedule s = calculator.buildSchedule($(1_000_000), new BigDecimal("1.5"), 3, LocalDate.of(2026, 11, 1));
        assertThat(s.lines().get(0).principal()).isEqualByComparingTo($(333_333));
        assertThat(s.lines().get(1).principal()).isEqualByComparingTo($(333_333));
        assertThat(s.lines().get(2).principal()).isEqualByComparingTo($(333_334));
        assertThat(s.lines().get(2).closingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(s.totalPrincipal()).isEqualByComparingTo($(1_000_000));
    }

    @Test
    @DisplayName("Tasa cero: solo capital")
    void zeroRate() {
        Schedule s = calculator.buildSchedule($(500_000), BigDecimal.ZERO, 5, LocalDate.of(2026, 11, 1));
        assertThat(s.totalInterest()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(s.lines().get(0).total()).isEqualByComparingTo($(100_000));
    }

    @Test
    @DisplayName("Abono: primero interés y luego capital; no puede exceder lo pendiente")
    void allocation() {
        LoanAmortizationCalculator.Allocation full = calculator.allocate($(120_000), $(20_000), $(100_000));
        assertThat(full.interest()).isEqualByComparingTo($(20_000));
        assertThat(full.principal()).isEqualByComparingTo($(100_000));

        LoanAmortizationCalculator.Allocation partial = calculator.allocate($(50_000), $(20_000), $(100_000));
        assertThat(partial.interest()).isEqualByComparingTo($(20_000));
        assertThat(partial.principal()).isEqualByComparingTo($(30_000));

        LoanAmortizationCalculator.Allocation small = calculator.allocate($(5_000), $(20_000), $(100_000));
        assertThat(small.interest()).isEqualByComparingTo($(5_000));
        assertThat(small.principal()).isEqualByComparingTo(BigDecimal.ZERO);

        assertThatThrownBy(() -> calculator.allocate($(130_000), $(20_000), $(100_000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("excede");
    }

    @Test
    @DisplayName("Validaciones de entrada")
    void validation() {
        assertThatThrownBy(() -> calculator.buildSchedule(BigDecimal.ZERO, BigDecimal.ONE, 3, LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.buildSchedule($(100), BigDecimal.ONE, 0, LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.buildSchedule($(100), new BigDecimal("-1"), 3, LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
