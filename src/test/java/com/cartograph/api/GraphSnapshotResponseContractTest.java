package com.cartograph.api;

import com.cartograph.graph.model.EdgeKind;
import com.cartograph.graph.model.GraphEdge;
import com.cartograph.graph.model.GraphMetrics;
import com.cartograph.graph.model.GraphNode;
import com.cartograph.graph.model.GraphSnapshot;
import com.cartograph.graph.model.GraphWarning;
import com.cartograph.graph.model.SourceLocation;
import com.cartograph.graph.model.SymbolKind;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the public JSON wire contract of {@link GraphSnapshotResponse}.
 *
 * <p>Regeneration: this golden is intentionally a literal string — update it
 * by hand ONLY when the contract change is intentional and announced; the
 * test failing is the announcement mechanism.
 */
class GraphSnapshotResponseContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesTheDocumentedWireShape() throws Exception {
        GraphSnapshotResponse response = GraphSnapshotResponse.from(new GraphSnapshot(
                "acme/widgets",
                "e1f4a2b9c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8",
                List.of(new GraphNode("fn:greet:1", SymbolKind.FUNCTION, "greet", "src/app.ts",
                        10, 20, 4, 12)),
                List.of(new GraphEdge("fn:greet:1", "fn:log:2", EdgeKind.CALLS, 1.0,
                        new SourceLocation("src/app.ts", 12, 8, 12, 20))),
                List.of(new GraphWarning("UNSUPPORTED_SYNTAX", "Decorator skipped", "src/app.ts", 3)),
                new GraphMetrics(1, 1, 1, 19, 5)));

        String golden = """
                {"repository":"acme/widgets","commitSha":"e1f4a2b9c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8","nodes":[{"stableId":"fn:greet:1","kind":"FUNCTION","name":"greet","filePath":"src/app.ts","startLine":10,"endLine":20,"startColumn":4,"endColumn":12}],"edges":[{"fromId":"fn:greet:1","toId":"fn:log:2","kind":"CALLS","confidence":1.0,"location":{"filePath":"src/app.ts","startLine":12,"startColumn":8,"endLine":12,"endColumn":20}}],"warnings":[{"code":"UNSUPPORTED_SYNTAX","message":"Decorator skipped","filePath":"src/app.ts","line":3}],"metrics":{"filesSeen":19,"filesParsed":5,"nodes":1,"edges":1}}""";

        assertEquals(golden, mapper.writeValueAsString(response));
    }

    @Test
    void emptyCollectionsSerializeAsArraysNeverOmittedOrNull() throws Exception {
        GraphSnapshotResponse response = GraphSnapshotResponse.from(new GraphSnapshot(
                "acme/empty", "0123456789abcdef0123456789abcdef01234567",
                List.of(), List.of(), List.of(), new GraphMetrics(0, 0, 0, 0, 0)));

        String golden = """
                {"repository":"acme/empty","commitSha":"0123456789abcdef0123456789abcdef01234567","nodes":[],"edges":[],"warnings":[],"metrics":{"filesSeen":0,"filesParsed":0,"nodes":0,"edges":0}}""";

        assertEquals(golden, mapper.writeValueAsString(response));
    }
}
