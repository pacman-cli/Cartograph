package com.cartograph.graph.model;

/** Directed, typed relationship between two stable graph node identifiers. */
public record GraphEdge(String fromId, String toId, EdgeKind kind, double confidence, SourceLocation location) {
    public GraphEdge(String fromId, String toId, EdgeKind kind, double confidence) {
        this(fromId, toId, kind, confidence, null);
    }

    public GraphEdge {
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Confidence must be between 0 and 1");
        }
    }
}
