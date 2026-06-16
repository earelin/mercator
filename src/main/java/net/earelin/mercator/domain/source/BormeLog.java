package net.earelin.mercator.domain.source;

/**
 * Driven port for recording per-document processing outcomes in {@code borme_log}. Fetch records a
 * {@link BormeLogStatus#FETCHED} row on success and a {@link BormeLogStatus#ERROR} row on final
 * give-up (ADR-0018). The JDBC-backed adapter is provided by the persistence feature; until then a
 * logging implementation satisfies this port.
 */
public interface BormeLog {

    /** Upsert one {@code borme_log} row (keyed by {@code borme_id}). */
    void record(BormeLogEntry entry);
}
