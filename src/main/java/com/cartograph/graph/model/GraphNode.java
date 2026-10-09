package com.cartograph.graph.model;

/** A named source symbol with stable identity, 1-based lines, 0-based columns, and an exclusive end position. */
public record GraphNode(
        String stableId,
        SymbolKind kind,
        String name,
        String filePath,
        int startLine,
        int endLine,
        int startColumn,
        int endColumn) {
    public GraphNode(String stableId, SymbolKind kind, String name, String filePath, int startLine, int endLine) {
        this(stableId, kind, name, filePath, startLine, endLine, 0, 0);
    }

    public GraphNode {
        if (startLine < 1 || endLine < startLine) {
            throw new IllegalArgumentException("Source location must have positive, ordered lines");
        }
        if (startColumn < 0 || endColumn < 0) {
            throw new IllegalArgumentException("Source columns must be non-negative");
        }
    }

    public SourceLocation location() {
        return new SourceLocation(filePath, startLine, startColumn, endLine, endColumn);
    }
}
