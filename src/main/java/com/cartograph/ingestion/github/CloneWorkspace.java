package com.cartograph.ingestion.github;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Owns an isolated temporary clone root and its bounded, symlink-safe cleanup. */
public final class CloneWorkspace implements AutoCloseable {
    private final Path root;
    private final Path realRoot;
    private final AtomicBoolean cleaned = new AtomicBoolean();

    private CloneWorkspace(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        this.realRoot = this.root.toRealPath();
    }

    public static CloneWorkspace create() {
        try {
            return new CloneWorkspace(Files.createTempDirectory("cartograph-clone-"));
        } catch (IOException error) {
            throw new CloneFailureException(CloneFailureException.Kind.CLONE_UNAVAILABLE, error);
        }
    }

    public Path root() {
        return root;
    }

    /** Returns the absent destination Git clone is expected to create. */
    public Path destination() {
        Path destination = resolve("repository");
        if (Files.exists(destination, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Clone destination already exists");
        }
        return destination;
    }

    public Path resolve(String relativePath) {
        Objects.requireNonNull(relativePath, "relativePath must not be null");
        Path candidate = root.resolve(relativePath).normalize();
        if (!candidate.startsWith(root)) throw new IllegalArgumentException("Path escapes clone workspace");
        try {
            if (Files.exists(candidate, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(candidate)
                    && !candidate.toRealPath().startsWith(realRoot)) {
                throw new IllegalArgumentException("Path escapes clone workspace");
            }
            Path existing = candidate;
            while (!Files.exists(existing, java.nio.file.LinkOption.NOFOLLOW_LINKS) && existing.startsWith(root)) {
                existing = existing.getParent();
            }
            if (existing != null && existing.toRealPath().startsWith(realRoot) == false) {
                throw new IllegalArgumentException("Path escapes clone workspace");
            }
            if (Files.isSymbolicLink(candidate) || hasSymlinkAncestor(candidate)) {
                throw new IllegalArgumentException("Path traverses a symbolic link");
            }
            return candidate;
        } catch (IOException error) {
            throw new IllegalArgumentException("Unable to validate workspace path", error);
        }
    }

    public void cleanup(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative())
            throw new IllegalArgumentException("Cleanup timeout must be positive");
        if (!cleaned.compareAndSet(false, true)) return;
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(
                () -> {
                    try {
                        deleteTree();
                    } catch (Throwable error) {
                        failure.set(error);
                    }
                },
                "clone-workspace-cleanup");
        worker.setDaemon(true);
        worker.start();
        try {
            join(worker, timeout.toNanos());
            if (worker.isAlive()) {
                worker.interrupt();
                join(worker, TimeUnit.MILLISECONDS.toNanos(50));
                String state = worker.isAlive()
                        ? "cleanup remains in progress; retry is not supported"
                        : "cleanup stopped after timeout";
                throw new CloneFailureException(
                        CloneFailureException.Kind.CLONE_FAILED,
                        new CleanupDiagnostic(
                                "Clone workspace cleanup timeout root=" + root + " timeout=" + timeout + " " + state));
            }
            if (failure.get() != null) {
                throw new CloneFailureException(CloneFailureException.Kind.CLONE_FAILED, failure.get());
            }
        } catch (InterruptedException error) {
            worker.interrupt();
            try {
                join(worker, TimeUnit.MILLISECONDS.toNanos(50));
            } catch (InterruptedException ignored) {
            }
            Thread.currentThread().interrupt();
            throw new CloneFailureException(
                    CloneFailureException.Kind.CLONE_FAILED,
                    new CleanupDiagnostic("Clone workspace cleanup interrupted root=" + root + " timeout=" + timeout
                            + " cleanup status unknown"));
        }
    }

    public void cleanup(Duration timeout, Throwable originalFailure) {
        Objects.requireNonNull(originalFailure, "originalFailure must not be null");
        try {
            cleanup(timeout);
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    @Override
    public void close() {
        cleanup(Duration.ofSeconds(10));
    }

    private boolean hasSymlinkAncestor(Path candidate) {
        Path current = candidate.getParent();
        while (current != null && current.startsWith(root)) {
            if (Files.isSymbolicLink(current)) return true;
            current = current.getParent();
        }
        return false;
    }

    private void deleteTree() {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                    if (failure != null) throw failure;
                    Files.deleteIfExists(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException error) {
            throw new CleanupFailure(root, error);
        }
    }

    private static void join(Thread worker, long timeoutNanos) throws InterruptedException {
        long millis = timeoutNanos / 1_000_000;
        int nanos = (int) (timeoutNanos % 1_000_000);
        if (millis == 0 && nanos == 0) nanos = 1;
        worker.join(millis, nanos);
    }

    private static final class CleanupFailure extends RuntimeException {
        private CleanupFailure(Path root, Throwable cause) {
            super("Clone workspace cleanup failed root=" + root, cause);
        }
    }

    private static final class CleanupDiagnostic extends RuntimeException {
        private CleanupDiagnostic(String message) {
            super(message);
        }
    }
}
