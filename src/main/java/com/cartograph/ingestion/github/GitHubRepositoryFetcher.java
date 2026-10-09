package com.cartograph.ingestion.github;

import com.cartograph.application.RepositoryFetcher;
import com.cartograph.graph.model.GraphWarning;
import com.cartograph.graph.model.RepositoryRef;
import com.cartograph.graph.model.RepositorySnapshot;
import com.cartograph.graph.model.SourceFile;
import com.cartograph.ingestion.IndexingLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Fetches repository metadata and source files through the GitHub REST client. */
public class GitHubRepositoryFetcher implements RepositoryFetcher {
    private final GitHubClient client;
    private final IndexingLimits limits;

    public GitHubRepositoryFetcher(GitHubClient client, IndexingLimits limits) {
        this.client = client;
        this.limits = limits;
    }

    public GitHubRepositoryFetcher(GitHubClient client, GitHubProperties properties) {
        this(client, properties.limits());
    }

    @Override
    public String resolveCommit(RepositoryRef ref) {
        String requested = ref.ref();
        if (requested == null || requested.isBlank()) {
            requested = requireBody(client.repository(ref)).default_branch();
            validateBranch(requested);
        }
        String sha = requireBody(client.commit(ref, requested)).sha();
        validateSha(sha);
        return sha;
    }

    @Override
    public RepositorySnapshot fetch(RepositoryRef ref) {
        return fetchResolved(ref, resolveCommit(ref));
    }

    @Override
    public RepositorySnapshot fetchResolved(RepositoryRef ref, String sha) {
        validateSha(sha);
        GitHubClient.TreeDto tree = requireBody(client.tree(ref, sha));
        if (Boolean.TRUE.equals(tree.truncated()))
            throw new GitHubFetchException(
                    GitHubFetchException.Kind.UPSTREAM, 200, "GitHub tree response was truncated");
        if (tree.truncated() == null || tree.tree() == null) throw invalidResponse();
        for (GitHubClient.TreeEntry entry : tree.tree()) {
            if (entry == null
                    || !validPath(entry.path())
                    || !("blob".equals(entry.type()) || "tree".equals(entry.type()) || "commit".equals(entry.type()))
                    || ("blob".equals(entry.type()) && (entry.size() == null || entry.size() < 0))) {
                throw invalidResponse();
            }
        }

        List<GitHubClient.TreeEntry> blobs = tree.tree().stream()
                .filter(entry -> "blob".equals(entry.type()))
                .toList();
        List<GitHubClient.TreeEntry> candidates =
                blobs.stream().filter(entry -> supported(entry.path())).toList();
        limits.validateFetchedTree(
                candidates.stream().map(GitHubClient.TreeEntry::size).toList());
        List<SourceFile> files = new ArrayList<>(candidates.size());
        long downloadedBytes = 0;
        for (GitHubClient.TreeEntry entry : candidates) {
            byte[] contentBytes =
                    requireBody(client.content(ref, entry.path(), sha, null)).decodedBytes();
            downloadedBytes = limits.validateFetchedContent(contentBytes.length, downloadedBytes);
            files.add(new SourceFile(
                    entry.path(),
                    new String(contentBytes, java.nio.charset.StandardCharsets.UTF_8),
                    language(entry.path())));
        }
        List<GraphWarning> warnings = blobs.stream()
                .filter(entry -> !supported(entry.path()))
                .map(entry -> new GraphWarning(
                        "UNSUPPORTED_FILE", "File type is not supported and was skipped", entry.path(), null))
                .toList();
        return new RepositorySnapshot(ref.coordinate(), sha, files, warnings, blobs.size());
    }

    private static <T> T requireBody(GitHubClient.Response<T> response) {
        if (response == null || response.body() == null) throw invalidResponse();
        return response.body();
    }

    private static void validateSha(String sha) {
        if (sha == null || !sha.matches("[0-9a-fA-F]{40}")) throw invalidResponse();
    }

    private static void validateBranch(String branch) {
        if (!validPath(branch)
                || branch.contains("..")
                || branch.contains("@{")
                || branch.endsWith(".")
                || branch.equals("@")
                || branch.chars().anyMatch(c -> Character.isWhitespace(c) || "~^:?*[".indexOf(c) >= 0)) {
            throw invalidResponse();
        }
        for (String segment : branch.split("/")) {
            if (segment.startsWith(".") || segment.endsWith(".lock")) throw invalidResponse();
        }
    }

    private static boolean validPath(String path) {
        if (path == null
                || path.isBlank()
                || path.contains("\\")
                || path.chars().anyMatch(Character::isISOControl)) return false;
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) return false;
        }
        return true;
    }

    private static GitHubFetchException invalidResponse() {
        return new GitHubFetchException(GitHubFetchException.Kind.UPSTREAM, 200, "Invalid GitHub response");
    }

    private static boolean supported(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".js") || lower.endsWith(".jsx") || lower.endsWith(".ts") || lower.endsWith(".tsx");
    }

    private static String language(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return (lower.endsWith(".ts") || lower.endsWith(".tsx")) ? "typescript" : "javascript";
    }
}
