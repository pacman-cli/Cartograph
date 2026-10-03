/**
 * Source-extraction boundary: {@code SourceParser} implementations turn raw
 * source files into parsed symbols and call sites for the graph builder.
 *
 * <p>Boundary rules: no HTTP and no persistence in this package; language
 * extractors live in subpackages and register as adapters.
 */
package com.cartograph.parsing;
