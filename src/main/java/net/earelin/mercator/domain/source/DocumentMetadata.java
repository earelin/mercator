package net.earelin.mercator.domain.source;

import io.micronaut.core.annotation.Nullable;
import java.net.URI;
import java.time.LocalDate;

/**
 * The {@code <metadatos>} block of a per-document XML response. Only available for the
 * {@link Representation#XML} representation; {@code null} for the TXT/PDF fallbacks, which carry
 * no structured metadata. All fields are best-effort &mdash; a field absent from the source is
 * simply {@code null}.
 *
 * @param identificador the document id (same opaque string as {@code borme_id})
 * @param titulo        the document title (the province, for Secci&oacute;n A)
 * @param seccion       the BORME section code (e.g. {@code "A"})
 * @param pubDate       publication date, or {@code null} if absent/unparseable
 * @param pages         page count, or {@code null} if not derivable
 * @param urlPdf        the authentic PDF URL advertised by the metadata, or {@code null}
 */
public record DocumentMetadata(
        @Nullable String identificador,
        @Nullable String titulo,
        @Nullable String seccion,
        @Nullable LocalDate pubDate,
        @Nullable Integer pages,
        @Nullable URI urlPdf) {
}
