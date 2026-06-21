package net.earelin.mercator.application.rest.admin.imports;

import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.earelin.mercator.domain.ingest.ImportJob;
import net.earelin.mercator.domain.ingest.ImportKind;
import net.earelin.mercator.domain.ingest.JobStatus;
import net.earelin.mercator.domain.ingest.JobStore;

/**
 * In-memory {@link JobStore} adapter. Sufficient for a single-instance server (ADR-0011); job
 * status is operational and lost on restart, which is acceptable because an interrupted import is
 * resumable via {@code borme_log} (ADR-0006).
 */
@Singleton
public final class InMemoryJobStore implements JobStore {

    private final ConcurrentMap<String, ImportJob> jobs = new ConcurrentHashMap<>();

    @Override
    public ImportJob create(ImportKind kind, String target) {
        ImportJob job = new ImportJob(
                "imp-" + UUID.randomUUID(), kind, target, JobStatus.ACCEPTED, null, Instant.now());
        jobs.put(job.id(), job);
        return job;
    }

    @Override
    public Optional<ImportJob> find(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    public void save(ImportJob job) {
        jobs.put(job.id(), job);
    }
}
