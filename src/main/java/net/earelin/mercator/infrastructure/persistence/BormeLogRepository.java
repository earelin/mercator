package net.earelin.mercator.infrastructure.persistence;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.annotation.Transactional;
import java.time.LocalDate;

/**
 * Micronaut Data JDBC repository for {@code borme_log}. Extends {@link GenericRepository} (no
 * inherited CRUD surface) and exposes a single deliberate operation: the idempotent upsert keyed by
 * {@code borme_id}.
 *
 * <p>The {@code ON CONFLICT (borme_id) DO UPDATE} lets a later lifecycle row (PARSED/MERGED) advance
 * the same document and refreshes {@code processed_at} via the database clock, while re-recording
 * the same outcome is a harmless no-op rewrite — re-processing a document is a no-op (ADR-0006). The
 * conflict semantics are not expressible as a derived method, so the SQL is given explicitly.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public interface BormeLogRepository extends GenericRepository<BormeLogRecord, String> {

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
            String status,
            @Nullable String errorKind,
            String sourcePath,
            @Nullable String errorDetail);
}
