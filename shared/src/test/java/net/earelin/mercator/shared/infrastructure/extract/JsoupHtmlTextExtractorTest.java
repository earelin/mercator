package net.earelin.mercator.shared.infrastructure.extract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JsoupHtmlTextExtractorTest {

    private final JsoupHtmlTextExtractor extractor = new JsoupHtmlTextExtractor();

    @Test
    void stripsChromeAndReturnsDocumentText() {
        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>BORME</title>"
                + "<style>.x{}</style></head><body>"
                + "<nav>Inicio Buscar Ayuda</nav>"
                + "<div id=\"textoxslt\"><p>158029 - COMPAÑÍA ESPAÑOLA DE EJEMPLOS SL. "
                + "Constitución. Datos registrales: T 100, F 50, H M-123.</p></div>"
                + "<footer>Agencia Estatal Boletín Oficial del Estado</footer>"
                + "</body></html>";

        Optional<String> text = extractor.extractText(html.getBytes(StandardCharsets.UTF_8));

        assertTrue(text.isPresent());
        assertTrue(text.get().contains("COMPAÑÍA ESPAÑOLA"), text.get());
        assertFalse(text.get().contains("Inicio Buscar"), "site chrome must be stripped");
        assertFalse(text.get().contains("Agencia Estatal"), "footer must be stripped");
    }

    @Test
    void returnsEmptyForErrorPage() {
        String html = "<html><body><div id=\"textoxslt\"><p>La p&aacute;gina solicitada "
                + "no se ha encontrado en el servidor.</p></div></body></html>";

        Optional<String> text = extractor.extractText(html.getBytes(StandardCharsets.UTF_8));

        assertTrue(text.isEmpty(), "an error page must not be treated as a document");
    }

    @Test
    void returnsEmptyForTrivialContent() {
        String html = "<html><body><div id=\"textoxslt\">x</div></body></html>";
        assertTrue(extractor.extractText(html.getBytes(StandardCharsets.UTF_8)).isEmpty());
    }
}
