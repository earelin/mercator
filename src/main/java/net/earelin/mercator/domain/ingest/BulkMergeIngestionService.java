package net.earelin.mercator.domain.ingest;

import jakarta.inject.Singleton;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Application service that implements the historical import over the bulk staging + SQL merge write
 * path (ADR-0006). Carries only the vendor-neutral {@code jakarta.inject} (JSR-330) annotation so
 * the DI container auto-discovers it and injects a {@link JobStore} adapter through the single
 * constructor; the core stays free of Micronaut-specific types (ADR-0014).
 *
 * <p><strong>Scaffold.</strong> The bulk-merge engine is not built yet — see the backlog in
 * {@code docs/features/historical-backfill.md}. Each request is accepted, recorded as a job, then
 * marked {@link JobStatus#FAILED} so callers get an honest status rather than a silent no-op. When
 * the engine lands it will, asynchronously: enumerate the day/month summary &rarr; fetch &rarr;
 * parse &rarr; bulk-{@code COPY} into {@code staging_act} &rarr; run the SQL merge
 * ({@code resolve_*} + upsert, migrations {@code V1.7.0}/{@code V1.9.0}) &rarr; run the errata
 * reconciliation pass, transitioning the job through {@code RUNNING} to {@code COMPLETED}.
 */
@Singleton
public final class BulkMergeIngestionService implements IngestionService {

    private static final String NOT_IMPLEMENTED =
            "bulk-merge ingestion engine not yet implemented";

    private final JobStore jobStore;

    public BulkMergeIngestionService(JobStore jobStore) {
        this.jobStore = Objects.requireNonNull(jobStore, "jobStore");
    }

    @Override
    public ImportJob importByDate(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return submit(ImportKind.BY_DATE, date.toString());
    }

    @Override
    public ImportJob importByMonth(YearMonth month) {
        Objects.requireNonNull(month, "month");
        return submit(ImportKind.BY_MONTH, month.toString());
    }

    private ImportJob submit(ImportKind kind, String target) {
        ImportJob accepted = jobStore.create(kind, target);
        // TODO(historical-backfill): drive the bulk staging + SQL merge engine asynchronously here.
        ImportJob failed = accepted.withStatus(JobStatus.FAILED, NOT_IMPLEMENTED);
        jobStore.save(failed);
        return failed;
    }
}
