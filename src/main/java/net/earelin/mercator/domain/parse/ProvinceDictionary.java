package net.earelin.mercator.domain.parse;

import static java.util.Map.entry;

import io.micronaut.core.annotation.Nullable;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves a province name from a document's province block to its two-digit {@code province.code}
 * (the canonical codes seeded in {@code province}). Lookup is accent- and case-insensitive, and
 * accepts the regional-language and historical spellings (Girona/Gerona, A Coruña/La Coruña,
 * Bizkaia/Vizcaya, Araba/Álava, …).
 *
 * <p>Ported from bormeparser (GPLv3, &copy; Pablo Castellano) &mdash; {@code provincia.py};
 * see ADR-0012.
 */
public final class ProvinceDictionary {

    private static final Map<String, String> CODE_BY_NAME = Map.ofEntries(
        entry("A CORUNA", "15"),
        entry("ALAVA", "01"),
        entry("ALBACETE", "02"),
        entry("ALICANTE", "03"),
        entry("ALMERIA", "04"),
        entry("ARABA", "01"),
        entry("ARABA ALAVA", "01"),
        entry("ASTURIAS", "33"),
        entry("AVILA", "05"),
        entry("BADAJOZ", "06"),
        entry("BARCELONA", "08"),
        entry("BIZKAIA", "48"),
        entry("BURGOS", "09"),
        entry("CACERES", "10"),
        entry("CADIZ", "11"),
        entry("CANTABRIA", "39"),
        entry("CASTELLON", "12"),
        entry("CEUTA", "51"),
        entry("CIUDAD REAL", "13"),
        entry("CORDOBA", "14"),
        entry("CUENCA", "16"),
        entry("GERONA", "17"),
        entry("GIPUZKOA", "20"),
        entry("GIRONA", "17"),
        entry("GRANADA", "18"),
        entry("GUADALAJARA", "19"),
        entry("GUIPUZCOA", "20"),
        entry("HUELVA", "21"),
        entry("HUESCA", "22"),
        entry("ILLES BALEARS", "07"),
        entry("ISLAS BALEARES", "07"),
        entry("JAEN", "23"),
        entry("LA CORUNA", "15"),
        entry("LA RIOJA", "26"),
        entry("LAS PALMAS", "35"),
        entry("LEON", "24"),
        entry("LERIDA", "25"),
        entry("LLEIDA", "25"),
        entry("LUGO", "27"),
        entry("MADRID", "28"),
        entry("MALAGA", "29"),
        entry("MELILLA", "52"),
        entry("MURCIA", "30"),
        entry("NAVARRA", "31"),
        entry("ORENSE", "32"),
        entry("OURENSE", "32"),
        entry("PALENCIA", "34"),
        entry("PONTEVEDRA", "36"),
        entry("SALAMANCA", "37"),
        entry("SANTA CRUZ DE TENERIFE", "38"),
        entry("SEGOVIA", "40"),
        entry("SEVILLA", "41"),
        entry("SORIA", "42"),
        entry("TARRAGONA", "43"),
        entry("TERUEL", "44"),
        entry("TOLEDO", "45"),
        entry("VALENCIA", "46"),
        entry("VALLADOLID", "47"),
        entry("VIZCAYA", "48"),
        entry("ZAMORA", "49"),
        entry("ZARAGOZA", "50"));

    private static final Set<String> CODES = Set.copyOf(CODE_BY_NAME.values());

    private ProvinceDictionary() {
    }

    /** The distinct province codes this dictionary resolves to (the {@code province} seed). */
    public static Set<String> codes() {
        return CODES;
    }

    /** The two-digit province code for {@code name}, or empty if it is not recognised. */
    public static Optional<String> codeForName(@Nullable String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(CODE_BY_NAME.get(normalise(name)));
    }

    private static String normalise(String value) {
        String stripped = Normalizer.normalize(value, Normalizer.Form.NFKD)
            .replaceAll("\\p{M}+", "");
        return stripped.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").strip();
    }
}
