package com.cartograph.application;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.cartograph.graph.GraphBuilder;
import com.cartograph.graph.model.GraphMetrics;
import com.cartograph.graph.model.GraphSnapshot;
import com.cartograph.graph.model.RepositoryRef;
import com.cartograph.graph.model.RepositorySnapshot;
import com.cartograph.graph.model.SourceFile;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Same-commit concurrent indexing must fetch exactly once and never race the cache. */
class ConcurrentIndexingTest {
    private final GraphSnapshot graphSnapshot = new GraphSnapshot(
            "acme/widgets", "sha-1", List.of(), List.of(), List.of(), new GraphMetrics(0, 0, 0, 1, 1));

    @Test
    void concurrentSameCommitIndexingFetchesExactlyOnce() throws Exception {
        AtomicInteger fetches = new AtomicInteger();
        CountDownLatch releaseFetch = new CountDownLatch(1);
        ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

        RepositoryFetcher fetcher = new RepositoryFetcher() {
            @Override
            public String resolveCommit(RepositoryRef ref) {
                return "sha-1";
            }

            @Override
            public RepositorySnapshot fetch(RepositoryRef ref) {
                fetches.incrementAndGet();
                try {
                    releaseFetch.await(5, SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new RepositorySnapshot(
                        "acme/widgets",
                        "sha-1",
                        List.of(new SourceFile("src/App.ts", "export const x = 1;", "typescript")));
            }
        };
        SourceParser parser = file -> new com.cartograph.graph.model.ParsedFile(file.path(), List.of(), List.of());
        GraphSnapshotRepository repository = new GraphSnapshotRepository() {
            private final ConcurrentHashMap<String, GraphSnapshot> store = new ConcurrentHashMap<>();

            @Override
            public java.util.Optional<GraphSnapshot> find(String repository, String commitSha) {
                return java.util.Optional.ofNullable(store.get(repository + "@" + commitSha));
            }

            @Override
            public java.util.Optional<GraphSnapshot> findLatest(String repositoryName) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.List<RepositorySummary> listRepositories() {
                return java.util.List.of();
            }

            @Override
            public void save(GraphSnapshot snapshot) {
                store.put(snapshot.repository() + "@" + snapshot.commitSha(), snapshot);
            }
        };
        GraphBuilder builder = new GraphBuilder(parser);
        IndexRepositoryService service = new IndexRepositoryService(
                new com.cartograph.ingestion.GitHubUrlNormalizer(), fetcher, repository, builder);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<GraphSnapshot> first = pool.submit(() -> service.index("https://github.com/acme/widgets"));
            Future<GraphSnapshot> second = pool.submit(() -> service.index("https://github.com/acme/widgets"));

            long deadline = System.nanoTime() + SECONDS.toNanos(5);
            while (fetches.get() < 1 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertThat(fetches.get()).as("first caller fetches").isEqualTo(1);
            releaseFetch.countDown();

            GraphSnapshot a = first.get(5, SECONDS);
            GraphSnapshot b = second.get(5, SECONDS);

            assertThat(a).isSameAs(b);
            assertThat(fetches.get())
                    .as("exactly one upstream fetch for the same commit")
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, SECONDS);
        }
    }
}
