package com.cartograph.ingestion.github;

import com.cartograph.graph.model.RepositoryRef;
import com.cartograph.graph.model.RepositorySnapshot;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Materializes an exact resolved commit through a bounded shallow Git clone. */
public final class ShallowCloneRepositoryFetcher implements RepositorySourceFetcher {
    @FunctionalInterface
    public interface ProcessRunner {
        ProcessResult run(List<String> arguments, Path workingDirectory, Duration timeout, int outputLimit);
    }

    private final CloneProperties properties;
    private final ProcessRunner runner;
    private final Supplier<CloneWorkspace> workspaces;
    private final CloneSourceWalker walker;

    public ShallowCloneRepositoryFetcher(CloneProperties properties, GitProcessRunner runner,
                                         CloneSourceWalker walker) {
        this(properties, runner::run, CloneWorkspace::create, walker);
    }

    public ShallowCloneRepositoryFetcher(CloneProperties properties, GitProcessRunner runner,
                                         com.cartograph.ingestion.IndexingLimits limits) {
        this(properties, runner, new CloneSourceWalker(limits));
    }

    public ShallowCloneRepositoryFetcher(CloneProperties properties, ProcessRunner runner,
                                         Supplier<CloneWorkspace> workspaces, CloneSourceWalker walker) {
        this.properties = Objects.requireNonNull(properties);
        this.runner = Objects.requireNonNull(runner);
        this.workspaces = Objects.requireNonNull(workspaces);
        this.walker = Objects.requireNonNull(walker);
    }

    @Override
    public RepositorySnapshot fetch(RepositoryRef ref, String resolvedCommitSha) {
        validateSha(resolvedCommitSha);
        Objects.requireNonNull(ref, "repository reference");
        String url = githubUrl(ref);
        CloneWorkspace workspace = workspaces.get();
        Throwable failure = null;
        try {
            Path destination = workspace.destination();
            runner.run(List.of(properties.executable(), "clone", "--depth", "1", "--no-tags",
                            "--single-branch", url, destination.toString()), workspace.root(), timeout(), 65_536);
            runner.run(List.of(properties.executable(), "-C", destination.toString(), "fetch", "--depth", "1",
                            "origin", resolvedCommitSha), workspace.root(), timeout(), 65_536);
            runner.run(List.of(properties.executable(), "-C", destination.toString(), "checkout", "--detach", resolvedCommitSha),
                    workspace.root(), timeout(), 65_536);
            ProcessResult verified = runner.run(List.of(properties.executable(), "-C", destination.toString(), "rev-parse", "HEAD"),
                    workspace.root(), timeout(), 256);
            if (!resolvedCommitSha.equalsIgnoreCase(verified.stdout().trim())) {
                throw new CloneFailureException(CloneFailureException.Kind.CLONE_COMMIT_MISMATCH);
            }
            return walker.walk(destination, ref, resolvedCommitSha.toLowerCase(java.util.Locale.ROOT));
        } catch (Throwable error) {
            failure = error;
            if (error instanceof CloneFailureException cloneFailure) throw cloneFailure;
            if (error instanceof RuntimeException runtime) throw runtime;
            throw new CloneFailureException(CloneFailureException.Kind.CLONE_FAILED, error);
        } finally {
            if (failure == null) workspace.cleanup(Duration.ofSeconds(properties.cleanupTimeoutSeconds()));
            else workspace.cleanup(Duration.ofSeconds(properties.cleanupTimeoutSeconds()), failure);
        }
    }

    private Duration timeout() { return Duration.ofSeconds(properties.timeoutSeconds()); }

    private static String githubUrl(RepositoryRef ref) {
        if (!validSegment(ref.owner()) || !validSegment(ref.repository())) {
            throw new IllegalArgumentException("Invalid GitHub repository reference");
        }
        return "https://github.com/" + ref.owner() + "/" + ref.repository() + ".git";
    }

    private static boolean validSegment(String value) {
        return value != null && value.matches("[A-Za-z0-9_.-]+") && !value.equals(".") && !value.equals("..");
    }

    private static void validateSha(String sha) {
        if (sha == null || !sha.matches("[0-9a-fA-F]{40}")) throw new IllegalArgumentException("Resolved commit must be a 40-character SHA");
    }
}
