package net.earelin.mercator.domain.parse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resolves a BORME cargo label (the text before the {@code :} in a cargo act) to its canonical
 * {@link Cargo}. Matching is lowercase and layered: an exact literal, then a regex whose digit runs
 * are generalised (so {@code Vocal 3} matches even though only {@code Vocal 1}/{@code Vocal 2} are
 * catalogued), then a head-word fallback that maps an unmatched label's first word to its base
 * cargo ({@code Vocal Comisión Z} &rarr; {@link Cargo#VOCAL}).
 *
 * <p>Ported from bormeparser (GPLv3, &copy; Pablo Castellano) &mdash; {@code cargo.py}; the rules
 * live in the {@code cargo-variants.tsv} resource (lines tagged {@code L}/{@code R}/{@code H}).
 * See ADR-0012.
 */
public final class CargoDictionary {

    private static final String RESOURCE = "cargo-variants.tsv";
    private static final Rules RULES = load();

    private CargoDictionary() {
    }

    /** The canonical cargo for {@code label}, or empty if neither a rule nor its head word matches. */
    public static Optional<Cargo> lookup(String label) {
        if (label == null) {
            return Optional.empty();
        }
        String normalised = normalise(label);
        if (normalised.isEmpty()) {
            return Optional.empty();
        }
        Cargo literal = RULES.literal().get(normalised);
        if (literal != null) {
            return Optional.of(literal);
        }
        for (Rule rule : RULES.patterns()) {
            if (rule.pattern().matcher(normalised).matches()) {
                return Optional.of(rule.cargo());
            }
        }
        int space = normalised.indexOf(' ');
        String head = space < 0 ? normalised : normalised.substring(0, space);
        return Optional.ofNullable(RULES.head().get(head));
    }

    private static String normalise(String label) {
        return label.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static Rules load() {
        Map<String, Cargo> literal = new HashMap<>();
        Map<String, Cargo> head = new HashMap<>();
        List<Rule> patterns = new ArrayList<>();
        try (InputStream in = Objects.requireNonNull(
                CargoDictionary.class.getResourceAsStream(RESOURCE), "missing classpath resource " + RESOURCE);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.charAt(0) == '#') {
                    continue;
                }
                int firstTab = line.indexOf('\t');
                int secondTab = line.indexOf('\t', firstTab + 1);
                String key = line.substring(firstTab + 1, secondTab);
                Cargo cargo = Cargo.valueOf(line.substring(secondTab + 1));
                switch (line.charAt(0)) {
                    case 'L' -> literal.put(key, cargo);
                    case 'R' -> patterns.add(new Rule(Pattern.compile(key), cargo));
                    case 'H' -> head.put(key, cargo);
                    default -> throw new IllegalStateException("unknown rule type in " + RESOURCE + ": " + line);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + RESOURCE, e);
        }
        return new Rules(Map.copyOf(literal), List.copyOf(patterns), Map.copyOf(head));
    }

    private record Rule(Pattern pattern, Cargo cargo) {
    }

    private record Rules(Map<String, Cargo> literal, List<Rule> patterns, Map<String, Cargo> head) {
    }
}
