package net.earelin.mercator.shared.domain.source;

/**
 * The {@code class} of a {@code <p>} in the per-document XML {@code <texto>} body, which already
 * segments the document into company headers and act blocks (ADR-0002):
 * <ul>
 *   <li>{@link #ARTICULO} &mdash; {@code <p class="articulo">}, a company header line.</li>
 *   <li>{@link #PARRAFO} &mdash; {@code <p class="parrafo">}, an act block (free Spanish prose).</li>
 * </ul>
 */
public enum ParagraphClass {
    ARTICULO,
    PARRAFO
}
