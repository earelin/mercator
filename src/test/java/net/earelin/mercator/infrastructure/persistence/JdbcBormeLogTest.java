package net.earelin.mercator.infrastructure.persistence;

import static org.assertj.db.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import javax.sql.DataSource;
import net.earelin.mercator.domain.source.BormeLogEntry;
import net.earelin.mercator.domain.source.BormeLogStatus;
import net.earelin.mercator.domain.source.ErrorKind;
import net.earelin.mercator.domain.source.SourcePath;
import org.assertj.db.type.AssertDbConnection;
import org.assertj.db.type.AssertDbConnectionFactory;
import org.assertj.db.type.DateValue;
import org.assertj.db.type.Table;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises {@link JdbcBormeLog} against a real Postgres 18 with the canonical schema applied by
 * Flyway from this module's {@code db/migration}, so the upsert and the {@code borme_log} CHECK
 * constraints are tested for real (no hand-maintained DDL copy).
 */
@Testcontainers
class JdbcBormeLogTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4");

    private static DataSource dataSource;
    private static AssertDbConnection assertDb;

    private JdbcBormeLog bormeLog;

    @BeforeAll
    static void migrate_schema() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        dataSource = ds;
        Flyway.configure().dataSource(ds).load().migrate();
        assertDb = AssertDbConnectionFactory.of(ds).create();
    }

    @BeforeEach
    void setUp() {
        bormeLog = new JdbcBormeLog(dataSource);
    }

    @AfterEach
    void truncate() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE borme_log");
        }
    }

    @Test
    void records_a_fetched_row() {
        bormeLog.record(new BormeLogEntry(
                "BORME-A-2024-1-01", LocalDate.of(2024, 1, 15),
                BormeLogStatus.FETCHED, null, SourcePath.DAILY_INCREMENTAL, null));

        Table table = assertDb.table("borme_log").build();
        assertThat(table).hasNumberOfRows(1)
                .row()
                    .value("borme_id").isEqualTo("BORME-A-2024-1-01")
                    .value("pub_date").isEqualTo(DateValue.of(2024, 1, 15))
                    .value("status").isEqualTo("FETCHED")
                    .value("source_path").isEqualTo("daily_incremental")
                    .value("error_kind").isNull()
                    .value("error_detail").isNull()
                    .value("processed_at").isNotNull();
    }

    @Test
    void records_an_error_row_with_kind_and_detail() {
        bormeLog.record(new BormeLogEntry(
                "BORME-A-2024-1-02", LocalDate.of(2024, 1, 15),
                BormeLogStatus.ERROR, ErrorKind.RETRYABLE, SourcePath.BACKFILL, "503 on every representation"));

        Table table = assertDb.table("borme_log").build();
        assertThat(table).hasNumberOfRows(1)
                .row()
                    .value("status").isEqualTo("ERROR")
                    .value("error_kind").isEqualTo("RETRYABLE")
                    .value("error_detail").isEqualTo("503 on every representation")
                    .value("source_path").isEqualTo("backfill");
    }

    @Test
    void upsert_advances_status_for_the_same_borme_id() {
        String bormeId = "BORME-A-2024-1-03";
        LocalDate pubDate = LocalDate.of(2024, 1, 15);
        bormeLog.record(new BormeLogEntry(
                bormeId, pubDate, BormeLogStatus.FETCHED, null, SourcePath.BACKFILL, null));

        bormeLog.record(new BormeLogEntry(
                bormeId, pubDate, BormeLogStatus.MERGED, null, SourcePath.BACKFILL, null));

        Table table = assertDb.table("borme_log").build();
        assertThat(table).hasNumberOfRows(1)
                .row()
                    .value("borme_id").isEqualTo(bormeId)
                    .value("status").isEqualTo("MERGED");
    }

    @Test
    void clears_error_fields_when_a_retry_later_succeeds() {
        String bormeId = "BORME-A-2024-1-04";
        LocalDate pubDate = LocalDate.of(2024, 1, 15);
        bormeLog.record(new BormeLogEntry(
                bormeId, pubDate, BormeLogStatus.ERROR, ErrorKind.RETRYABLE, SourcePath.DAILY_INCREMENTAL, "timeout"));

        bormeLog.record(new BormeLogEntry(
                bormeId, pubDate, BormeLogStatus.FETCHED, null, SourcePath.DAILY_INCREMENTAL, null));

        Table table = assertDb.table("borme_log").build();
        assertThat(table).hasNumberOfRows(1)
                .row()
                    .value("status").isEqualTo("FETCHED")
                    .value("error_kind").isNull()
                    .value("error_detail").isNull();
    }

    @Test
    void re_recording_the_same_outcome_is_idempotent() {
        BormeLogEntry entry = new BormeLogEntry(
                "BORME-A-2024-1-05", LocalDate.of(2024, 1, 15),
                BormeLogStatus.FETCHED, null, SourcePath.DAILY_INCREMENTAL, null);

        bormeLog.record(entry);
        bormeLog.record(entry);

        assertThat(assertDb.table("borme_log").build()).hasNumberOfRows(1);
    }
}
