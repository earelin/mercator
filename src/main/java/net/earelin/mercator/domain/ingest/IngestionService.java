package net.earelin.mercator.domain.ingest;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Inbound (driving) port for the massive historical import. Both entry points drive the
 * <strong>bulk staging + SQL merge</strong> write path (ADR-0006) — distinct from the daily
 * incremental's row-by-row upsert — and return immediately with an {@link JobStatus#ACCEPTED}
 * job handle; the work proceeds asynchronously and its progress is observed via the {@link JobStore}.
 */
public interface IngestionService {

    /** Imports every Secci&oacute;n A document published on the given day. */
    ImportJob importByDate(LocalDate date);

    /** Imports every Secci&oacute;n A document published in the given calendar month. */
    ImportJob importByMonth(YearMonth month);
}
