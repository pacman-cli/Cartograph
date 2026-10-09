package com.cartograph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cartograph.graph.model.GraphSnapshot;
import com.cartograph.graph.model.ParsedFile;
import com.cartograph.graph.model.RepositoryRef;
import com.cartograph.graph.model.RepositorySnapshot;
import com.cartograph.graph.model.SourceFile;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApplicationPortsTest {
    @Test
    void portsUseDomainRecordsAndReturnDomainResults() {
        RepositoryRef ref = new RepositoryRef("acme", "widgets", "main");
        RepositorySnapshot repositorySnapshot = new RepositorySnapshot("acme/widgets", "sha", List.of());
        SourceFile sourceFile = new SourceFile("src/A.java", "class A {}", "java");
        ParsedFile parsedFile = new ParsedFile(sourceFile.path(), List.of(), List.of());
        GraphSnapshot graphSnapshot = new GraphSnapshot(
                "acme/widgets",
                "sha",
                List.of(),
                List.of(),
                List.of(),
                new com.cartograph.graph.model.GraphMetrics(0, 0, 0, 0));

        RepositoryFetcher fetcher = ignored -> repositorySnapshot;
        SourceParser parser = ignored -> parsedFile;
        GraphSnapshotRepository repository = new GraphSnapshotRepository() {
            @Override
            public Optional<GraphSnapshot> find(String repository, String commitSha) {
                return Optional.of(graphSnapshot);
            }

            @Override
            public Optional<GraphSnapshot> findLatest(String repository) {
                return Optional.empty();
            }

            @Override
            public java.util.List<GraphSnapshotRepository.RepositorySummary> listRepositories() {
                return java.util.List.of();
            }

            @Override
            public void save(GraphSnapshot snapshot) {}
        };

        assertEquals(repositorySnapshot, fetcher.fetch(ref));
        assertEquals(parsedFile, parser.parse(sourceFile));
        assertEquals(Optional.of(graphSnapshot), repository.find("acme/widgets", "sha"));
    }
}
