package com.cartograph.ingestion.github;

import com.cartograph.graph.model.RepositoryRef;
import com.cartograph.graph.model.RepositorySnapshot;
import java.util.Objects;

/** Chooses REST or shallow-clone materialization after inspecting the resolved commit tree. */
public final class RepositorySourceStrategy implements RepositorySourceFetcher {
    @FunctionalInterface
    public interface TreeInspector {
        TreeInspection inspect(RepositoryRef ref, String resolvedCommitSha);
    }

    public record TreeInspection(long fileCount, boolean truncated) {
        public TreeInspection {
            if (fileCount < 0) throw new IllegalArgumentException("Tree file count must not be negative");
        }
    }

    private final RepositorySourceFetcher rest;
    private final RepositorySourceFetcher clone;
    private final CloneProperties properties;
    private final TreeInspector inspector;

    public RepositorySourceStrategy(
            GitHubRepositoryFetcher rest, RepositorySourceFetcher clone, CloneProperties properties) {
        this(rest, clone, properties, rest::inspectTree);
    }

    public RepositorySourceStrategy(
            RepositorySourceFetcher rest,
            RepositorySourceFetcher clone,
            CloneProperties properties,
            TreeInspector inspector) {
        this.rest = Objects.requireNonNull(rest, "rest fetcher");
        this.clone = Objects.requireNonNull(clone, "clone fetcher");
        this.properties = Objects.requireNonNull(properties, "clone properties");
        this.inspector = Objects.requireNonNull(inspector, "tree inspector");
    }

    @Override
    public RepositorySnapshot fetch(RepositoryRef ref, String resolvedCommitSha) {
        Objects.requireNonNull(ref, "repository reference");
        requireSha(resolvedCommitSha);
        TreeInspection tree = Objects.requireNonNull(inspector.inspect(ref, resolvedCommitSha), "tree inspection");
        boolean requiresClone = tree.truncated() || tree.fileCount() >= properties.fileThreshold();
        if (!requiresClone) return rest.fetch(ref, resolvedCommitSha);
        if (!properties.enabled()) throw new CloneFailureException(CloneFailureException.Kind.CLONE_DISABLED);
        return clone.fetch(ref, resolvedCommitSha);
    }

    private static void requireSha(String sha) {
        if (sha == null || sha.isBlank()) throw new IllegalArgumentException("Resolved commit must not be blank");
    }
}
