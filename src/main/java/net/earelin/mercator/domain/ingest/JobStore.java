package net.earelin.mercator.domain.ingest;

import java.util.Optional;

/**
 * Driven port for tracking historical-import jobs and their status. The scaffold ships an
 * in-memory adapter; job status is operational/ephemeral and a single-instance server (ADR-0011)
 * legitimately abandons an in-flight import on restart (it is resumable via {@code borme_log}).
 */
public interface JobStore {

    /** Creates and stores a new {@link JobStatus#ACCEPTED} job for the given target. */
    ImportJob create(ImportKind kind, String target);

    /** Finds a job by id, if present. */
    Optional<ImportJob> find(String id);

    /** Persists an updated job snapshot (overwriting the prior one with the same id). */
    void save(ImportJob job);
}
