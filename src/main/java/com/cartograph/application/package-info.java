/**
 * Framework-free application core: the repository-indexing use case and the
 * driven ports every adapter must implement —
 * {@link com.cartograph.application.RepositoryFetcher},
 * {@link com.cartograph.application.SourceParser}, and
 * {@link com.cartograph.application.GraphSnapshotRepository}.
 *
 * <p>Boundary rules: nothing in this package may import Spring Web, HTTP
 * clients, JDBC/SQLite, or tree-sitter types. Adapters live in
 * {@code com.cartograph.ingestion}, {@code com.cartograph.parsing}, and
 * {@code com.cartograph.persistence} and are wired in via Spring registration.
 */
package com.cartograph.application;
