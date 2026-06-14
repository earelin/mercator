package net.earelin.mercator.shared.domain.source;

/**
 * Which raw representation a fetched BORME document came from, in the fallback order
 * XML &rarr; TXT &rarr; PDF (ADR-0002). The tag is stored with each cache entry so a re-run knows
 * whether the cached body carries the {@code articulo}/{@code parrafo} markup the splitter
 * expects (XML) or a degraded fallback (TXT/PDF).
 */
public enum Representation {
    XML,
    TXT,
    PDF
}
