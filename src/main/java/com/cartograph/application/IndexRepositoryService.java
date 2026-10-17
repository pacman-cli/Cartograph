package com.cartograph.application;

import com.cartograph.graph.GraphBuilder;
import com.cartograph.graph.model.GraphSnapshot;
import com.cartograph.graph.model.RepositoryRef;
import com.cartograph.graph.model.RepositorySnapshot;
import com.cartograph.ingestion.GitHubUrlNormalizer;
import com.cartograph.ingestion.github.RepositorySourceFetcher;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Application orchestration that reuses completed graph snapshots for resolved commits. */
@Service
public final class IndexRepositoryService {
    private static final Logger LOG = LoggerFactory.getLogger(IndexRepositoryService.class);
    private final GitHubUrlNormalizer normalizer;
    private final RepositoryFetcher fetcher;
    private final RepositorySourceFetcher sourceFetcher;
    private final GraphSnapshotRepository snapshots;
    private final GraphBuilder graphBuilder;
    private final ConcurrentHashMap<String, Object> indexLocks = new ConcurrentHashMap<>();

    public IndexRepositoryService(
            GitHubUrlNormalizer normalizer,
            RepositoryFetcher fetcher,
            GraphSnapshotRepository snapshots,
            GraphBuilder graphBuilder) {
        this(normalizer, fetcher, fetcher::fetchResolved, snapshots, graphBuilder);
    }

    @Autowired
    public IndexRepositoryService(
            GitHubUrlNormalizer normalizer,
            RepositoryFetcher fetcher,
            @Qualifier("repositorySourceFetcher") RepositorySourceFetcher sourceFetcher,
            GraphSnapshotRepository snapshots,
            GraphBuilder graphBuilder) {
        this.normalizer = Objects.requireNonNull(normalizer);
        this.fetcher = Objects.requireNonNull(fetcher);
        this.sourceFetcher = Objects.requireNonNull(sourceFetcher);
        this.snapshots = Objects.requireNonNull(snapshots);
        this.graphBuilder = Objects.requireNonNull(graphBuilder);
    }

    public GraphSnapshot index(String repositoryUrl) {
        return index(normalizer.normalize(repositoryUrl));
    }

    public GraphSnapshot index(RepositoryRef ref) {
        Objects.requireNonNull(ref, "repository reference");
        Object lock = indexLocks.computeIfAbsent(ref.coordinate(), key -> new Object());
        synchronized (lock) {
            long startedAt = System.nanoTime();
            String commit = fetcher.resolveCommit(ref);
            var cached = snapshots.find(ref.coordinate(), commit);
            GraphSnapshot result = cached.orElseGet(() -> {
                RepositorySnapshot fetched = sourceFetcher.fetch(ref, commit);
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

    public Optional<GraphSnapshot> snapshot(String repository) {
        Objects.requireNonNull(repository, "repository");
        return snapshots.findLatest(repository);
    }

    public List<GraphSnapshotRepository.RepositorySummary> repositories() {
        return snapshots.listRepositories();
    }
}
