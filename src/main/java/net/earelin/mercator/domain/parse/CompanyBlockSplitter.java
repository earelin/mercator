package net.earelin.mercator.domain.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.earelin.mercator.domain.source.FetchedDocument;
import net.earelin.mercator.domain.source.Paragraph;
import net.earelin.mercator.domain.source.ParagraphClass;

/**
 * Groups a fetched document into {@link CompanyBlock}s — one header line plus the act paragraph(s)
 * that follow it.
 *
 * <p>For the XML representation the BOE has already segmented the body, so a block is one
 * {@code <p class="articulo">} and every {@code <p class="parrafo">} up to the next
 * {@code articulo}. The degraded TXT/PDF fallbacks carry no such markup, so the splitter recovers
 * blocks line by line: a line that looks like a company header (an optional {@code NNNNN - } prefix
 * then a {@link LegalSuffixes#looksLikeCompany(String) company-shaped} name) starts a block and the
 * lines beneath it are its paragraphs.
 *
 * <p>The fallback is best-effort and depends on line breaks surviving extraction: a TXT body whose
 * whitespace has been collapsed onto a single line yields no blocks. Robust pre-parse normalisation
 * is a later act-parsing stage.
 */
public final class CompanyBlockSplitter {

    private static final Pattern LINE = Pattern.compile("\\R");
    private static final Pattern NUMERIC_PREFIX = Pattern.compile("^\\d+\\s*[-–]\\s*");

    /** Split {@code document} into company blocks, in document order. */
    public List<CompanyBlock> split(FetchedDocument document) {
        List<Paragraph> paragraphs = document.paragraphs();
        List<Segment> segments = paragraphs.isEmpty()
                ? textSegments(document.rawBody())
                : paragraphSegments(paragraphs);
        return group(segments);
    }

    private static List<CompanyBlock> group(List<Segment> segments) {
        List<CompanyBlock> blocks = new ArrayList<>();
        String header = null;
        List<String> body = new ArrayList<>();
        for (Segment segment : segments) {
            if (segment.header()) {
                if (header != null) {
                    blocks.add(new CompanyBlock(header, body));
                }
                header = segment.text();
                body = new ArrayList<>();
            } else if (header != null) {
                body.add(segment.text());
            }
        }
        if (header != null) {
            blocks.add(new CompanyBlock(header, body));
        }
        return blocks;
    }

    private static List<Segment> paragraphSegments(List<Paragraph> paragraphs) {
        List<Segment> segments = new ArrayList<>(paragraphs.size());
        for (Paragraph paragraph : paragraphs) {
            segments.add(new Segment(paragraph.styleClass() == ParagraphClass.ARTICULO, paragraph.text()));
        }
        return segments;
    }

    private static List<Segment> textSegments(String rawBody) {
        List<Segment> segments = new ArrayList<>();
        for (String rawLine : LINE.split(rawBody)) {
            String line = rawLine.strip();
            if (!line.isEmpty()) {
                segments.add(new Segment(isCompanyHeader(line), line));
            }
        }
        return segments;
    }

    private static boolean isCompanyHeader(String line) {
        // Colon-bearing lines are act payloads ("Objeto social:", "Adm. Unico: DELOITTE SL.",
        // "Datos registrales: …"); a company header line carries none.
        if (line.indexOf(':') >= 0) {
            return false;
        }
        String name = NUMERIC_PREFIX.matcher(line).replaceFirst("");
        return LegalSuffixes.looksLikeCompany(name);
    }

    private record Segment(boolean header, String text) {
    }
}
