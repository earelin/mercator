package net.earelin.mercator.shared.infrastructure.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JsoupHtmlTextExtractorTest {

    private final JsoupHtmlTextExtractor extractor = new JsoupHtmlTextExtractor();

    @Test
    void strips_chrome_and_returns_document_text() {
        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>BORME</title>"
                + "<style>.x{}</style></head><body>"
                + "<nav>Inicio Buscar Ayuda</nav>"
                + "<div id=\"textoxslt\"><p>158029 - COMPAÑÍA ESPAÑOLA DE EJEMPLOS SL. "
                + "Constitución. Datos registrales: T 100, F 50, H M-123.</p></div>"
                + "<footer>Agencia Estatal Boletín Oficial del Estado</footer>"
                + "</body></html>";

        Optional<String> text = extractor.extractText(html.getBytes(StandardCharsets.UTF_8));

        assertThat(text).isPresent();
        assertThat(text.get())
                .contains("COMPAÑÍA ESPAÑOLA")
                .as("site chrome must be stripped").doesNotContain("Inicio Buscar")
                .as("footer must be stripped").doesNotContain("Agencia Estatal");
    }

    @Test
    void returns_empty_for_error_page() {
        String html = "<html><body><div id=\"textoxslt\"><p>La p&aacute;gina solicitada "
                + "no se ha encontrado en el servidor.</p></div></body></html>";

        Optional<String> text = extractor.extractText(html.getBytes(StandardCharsets.UTF_8));

        assertThat(text).as("an error page must not be treated as a document").isEmpty();
    }

    @Test
    void returns_empty_for_trivial_content() {
        String html = "<html><body><div id=\"textoxslt\">x</div></body></html>";
        assertThat(extractor.extractText(html.getBytes(StandardCharsets.UTF_8))).isEmpty();
    }
}
