package com.cartograph.parsing.javascript;

import static org.junit.jupiter.api.Assertions.*;

import com.cartograph.graph.model.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JavaScriptTypeScriptParserTest {
    private final JavaScriptTypeScriptParser parser = new JavaScriptTypeScriptParser();

    public static SourceFile fixture() throws Exception {
        try (var input = JavaScriptTypeScriptParserTest.class.getResourceAsStream("/fixtures/simple-ts-repo/main.ts")) {
            return new SourceFile("main.ts", new String(input.readAllBytes(), StandardCharsets.UTF_8), "typescript");
        }
    }

    @Test
    void extractsGoldenDefinitionsImportsExportsAndOnlyProvenCalls() throws Exception {
        ParsedFile result = parser.parse(fixture());
        assertEquals(
                List.of(
                        "FILE:main.ts:1:16",
                        "MODULE:./external:1:1",
                        "FUNCTION:greet:3:5",
                        "FUNCTION:run:7:10",
                        "CLASS:Greeter:12:16",
                        "METHOD:Greeter.say:13:15"),
                result.nodes().stream()
                        .map(n -> n.kind() + ":" + n.name() + ":" + n.startLine() + ":" + n.endLine())
                        .toList());
        assertEquals(List.of("greet", "run", "Greeter"), result.exports());
        assertTrue(result.exportDetails().stream().allMatch(exported -> exported.location() != null));
        assertTrue(result.directCallSites().stream()
                .allMatch(call -> call.location().startLine() >= 1));
        assertEquals(
                2,
                result.edges().stream().filter(e -> e.kind() == EdgeKind.CALLS).count());
        assertEquals(
                1,
                result.edges().stream()
                        .filter(e -> e.kind() == EdgeKind.IMPORTS)
                        .count());
        assertTrue(result.edges().stream().allMatch(e -> e.confidence() == 1.0));
        assertEquals(
                List.of(new GraphWarning("UNRESOLVED_CALL", "Call target cannot be proven: callback", "main.ts", 8)),
                result.warnings());
        assertEquals(result, parser.parse(fixture()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"js", "jsx", "ts", "tsx"})
    void selectsGrammarByExtension(String extension) {
        String source = extension.endsWith("x")
                ? "export function View() { return <div/>; }"
                : "export function hello() { return 1; }";
        ParsedFile parsed = parser.parse(new SourceFile("view." + extension, source, "ignored"));
        assertTrue(parsed.warnings().isEmpty(), parsed.warnings().toString());
        assertEquals(2, parsed.nodes().size());
    }

    @Test
    void ignoresCommentsStringsAndPreservesUnicodeLocations() {
        ParsedFile result = parse(
                "// function fake() {}\nconst text = 'function nope() {} 😀';\nfunction café() {}\nfunction run() { café(); }");
        assertEquals(
                List.of("input.ts", "café", "run"),
                result.nodes().stream().map(GraphNode::name).toList());
        assertEquals(1, calls(result));
        assertEquals(3, result.nodes().get(1).startLine());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "function target() {} function run(target: () => void) { target(); }",
                "function target() {} function run() { const target = other; target(); }",
                "function target() {} target = other; function run() { target(); }",
                "function run() { object.method(); object[key](); missing(); }",
                "function outer() { function target() {} } function run() { target(); }",
                "function target() {} function run({target}) { target(); }",
                "function target() {} function run() { eval(code); target(); }"
            })
    void keepsDynamicShadowedMutatedAndOutOfScopeCallsUnresolved(String source) {
        ParsedFile result = parse(source);
        assertEquals(0, calls(result));
        assertTrue(result.warnings().stream().anyMatch(w -> w.code().equals("UNRESOLVED_CALL")));
    }

    @Test
    void warnsOnSyntaxErrorsUnsupportedDefinitionsAndFiles() {
        assertTrue(parse("function broken( {").warnings().stream()
                .anyMatch(w -> w.code().equals("SYNTAX_ERROR")));
        assertTrue(parse("const fn = () => unknown();").warnings().stream()
                .anyMatch(w -> w.code().equals("UNSUPPORTED_SYNTAX")));
        ParsedFile unsupported = parser.parse(new SourceFile("sample.py", "def nope(): pass", "python"));
        assertTrue(unsupported.nodes().isEmpty());
        assertEquals("UNSUPPORTED_FILE", unsupported.warnings().get(0).code());
    }

    @Test
    void distinguishesSameLineDeclarationsAndExtractsExportAliases() {
        ParsedFile parsed =
                parse("function outer() { function same() {} } function same() {} export {same as renamed};");
        assertEquals(
                parsed.nodes().size(),
                parsed.nodes().stream().map(GraphNode::stableId).distinct().count());
        assertEquals(List.of("renamed"), parsed.exports());
    }

    private ParsedFile parse(String source) {
        return parser.parse(new SourceFile("input.ts", source, "typescript"));
    }

    private long calls(ParsedFile file) {
        return file.edges().stream().filter(e -> e.kind() == EdgeKind.CALLS).count();
    }
}
