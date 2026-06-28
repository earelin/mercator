package net.earelin.mercator.domain.parse;

import java.util.List;
import java.util.Objects;

/**
 * One company block of a Sección A document: a company header line and the raw act paragraph(s)
 * published under it, both kept verbatim. Interpreting the header into name/legal-form fields,
 * normalising the prose and splitting it into acts are later parser stages.
 *
 * @param header     the raw {@code <p class="articulo">} line (numeric prefix, name and legal-form
 *                   token all intact)
 * @param paragraphs the raw {@code <p class="parrafo">} text(s) for this company, in document order;
 *                   empty when the header carries no act paragraph
 */
public record CompanyBlock(String header, List<String> paragraphs) {

    public CompanyBlock {
        Objects.requireNonNull(header, "header");
        if (header.isBlank()) {
            throw new IllegalArgumentException("header must not be blank");
        }
        paragraphs = paragraphs == null ? List.of() : List.copyOf(paragraphs);
    }
}
