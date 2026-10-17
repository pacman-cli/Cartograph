package com.cartograph.api;

import com.cartograph.graph.model.GraphEdge;
import com.cartograph.graph.model.GraphMetrics;
import com.cartograph.graph.model.GraphNode;
import com.cartograph.graph.model.GraphSnapshot;
import com.cartograph.graph.model.GraphWarning;
import java.util.List;

/**
 * JSON representation of the graph produced for one resolved repository commit.
 *
 * @param repository normalized owner/repository coordinate
 * @param commitSha immutable commit identifier used to create the snapshot
 * @param nodes graph nodes with source locations
 * @param edges graph relationships between nodes
 * @param warnings parser or indexing diagnostics associated with the snapshot
 * @param metrics counts describing the indexing result
 */
public record GraphSnapshotResponse(
        String repository,
        String commitSha,
        List<GraphNode> nodes,
        List<GraphEdge> edges,
        List<GraphWarning> warnings,
        Metrics metrics) {
    /**
     * Creates the API response from the completed domain snapshot.
     *
     * @param snapshot completed graph snapshot
     * @return response containing the snapshot data and API metrics
     */
    public static GraphSnapshotResponse from(GraphSnapshot snapshot) {
        return new GraphSnapshotResponse(
                snapshot.repository(),
                snapshot.commitSha(),
                snapshot.nodes(),
                snapshot.edges(),
                snapshot.warnings(),
                Metrics.from(snapshot.metrics()));
    }

    /**
     * Counts files and graph elements reported by the indexer.
     *
     * @param filesSeen repository files considered by the fetcher
     * @param filesParsed files successfully passed to a parser
     * @param nodes nodes present in the graph
     * @param edges edges present in the graph
     */
    public record Metrics(int filesSeen, int filesParsed, int nodes, int edges) {
        static Metrics from(GraphMetrics metrics) {
            return new Metrics(metrics.filesSeen(), metrics.filesParsed(), metrics.nodeCount(), metrics.edgeCount());
        }
    }
}
