package com.cartograph.persistence.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import org.sqlite.SQLiteDataSource;

import com.cartograph.graph.model.EdgeKind;
import com.cartograph.graph.model.GraphEdge;
import com.cartograph.graph.model.GraphMetrics;
import com.cartograph.graph.model.GraphNode;
import com.cartograph.graph.model.GraphSnapshot;
import com.cartograph.graph.model.GraphWarning;
import com.cartograph.graph.model.SourceLocation;
import com.cartograph.graph.model.SymbolKind;

class SQLiteGraphSnapshotRepositoryTest {
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

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM repository_snapshot", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM graph_node", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM graph_edge", Integer.class)).isEqualTo(1);
    }

    @Test
    void deletingSnapshotCascadesNodesAndEdges() {
        repository.save(snapshot("acme/app", "abc123"));
        jdbc.update("DELETE FROM repository_snapshot WHERE repository = ? AND commit_sha = ?", "acme/app", "abc123");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM graph_node", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM graph_edge", Integer.class)).isZero();
    }

    @Test
    void preservesWarningsMetricsConfidenceAndEdgeLocation() {
        GraphSnapshot expected = snapshot("acme/app", "abc123");

        repository.save(expected);

        GraphSnapshot actual = repository.find("acme/app", "abc123").orElseThrow();
        assertThat(actual.metrics()).isEqualTo(new GraphMetrics(2, 1, 1, 2));
        assertThat(actual.warnings()).containsExactly(new GraphWarning("DYNAMIC_CALL", "cannot resolve", "src/a.ts", 8));
        assertThat(actual.edges().get(0).confidence()).isEqualTo(0.75);
        assertThat(actual.edges().get(0).location()).isEqualTo(new SourceLocation("src/a.ts", 8, 2, 8, 7));
    }

    private static GraphSnapshot snapshot(String repository, String commit) {
        GraphNode from = new GraphNode("n1", SymbolKind.FUNCTION, "load", "src/a.ts", 1, 3, 0, 4);
        GraphNode to = new GraphNode("n2", SymbolKind.FUNCTION, "save", "src/a.ts", 5, 7, 0, 4);
        GraphEdge edge = new GraphEdge("n1", "n2", EdgeKind.CALLS, 0.75, new SourceLocation("src/a.ts", 8, 2, 8, 7));
        return new GraphSnapshot(repository, commit, List.of(from, to), List.of(edge),
                List.of(new GraphWarning("DYNAMIC_CALL", "cannot resolve", "src/a.ts", 8)),
                new GraphMetrics(2, 1, 1, 2));
    }
}
