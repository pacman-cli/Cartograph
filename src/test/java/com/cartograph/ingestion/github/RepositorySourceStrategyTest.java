package com.cartograph.ingestion.github;

import com.cartograph.graph.model.RepositoryRef;
import com.cartograph.graph.model.RepositorySnapshot;
import com.cartograph.ingestion.IndexingLimits;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RepositorySourceStrategyTest {
    private static final String SHA = "a".repeat(40);
    private final RepositoryRef ref = new RepositoryRef("acme", "demo", "main");

    @Test
    void completeTreeBelowThresholdUsesRest() {
        RepositorySourceFetcher rest = (r, sha) -> snapshot(sha);
        RepositorySourceFetcher clone = mock(RepositorySourceFetcher.class);
        var strategy = strategy(rest, clone, new CloneProperties(), new RepositorySourceStrategy.TreeInspection(9_999, false));

        assertThat(strategy.fetch(ref, SHA).commitSha()).isEqualTo(SHA);
        verifyNoInteractions(clone);
    }

    @Test
    void thresholdSelectsClone() {
        RepositorySourceFetcher rest = mock(RepositorySourceFetcher.class);
        RepositorySourceFetcher clone = (r, sha) -> snapshot(sha);
        var properties = new CloneProperties();
        var strategy = strategy(rest, clone, properties, new RepositorySourceStrategy.TreeInspection(10_000, false));

        assertThat(strategy.fetch(ref, SHA).commitSha()).isEqualTo(SHA);
        verifyNoInteractions(rest);
    }

    @Test
    void truncatedTreeSelectsClone() {
        RepositorySourceFetcher rest = mock(RepositorySourceFetcher.class);
        RepositorySourceFetcher clone = (r, sha) -> snapshot(sha);
        var strategy = strategy(rest, clone, new CloneProperties(), new RepositorySourceStrategy.TreeInspection(1, true));

        assertThat(strategy.fetch(ref, SHA).commitSha()).isEqualTo(SHA);
        verifyNoInteractions(rest);
    }

    @Test
    void disabledCloneFailsWithoutCallingEitherSource() {
        RepositorySourceFetcher rest = mock(RepositorySourceFetcher.class);
        RepositorySourceFetcher clone = mock(RepositorySourceFetcher.class);
        var properties = new CloneProperties();
        properties.setEnabled(false);
        var strategy = strategy(rest, clone, properties, new RepositorySourceStrategy.TreeInspection(1, true));

        assertThatThrownBy(() -> strategy.fetch(ref, SHA))
                .isInstanceOf(CloneFailureException.class)
                .extracting("kind").isEqualTo(CloneFailureException.Kind.CLONE_DISABLED);
        verifyNoInteractions(rest, clone);
    }

    @Test
    void passesExactShaToSelectedSource() {
        AtomicInteger calls = new AtomicInteger();
        RepositorySourceFetcher clone = (r, sha) -> {
            calls.incrementAndGet();
            assertThat(sha).isEqualTo(SHA);
            return snapshot(sha);
        };
        var strategy = strategy((r, sha) -> snapshot(sha), clone, new CloneProperties(),
                new RepositorySourceStrategy.TreeInspection(10_000, false));

        strategy.fetch(ref, SHA);
        assertThat(calls).hasValue(1);
    }

    @Test
    void truncatedNullTreeRoutesToCloneDisabledWithoutDereferencingPartialTree() {
        GitHubClient client = mock(GitHubClient.class);
        when(client.tree(ref, SHA)).thenReturn(new GitHubClient.Response<>(
                new GitHubClient.TreeDto(true, null), null));
        var rest = new GitHubRepositoryFetcher(client, new IndexingLimits(10, 100, 100));
        var clone = mock(RepositorySourceFetcher.class);
        var properties = new CloneProperties();
        properties.setEnabled(false);
        var strategy = new RepositorySourceStrategy(rest, clone, properties);

        assertThatThrownBy(() -> strategy.fetch(ref, SHA))
                .isInstanceOf(CloneFailureException.class)
                .extracting("kind").isEqualTo(CloneFailureException.Kind.CLONE_DISABLED);
        verifyNoInteractions(clone);
    }

    private static RepositorySourceStrategy strategy(RepositorySourceFetcher rest, RepositorySourceFetcher clone,
                                                       CloneProperties properties, RepositorySourceStrategy.TreeInspection tree) {
        return new RepositorySourceStrategy(rest, clone, properties, (ref, sha) -> tree);
    }

    private static RepositorySnapshot snapshot(String sha) {
        return new RepositorySnapshot("acme/demo", sha, List.of());
    }
}
