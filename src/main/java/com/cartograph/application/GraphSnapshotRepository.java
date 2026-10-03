package com.cartograph.application;

import java.util.Optional;

import com.cartograph.graph.model.GraphSnapshot;

/**
 * Driven port for graph snapshot persistence. Implementations (the SQLite
 * adapter today) key snapshots by {@code (repository, commitSha)} so the
 * same commit always returns the same cached graph.
 *
 * <p>Contract: {@link #find} must return an empty result for unknown pairs —
 * never an error — and {@link #save} must store the snapshot atomically so a
 * later {@link #find} with the same keys returns an equivalent snapshot.
 * Implementations must not mutate a stored snapshot (snapshots are
 * immutable once saved).
 */
public interface GraphSnapshotRepository {
    Optional<GraphSnapshot> find(String repository, String commitSha);

    /**
     * Returns the most recently stored snapshot for {@code repository},
     * regardless of commit; empty when the repository was never indexed.
     */
    Optional<GraphSnapshot> findLatest(String repository);

    void save(GraphSnapshot snapshot);
}
