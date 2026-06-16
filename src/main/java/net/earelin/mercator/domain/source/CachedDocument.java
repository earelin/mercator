package net.earelin.mercator.domain.source;

import java.nio.charset.Charset;
import java.util.Objects;

/**
 * A raw cache entry: the exact bytes downloaded from the BOE (the XML, the txt.php HTML, or the
 * PDF binary), the {@link Representation} tag identifying which, and the charset to decode the
 * (non-PDF) body with. Keyed by {@code borme_id}; the representation tag is part of the entry, not
 * the key (document-fetch feature).
 *
 * <p>The byte array is held by reference for efficiency; treat instances as effectively immutable
 * and do not mutate the array after construction.
 */
public record CachedDocument(Representation representation, byte[] body, Charset charset) {

    public CachedDocument {
        Objects.requireNonNull(representation, "representation");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(charset, "charset");
    }
}
