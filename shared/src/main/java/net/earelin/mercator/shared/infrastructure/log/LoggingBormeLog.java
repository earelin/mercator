package net.earelin.mercator.shared.infrastructure.log;

import net.earelin.mercator.shared.domain.source.BormeLog;
import net.earelin.mercator.shared.domain.source.BormeLogEntry;
import net.earelin.mercator.shared.domain.source.BormeLogStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link BormeLog} that logs each entry instead of persisting it. Lets document-fetch run
 * before the persistence feature lands; the JDBC-backed adapter that writes the actual
 * {@code borme_log} table will replace this without touching the core.
 */
public final class LoggingBormeLog implements BormeLog {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingBormeLog.class);

    @Override
    public void record(BormeLogEntry entry) {
        if (entry.status() == BormeLogStatus.ERROR) {
            LOG.warn("borme_log {} {} {} [{}] {}",
                    entry.status(), entry.bormeId(), entry.sourcePath().dbValue(),
                    entry.errorKind(), entry.errorDetail());
        } else {
            LOG.info("borme_log {} {} {}",
                    entry.status(), entry.bormeId(), entry.sourcePath().dbValue());
        }
    }
}
