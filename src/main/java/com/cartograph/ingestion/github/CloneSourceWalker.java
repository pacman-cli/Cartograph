package com.cartograph.ingestion.github;

import com.cartograph.graph.model.GraphWarning;
import com.cartograph.graph.model.RepositoryRef;
import com.cartograph.graph.model.RepositorySnapshot;
import com.cartograph.graph.model.SourceFile;
import com.cartograph.ingestion.IndexingLimits;
import com.cartograph.ingestion.RepositoryLimitException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
        Path normalizedRoot = root.toAbsolutePath().normalize();
        List<SourceFile> files = new ArrayList<>();
        List<GraphWarning> warnings = new ArrayList<>();
        WalkState state = new WalkState(normalizedRoot, files, warnings);
        try {
            Files.walkFileTree(normalizedRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (!directory.equals(normalizedRoot)
                            && (Files.isSymbolicLink(directory) || isIgnoredDirectory(directory))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) {
                    if (!Files.isSymbolicLink(path) && attributes.isRegularFile()) {
                        visitRegularFile(path, state);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path path, IOException error) {
                    throw new CloneFailureException(CloneFailureException.Kind.CLONE_FAILED, error);
                }
            });
        } catch (IOException error) {
            throw new CloneFailureException(CloneFailureException.Kind.CLONE_FAILED, error);
        }
        files.sort(Comparator.comparing(SourceFile::path));
        return new RepositorySnapshot(repository, commitSha, files, warnings, state.filesSeen);
    }

    private void visitRegularFile(Path path, WalkState state) {
        state.filesSeen++;
        String relative = state.root.relativize(path).toString()
                .replace(path.getFileSystem().getSeparator(), "/");
        String language = language(relative);
        if (language == null) {
            state.warnings.add(new GraphWarning(isPython(relative) ? "UNSUPPORTED_LANGUAGE" : "UNSUPPORTED_FILE",
                    isPython(relative) ? "Python source is not supported and was skipped"
                            : "File type is not supported and was skipped", relative, null));
            return;
        }
        long candidate = state.candidates + 1;
        if (candidate > limits.maxFileCount()) {
            throw new RepositoryLimitException(IndexingLimits.Limit.FILE_COUNT, candidate, limits.maxFileCount());
        }
        state.candidates = candidate;
        try {
            long size = Files.size(path);
            limits.validateFetchedContent(size, state.totalBytes);
            byte[] content = readBounded(path, limits.maxFileBytes());
            state.totalBytes = limits.validateFetchedContent(content.length, state.totalBytes);
            state.files.add(new SourceFile(relative, new String(content, StandardCharsets.UTF_8), language));
        } catch (IOException error) {
            throw new CloneFailureException(CloneFailureException.Kind.CLONE_FAILED, error);
        }
    }

    private static byte[] readBounded(Path path, long maximumBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(maximumBytes, 8192));
        byte[] buffer = new byte[8192];
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            int read;
            long total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maximumBytes) {
                    throw new RepositoryLimitException(IndexingLimits.Limit.FILE_BYTES, total, maximumBytes);
                }
                output.write(buffer, 0, read);
            }
        }
        return output.toByteArray();
    }

    private boolean isIgnoredDirectory(Path path) {
        Path name = path.getFileName();
        return name != null && IGNORED_DIRECTORIES.contains(name.toString());
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

    private static final class WalkState {
        private final Path root;
        private final List<SourceFile> files;
        private final List<GraphWarning> warnings;
        private long totalBytes;
        private long candidates;
        private int filesSeen;

        private WalkState(Path root, List<SourceFile> files, List<GraphWarning> warnings) {
            this.root = root;
            this.files = files;
            this.warnings = warnings;
        }
    }
}
