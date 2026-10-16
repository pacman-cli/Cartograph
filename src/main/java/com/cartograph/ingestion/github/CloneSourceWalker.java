package com.cartograph.ingestion.github;

import com.cartograph.graph.model.GraphWarning;
import com.cartograph.graph.model.RepositoryRef;
import com.cartograph.graph.model.RepositorySnapshot;
import com.cartograph.graph.model.SourceFile;
import com.cartograph.ingestion.IndexingLimits;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** Reads only bounded, supported source files from an isolated clone. */
public final class CloneSourceWalker {
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".git", "node_modules", "vendor", "dist", "build", "target", "coverage",
            "out", "generated", "__pycache__", ".venv", "venv");

    private final IndexingLimits limits;

    public CloneSourceWalker(IndexingLimits limits) {
        this.limits = limits;
    }

    public RepositorySnapshot walk(Path root, RepositoryRef ref, String commitSha) {
        return walk(root, ref.coordinate(), commitSha);
    }

    public RepositorySnapshot walk(Path root, String repository, String commitSha) {
        List<Path> paths;
        try (Stream<Path> stream = Files.walk(root)) {
            paths = stream.filter(path -> !path.equals(root))
                    .filter(this::notIgnored)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
                    .toList();
        } catch (IOException error) {
            throw new CloneFailureException(CloneFailureException.Kind.CLONE_FAILED, error);
        }

        List<SourceFile> files = new ArrayList<>();
        List<GraphWarning> warnings = new ArrayList<>();
        long totalBytes = 0;
        int filesSeen = 0;
        long candidates = 0;
        for (Path path : paths) {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
            filesSeen++;
            String relative = root.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
            String language = language(relative);
            if (language != null && ++candidates > limits.maxFileCount()) {
                throw new com.cartograph.ingestion.RepositoryLimitException(
                        IndexingLimits.Limit.FILE_COUNT, candidates, limits.maxFileCount());
            }
            if (language == null) {
                warnings.add(new GraphWarning(isPython(relative) ? "UNSUPPORTED_LANGUAGE" : "UNSUPPORTED_FILE",
                        isPython(relative) ? "Python source is not supported and was skipped"
                                : "File type is not supported and was skipped", relative, null));
                continue;
            }
            try {
                long size = Files.size(path);
                limits.validateFetchedContent(size, totalBytes);
                byte[] content = Files.readAllBytes(path);
                totalBytes = limits.validateFetchedContent(content.length, totalBytes);
                files.add(new SourceFile(relative, new String(content, StandardCharsets.UTF_8), language));
            } catch (IOException error) {
                throw new CloneFailureException(CloneFailureException.Kind.CLONE_FAILED, error);
            }
        }
        return new RepositorySnapshot(repository, commitSha, files, warnings, filesSeen);
    }

    private boolean notIgnored(Path path) {
        for (Path segment : path) if (IGNORED_DIRECTORIES.contains(segment.toString())) return false;
        return true;
    }

    private static boolean isPython(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".py") || lower.endsWith(".pyw");
    }

    private static String language(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".js") || lower.endsWith(".jsx")) return "javascript";
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) return "typescript";
        return null;
    }
}
