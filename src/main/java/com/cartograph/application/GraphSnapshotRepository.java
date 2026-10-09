package com.cartograph.application;

import com.cartograph.graph.model.GraphSnapshot;
import java.util.List;
import java.util.Optional;

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

    /** Read model for the repository-discovery endpoint. */
    record RepositorySummary(String repository, String commitSha, String indexedAt) {}

    /**
     * Lists every indexed repository with its latest commit, newest first.
     * Pure local lookup — implementations must not perform I/O beyond the store.
     */
    List<RepositorySummary> listRepositories();

    void save(GraphSnapshot snapshot);
}
