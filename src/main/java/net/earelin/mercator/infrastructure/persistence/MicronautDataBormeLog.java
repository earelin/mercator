package net.earelin.mercator.infrastructure.persistence;

import jakarta.inject.Singleton;
import java.util.Objects;
import net.earelin.mercator.domain.source.BormeLog;
import net.earelin.mercator.domain.source.BormeLogEntry;
import net.earelin.mercator.domain.source.ErrorKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link BormeLog} adapter backed by {@link BormeLogRepository} (Micronaut Data JDBC). Translates
 * the domain {@link BormeLogEntry} to the {@code TEXT} forms the {@code borme_log} columns store
 * ({@code status.name()}, {@link ErrorKind#name()}, {@link net.earelin.mercator.domain.source.SourcePath#dbValue()})
 * and delegates the idempotent upsert to the repository.
 *
 * <p>Keeping the translation here leaves the domain free of persistence concerns and the
 * persistence row free of domain enums (ADR-0014). Both write paths — the daily incremental and the
 * historical import — record document outcomes through this one adapter (ADR-0006).
 */
@Singleton
public final class MicronautDataBormeLog implements BormeLog {

    private static final Logger LOG = LoggerFactory.getLogger(MicronautDataBormeLog.class);

    private final BormeLogRepository repository;

    public MicronautDataBormeLog(BormeLogRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public void record(BormeLogEntry entry) {
        Objects.requireNonNull(entry, "entry");
        ErrorKind errorKind = entry.errorKind();
        repository.upsert(
                entry.bormeId(),
                entry.pubDate(),
                entry.status().name(),
                errorKind == null ? null : errorKind.name(),
                entry.sourcePath().dbValue(),
                entry.errorDetail());
        LOG.debug("borme_log {} {} {}", entry.status(), entry.bormeId(), entry.sourcePath().dbValue());
    }
}
