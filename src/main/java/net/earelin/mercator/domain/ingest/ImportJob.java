package net.earelin.mercator.domain.ingest;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable snapshot of one historical-import job. Framework-free domain value: the job model
 * and its status semantics are testable without Micronaut. Status transitions produce a new
 * instance via {@link #withStatus}.
 *
 * @param id        opaque job identifier (required)
 * @param kind      whether the import targets a day or a month (required)
 * @param target    the ISO target the import covers ({@code YYYY-MM-DD} or {@code YYYY-MM}) (required)
 * @param status    current lifecycle status (required)
 * @param detail    human-readable detail (e.g. a failure reason); may be {@code null}
 * @param createdAt when the job was accepted (required)
 */
public record ImportJob(
        String id,
        ImportKind kind,
        String target,
        JobStatus status,
        String detail,
        Instant createdAt) {

    public ImportJob {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Returns a copy of this job in {@code newStatus} with the given (nullable) detail. */
    public ImportJob withStatus(JobStatus newStatus, String newDetail) {
        return new ImportJob(id, kind, target, newStatus, newDetail, createdAt);
    }
}
