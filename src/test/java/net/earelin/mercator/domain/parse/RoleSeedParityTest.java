package net.earelin.mercator.domain.parse;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the chosen "enum mirrors the DB" design: the {@link Cargo} values must equal exactly the
 * {@code role.code}s seeded across every Flyway migration, so the {@code appointment.role} foreign
 * key can never reject a cargo the parser produces.
 */
class RoleSeedParityTest {

    private static final Pattern ROLE_CODE = Pattern.compile("\\('([A-Z_][A-Z0-9_]*)'");

    @Test
    void cargo_enum_matches_the_seeded_role_codes() throws IOException, URISyntaxException {
        Set<String> seeded = new TreeSet<>(seededRoleCodes());
        Set<String> enumNames = Arrays.stream(Cargo.values())
            .map(Enum::name)
            .collect(Collectors.toCollection(TreeSet::new));

        assertThat(enumNames)
            .as("Cargo enum values must equal the role codes seeded by the migrations")
            .isEqualTo(seeded);
    }

    private static Set<String> seededRoleCodes() throws IOException, URISyntaxException {
        Path dir = Path.of(RoleSeedParityTest.class.getResource("/db/migration").toURI());
        Set<String> codes = new TreeSet<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                collectRoleCodes(Files.readString(file, StandardCharsets.UTF_8), codes);
            }
        }
        return codes;
    }

    private static void collectRoleCodes(String sql, Set<String> codes) {
        int from = sql.indexOf("INSERT INTO role");
        while (from >= 0) {
            int end = sql.indexOf(';', from);
            Matcher matcher = ROLE_CODE.matcher(sql.substring(from, end));
            while (matcher.find()) {
                codes.add(matcher.group(1));
            }
            from = sql.indexOf("INSERT INTO role", end);
        }
    }
}
