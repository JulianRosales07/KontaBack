package com.app.accounting;

import com.app.accounting.voucher.repository.AccountBalanceProjection;
import com.app.accounting.voucher.repository.JournalEntryRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FlywayAndRepositoryIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Test
    @DisplayName("Debe ejecutar migraciones Flyway V1 y V2 sin errores SQL")
    void shouldRunFlywayMigrationsSuccessfully() {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        flyway.clean();
        int migrationsExecuted = flyway.migrate().migrationsExecuted;
        assertThat(migrationsExecuted).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Debe ejecutar las consultas JPQL de JournalEntryRepository sin errores")
    void shouldExecuteJournalEntryRepositoryQueries() {
        List<AccountBalanceProjection> before = journalEntryRepository.findAccountBalancesBeforeDate(LocalDate.now());
        assertThat(before).isNotNull();

        List<AccountBalanceProjection> between = journalEntryRepository.findAccountBalancesBetweenDates(LocalDate.now().minusMonths(1), LocalDate.now());
        assertThat(between).isNotNull();

        var partyBefore = journalEntryRepository.findPartyAccountBalancesBeforeDate(LocalDate.now());
        assertThat(partyBefore).isNotNull();

        var partyBetween = journalEntryRepository.findPartyAccountBalancesBetweenDates(LocalDate.now().minusMonths(1), LocalDate.now());
        assertThat(partyBetween).isNotNull();

        var partyUpTo = journalEntryRepository.findPartyAccountBalancesUpToDate(LocalDate.now());
        assertThat(partyUpTo).isNotNull();
    }
}

