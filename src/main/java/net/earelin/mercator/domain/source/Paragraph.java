package net.earelin.mercator.domain.source;

import java.util.Objects;

/**
 * A single {@code <p>} from the XML {@code <texto>} body, tagged with its segmentation class.
 * Fetch only carries the raw segmentation; interpreting the prose into act fields is the parser
 * feature's job.
 */
public record Paragraph(ParagraphClass styleClass, String text) {

    public Paragraph {
        Objects.requireNonNull(styleClass, "styleClass");
        Objects.requireNonNull(text, "text");
    }
}
