/**
 * Adapter implementing the {@code GraphSnapshotRepository} port on SQLite:
 * snapshot upsert/lookup keyed by {@code (repository, commitSha)} with an
 * explicit SQL schema (see {@code SQLiteGraphSnapshotRepository}).
 *
 * <p>Boundary rules: all SQL lives here; the application core only sees
 * {@code Optional<GraphSnapshot>} results. See
 * {@code docs/decisions/0001-sqlite-over-postgres.md} for why SQLite and
 * what would trigger a store swap.
 */
package com.cartograph.persistence;
