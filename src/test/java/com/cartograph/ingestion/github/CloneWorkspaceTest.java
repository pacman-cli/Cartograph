package com.cartograph.ingestion.github;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloneWorkspaceTest {
    @Test
    void createsUniqueRootAndAbsentDestination() throws Exception {
        try (CloneWorkspace first = CloneWorkspace.create(); CloneWorkspace second = CloneWorkspace.create()) {
            assertThat(first.root()).isDirectory();
            assertThat(second.root()).isDirectory();
            assertThat(first.root()).isNotEqualTo(second.root());
            assertThat(first.destination()).doesNotExist();
        }
    }

    @Test
    void resolvesOnlyPathsContainedByRootAndRejectsExistingSymlinkEscape() throws Exception {
        try (CloneWorkspace workspace = CloneWorkspace.create()) {
            assertThat(workspace.resolve("src/Main.java")).isEqualTo(workspace.root().resolve("src/Main.java"));
            assertThatThrownBy(() -> workspace.resolve("../outside")).isInstanceOf(IllegalArgumentException.class);

            Path outside = Files.createTempDirectory("outside-");
            Files.createSymbolicLink(workspace.root().resolve("link"), outside);
            assertThatThrownBy(() -> workspace.resolve("link/file.txt"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void recursivelyCleansNestedFilesAndSymlinksIdempotently() throws Exception {
        CloneWorkspace workspace = CloneWorkspace.create();
        Path nested = workspace.resolve("a/b/file.txt");
        Files.createDirectories(nested.getParent());
        Files.writeString(nested, "contents");
        Path outside = Files.createTempDirectory("outside-");
        Files.createSymbolicLink(workspace.resolve("a/link"), outside);

        workspace.cleanup(Duration.ofSeconds(2));
        workspace.cleanup(Duration.ofSeconds(2));

        assertThat(workspace.root()).doesNotExist();
        assertThat(outside).isDirectory();
    }

    @Test
    void rejectsInvalidCleanupTimeout() throws Exception {
        try (CloneWorkspace workspace = CloneWorkspace.create()) {
            assertThatIllegalArgumentException().isThrownBy(() -> workspace.cleanup(Duration.ZERO));
        }
    }

    @Test
    void cleanupFailureDoesNotReplaceOriginalFailure() throws Exception {
        CloneWorkspace workspace = CloneWorkspace.create();
        RuntimeException original = new RuntimeException("original");
        workspace.cleanup(Duration.ZERO, original);
        assertThat(original.getSuppressed()).hasSize(1);
        workspace.cleanup(Duration.ofSeconds(2));
    }
}
