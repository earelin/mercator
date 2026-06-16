package net.earelin.mercator.domain.source;

import java.util.Optional;

/**
 * Driven port for the read-through raw-response cache, keyed by the opaque {@code borme_id}. The
 * disk adapter is the only implementation today; an object-storage backend is a future adapter
 * behind this same port. Re-processing a cached document must require zero network requests
 * (document-fetch acceptance criterion).
 */
public interface DocumentCache {

    /** The cached raw document for {@code bormeId}, or empty on a miss. */
    Optional<CachedDocument> get(String bormeId);

    /** Store (or overwrite) the raw document for {@code bormeId}. */
    void put(String bormeId, CachedDocument document);
}
