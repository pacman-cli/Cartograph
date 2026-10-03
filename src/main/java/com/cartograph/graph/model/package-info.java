/**
 * Immutable graph snapshot model: {@code GraphNode}, {@code GraphEdge},
 * {@code GraphMetrics}, {@code GraphWarning}, plus repository/file references
 * and the {@code ParsedFile} hand-off between parsing and graph building.
 *
 * <p>Boundary rules: plain records and enums only — the model is shared by
 * every adapter and must stay free of framework annotations.
 */
package com.cartograph.graph.model;
