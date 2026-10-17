package com.cartograph.persistence.sqlite;

import com.cartograph.graph.model.EdgeKind;
import com.cartograph.graph.model.GraphEdge;
import com.cartograph.graph.model.GraphNode;
import com.cartograph.graph.model.SourceLocation;
import com.cartograph.graph.model.SymbolKind;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;

final class GraphSnapshotRowMapper {
    static final RowMapper<GraphNode> NODE = (rs, rowNum) -> new GraphNode(
            rs.getString("stable_id"),
            SymbolKind.valueOf(rs.getString("kind")),
            rs.getString("name"),
            rs.getString("file_path"),
            rs.getInt("start_line"),
            rs.getInt("end_line"),
            rs.getInt("start_column"),
            rs.getInt("end_column"));

    static final RowMapper<GraphEdge> EDGE = (rs, rowNum) -> new GraphEdge(
            rs.getString("from_id"),
            rs.getString("to_id"),
            EdgeKind.valueOf(rs.getString("kind")),
            rs.getDouble("confidence"),
            location(rs));

    private static SourceLocation location(ResultSet rs) throws SQLException {
        String path = rs.getString("file_path");
        if (path == null) return null;
        return new SourceLocation(
                path,
                rs.getInt("start_line"),
                rs.getInt("start_column"),
                rs.getInt("end_line"),
                rs.getInt("end_column"));
    }

    private GraphSnapshotRowMapper() {}
}
