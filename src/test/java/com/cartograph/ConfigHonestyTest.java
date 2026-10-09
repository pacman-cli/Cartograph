package com.cartograph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Keeps the README configuration table honest with {@code application.yml}:
 * every key defined under {@code cartograph.*} must be documented in the
 * README table, and every documented {@code cartograph.*} key must exist in
 * the yml. {@code server.port} is excluded (Spring's own surface). Spot-checks
 * a few default values so drift in numbers is caught too.
 */
class ConfigHonestyTest {
    private static final Path README = Path.of("README.md");
    private static final Path YML = Path.of("src/main/resources/application.yml");
    private static final Pattern TABLE_KEY = Pattern.compile("^\\| `(cartograph\\.[a-z0-9.-]+)` \\| ([^|]+) \\|");
    private static final Pattern YML_KEY = Pattern.compile("^([a-zA-Z][a-zA-Z0-9-]*):");

    @Test
    void readmeTableMatchesApplicationYmlUnderCartographNamespace() throws Exception {
        Set<String> documented = readmeCartographKeys();
        Set<String> defined = ymlCartographKeys();

        Set<String> documentedButUndefined = new HashSet<>(documented);
        documentedButUndefined.removeAll(defined);
        Set<String> definedButUndocumented = new HashSet<>(defined);
        definedButUndocumented.removeAll(documented);

        assertTrue(
                documentedButUndefined.isEmpty(),
                "README documents cartograph.* keys that application.yml does not define: " + documentedButUndefined);
        assertTrue(
                definedButUndocumented.isEmpty(),
                "application.yml defines cartograph.* keys missing from the README config table: "
                        + definedButUndocumented);
    }

    @Test
    void readmeDefaultsMatchTheImplementation() throws Exception {
        Map<String, String> defaults =
                readmeRows().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertEquals("10000", defaults.get("cartograph.github.max-files"), "cartograph.github.max-files");
        assertEquals("3", defaults.get("cartograph.github.max-attempts"), "cartograph.github.max-attempts");
        assertEquals(
                "250",
                defaults.get("cartograph.github.retry-backoff-millis"),
                "cartograph.github.retry-backoff-millis");
        assertEquals("30", defaults.get("cartograph.ratelimit.capacity"), "cartograph.ratelimit.capacity");
        assertEquals(
                "60", defaults.get("cartograph.ratelimit.refill-per-minute"), "cartograph.ratelimit.refill-per-minute");
    }

    private Set<String> readmeCartographKeys() throws Exception {
        return readmeRows().keySet();
    }

    /** key (with backticks removed) -> default cell, for every table row carrying a dotted key. */
    private Map<String, String> readmeRows() throws Exception {
        List<String> lines = Files.readAllLines(README);
        return lines.stream()
                .map(TABLE_KEY::matcher)
                .filter(java.util.regex.Matcher::find)
                .collect(Collectors.toUnmodifiableMap(
                        m -> m.group(1), m -> m.group(2).replace("`", "").strip(), (a, b) -> a));
    }

    /** Dotted keys under the top-level {@code cartograph:} block (two-space indentation). */
    private Set<String> ymlCartographKeys() throws Exception {
        Set<String> keys = new HashSet<>();
        boolean inCartograph = false;
        String parent = null;
        for (String line : Files.readAllLines(YML)) {
            if (line.isBlank() || line.strip().startsWith("#")) continue;
            int indent = line.indexOf(line.strip());
            String stripped = line.strip();
            if (indent == 0) {
                inCartograph = stripped.equals("cartograph:");
                parent = null;
                continue;
            }
            if (!inCartograph) continue;
            var matcher = YML_KEY.matcher(stripped);
            String key = matcher.find() ? matcher.group(1) : stripped;
            if (indent == 2) {
                parent = key;
                keys.add("cartograph." + key);
            } else if (indent == 4 && parent != null) {
                keys.add("cartograph." + parent + "." + key);
            }
        }
        // namespace groups (e.g. cartograph.github) are not configurable leaves
        return keys.stream()
                .filter(key -> keys.stream().noneMatch(other -> other.startsWith(key + ".")))
                .collect(Collectors.toUnmodifiableSet());
    }
}
