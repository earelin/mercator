package net.earelin.mercator.shared.infrastructure.extract;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.earelin.mercator.shared.domain.source.HtmlTextExtractor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link HtmlTextExtractor} backed by jsoup. Parses the {@code txt.php} page (auto-detecting its
 * charset from the meta/headers), removes site chrome, and returns the document's plain text.
 * Returns empty when the page is a BOE error page rather than a document, so the fetch chain
 * continues to the PDF.
 */
public final class JsoupHtmlTextExtractor implements HtmlTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(JsoupHtmlTextExtractor.class);

    /** BOE renders the document body into this container; prefer it, then fall back. */
    private static final List<String> CONTENT_SELECTORS = List.of("#textoxslt", "div.documento", "main");
    private static final String CHROME_SELECTOR = "script, style, nav, header, footer, form, aside, noscript";
    private static final List<String> ERROR_MARKERS = List.of(
            "no se ha encontrado", "no existe", "no está disponible", "no se encuentra",
            "página no encontrada", "documento no disponible", "error 404");
    private static final int MIN_CONTENT_LENGTH = 40;

    @Override
    public Optional<String> extractText(byte[] body) {
        Document doc;
        try {
            // null charset → jsoup auto-detects from the meta tag / BOM, falling back to UTF-8.
            doc = Jsoup.parse(new ByteArrayInputStream(body), null, "https://www.boe.es/");
        } catch (IOException e) {
            log.warn("could not parse txt.php HTML: {}", e.toString());
            return Optional.empty();
        }

        doc.select(CHROME_SELECTOR).remove();
        Element content = contentElement(doc);
        String text = content == null ? "" : content.text().strip();

        if (text.length() < MIN_CONTENT_LENGTH || looksLikeError(text)) {
            return Optional.empty();
        }
        return Optional.of(text);
    }

    private static Element contentElement(Document doc) {
        for (String selector : CONTENT_SELECTORS) {
            Element element = doc.selectFirst(selector);
            if (element != null) {
                return element;
            }
        }
        return doc.body();
    }

    private static boolean looksLikeError(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return ERROR_MARKERS.stream().anyMatch(lower::contains);
    }
}
