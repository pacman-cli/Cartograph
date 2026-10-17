package com.cartograph.graph.model;

/** Counts graph elements, diagnostics, and files processed for an index operation. */
public record GraphMetrics(int nodeCount, int edgeCount, int warningCount, int filesSeen, int filesParsed) {
    public GraphMetrics(int nodeCount, int edgeCount, int warningCount, int fileCount) {
        this(nodeCount, edgeCount, warningCount, fileCount, fileCount);
    }

    public GraphMetrics {
        if (nodeCount < 0
                || edgeCount < 0
                || warningCount < 0
                || filesSeen < 0
                || filesParsed < 0
                || filesParsed > filesSeen) {
            throw new IllegalArgumentException("Graph metrics cannot be negative");
        }
    }

    public int fileCount() {
        return filesParsed;
    }
}
