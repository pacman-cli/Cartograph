package com.cartograph.graph.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class GraphModelTest {
    @Test
    void preservesNodeIdentityAndSourceLocation() {
        GraphNode node = new GraphNode("node-1", SymbolKind.CLASS, "Widget", "src/Widget.java", 4, 18);

        assertEquals("node-1", node.stableId());
        assertEquals(SymbolKind.CLASS, node.kind());
        assertEquals("src/Widget.java", node.filePath());
        assertEquals(4, node.startLine());
        assertEquals(18, node.endLine());
    }

    @Test
    void preservesEdgeKindAndConfidence() {
        GraphEdge edge = new GraphEdge("from", "to", EdgeKind.CALLS, 0.87);

        assertEquals(EdgeKind.CALLS, edge.kind());
        assertEquals(0.87, edge.confidence());
    }

    @Test
    void snapshotIsKeyedByRepositoryAndCommitAndPreservesWarningsAndMetrics() {
        GraphWarning warning = new GraphWarning("UNRESOLVED", "Target could not be resolved", "src/A.java", 12);
        GraphMetrics metrics = new GraphMetrics(2, 1, 1, 0);
        GraphSnapshot snapshot =
                new GraphSnapshot("acme/widgets", "abc123", List.of(), List.of(), List.of(warning), metrics);

        assertEquals("acme/widgets", snapshot.repository());
        assertEquals("abc123", snapshot.commitSha());
        assertEquals(List.of(warning), snapshot.warnings());
        assertEquals(metrics, snapshot.metrics());
    }

    @Test
    void collectionFieldsAreImmutable() {
        GraphSnapshot snapshot =
                new GraphSnapshot("repo", "sha", List.of(), List.of(), List.of(), new GraphMetrics(0, 0, 0, 0));

        assertThrows(UnsupportedOperationException.class, () -> snapshot.nodes().add(null));
    }
}
