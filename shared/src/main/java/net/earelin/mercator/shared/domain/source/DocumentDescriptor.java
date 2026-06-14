package net.earelin.mercator.shared.domain.source;

import java.net.URI;
import java.util.Objects;

/**
 * A single Secci&oacute;n A document to fetch, exactly as
 * {@code summary-enumeration} emits it from the daily summary. The {@code bormeId} is the
 * <strong>opaque</strong> {@code identificador} (e.g. {@code BORME-A-2026-9-01}) used verbatim as
 * the cache key &mdash; never parsed, zero-padded or reconstructed (ADR-0008, summary-enumeration).
 * The three per-representation URLs are supplied by the summary, so no URL construction is needed.
 *
 * @param bormeId  opaque document identifier and cache key (required)
 * @param province the province from the summary item title (may be {@code null})
 * @param urlXml   the {@code xml.php} URL &mdash; the primary source (required)
 * @param urlHtml  the {@code txt.php} URL &mdash; first fallback (may be {@code null} if absent)
 * @param urlPdf   the PDF URL &mdash; authentic last resort (may be {@code null} if absent)
 */
public record DocumentDescriptor(
        String bormeId,
        String province,
        URI urlXml,
        URI urlHtml,
        URI urlPdf) {

    public DocumentDescriptor {
        Objects.requireNonNull(bormeId, "bormeId");
        if (bormeId.isBlank()) {
            throw new IllegalArgumentException("bormeId must not be blank");
        }
        Objects.requireNonNull(urlXml, "urlXml");
    }
}
