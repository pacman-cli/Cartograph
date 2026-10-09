package com.cartograph.graph.model;

import java.util.List;

/** Parser output for one source file, including symbols, relations, exports, and diagnostics. */
public record ParsedFile(
        String path,
        List<GraphNode> nodes,
        List<GraphEdge> edges,
        List<GraphWarning> warnings,
        List<String> exports,
        List<GraphExport> exportDetails,
        List<DirectCallSite> directCallSites) {
    public ParsedFile(String path, List<GraphNode> nodes, List<GraphEdge> edges) {
        this(path, nodes, edges, List.of(), List.of(), List.of(), List.of());
    }

    public ParsedFile(
            String path,
            List<GraphNode> nodes,
            List<GraphEdge> edges,
            List<GraphWarning> warnings,
            List<String> exports) {
        this(
                path,
                nodes,
                edges,
                warnings,
                exports,
                exports.stream().map(name -> new GraphExport(name, null)).toList(),
                List.of());
    }

    public ParsedFile {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        warnings = List.copyOf(warnings);
        exports = List.copyOf(exports);
        exportDetails = List.copyOf(exportDetails);
        directCallSites = List.copyOf(directCallSites);
    }
}
