package net.earelin.mercator.infrastructure.persistence;

import static org.assertj.db.api.Assertions.assertThat;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Map;
import javax.sql.DataSource;
import net.earelin.mercator.domain.source.BormeLog;
import net.earelin.mercator.domain.source.BormeLogEntry;
import net.earelin.mercator.domain.source.BormeLogStatus;
import net.earelin.mercator.domain.source.ErrorKind;
import net.earelin.mercator.domain.source.SourcePath;
import org.assertj.db.type.AssertDbConnection;
import org.assertj.db.type.AssertDbConnectionFactory;
import org.assertj.db.type.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Exercises {@link BormeLogRepository} — which is both the Micronaut Data JDBC repository and the
 * {@link BormeLog} port implementation — against a real Postgres 18. A Testcontainers container is
 * started before the Micronaut context, its coordinates fed to {@code datasources.default} via
 * {@link TestPropertyProvider}; Micronaut's Flyway applies the canonical schema and Micronaut Data
 * wires the repository — so the {@code ON CONFLICT} upsert and the {@code borme_log} CHECK
 * constraints are tested for real (no hand-maintained DDL copy). Injection is through the domain
 * port to exercise the contract callers use.
 */
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BormeLogRepositoryTest implements TestPropertyProvider {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18.4");

    private static final String BORME_ID = "BORME-A-2024-1-36";
    private static final LocalDate PUB_DATE = LocalDate.of(2024, 1, 2);

    @Override
    public Map<String, String> getProperties() {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        return Map.of(
                "datasources.default.url", POSTGRES.getJdbcUrl(),
                "datasources.default.username", POSTGRES.getUsername(),
                "datasources.default.password", POSTGRES.getPassword(),
                "datasources.default.driver-class-name", "org.postgresql.Driver",
                "flyway.datasources.default.enabled", "true");
    }

    @Inject
    BormeLog bormeLog;

    private AssertDbConnection assertDb;

    /**
     * A plain datasource on the same container for assertions. The Micronaut-managed {@code
     * DataSource} is a transaction-aware proxy that only yields a connection inside a managed scope,
     * so reads/truncates here go through an independent connection, decoupled from the SUT.
     */
    private DataSource assertionDataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        return ds;
    }

    @BeforeEach
    void setUp() {
        assertDb = AssertDbConnectionFactory.of(assertionDataSource()).create();
    }

    @AfterEach
    void truncate() throws SQLException {
        try (Connection connection = assertionDataSource().getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE borme_log");
        }
    }

    @Test
    void records_a_fetched_entry() {
        bormeLog.record(new BormeLogEntry(
                BORME_ID, PUB_DATE, BormeLogStatus.FETCHED, null, SourcePath.DAILY_INCREMENTAL, null));

        Table table = assertDb.table("borme_log").build();
        assertThat(table).hasNumberOfRows(1);
        assertThat(table).row(0)
                .value("borme_id").isEqualTo(BORME_ID)
                .value("status").isEqualTo("FETCHED")
                .value("source_path").isEqualTo("daily_incremental")
                .value("error_kind").isNull()
                .value("error_detail").isNull();
    }

    @Test
    void records_an_error_entry_with_its_classification() {
        bormeLog.record(new BormeLogEntry(
                BORME_ID, PUB_DATE, BormeLogStatus.ERROR, ErrorKind.PERMANENT, SourcePath.BACKFILL,
                "404 in every representation"));

        Table table = assertDb.table("borme_log").build();
        assertThat(table).hasNumberOfRows(1);
        assertThat(table).row(0)
                .value("status").isEqualTo("ERROR")
                .value("source_path").isEqualTo("backfill")
                .value("error_kind").isEqualTo("PERMANENT")
                .value("error_detail").isEqualTo("404 in every representation");
    }

    @Test
    void re_recording_the_same_document_upserts_in_place() {
        bormeLog.record(new BormeLogEntry(
                BORME_ID, PUB_DATE, BormeLogStatus.FETCHED, null, SourcePath.DAILY_INCREMENTAL, null));
        // A later lifecycle row for the same borme_id advances the document rather than duplicating.
        bormeLog.record(new BormeLogEntry(
                BORME_ID, PUB_DATE, BormeLogStatus.PARSED, null, SourcePath.DAILY_INCREMENTAL, null));

        Table table = assertDb.table("borme_log").build();
        assertThat(table).hasNumberOfRows(1);
        assertThat(table).row(0)
                .value("borme_id").isEqualTo(BORME_ID)
                .value("status").isEqualTo("PARSED");
    }
}
