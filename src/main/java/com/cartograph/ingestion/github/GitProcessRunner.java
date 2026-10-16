package com.cartograph.ingestion.github;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Runs an external Git command without invoking a shell or retaining unbounded output. */
public final class GitProcessRunner {
    public ProcessResult run(List<String> arguments, Path workingDirectory, Duration timeout, int outputLimit) {
        Objects.requireNonNull(arguments, "arguments must not be null");
        Objects.requireNonNull(workingDirectory, "workingDirectory must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (arguments.isEmpty() || arguments.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Process arguments must not be empty or contain nulls");
        }
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("Timeout must be positive");
        if (outputLimit <= 0) throw new IllegalArgumentException("Output limit must be positive");
        if (!Files.isDirectory(workingDirectory)) throw new IllegalArgumentException("Working directory must exist");

        Process process;
        try {
            process = new ProcessBuilder(List.copyOf(arguments)).directory(workingDirectory.toFile()).start();
        } catch (IOException | SecurityException error) {
            throw new CloneFailureException(CloneFailureException.Kind.CLONE_UNAVAILABLE, error);
        }

        ExecutorService readers = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "clone-process-output");
            thread.setDaemon(true);
            return thread;
        });
        Future<String> stdout = readers.submit(() -> readBounded(process.getInputStream(), outputLimit));
        Future<String> stderr = readers.submit(() -> readBounded(process.getErrorStream(), outputLimit));
        try {
            if (!process.waitFor(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                terminate(process);
                throw new CloneFailureException(CloneFailureException.Kind.CLONE_TIMEOUT);
            }
            ProcessResult result = new ProcessResult(process.exitValue(), output(stdout), output(stderr));
            if (result.exitCode() != 0) {
                throw new CloneFailureException(CloneFailureException.Kind.CLONE_FAILED, result);
            }
            return result;
        } catch (InterruptedException error) {
            terminate(process);
            Thread.currentThread().interrupt();
            throw new CloneFailureException(CloneFailureException.Kind.CLONE_TIMEOUT, error);
        } finally {
            readers.shutdownNow();
        }
    }

    public ProcessResult execute(List<String> arguments, Path workingDirectory, Duration timeout, int outputLimit) {
        return run(arguments, workingDirectory, timeout, outputLimit);
    }

    private static String output(Future<String> output) {
        try {
            return output.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new CloneFailureException(CloneFailureException.Kind.CLONE_TIMEOUT, error);
        } catch (ExecutionException error) {
            throw new CloneFailureException(CloneFailureException.Kind.CLONE_FAILED, error.getCause());
        }
    }

    private static String readBounded(InputStream stream, int limit) throws IOException {
        try (stream) {
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8192));
            byte[] buffer = new byte[4096];
            int captured = 0;
            int read;
            while ((read = stream.read(buffer)) != -1) {
                int remaining = limit - captured;
                if (remaining > 0) {
                    int count = Math.min(read, remaining);
                    output.write(buffer, 0, count);
                    captured += count;
                }
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private static void terminate(Process process) {
        process.descendants().forEach(child -> {
            child.destroy();
            if (child.isAlive()) child.destroyForcibly();
        });
        process.destroy();
        if (process.isAlive()) process.destroyForcibly();
    }
}
