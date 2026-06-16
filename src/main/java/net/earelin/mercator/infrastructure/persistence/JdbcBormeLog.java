package net.earelin.mercator.infrastructure.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;
import javax.sql.DataSource;
import net.earelin.mercator.domain.source.BormeLog;
import net.earelin.mercator.domain.source.BormeLogEntry;
import net.earelin.mercator.domain.source.ErrorKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC-backed {@link BormeLog}: upserts one {@code borme_log} row per {@code borme_id} (migration
 * V1.7.0). Replaces the logging stub so document-fetch outcomes are durably persisted for the
 * idempotency short-circuit and resumable backfill (ADR-0006).
 *
 * <p>Depends only on a JDK-standard {@link DataSource}, keeping the {@code infrastructure} layer
 * framework-agnostic (ADR-0014): the Micronaut layer injects its Hikari datasource, so both the
 * daily-incremental and historical-import write paths record through the same adapter.
 *
 * <p>The {@code ON CONFLICT (borme_id) DO UPDATE} upsert lets a later lifecycle row (PARSED/MERGED)
 * advance the same document and refreshes {@code processed_at}, while re-recording the same outcome
 * is a harmless no-op rewrite (ADR-0006). Unlike the best-effort cache, a genuine SQL failure is
 * surfaced as an {@link UncheckedSqlException} rather than swallowed.
 */
public final class JdbcBormeLog implements BormeLog {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcBormeLog.class);

    private static final String UPSERT_SQL = """
            INSERT INTO borme_log (borme_id, pub_date, status, error_kind, source_path, error_detail, processed_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (borme_id) DO UPDATE SET
                pub_date     = EXCLUDED.pub_date,
                status       = EXCLUDED.status,
                error_kind   = EXCLUDED.error_kind,
                source_path  = EXCLUDED.source_path,
                error_detail = EXCLUDED.error_detail,
                processed_at = NOW()
            """;

    private final DataSource dataSource;

    public JdbcBormeLog(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public void record(BormeLogEntry entry) {
        Objects.requireNonNull(entry, "entry");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setString(1, entry.bormeId());
            statement.setObject(2, entry.pubDate());
            statement.setString(3, entry.status().name());
            ErrorKind errorKind = entry.errorKind();
            if (errorKind == null) {
                statement.setNull(4, Types.VARCHAR);
            } else {
                statement.setString(4, errorKind.name());
            }
            statement.setString(5, entry.sourcePath().dbValue());
            if (entry.errorDetail() == null) {
                statement.setNull(6, Types.VARCHAR);
            } else {
                statement.setString(6, entry.errorDetail());
            }
            statement.executeUpdate();
            LOG.debug("borme_log {} {} {}", entry.status(), entry.bormeId(), entry.sourcePath().dbValue());
        } catch (SQLException e) {
            throw new UncheckedSqlException(
                    "could not record borme_log entry for " + entry.bormeId(), e);
        }
    }
}
