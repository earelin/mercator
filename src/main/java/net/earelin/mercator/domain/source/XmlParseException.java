package net.earelin.mercator.domain.source;

/**
 * Thrown by {@link XmlDocumentParser} when a body is not well-formed per-document XML. The fetch
 * chain catches this and treats it as a permanent failure of the XML representation, falling back
 * to txt.php.
 */
public class XmlParseException extends RuntimeException {

    public XmlParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
