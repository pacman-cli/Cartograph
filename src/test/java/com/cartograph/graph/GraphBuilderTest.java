package com.cartograph.graph;

import static org.junit.jupiter.api.Assertions.*;

import com.cartograph.graph.model.*;
import com.cartograph.parsing.javascript.JavaScriptTypeScriptParser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class GraphBuilderTest {
    @Test
    void buildsGoldenGraphWithScopedIdsWarningsAndMetrics() throws Exception {
        String source;
        try (var in = getClass().getResourceAsStream("/fixtures/simple-ts-repo/main.ts")) {
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var repo =
                new RepositorySnapshot("acme/demo", "abc123", List.of(new SourceFile("main.ts", source, "typescript")));
        var builder = new GraphBuilder(new JavaScriptTypeScriptParser());
        GraphSnapshot graph = builder.build(repo);
        assertEquals(graph, builder.build(repo));
        assertEquals(new GraphMetrics(6, 7, 1, 1), graph.metrics());
        Map<String, String> names =
                graph.nodes().stream().collect(Collectors.toMap(GraphNode::stableId, GraphNode::name));
        assertEquals(
                List.of("Greeter.say -> greet", "run -> greet"),
                graph.edges().stream()
                        .filter(e -> e.kind() == EdgeKind.CALLS)
                        .map(e -> names.get(e.fromId()) + " -> " + names.get(e.toId()))
                        .sorted()
                        .toList());
        assertTrue(graph.edges().stream().allMatch(e -> names.containsKey(e.fromId()) && names.containsKey(e.toId())));
        var changed = builder.build(new RepositorySnapshot("acme/demo", "other", repo.files()));
        assertTrue(changed.nodes().stream().noneMatch(n -> names.containsKey(n.stableId())));
    }

    @Test
    void stableIdentityIncludesAllInputsAndNormalizesPaths() {
        String id = StableNodeId.create("acme/demo", "abc123", "src/./a.ts", SymbolKind.FUNCTION, "run", 3, 1);
        assertEquals(id, StableNodeId.create("acme/demo", "abc123", "src\\a.ts", SymbolKind.FUNCTION, "run", 3, 1));
        assertNotEquals(id, StableNodeId.create("acme/other", "abc123", "src/a.ts", SymbolKind.FUNCTION, "run", 3, 1));
        assertNotEquals(id, StableNodeId.create("acme/demo", "next", "src/a.ts", SymbolKind.FUNCTION, "run", 3, 1));
        assertNotEquals(id, StableNodeId.create("acme/demo", "abc123", "src/b.ts", SymbolKind.FUNCTION, "run", 3, 1));
        assertNotEquals(id, StableNodeId.create("acme/demo", "abc123", "src/a.ts", SymbolKind.METHOD, "run", 3, 1));
        assertNotEquals(id, StableNodeId.create("acme/demo", "abc123", "src/a.ts", SymbolKind.FUNCTION, "other", 3, 1));
        assertNotEquals(id, StableNodeId.create("acme/demo", "abc123", "src/a.ts", SymbolKind.FUNCTION, "run", 4, 1));
        assertNotEquals(id, StableNodeId.create("acme/demo", "abc123", "src/a.ts", SymbolKind.FUNCTION, "run", 3, 2));
    }

    @Test
    void repositoryFileOrderDoesNotChangeGraphAndCallsNeverResolveAcrossFiles() {
        var a = new SourceFile("a.ts", "function target() {}", "typescript");
        var b = new SourceFile("b.ts", "function run() { target(); }", "typescript");
        var builder = new GraphBuilder(new JavaScriptTypeScriptParser());
        var graph = builder.build(new RepositorySnapshot("repo", "sha", List.of(a, b)));
        assertEquals(graph, builder.build(new RepositorySnapshot("repo", "sha", List.of(b, a))));
        assertTrue(graph.edges().stream().noneMatch(e -> e.kind() == EdgeKind.CALLS));
        assertEquals(1, graph.warnings().size());
    }

    @Test
    void sameLineDefinitionsHaveDistinctFinalIdsAndPreserveNodeLocations() {
        var file = new SourceFile("same-line.ts", "function first() {} function second() {}", "typescript");
        var graph = new GraphBuilder(new JavaScriptTypeScriptParser())
                .build(new RepositorySnapshot("repo", "sha", List.of(file)));

        var definitions = graph.nodes().stream()
                .filter(n -> n.kind() == SymbolKind.FUNCTION)
                .toList();
        assertEquals(2, definitions.size());
        assertNotEquals(definitions.get(0).stableId(), definitions.get(1).stableId());
        assertNotEquals(definitions.get(0).startColumn(), definitions.get(1).startColumn());
        assertTrue(definitions.stream().allMatch(n -> n.startLine() == 1 && n.endLine() == 1));
    }

    @Test
    void finalSnapshotEdgesRetainParserSourceLocations() {
        var file =
                new SourceFile("locations.ts", "function target() {}\nfunction caller() { target(); }", "typescript");
        var graph = new GraphBuilder(new JavaScriptTypeScriptParser())
                .build(new RepositorySnapshot("repo", "sha", List.of(file)));

        var call = graph.edges().stream()
                .filter(e -> e.kind() == EdgeKind.CALLS)
                .findFirst()
                .orElseThrow();
        assertEquals(new SourceLocation("locations.ts", 2, 20, 2, 28), call.location());
        assertTrue(graph.edges().stream().allMatch(e -> e.location() != null));
    }

    @Test
    void nodeIdsAreDeterministicUnderShuffledFileOrder() {
        var files = List.of(
                new SourceFile(
                        "main.ts",
                        "export const greet = (n: string) => `hi ${n}`;\nexport function run() { return greet(\"x\"); }",
                        "typescript"),
                new SourceFile(
                        "utils.ts",
                        "export function helper() { return run(); }\nexport const answer = 42;",
                        "typescript"),
                new SourceFile(
                        "api.ts",
                        "export * from \"./utils\";\nexport class Client { ping() { return helper(); } }",
                        "typescript"));

        var builder = new GraphBuilder(new JavaScriptTypeScriptParser());
        GraphSnapshot first = builder.build(new RepositorySnapshot("acme/demo", "abc123", files));

        var random = new java.util.Random(20261003L);
        for (int iteration = 0; iteration < 50; iteration++) {
            var shuffled = new java.util.ArrayList<>(files);
            java.util.Collections.shuffle(shuffled, random);
            GraphSnapshot next = builder.build(new RepositorySnapshot("acme/demo", "abc123", shuffled));
            org.junit.jupiter.api.Assertions.assertEquals(first, next, "iteration " + iteration);
        }
    }
}
