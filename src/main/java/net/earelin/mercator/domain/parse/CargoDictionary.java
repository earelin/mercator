package net.earelin.mercator.domain.parse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Maps a BORME cargo label (the text before the {@code :} in a cargo act, e.g. {@code Adm. Unico},
 * {@code ADM.SOLIDAR.}) to its canonical {@link Cargo}. The spelling/spacing variants are matched
 * verbatim, so the table enumerates every form bormeparser knows rather than normalising.
 *
 * <p>Ported from bormeparser (GPLv3, &copy; Pablo Castellano) &mdash; {@code cargo.py}; the variant
 * table lives in the {@code cargo-variants.tsv} resource. See ADR-0012.
 */
public final class CargoDictionary {

    private static final String RESOURCE = "cargo-variants.tsv";
    private static final Map<String, Cargo> BY_VARIANT = load();

    private CargoDictionary() {
    }

    /** The canonical cargo for {@code label}, or empty if the spelling is not catalogued. */
    public static Optional<Cargo> lookup(String label) {
        if (label == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_VARIANT.get(label.strip()));
    }

    private static Map<String, Cargo> load() {
        Map<String, Cargo> byVariant = new HashMap<>();
        try (InputStream in = Objects.requireNonNull(
                CargoDictionary.class.getResourceAsStream(RESOURCE), "missing classpath resource " + RESOURCE);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.charAt(0) == '#') {
                    continue;
                }
                int tab = line.indexOf('\t');
                byVariant.put(line.substring(0, tab), Cargo.valueOf(line.substring(tab + 1).strip()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + RESOURCE, e);
        }
        return Map.copyOf(byVariant);
    }
}
