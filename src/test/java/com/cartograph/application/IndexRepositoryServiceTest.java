package com.cartograph.application;

import com.cartograph.graph.GraphBuilder;
import com.cartograph.graph.model.*;
import com.cartograph.ingestion.GitHubUrlNormalizer;
import com.cartograph.ingestion.github.RepositorySourceFetcher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class IndexRepositoryServiceTest {
    private final RepositoryRef ref = new RepositoryRef("acme", "widgets", "main");
    private final GraphSnapshot cached = snapshot("sha");

    @Test
    void movingBranchIsFetchedAtTheCommitUsedForTheCacheLookup() {
        RepositoryFetcher fetcher = new RepositoryFetcher() {
            @Override
            public String resolveCommit(RepositoryRef requested) { return "original"; }

            @Override
            public RepositorySnapshot fetch(RepositoryRef requested) {
                return new RepositorySnapshot(requested.coordinate(),
                        "main".equals(requested.ref()) ? "moved" : requested.ref(), List.of());
            }
        };
        GraphSnapshotRepository repository = mock(GraphSnapshotRepository.class);
        when(repository.find(ref.coordinate(), "original")).thenReturn(Optional.empty());
        IndexRepositoryService service = new IndexRepositoryService(new GitHubUrlNormalizer(), fetcher, repository,
                new GraphBuilder(file -> new ParsedFile(file.path(), List.of(), List.of())));

        assertEquals("original", service.index(ref).commitSha());
    }

    @Test
    void compatibilityFallbackRejectsFetchersThatIgnorePinnedCommit() {
        RepositoryFetcher fetcher = ignored -> new RepositorySnapshot(ref.coordinate(), "moved", List.of());

        assertThrows(IllegalStateException.class, () -> fetcher.fetchResolved(ref, "original"));
    }

    @Test
    void refusesMismatchedSnapshotBeforeParsingOrSaving() {
        RepositoryFetcher fetcher = mock(RepositoryFetcher.class);
        GraphSnapshotRepository repository = mock(GraphSnapshotRepository.class);
        when(fetcher.resolveCommit(ref)).thenReturn("original");
        when(repository.find(ref.coordinate(), "original")).thenReturn(Optional.empty());
        when(fetcher.fetchResolved(ref, "original"))
                .thenReturn(new RepositorySnapshot(ref.coordinate(), "moved", List.of()));
        IndexRepositoryService service = new IndexRepositoryService(new GitHubUrlNormalizer(), fetcher, repository,
                new GraphBuilder(file -> { throw new AssertionError("mismatched source must not be parsed"); }));

        assertThrows(IllegalStateException.class, () -> service.index(ref));
        verify(repository, never()).save(any());
    }

    @Test
    void cacheHitDoesNotFetchParseBuildOrSave() {
        RepositoryFetcher fetcher = mock(RepositoryFetcher.class);
        GraphSnapshotRepository repository = mock(GraphSnapshotRepository.class);
        when(fetcher.resolveCommit(ref)).thenReturn("sha");
        when(repository.find("acme/widgets", "sha")).thenReturn(Optional.of(cached));
        IndexRepositoryService service = new IndexRepositoryService(new GitHubUrlNormalizer(), fetcher, repository,
                new GraphBuilder(file -> { throw new AssertionError("parser must not run"); }));

        assertSame(cached, service.index(ref));
        verify(fetcher, never()).fetch(any());
        verify(fetcher, never()).fetchResolved(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void missFetchesBuildsAndSavesSnapshot() {
        RepositoryFetcher fetcher = mock(RepositoryFetcher.class);
        GraphSnapshotRepository repository = mock(GraphSnapshotRepository.class);
        RepositorySnapshot source = new RepositorySnapshot("acme/widgets", "sha", List.of(new SourceFile("a.ts", "", "typescript")));
        when(fetcher.resolveCommit(ref)).thenReturn("sha");
        when(repository.find("acme/widgets", "sha")).thenReturn(Optional.empty());
        when(fetcher.fetchResolved(ref, "sha")).thenReturn(source);
        IndexRepositoryService service = new IndexRepositoryService(new GitHubUrlNormalizer(), fetcher, repository,
                new GraphBuilder(file -> new ParsedFile(file.path(), List.of(), List.of())));

        GraphSnapshot built = service.index(ref);
        var order = inOrder(fetcher, repository);
        order.verify(fetcher).resolveCommit(ref);
        order.verify(repository).find("acme/widgets", "sha");
        order.verify(fetcher).fetchResolved(ref, "sha");
        order.verify(repository).save(built);
        verifyNoMoreInteractions(fetcher);
    }

    @Test
    void sourceStrategyReceivesTheResolvedShaOnlyAfterCacheMiss() {
        RepositoryFetcher resolver = mock(RepositoryFetcher.class);
        RepositorySourceFetcher source = mock(RepositorySourceFetcher.class);
        GraphSnapshotRepository repository = mock(GraphSnapshotRepository.class);
        when(resolver.resolveCommit(ref)).thenReturn("sha");
        when(repository.find(ref.coordinate(), "sha")).thenReturn(Optional.empty());
        when(source.fetch(ref, "sha")).thenReturn(new RepositorySnapshot(ref.coordinate(), "sha", List.of()));
        IndexRepositoryService service = new IndexRepositoryService(new GitHubUrlNormalizer(), resolver, source,
                repository, new GraphBuilder(file -> new ParsedFile(file.path(), List.of(), List.of())));

        service.index(ref);

        verify(source).fetch(ref, "sha");
        verify(resolver).resolveCommit(ref);
        verify(resolver, never()).fetchResolved(any(), any());
    }

    private static GraphSnapshot snapshot(String sha) {
        return new GraphSnapshot("acme/widgets", sha, List.of(), List.of(), List.of(), new GraphMetrics(0, 0, 0, 0));
    }
}
