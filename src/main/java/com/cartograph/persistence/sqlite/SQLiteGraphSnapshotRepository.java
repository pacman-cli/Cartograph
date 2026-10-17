package com.cartograph.persistence.sqlite;

import com.cartograph.application.GraphSnapshotRepository;
import com.cartograph.graph.model.GraphEdge;
import com.cartograph.graph.model.GraphMetrics;
import com.cartograph.graph.model.GraphNode;
import com.cartograph.graph.model.GraphSnapshot;
import com.cartograph.graph.model.GraphWarning;
import com.cartograph.graph.model.SourceLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

/** SQLite adapter. Schema setup is explicit because Flyway is disabled for SQLite. */
public final class SQLiteGraphSnapshotRepository implements GraphSnapshotRepository {
    private static final String SCHEMA = "db/migration/V1__create_graph_snapshot_tables.sql";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper objectMapper;
    private final int retentionPerRepo;

    public SQLiteGraphSnapshotRepository(DataSource dataSource) {
        this(dataSource, new ObjectMapper(), 10);
    }

    SQLiteGraphSnapshotRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this(dataSource, objectMapper, 10);
    }

    /**
     * @param retentionPerRepo snapshots kept per repository; zero or negative disables retention
     */
    public SQLiteGraphSnapshotRepository(DataSource dataSource, ObjectMapper objectMapper, int retentionPerRepo) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.objectMapper = objectMapper;
        this.retentionPerRepo = retentionPerRepo;
        initializeSchema(dataSource);
    }

    @Override
    public Optional<GraphSnapshot> find(String repository, String commitSha) {
        List<SnapshotRecord> records = jdbc.query(
                "SELECT id, repository, commit_sha, metrics_json, warnings_json FROM repository_snapshot "
                        + "WHERE repository = ? AND commit_sha = ?",
                (rs, n) -> new SnapshotRecord(
                        rs.getLong("id"),
                        rs.getString("repository"),
                        rs.getString("commit_sha"),
                        rs.getString("metrics_json"),
                        rs.getString("warnings_json")),
                repository,
                commitSha);
        if (records.isEmpty()) return Optional.empty();
        return Optional.of(loadSnapshot(records.get(0)));
    }

    @Override
    public Optional<GraphSnapshot> findLatest(String repository) {
        List<SnapshotRecord> records = jdbc.query(
                "SELECT id, repository, commit_sha, metrics_json, warnings_json FROM repository_snapshot "
                        + "WHERE repository = ? ORDER BY indexed_at DESC, id DESC LIMIT 1",
                (rs, n) -> new SnapshotRecord(
                        rs.getLong("id"),
                        rs.getString("repository"),
                        rs.getString("commit_sha"),
                        rs.getString("metrics_json"),
                        rs.getString("warnings_json")),
                repository);
        if (records.isEmpty()) return Optional.empty();
        return Optional.of(loadSnapshot(records.get(0)));
    }

    @Override
    public List<GraphSnapshotRepository.RepositorySummary> listRepositories() {
        return jdbc.query(
                "SELECT rs.repository, rs.commit_sha, rs.indexed_at FROM repository_snapshot rs "
                        + "WHERE rs.id = (SELECT MAX(r2.id) FROM repository_snapshot r2 WHERE r2.repository = rs.repository) "
                        + "ORDER BY rs.indexed_at DESC, rs.id DESC",
                (rs, n) -> new GraphSnapshotRepository.RepositorySummary(
                        rs.getString("repository"), rs.getString("commit_sha"), rs.getString("indexed_at")));
    }

    private GraphSnapshot loadSnapshot(SnapshotRecord record) {
        List<GraphNode> nodes = jdbc.query(
                "SELECT * FROM graph_node WHERE snapshot_id = ? ORDER BY id", GraphSnapshotRowMapper.NODE, record.id());
        List<GraphEdge> edges = jdbc.query(
                "SELECT * FROM graph_edge WHERE snapshot_id = ? ORDER BY id", GraphSnapshotRowMapper.EDGE, record.id());
        return new GraphSnapshot(
                record.repository(),
                record.commitSha(),
                nodes,
                edges,
                read(record.warningsJson(), new TypeReference<List<GraphWarning>>() {}),
                read(record.metricsJson(), GraphMetrics.class));
    }

    @Override
    public void save(GraphSnapshot snapshot) {
        transaction.executeWithoutResult(status -> {
            jdbc.execute("PRAGMA foreign_keys = ON");
            jdbc.update(
                    "DELETE FROM graph_node WHERE snapshot_id IN (SELECT id FROM repository_snapshot WHERE repository = ? AND commit_sha = ?)",
                    snapshot.repository(),
                    snapshot.commitSha());
            jdbc.update(
                    "DELETE FROM graph_edge WHERE snapshot_id IN (SELECT id FROM repository_snapshot WHERE repository = ? AND commit_sha = ?)",
                    snapshot.repository(),
                    snapshot.commitSha());
            jdbc.update(
                    "DELETE FROM repository_snapshot WHERE repository = ? AND commit_sha = ?",
                    snapshot.repository(),
                    snapshot.commitSha());
            long snapshotId = jdbc.queryForObject(
                    "INSERT INTO repository_snapshot(repository, commit_sha, metrics_json, warnings_json) VALUES (?, ?, ?, ?) RETURNING id",
                    Long.class,
                    snapshot.repository(),
                    snapshot.commitSha(),
                    write(snapshot.metrics()),
                    write(snapshot.warnings()));
            for (GraphNode node : snapshot.nodes()) {
                jdbc.update(
                        "INSERT INTO graph_node(snapshot_id, stable_id, kind, name, file_path, start_line, end_line, start_column, end_column) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        snapshotId,
                        node.stableId(),
                        node.kind().name(),
                        node.name(),
                        node.filePath(),
                        node.startLine(),
                        node.endLine(),
                        node.startColumn(),
                        node.endColumn());
            }
            for (GraphEdge edge : snapshot.edges()) {
                SourceLocation location = edge.location();
                jdbc.update(
                        "INSERT INTO graph_edge(snapshot_id, from_id, to_id, kind, confidence, file_path, start_line, start_column, end_line, end_column) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        snapshotId,
                        edge.fromId(),
                        edge.toId(),
                        edge.kind().name(),
                        edge.confidence(),
                        location == null ? null : location.filePath(),
                        location == null ? null : location.startLine(),
                        location == null ? null : location.startColumn(),
                        location == null ? null : location.endLine(),
                        location == null ? null : location.endColumn());
            }
            if (retentionPerRepo > 0) {
                pruneOldSnapshots(snapshot.repository());
            }
        });
    }

    private void pruneOldSnapshots(String repository) {
        jdbc.update(
                "DELETE FROM repository_snapshot WHERE repository = ? AND id NOT IN "
                        + "(SELECT id FROM repository_snapshot WHERE repository = ? ORDER BY indexed_at DESC, id DESC LIMIT ?)",
                repository,
                repository,
                retentionPerRepo);
    }

    private void initializeSchema(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("PRAGMA foreign_keys = ON");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(SCHEMA));
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to initialize SQLite schema", e);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize snapshot", e);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read snapshot", e);
        }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read snapshot", e);
        }
    }

    private record SnapshotRecord(
            long id, String repository, String commitSha, String metricsJson, String warningsJson) {}
}
