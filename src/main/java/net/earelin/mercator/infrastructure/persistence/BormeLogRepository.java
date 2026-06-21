package net.earelin.mercator.infrastructure.persistence;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Objects;
import net.earelin.mercator.domain.source.BormeLog;
import net.earelin.mercator.domain.source.BormeLogEntry;
import net.earelin.mercator.domain.source.BormeLogStatus;
import net.earelin.mercator.domain.source.ErrorKind;
import net.earelin.mercator.domain.source.SourcePath;

/**
 * Micronaut Data JDBC repository for {@code borme_log} that <strong>is</strong> the {@link BormeLog}
 * port — the domain {@link BormeLogEntry} is itself the mapped entity, so no separate persistence
 * row or translating adapter is needed (ADR-0014).
 *
 * <p>{@link #record(BormeLogEntry)} translates the entry's enums to their stored {@code TEXT} forms
 * and delegates to the idempotent {@link #upsert} . The {@code ON CONFLICT (borme_id) DO UPDATE}
 * lets a later lifecycle row (PARSED/MERGED) advance the same document and refreshes
 * {@code processed_at} via the database clock, while re-recording the same outcome is a harmless
 * no-op rewrite — re-processing a document is a no-op (ADR-0006). The conflict semantics are not
 * expressible as a derived method, so the SQL is given explicitly.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public interface BormeLogRepository extends GenericRepository<BormeLogEntry, String>, BormeLog {

    @Override
    default void record(BormeLogEntry entry) {
        Objects.requireNonNull(entry, "entry");
        // The parameters carry the domain enums; Micronaut Data maps :status/:errorKind by name()
        // and :sourcePath via SourcePathAttributeConverter (the BormeLogEntry property mapping).
        upsert(
                entry.bormeId(),
                entry.pubDate(),
                entry.status(),
                entry.errorKind(),
                entry.sourcePath(),
                entry.errorDetail());
    }

    @Query("""
            INSERT INTO borme_log
                (borme_id, pub_date, status, error_kind, source_path, error_detail, processed_at)
            VALUES (:bormeId, :pubDate, :status, :errorKind, :sourcePath, :errorDetail, NOW())
            ON CONFLICT (borme_id) DO UPDATE SET
                pub_date     = EXCLUDED.pub_date,
                status       = EXCLUDED.status,
                error_kind   = EXCLUDED.error_kind,
                source_path  = EXCLUDED.source_path,
                error_detail = EXCLUDED.error_detail,
                processed_at = NOW()
            """)
    @Transactional
    void upsert(
            String bormeId,
            LocalDate pubDate,
            BormeLogStatus status,
            @Nullable ErrorKind errorKind,
            SourcePath sourcePath,
            @Nullable String errorDetail);
}
