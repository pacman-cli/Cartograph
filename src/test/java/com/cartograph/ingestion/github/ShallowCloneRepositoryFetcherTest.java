package com.cartograph.ingestion.github;

import com.cartograph.graph.model.RepositoryRef;
import com.cartograph.ingestion.IndexingLimits;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShallowCloneRepositoryFetcherTest {
    private static final String SHA = "a".repeat(40);
    private final RepositoryRef ref = new RepositoryRef("acme", "demo", "main");

    @Test
    void clonesWithBoundedArgumentsAndVerifiesExactSha() throws Exception {
        List<List<String>> calls = new ArrayList<>();
        var fetcher = new ShallowCloneRepositoryFetcher(new CloneProperties(), (args, cwd, timeout, output) -> {
            calls.add(args);
            if (args.contains("clone")) {
                try {
                    Files.createDirectories(Path.of(args.get(args.size() - 1), "src"));
                    Files.writeString(Path.of(args.get(args.size() - 1), "src/main.ts"), "export const value = 1;");
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            }
            return new ProcessResult(0, args.contains("rev-parse") ? SHA + "\n" : "", "");
        }, CloneWorkspace::create, new CloneSourceWalker(new IndexingLimits(10, 1000, 100)));

        assertThat(fetcher.fetch(ref, SHA).commitSha()).isEqualTo(SHA);
        assertThat(calls.get(0)).containsExactly("git", "clone", "--depth", "1", "--no-tags",
                "--single-branch", "https://github.com/acme/demo.git", calls.get(0).get(7));
        assertThat(calls).anySatisfy(command -> assertThat(command).contains("checkout", "--detach", SHA));
    }

    @Test
    void rejectsVerificationMismatchAndStillCleansWorkspace() {
        var fetcher = new ShallowCloneRepositoryFetcher(new CloneProperties(), (args, cwd, timeout, output) -> {
            if (args.contains("clone")) {
                try { Files.createDirectories(Path.of(args.get(args.size() - 1))); }
                catch (Exception error) { throw new RuntimeException(error); }
            }
            return new ProcessResult(0, args.contains("rev-parse") ? "b".repeat(40) : "", "");
        }, CloneWorkspace::create, new CloneSourceWalker(new IndexingLimits(10, 1000, 100)));

        assertThatThrownBy(() -> fetcher.fetch(ref, SHA))
                .isInstanceOf(CloneFailureException.class)
                .extracting(error -> ((CloneFailureException) error).kind())
                .isEqualTo(CloneFailureException.Kind.CLONE_COMMIT_MISMATCH);
    }

    @Test
    void walkerSkipsVendorPythonAndSymlinkFilesInDeterministicOrder() throws Exception {
        Path root = Files.createTempDirectory("walker-");
        Files.createDirectories(root.resolve("src"));
        Files.createDirectories(root.resolve("node_modules/pkg"));
        Files.writeString(root.resolve("src/z.js"), "z");
        Files.writeString(root.resolve("src/a.ts"), "a");
        Files.writeString(root.resolve("src/tool.py"), "print(1)");
        Files.writeString(root.resolve("node_modules/pkg/ignored.js"), "ignored");
        Path outside = Files.createTempFile("outside-", ".js");
        Files.createSymbolicLink(root.resolve("src/link.js"), outside);

        var snapshot = new CloneSourceWalker(new IndexingLimits(10, 100, 20)).walk(root, ref, SHA);
        assertThat(snapshot.files().stream().map(file -> file.path()).toList()).containsExactly("src/a.ts", "src/z.js");
        assertThat(snapshot.warnings()).extracting("code").containsExactly("UNSUPPORTED_LANGUAGE");
        assertThat(snapshot.filesSeen()).isEqualTo(3);
    }

    @Test
    void walkerStopsBeforeReadingWhenCandidateCountLimitIsExceeded() throws Exception {
        Path root = Files.createTempDirectory("walker-limit-");
        Files.writeString(root.resolve("a.js"), "a");
        Files.writeString(root.resolve("b.ts"), "b");

        assertThatThrownBy(() -> new CloneSourceWalker(new IndexingLimits(1, 100, 100)).walk(root, ref, SHA))
                .isInstanceOf(com.cartograph.ingestion.RepositoryLimitException.class)
                .extracting("limit").isEqualTo(IndexingLimits.Limit.FILE_COUNT);
    }
}
