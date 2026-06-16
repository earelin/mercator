package net.earelin.mercator.domain.ingest;

/**
 * Lifecycle of a historical-import job. The endpoint accepts a request ({@code ACCEPTED}), the
 * runner moves it through {@code RUNNING} to a terminal {@code COMPLETED} or {@code FAILED}.
 */
public enum JobStatus {
    ACCEPTED,
    RUNNING,
    COMPLETED,
    FAILED
}
