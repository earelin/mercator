package net.earelin.mercator.domain.source;

import io.micronaut.core.annotation.Nullable;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;

/**
 * The raw input the parser consumes: a successfully fetched (or cache-served) document, tagged
 * with the {@link Representation} it came from.
 *
 * <ul>
 *   <li>{@link #rawBody()} is the decoded text &mdash; the full XML for {@link Representation#XML},
 *       the HTML-stripped text for {@link Representation#TXT}, or the extracted text for
 *       {@link Representation#PDF}.</li>
 *   <li>{@link #metadata()} is present only for the XML representation; {@code null} otherwise.</li>
 *   <li>{@link #paragraphs()} carries the {@code articulo}/{@code parrafo} segmentation only for
 *       the XML representation; it is empty for the degraded TXT/PDF fallbacks (which have no such
 *       markup &mdash; the splitter must fall back to its header/footer heuristic).</li>
 * </ul>
 *
 * @param bormeId        the opaque document id
 * @param representation which representation this body came from
 * @param charset        the charset {@link #rawBody()} was decoded with
 * @param rawBody        the decoded document text
 * @param metadata       parsed {@code <metadatos>} (XML only), else {@code null}
 * @param paragraphs     the XML paragraph segmentation (XML only), else empty
 */
public record FetchedDocument(
        String bormeId,
        Representation representation,
        Charset charset,
        String rawBody,
        @Nullable DocumentMetadata metadata,
        List<Paragraph> paragraphs) {

    public FetchedDocument {
        Objects.requireNonNull(bormeId, "bormeId");
        Objects.requireNonNull(representation, "representation");
        Objects.requireNonNull(charset, "charset");
        Objects.requireNonNull(rawBody, "rawBody");
        paragraphs = paragraphs == null ? List.of() : List.copyOf(paragraphs);
    }
}
