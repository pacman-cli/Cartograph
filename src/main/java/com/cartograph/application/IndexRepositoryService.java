package com.cartograph.application;

import com.cartograph.graph.GraphBuilder;
import com.cartograph.graph.model.GraphSnapshot;
import com.cartograph.graph.model.RepositoryRef;
import com.cartograph.graph.model.RepositorySnapshot;
import com.cartograph.ingestion.GitHubUrlNormalizer;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Application orchestration that reuses completed graph snapshots for resolved commits. */
@Service
public final class IndexRepositoryService {
    private static final Logger LOG = LoggerFactory.getLogger(IndexRepositoryService.class);
    private final GitHubUrlNormalizer normalizer;
    private final RepositoryFetcher fetcher;
    private final GraphSnapshotRepository snapshots;
    private final GraphBuilder graphBuilder;
    /**
     * Instance-local per-repository locks: concurrent same-commit indexing
     * performs exactly one resolve/fetch/build/save sequence. Multi-instance
     * deployments need server-side coordination instead (see ADR 0001).
     */
    private final ConcurrentHashMap<String, Object> indexLocks = new ConcurrentHashMap<>();

    /**
     * @param normalizer validates and converts repository URLs into references
     * @param fetcher resolves commits and obtains repository snapshots
     * @param snapshots stores and retrieves completed graph snapshots
     * @param graphBuilder builds a graph from a fetched repository snapshot
     */
    public IndexRepositoryService(
            GitHubUrlNormalizer normalizer,
            RepositoryFetcher fetcher,
            GraphSnapshotRepository snapshots,
            GraphBuilder graphBuilder) {
        this.normalizer = Objects.requireNonNull(normalizer);
        this.fetcher = Objects.requireNonNull(fetcher);
        this.snapshots = Objects.requireNonNull(snapshots);
        this.graphBuilder = Objects.requireNonNull(graphBuilder);
    }

    /**
     * Indexes a repository URL, reusing a cached graph for the resolved commit when available.
     *
     * @param repositoryUrl public repository URL to index
     * @return cached or newly built graph snapshot
     */
    public GraphSnapshot index(String repositoryUrl) {
        return index(normalizer.normalize(repositoryUrl));
    }

    /**
     * Indexes a normalized repository reference at its currently resolved commit.
     *
     * @param ref normalized owner, repository, and requested ref
     * @return cached or newly built graph snapshot
     */
    public GraphSnapshot index(RepositoryRef ref) {
        Objects.requireNonNull(ref, "repository reference");
        Object lock = indexLocks.computeIfAbsent(ref.coordinate(), key -> new Object());
        synchronized (lock) {
            long startedAt = System.nanoTime();
            String commit = fetcher.resolveCommit(ref);
            var cached = snapshots.find(ref.coordinate(), commit);
            GraphSnapshot result = cached.orElseGet(() -> {
                RepositorySnapshot fetched = fetcher.fetchResolved(ref, commit);
                if (fetched == null
                        || !Objects.equals(commit, fetched.commitSha())
                        || !ref.coordinate().equals(fetched.repository())) {
                    throw new IllegalStateException("Fetched repository does not match the resolved commit");
                }
                GraphSnapshot built = graphBuilder.build(fetched);
                snapshots.save(built);
                return built;
            });
            LOG.info(
                    "Indexed repository={} commit={} outcome={} durationMs={}",
                    ref.coordinate(),
                    commit,
                    cached.isPresent() ? "cache-hit" : "built",
                    (System.nanoTime() - startedAt) / 1_000_000);
            return result;
        }
    }

    /** Returns the latest stored snapshot for {@code repository} without touching GitHub. */
    public Optional<GraphSnapshot> snapshot(String repository) {
        Objects.requireNonNull(repository, "repository");
        return snapshots.findLatest(repository);
    }

    /** Lists every indexed repository with its latest commit, newest first. */
    public List<GraphSnapshotRepository.RepositorySummary> repositories() {
        return snapshots.listRepositories();
    }
}
