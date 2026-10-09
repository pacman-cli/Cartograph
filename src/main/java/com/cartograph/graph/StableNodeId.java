package com.cartograph.graph;

import com.cartograph.graph.model.SymbolKind;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Creates deterministic node identifiers from repository and source identity. */
public final class StableNodeId {
    private StableNodeId() {}

    public static String create(
            String repository,
            String commitSha,
            String path,
            SymbolKind kind,
            String name,
            int startLine,
            int startColumn) {
        String value = String.join(
                "\u001f",
                repository,
                commitSha,
                normalizePath(path),
                kind.name(),
                name,
                Integer.toString(startLine),
                Integer.toString(startColumn));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder("n_");
            for (byte b : digest) result.append(String.format("%02x", b));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String normalizePath(String path) {
        return path.replace('\\', '/').replaceAll("(^|/)\\./", "$1");
    }
}
