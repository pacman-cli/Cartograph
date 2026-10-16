package com.cartograph.ingestion.github;

import com.cartograph.graph.model.RepositoryRef;
import com.cartograph.graph.model.RepositorySnapshot;
import com.cartograph.ingestion.IndexingLimits;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Offline integration coverage using the local Git executable and temporary repositories. */
class ShallowCloneIntegrationTest {
    private static final RepositoryRef REF = new RepositoryRef("local", "fixture", "main");
    private static final String GIT = "git";

    @Test
    void indexesRecordedCommitEvenWhenMovingBranchHasNewerContent() throws Exception {
        assumeGitAvailable();
        Path repository = createTwoCommitRepository();
        CommitPair commits = commits(repository);
        LocalGitRunner runner = new LocalGitRunner(repository);
        var fetcher = fetcher(runner, CloneSourceWalker::new);

        RepositorySnapshot snapshot = fetcher.fetch(REF, commits.first());

        assertThat(snapshot.commitSha()).isEqualTo(commits.first());
        assertThat(snapshot.files()).extracting("path").containsExactly("source.ts");
        assertThat(snapshot.files().get(0).content()).contains("first commit");
        assertThat(snapshot.files()).noneMatch(file -> file.content().contains("moving branch tip"));
        assertThat(runner.workspaceRoots).allMatch(path -> !Files.exists(path));
    }

    @Test
    void cleansWorkspaceAfterCloneFailure() throws Exception {
        assumeGitAvailable();
        TrackingWorkspaces workspaces = new TrackingWorkspaces();
        var fetcher = new ShallowCloneRepositoryFetcher(new CloneProperties(), (args, cwd, timeout, output) -> {
            throw new CloneFailureException(CloneFailureException.Kind.CLONE_FAILED);
        }, workspaces::create, new CloneSourceWalker(new IndexingLimits(10, 1000, 100)));

        assertThatThrownBy(() -> fetcher.fetch(REF, "a".repeat(40)))
                .isInstanceOf(CloneFailureException.class);
        assertWorkspaceRootsDeleted(workspaces);
    }

    @Test
    void cleansWorkspaceAfterTimeout() throws Exception {
        assumeGitAvailable();
        TrackingWorkspaces workspaces = new TrackingWorkspaces();
        var fetcher = new ShallowCloneRepositoryFetcher(new CloneProperties(), (args, cwd, timeout, output) -> {
            throw new CloneFailureException(CloneFailureException.Kind.CLONE_TIMEOUT);
        }, workspaces::create, new CloneSourceWalker(new IndexingLimits(10, 1000, 100)));

        assertThatThrownBy(() -> fetcher.fetch(REF, "a".repeat(40)))
                .isInstanceOf(CloneFailureException.class)
                .extracting(error -> ((CloneFailureException) error).kind())
                .isEqualTo(CloneFailureException.Kind.CLONE_TIMEOUT);
        assertWorkspaceRootsDeleted(workspaces);
    }

    @Test
    void cleansWorkspaceAfterInterruptedOperation() throws Exception {
        assumeGitAvailable();
        TrackingWorkspaces workspaces = new TrackingWorkspaces();
        CountDownLatch started = new CountDownLatch(1);
        var fetcher = new ShallowCloneRepositoryFetcher(new CloneProperties(), (args, cwd, timeout, output) -> {
            started.countDown();
            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(1));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new CloneFailureException(CloneFailureException.Kind.CLONE_TIMEOUT, interrupted);
            }
            throw new AssertionError("interrupted test runner did not block");
        }, workspaces::create, new CloneSourceWalker(new IndexingLimits(10, 1000, 100)));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread operation = new Thread(() -> {
            try {
                fetcher.fetch(REF, "a".repeat(40));
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        operation.start();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        operation.interrupt();
        operation.join(5_000);

        assertThat(operation.isAlive()).isFalse();
        assertThat(failure.get()).isInstanceOf(CloneFailureException.class);
        assertWorkspaceRootsDeleted(workspaces);
    }

    @Test
    void routesDeterministicLargeRepositoryToCloneWithoutWalkingFiles() throws Exception {
        assumeGitAvailable();
        Path repository = Files.createTempDirectory("cartograph-large-repo-");
        initialize(repository);
        for (int index = 0; index < 10_001; index++) {
            Files.writeString(repository.resolve(String.format("file-%05d.js", index)), "export const value = 1;\n");
        }
        runGit(repository, "add", ".");
        runGit(repository, "commit", "--quiet", "-m", "large fixture");

        CloneProperties properties = new CloneProperties();
        properties.setFileThreshold(10_000);
        AtomicReference<String> selected = new AtomicReference<>();
        RepositorySourceFetcher clone = (ref, sha) -> {
            selected.set("clone");
            return new RepositorySnapshot(ref.coordinate(), sha, List.of());
        };
        RepositorySourceFetcher rest = (ref, sha) -> {
            selected.set("rest");
            return new RepositorySnapshot(ref.coordinate(), sha, List.of());
        };
        var strategy = new RepositorySourceStrategy(rest, clone, properties,
                (ref, sha) -> new RepositorySourceStrategy.TreeInspection(10_001, false));

        strategy.fetch(REF, "b".repeat(40));

        assertThat(selected).hasValue("clone");
    }

    private static ShallowCloneRepositoryFetcher fetcher(LocalGitRunner runner,
                                                           java.util.function.Function<IndexingLimits, CloneSourceWalker> walkers) {
        return new ShallowCloneRepositoryFetcher(new CloneProperties(), runner,
                () -> {
                    CloneWorkspace workspace = CloneWorkspace.create();
                    runner.workspaceRoots.add(workspace.root());
                    return workspace;
                }, walkers.apply(new IndexingLimits(10, 1000, 100)));
    }

    private static void assertWorkspaceRootsDeleted(TrackingWorkspaces workspaces) {
        assertThat(workspaces.roots).isNotEmpty().allMatch(path -> !Files.exists(path));
    }

    private static void assumeGitAvailable() {
        try {
            Process process = new ProcessBuilder(GIT, "--version").redirectErrorStream(true).start();
            assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
            Assumptions.assumeTrue(process.exitValue() == 0, "local git executable is unavailable");
        } catch (IOException | InterruptedException unavailable) {
            Thread.currentThread().interrupt();
            Assumptions.assumeTrue(false, "local git executable is unavailable");
        }
    }

    private static Path createTwoCommitRepository() throws IOException {
        Path repository = Files.createTempDirectory("cartograph-two-commit-");
        initialize(repository);
        Files.writeString(repository.resolve("source.ts"), "export const value = 'first commit';\n");
        runGit(repository, "add", "source.ts");
        runGit(repository, "commit", "--quiet", "-m", "first");
        Files.writeString(repository.resolve("source.ts"), "export const value = 'moving branch tip';\n");
        runGit(repository, "commit", "--quiet", "-am", "second");
        return repository;
    }

    private static CommitPair commits(Path repository) {
        String first = runGit(repository, "rev-list", "--max-count=2", "HEAD");
        List<String> values = first.lines().toList();
        return new CommitPair(values.get(1), values.get(0));
    }

    private static void initialize(Path repository) {
        runGit(repository, "init", "--quiet", "-b", "main");
        runGit(repository, "config", "user.email", "cartograph-tests@example.invalid");
        runGit(repository, "config", "user.name", "Cartograph Tests");
    }

    private static String runGit(Path directory, String... arguments) {
        try {
            List<String> command = new ArrayList<>();
            command.add(GIT);
            command.addAll(List.of(arguments));
            Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new AssertionError("git command failed: " + command + " output=" + output);
            }
            return output.trim();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError("git command interrupted", error);
        }
    }

    private record CommitPair(String first, String second) { }

    private static final class TrackingWorkspaces {
        private final List<Path> roots = new ArrayList<>();

        CloneWorkspace create() {
            CloneWorkspace workspace = CloneWorkspace.create();
            roots.add(workspace.root());
            return workspace;
        }
    }

    private static final class LocalGitRunner implements ShallowCloneRepositoryFetcher.ProcessRunner {
        private final Path source;
        private final GitProcessRunner delegate = new GitProcessRunner();
        private final List<Path> workspaceRoots = new ArrayList<>();

        private LocalGitRunner(Path source) { this.source = source; }

        @Override
        public ProcessResult run(List<String> arguments, Path workingDirectory, Duration timeout, int outputLimit) {
            List<String> translated = new ArrayList<>(arguments);
            int sourceIndex = translated.indexOf("https://github.com/local/fixture.git");
            if (sourceIndex >= 0) translated.set(sourceIndex, source.toString());
            return delegate.run(translated, workingDirectory, timeout, outputLimit);
        }
    }
}
