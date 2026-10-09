package com.cartograph.persistence.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import com.cartograph.graph.model.EdgeKind;
import com.cartograph.graph.model.GraphEdge;
import com.cartograph.graph.model.GraphMetrics;
import com.cartograph.graph.model.GraphNode;
import com.cartograph.graph.model.GraphSnapshot;
import com.cartograph.graph.model.GraphWarning;
import com.cartograph.graph.model.SourceLocation;
import com.cartograph.graph.model.SymbolKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

class SQLiteGraphSnapshotRepositoryTest {
    @org.junit.jupiter.api.Test
    void prunesOldSnapshotsBeyondRetentionPerRepository() {
        SQLiteGraphSnapshotRepository limited =
                new SQLiteGraphSnapshotRepository(dataSource, new com.fasterxml.jackson.databind.ObjectMapper(), 2);
        limited.save(snapshot("acme/app", "sha-1"));
        limited.save(snapshot("acme/app", "sha-2"));
        limited.save(snapshot("acme/app", "sha-3"));
        limited.save(snapshot("acme/other", "sha-9"));

        assertThat(limited.find("acme/app", "sha-1")).isEmpty();
        assertThat(limited.find("acme/app", "sha-2")).isPresent();
        assertThat(limited.find("acme/app", "sha-3")).isPresent();
        assertThat(limited.findLatest("acme/app")).isPresent();
        assertThat(limited.find("acme/other", "sha-9")).isPresent();
        var orphanedNodes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM graph_node WHERE snapshot_id NOT IN (SELECT id FROM repository_snapshot)",
                Integer.class);
        assertThat(orphanedNodes).isZero();
    }

    @org.junit.jupiter.api.Test
    void nonPositiveRetentionKeepsEverything() {
        SQLiteGraphSnapshotRepository unlimited =
                new SQLiteGraphSnapshotRepository(dataSource, new com.fasterxml.jackson.databind.ObjectMapper(), 0);
        for (int i = 0; i < 5; i++) {
            unlimited.save(snapshot("acme/app", "sha-" + i));
        }
        assertThat(unlimited.find("acme/app", "sha-0")).isPresent();
        assertThat(unlimited.find("acme/app", "sha-4")).isPresent();
    }

    @org.junit.jupiter.api.Test
    void defaultConstructorKeepsTenSnapshotsPerRepository() {
        for (int i = 0; i < 12; i++) {
            repository.save(snapshot("acme/app", "sha-" + i));
        }
        var remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM repository_snapshot WHERE repository = ?", Integer.class, "acme/app");
        assertThat(remaining).isEqualTo(10);
    }

    private SQLiteDataSource dataSource;
    private SQLiteGraphSnapshotRepository repository;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        dataSource = new SQLiteDataSource();
        dataSource.setEnforceForeignKeys(true);
        try {
            Path database = Files.createTempFile("cartograph-", ".db");
            dataSource.setUrl("jdbc:sqlite:" + database);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        repository = new SQLiteGraphSnapshotRepository(dataSource);
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void roundTripsCompleteSnapshot() {
        GraphSnapshot expected = snapshot("acme/app", "abc123");
        repository.save(expected);

        assertThat(repository.find("acme/app", "abc123")).contains(expected);
    }

    @Test
    void returnsMostRecentlySavedSnapshotPerRepository() {
        repository.save(snapshot("acme/app", "sha-1"));
        GraphSnapshot later = snapshot("acme/app", "sha-2");
        repository.save(later);
        repository.save(snapshot("acme/other", "sha-9"));

        assertThat(repository.findLatest("acme/app")).contains(later);
    }

    @Test
    void findLatestIsEmptyForUnknownRepository() {
        assertThat(repository.findLatest("never/indexed")).isEmpty();
    }

    @Test
    void missingSnapshotIsEmpty() {
        assertThat(repository.find("missing", "commit")).isEmpty();
    }

    @Test
    void duplicateWriteReplacesRowsWithoutDuplicates() {
        repository.save(snapshot("acme/app", "abc123"));
        repository.save(snapshot("acme/app", "abc123"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM repository_snapshot", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM graph_node", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM graph_edge", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void deletingSnapshotCascadesNodesAndEdges() {
        repository.save(snapshot("acme/app", "abc123"));
        jdbc.update("DELETE FROM repository_snapshot WHERE repository = ? AND commit_sha = ?", "acme/app", "abc123");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM graph_node", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM graph_edge", Integer.class))
                .isZero();
    }

    @Test
    void preservesWarningsMetricsConfidenceAndEdgeLocation() {
        GraphSnapshot expected = snapshot("acme/app", "abc123");

        repository.save(expected);

        GraphSnapshot actual = repository.find("acme/app", "abc123").orElseThrow();
        assertThat(actual.metrics()).isEqualTo(new GraphMetrics(2, 1, 1, 2));
        assertThat(actual.warnings())
                .containsExactly(new GraphWarning("DYNAMIC_CALL", "cannot resolve", "src/a.ts", 8));
        assertThat(actual.edges().get(0).confidence()).isEqualTo(0.75);
        assertThat(actual.edges().get(0).location()).isEqualTo(new SourceLocation("src/a.ts", 8, 2, 8, 7));
    }

    private static GraphSnapshot snapshot(String repository, String commit) {
        GraphNode from = new GraphNode("n1", SymbolKind.FUNCTION, "load", "src/a.ts", 1, 3, 0, 4);
        GraphNode to = new GraphNode("n2", SymbolKind.FUNCTION, "save", "src/a.ts", 5, 7, 0, 4);
        GraphEdge edge = new GraphEdge("n1", "n2", EdgeKind.CALLS, 0.75, new SourceLocation("src/a.ts", 8, 2, 8, 7));
        return new GraphSnapshot(
                repository,
                commit,
                List.of(from, to),
                List.of(edge),
                List.of(new GraphWarning("DYNAMIC_CALL", "cannot resolve", "src/a.ts", 8)),
                new GraphMetrics(2, 1, 1, 2));
    }

    @org.junit.jupiter.api.Test
    void listsEveryRepositoryWithItsLatestCommitNewestFirst() {
        repository.save(snapshot("acme/app", "sha-1"));
        repository.save(snapshot("acme/app", "sha-2"));
        repository.save(snapshot("acme/other", "sha-9"));

        var summaries = repository.listRepositories();

        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(0).repository()).isEqualTo("acme/other");
        assertThat(summaries.get(0).commitSha()).isEqualTo("sha-9");
        assertThat(summaries.get(1).repository()).isEqualTo("acme/app");
        assertThat(summaries.get(1).commitSha()).isEqualTo("sha-2");
    }

    @org.junit.jupiter.api.Test
    void listRepositoriesIsEmptyOnFreshStore() {
        assertThat(repository.listRepositories()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void concurrentWritersForDistinctRepositoriesAllSucceed() throws Exception {
        org.sqlite.SQLiteDataSource wal = new org.sqlite.SQLiteDataSource();
        wal.setEnforceForeignKeys(true);
        Path database = java.nio.file.Files.createTempFile("cartograph-wal-", ".db");
        wal.setUrl("jdbc:sqlite:" + database + "?journal_mode=wal&busy_timeout=5000");
        SQLiteGraphSnapshotRepository walRepository = new SQLiteGraphSnapshotRepository(wal);

        int threads = 4;
        int rounds = 5;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        var start = new java.util.concurrent.CountDownLatch(1);
        var errors = new java.util.concurrent.ConcurrentLinkedQueue<Exception>();
        try {
            var futures = java.util.stream.IntStream.range(0, threads)
                    .mapToObj(t -> pool.submit(() -> {
                        start.await();
                        for (int round = 0; round < rounds; round++) {
                            try {
                                walRepository.save(snapshot("wal/repo-" + t, "sha-" + round));
                            } catch (Exception e) {
                                errors.add(e);
                            }
                        }
                        return null;
                    }))
                    .toList();
            start.countDown();
            for (var future : futures) future.get(30, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(errors).isEmpty();
            for (int t = 0; t < threads; t++) {
                for (int round = 0; round < rounds; round++) {
                    assertThat(walRepository.find("wal/repo-" + t, "sha-" + round))
                            .isPresent();
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
