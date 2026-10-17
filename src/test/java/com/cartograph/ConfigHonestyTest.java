package com.cartograph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
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
        assertEquals("true", defaults.get("cartograph.github.clone.enabled"), "cartograph.github.clone.enabled");
        assertEquals(
                "10000",
                defaults.get("cartograph.github.clone.file-threshold"),
                "cartograph.github.clone.file-threshold");
        assertEquals(
                "120",
                defaults.get("cartograph.github.clone.timeout-seconds"),
                "cartograph.github.clone.timeout-seconds");
        assertEquals(
                "10",
                defaults.get("cartograph.github.clone.cleanup-timeout-seconds"),
                "cartograph.github.clone.cleanup-timeout-seconds");
        assertEquals("git", defaults.get("cartograph.github.clone.executable"), "cartograph.github.clone.executable");
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

    /** Leaf keys at any nesting depth under the top-level {@code cartograph:} block. */
    private Set<String> ymlCartographKeys() throws Exception {
        Set<String> keys = new HashSet<>();
        Deque<YamlKey> path = new ArrayDeque<>();
        List<String> lines = Files.readAllLines(YML);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank() || line.strip().startsWith("#")) continue;
            int indent = line.indexOf(line.strip());
            String stripped = line.strip();
            var matcher = YML_KEY.matcher(stripped);
            if (!matcher.find()) continue;
            String key = matcher.group(1);
            while (!path.isEmpty() && path.peek().indent() >= indent) path.pop();
            if (indent == 0) {
                path.clear();
                if (!key.equals("cartograph")) continue;
            } else if (path.isEmpty() || !path.peek().fullPath().startsWith("cartograph")) {
                continue;
            }
            path.push(
                    new YamlKey(indent, key, path.isEmpty() ? key : path.peek().fullPath() + "." + key));
            int nextIndent = nextKeyIndent(lines, index + 1);
            if (nextIndent <= indent) keys.add(path.peek().fullPath());
        }
        return Set.copyOf(keys);
    }

    private int nextKeyIndent(List<String> lines, int start) {
        for (int index = start; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank() || line.strip().startsWith("#") || line.strip().startsWith("-")) continue;
            return line.indexOf(line.strip());
        }
        return -1;
    }

    private record YamlKey(int indent, String key, String fullPath) {}
}
