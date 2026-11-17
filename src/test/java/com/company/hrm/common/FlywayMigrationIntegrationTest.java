package com.company.hrm.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.hrm.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;

class FlywayMigrationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void should_apply_flyway_migrations_when_database_is_empty() {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        assertThat(flyway.info().applied()).hasSize(2);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
    }
}
