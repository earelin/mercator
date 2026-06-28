package net.earelin.mercator.domain.parse;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards that every code {@link ProvinceDictionary} resolves to is a real {@code province.code} and
 * that the dictionary covers the whole seed, so neither a typo nor a dropped province goes uncaught.
 */
class ProvinceSeedParityTest {

    private static final Pattern PROVINCE_CODE = Pattern.compile("\\('(\\d{2})'");

    @Test
    void province_dictionary_codes_match_the_seeded_province_codes() throws IOException, URISyntaxException {
        assertThat(new TreeSet<>(ProvinceDictionary.codes()))
            .as("ProvinceDictionary codes must equal the seeded province codes")
            .isEqualTo(new TreeSet<>(seededProvinceCodes()));
    }

    private static Set<String> seededProvinceCodes() throws IOException, URISyntaxException {
        Path dir = Path.of(ProvinceSeedParityTest.class.getResource("/db/migration").toURI());
        Set<String> codes = new TreeSet<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                collectProvinceCodes(Files.readString(file, StandardCharsets.UTF_8), codes);
            }
        }
        return codes;
    }

    private static void collectProvinceCodes(String sql, Set<String> codes) {
        int from = sql.indexOf("INSERT INTO province");
        while (from >= 0) {
            int end = sql.indexOf(';', from);
            Matcher matcher = PROVINCE_CODE.matcher(sql.substring(from, end));
            while (matcher.find()) {
                codes.add(matcher.group(1));
            }
            from = sql.indexOf("INSERT INTO province", end);
        }
    }
}
