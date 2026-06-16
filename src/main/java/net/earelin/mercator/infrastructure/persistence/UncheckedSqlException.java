package net.earelin.mercator.infrastructure.persistence;

import java.sql.SQLException;
import java.util.Objects;

/**
 * Wraps a checked {@link SQLException} as an unchecked one, mirroring {@link java.io.UncheckedIOException}.
 * Persistence adapters surface genuine database failures (lost connection, constraint violation)
 * to the caller rather than swallowing them &mdash; unlike the best-effort cache.
 */
public class UncheckedSqlException extends RuntimeException {

    public UncheckedSqlException(String message, SQLException cause) {
        super(message, Objects.requireNonNull(cause, "cause"));
    }

    @Override
    public synchronized SQLException getCause() {
        return (SQLException) super.getCause();
    }
}
