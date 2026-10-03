package com.cartograph.application;

import com.cartograph.graph.model.ParsedFile;
import com.cartograph.graph.model.SourceFile;

/**
 * Driven port for source extraction. Implementations (e.g. the tree-sitter
 * JS/TS extractor) turn one {@link SourceFile} into a {@link ParsedFile}
 * containing symbols and call sites for the graph builder.
 *
 * <p>Contract: implementations must be deterministic and side-effect free,
 * must never fail the whole index for one unsupported construct — return a
 * {@link ParsedFile} with warnings instead — and must not perform I/O.
 */
@FunctionalInterface
public interface SourceParser {
    ParsedFile parse(SourceFile file);
}
