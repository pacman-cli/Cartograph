/**
 * Graph domain assembly: {@code GraphBuilder} turns parsed files into a
 * {@code GraphSnapshot} with stable node IDs, and {@code StableNodeId} pins
 * node identity across commits.
 *
 * <p>Boundary rules: pure Java — no framework, HTTP, or I/O types.
 */
package com.cartograph.graph;
