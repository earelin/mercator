package net.earelin.mercator.domain.parse;

import io.micronaut.core.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The catalogue of company legal-form suffixes (SL, S.L., SA, SLU, SAU, SCP, AIE…) and the minimal
 * company-vs-person heuristic act parsing needs: telling a company acting as administrator/auditor
 * (e.g. {@code DELOITTE SL}) from a natural person. Full company normalisation (stripping the suffix
 * to build {@code norm_name}) lives in entity-extraction-normalisation.
 *
 * <p>Ported from bormeparser (GPLv3, &copy; Pablo Castellano) &mdash; {@code sociedad.py};
 * see ADR-0012.
 */
public final class LegalSuffixes {

    private static final List<String> FORMS = List.of(
        "AIE", "AEIE", "COOP", "FP", "SA", "SAD", "SAL", "SAP", "SAS", "SAU", "SC", "S.COM.",
        "S.COM.P.A.", "SCP", "SICAV", "SL", "SLL", "SLLP", "SLNE", "SLP", "SLU", "SME", "SRL",
        "SRLL", "SRLP", "BVBA", "BV", "NV", "LTD");

    private static final Set<String> FORM_SET = Set.copyOf(FORMS);

    private static final List<Suffix> BY_LENGTH_DESC = FORMS.stream()
        .map(form -> new Suffix(form, canon(form)))
        .sorted(Comparator.comparingInt((Suffix suffix) -> suffix.canon().length()).reversed())
        .toList();

    private LegalSuffixes() {
    }

    /** The known legal-form suffixes. */
    public static Set<String> forms() {
        return FORM_SET;
    }

    /** The legal-form suffix terminating {@code rawName}, or empty if it carries none. */
    public static Optional<String> detect(@Nullable String rawName) {
        if (rawName == null) {
            return Optional.empty();
        }
        String canonName = canon(rawName);
        for (Suffix suffix : BY_LENGTH_DESC) {
            if (canonName.endsWith(" " + suffix.canon())) {
                return Optional.of(suffix.form());
            }
        }
        return Optional.empty();
    }

    /** Whether {@code rawName} looks like a company rather than a natural person. */
    public static boolean looksLikeCompany(@Nullable String rawName) {
        if (rawName == null) {
            return false;
        }
        return detect(rawName).isPresent() || canon(rawName).contains("SOCIEDAD");
    }

    private static String canon(String value) {
        return value.toUpperCase(Locale.ROOT).replace(".", "").replaceAll("\\s+", " ").strip();
    }

    private record Suffix(String form, String canon) {
    }
}
