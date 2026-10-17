package com.cartograph.graph;

import com.cartograph.application.SourceParser;
import com.cartograph.graph.model.*;
import java.util.*;

/** Combines parsed repository files into a deterministic graph snapshot. */
public final class GraphBuilder {
    // Parsers intentionally use empty repository/commit context; this is the sole owner of final snapshot IDs.
    private final SourceParser parser;
    /**
     * @param parser parser used for each source file in the repository snapshot
     */
    public GraphBuilder(SourceParser parser) {
        this.parser = Objects.requireNonNull(parser);
    }

    /**
     * Parses the files and assigns snapshot-scoped stable IDs to graph nodes.
     *
     * @param repository repository snapshot already fetched at a resolved commit
     * @return graph snapshot with sorted nodes, edges, and warnings
     */
    public GraphSnapshot build(RepositorySnapshot repository) {
        List<ParsedFile> parsed = repository.files().stream()
                .sorted(Comparator.comparing(SourceFile::path))
                .map(parser::parse)
                .toList();
        Map<String, String> ids = new HashMap<>();
        List<GraphNode> nodes = parsed.stream()
                .flatMap(p -> p.nodes().stream())
                .map(n -> {
                    String id = StableNodeId.create(
                            repository.repository(),
                            repository.commitSha(),
                            n.filePath(),
                            n.kind(),
                            n.name(),
                            n.startLine(),
                            n.startColumn());
                    ids.put(n.stableId(), id);
                    return new GraphNode(
                            id,
                            n.kind(),
                            n.name(),
                            n.filePath(),
                            n.startLine(),
                            n.endLine(),
                            n.startColumn(),
                            n.endColumn());
                })
                .sorted(Comparator.comparing(GraphNode::stableId))
                .toList();
        List<GraphEdge> edges = parsed.stream()
                .flatMap(p -> p.edges().stream())
                .map(e -> new GraphEdge(
                        ids.getOrDefault(e.fromId(), e.fromId()),
                        ids.getOrDefault(e.toId(), e.toId()),
                        e.kind(),
                        e.confidence(),
                        e.location()))
                .sorted(Comparator.comparing(GraphEdge::fromId)
                        .thenComparing(GraphEdge::toId)
                        .thenComparing(GraphEdge::kind))
                .toList();
        List<GraphWarning> warnings = java.util.stream.Stream.concat(
                        repository.warnings().stream(), parsed.stream().flatMap(p -> p.warnings().stream()))
                .sorted(Comparator.comparing(GraphWarning::filePath)
                        .thenComparing(w -> Optional.ofNullable(w.line()).orElse(0))
                        .thenComparing(GraphWarning::code))
                .toList();
        return new GraphSnapshot(
                repository.repository(),
                repository.commitSha(),
                nodes,
                edges,
                warnings,
                new GraphMetrics(
                        nodes.size(),
                        edges.size(),
                        warnings.size(),
                        repository.filesSeen(),
                        repository.files().size()));
    }
}
