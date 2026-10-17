package com.cartograph.ingestion.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.cartograph.application.GraphSnapshotRepository;
import com.cartograph.application.IndexRepositoryService;
import com.cartograph.graph.GraphBuilder;
import com.cartograph.graph.model.GraphMetrics;
import com.cartograph.graph.model.GraphSnapshot;
import com.cartograph.graph.model.ParsedFile;
import com.cartograph.graph.model.RepositoryRef;
import com.cartograph.ingestion.GitHubUrlNormalizer;
import com.cartograph.ingestion.IndexingLimits;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

class GitHubRepositoryFetcherTest {
    private static final String SHA = "a".repeat(40);
    private static final String MOVED_SHA = "b".repeat(40);
    private static final String BLOB_SHA = "cccccccccccccccccccccccccccccccccccccccc";
    private HttpServer server;
    private AtomicInteger contentRequests;
    private List<String> requests;
    private AtomicReference<String> branchTip;
    private String metadataBody;
    private String commitBody;
    private String treeBody;

    @BeforeEach
    void setUp() throws Exception {
        contentRequests = new AtomicInteger();
        requests = new CopyOnWriteArrayList<>();
        branchTip = new AtomicReference<>(SHA);
        metadataBody = "{\"default_branch\":\"main\"}";
        treeBody = "{\"truncated\":false,\"tree\":[{\"path\":\"src/a.ts\",\"type\":\"blob\",\"size\":12,\"sha\":\""
                + BLOB_SHA + "\"},{\"path\":\"README.md\",\"type\":\"blob\",\"size\":999,\"sha\":\"" + MOVED_SHA
                + "\"}]}";
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requests.add(exchange.getRequestURI().toString());
            String body;
            if (path.equals("/repos/acme/demo")) body = metadataBody;
            else if (path.endsWith("/commits/main"))
                body = commitBody == null ? "{\"sha\":\"" + branchTip.get() + "\"}" : commitBody;
            else if (path.contains("/git/trees/")) body = treeBody;
            else {
                contentRequests.incrementAndGet();
                body = "{\"type\":\"file\",\"size\":12,\"encoding\":\"base64\",\"content\":\"Y29uc3QgYSA9IDE7\"}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void inspectsTreeLimitsBeforeFetchingSupportedContent() {
        var props = new GitHubProperties(
                "", URI.create("http://localhost:" + server.getAddress().getPort()), new IndexingLimits(1, 12, 12));
        var fetcher =
                new GitHubRepositoryFetcher(new GitHubClient(RestClient.builder(), new ObjectMapper(), props), props);
        var snapshot = fetcher.fetch(new RepositoryRef("acme", "demo", null));
        assertThat(snapshot.commitSha()).isEqualTo(SHA);
        assertThat(snapshot.files()).singleElement().satisfies(file -> {
            assertThat(file.path()).isEqualTo("src/a.ts");
            assertThat(file.language()).isEqualTo("typescript");
            assertThat(file.content()).isEqualTo("const a = 1;");
        });
        assertThat(snapshot.filesSeen()).isEqualTo(2);
        assertThat(snapshot.warnings()).singleElement().satisfies(warning -> {
            assertThat(warning.code()).isEqualTo("UNSUPPORTED_FILE");
            assertThat(warning.filePath()).isEqualTo("README.md");
        });
        assertThat(contentRequests).hasValue(1);
        assertThat(requests)
                .containsExactly(
                        "/repos/acme/demo",
                        "/repos/acme/demo/commits/main",
                        "/repos/acme/demo/git/trees/" + SHA + "?recursive=1",
                        "/repos/acme/demo/contents/src/a.ts?ref=" + SHA);
    }

    @Test
    void serviceFetchesResolvedCommitEvenWhenBranchMovesDuringCacheLookup() {
        var repository = mock(GraphSnapshotRepository.class);
        when(repository.find("acme/demo", SHA)).thenAnswer(invocation -> {
            branchTip.set(MOVED_SHA);
            return Optional.empty();
        });
        var service = new IndexRepositoryService(
                new GitHubUrlNormalizer(),
                fetcher(),
                repository,
                new GraphBuilder(file -> new ParsedFile(file.path(), List.of(), List.of())));

        assertThat(service.index(new RepositoryRef("acme", "demo", null)).commitSha())
                .isEqualTo(SHA);
        assertThat(requests)
                .containsExactly(
                        "/repos/acme/demo",
                        "/repos/acme/demo/commits/main",
                        "/repos/acme/demo/git/trees/" + SHA + "?recursive=1",
                        "/repos/acme/demo/contents/src/a.ts?ref=" + SHA);
    }

    @Test
    void cacheHitResolvesOnceWithoutTreeOrContentRequests() {
        var repository = mock(GraphSnapshotRepository.class);
        var cached = new GraphSnapshot("acme/demo", SHA, List.of(), List.of(), List.of(), new GraphMetrics(0, 0, 0, 0));
        when(repository.find("acme/demo", SHA)).thenReturn(Optional.of(cached));
        var service =
                new IndexRepositoryService(new GitHubUrlNormalizer(), fetcher(), repository, new GraphBuilder(file -> {
                    throw new AssertionError("cached graph must not be rebuilt");
                }));

        assertThat(service.index(new RepositoryRef("acme", "demo", null))).isSameAs(cached);
        assertThat(requests).containsExactly("/repos/acme/demo", "/repos/acme/demo/commits/main");
        assertThat(contentRequests).hasValue(0);
        verify(repository, never()).save(any());
    }

    @Test
    void directFetchWithExplicitRefSkipsMetadataAndResolvesOnlyOnce() {
        assertThat(fetcher().fetch(new RepositoryRef("acme", "demo", "main")).commitSha())
                .isEqualTo(SHA);
        assertThat(requests)
                .containsExactly(
                        "/repos/acme/demo/commits/main",
                        "/repos/acme/demo/git/trees/" + SHA + "?recursive=1",
                        "/repos/acme/demo/contents/src/a.ts?ref=" + SHA);
    }

    @Test
    void resolvedFetchNeedsOnlyTreeAndContent() {
        assertThat(fetcher()
                        .fetchResolved(new RepositoryRef("acme", "demo", "main"), SHA)
                        .commitSha())
                .isEqualTo(SHA);
        assertThat(requests)
                .containsExactly(
                        "/repos/acme/demo/git/trees/" + SHA + "?recursive=1",
                        "/repos/acme/demo/contents/src/a.ts?ref=" + SHA);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "null",
                "{}",
                "{\"default_branch\":\"\"}",
                "{\"default_branch\":\"../main\"}",
                "{\"default_branch\":\"main?ref=evil\"}"
            })
    void rejectsInvalidMetadataBeforeCommitLookup(String response) {
        metadataBody = response;
        assertThatThrownBy(() -> fetcher().fetch(new RepositoryRef("acme", "demo", null)))
                .isInstanceOf(GitHubFetchException.class);
        assertThat(requests).containsExactly("/repos/acme/demo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"sha\":\"\"}", "{\"sha\":\"abc\"}", "{\"sha\":\"../main\"}"})
    void rejectsInvalidCommitBeforeTreeLookup(String response) {
        commitBody = response;
        assertThatThrownBy(() -> fetcher().fetch(new RepositoryRef("acme", "demo", "main")))
                .isInstanceOf(GitHubFetchException.class);
        assertThat(requests).containsExactly("/repos/acme/demo/commits/main");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "main", "abc", "../main", "a?ref=evil"})
    void rejectsInvalidPinnedCommitWithoutRequests(String sha) {
        assertThatThrownBy(() -> fetcher().fetchResolved(new RepositoryRef("acme", "demo", null), sha))
                .isInstanceOf(GitHubFetchException.class);
        assertThat(requests).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "null",
                "{}",
                "{\"truncated\":true,\"tree\":[]}",
                "{\"truncated\":false}",
                "{\"truncated\":false,\"tree\":[null]}",
                "{\"truncated\":false,\"tree\":[{\"type\":\"blob\",\"size\":1,\"sha\":\"" + BLOB_SHA + "\"}]}",
                "{\"truncated\":false,\"tree\":[{\"path\":\"../a.ts\",\"type\":\"blob\",\"size\":1,\"sha\":\""
                        + BLOB_SHA + "\"}]}",
                "{\"truncated\":false,\"tree\":[{\"path\":\"a.ts\",\"type\":\"blob\",\"size\":-1,\"sha\":\"" + BLOB_SHA
                        + "\"}]}",
                "{\"truncated\":false,\"tree\":[{\"path\":\"a.ts\",\"type\":\"blob\",\"sha\":\"" + BLOB_SHA + "\"}]}",
                "{\"truncated\":false,\"tree\":[{\"path\":\"a.ts\",\"type\":\"blob\",\"size\":1}]}"
            })
    void rejectsInvalidTreeBeforeContentRequests(String response) {
        treeBody = response;
        assertThatThrownBy(() -> fetcher().fetchResolved(new RepositoryRef("acme", "demo", null), SHA))
                .isInstanceOf(GitHubFetchException.class);
        assertThat(requests).containsExactly("/repos/acme/demo/git/trees/" + SHA + "?recursive=1");
        assertThat(contentRequests).hasValue(0);
    }

    private GitHubRepositoryFetcher fetcher() {
        var props = new GitHubProperties(
                "",
                URI.create("http://localhost:" + server.getAddress().getPort()),
                new IndexingLimits(10, 1000, 1000));
        return new GitHubRepositoryFetcher(new GitHubClient(RestClient.builder(), new ObjectMapper(), props), props);
    }

    @Test
    void validatesActualDecodedContentBeforeAddingFile() {
        // Deliberately understate tree size: the valid content response still reports its actual 12 bytes.
        treeBody = "{\"truncated\":false,\"tree\":[{\"path\":\"src/a.ts\",\"type\":\"blob\",\"size\":5,\"sha\":\""
                + BLOB_SHA + "\"}]}";
        var props = new GitHubProperties(
                "", URI.create("http://localhost:" + server.getAddress().getPort()), new IndexingLimits(1, 1000, 5));
        var fetcher =
                new GitHubRepositoryFetcher(new GitHubClient(RestClient.builder(), new ObjectMapper(), props), props);
        assertThatThrownBy(() -> fetcher.fetch(new RepositoryRef("acme", "demo", null)))
                .isInstanceOf(com.cartograph.ingestion.RepositoryLimitException.class)
                .extracting("limit")
                .isEqualTo(IndexingLimits.Limit.FILE_BYTES);
        assertThat(contentRequests).hasValue(1);
    }

    @Test
    void rejectsLimitsBeforeAnyContentRequest() {
        var props = new GitHubProperties(
                "", URI.create("http://localhost:" + server.getAddress().getPort()), new IndexingLimits(1, 4, 5));
        var fetcher =
                new GitHubRepositoryFetcher(new GitHubClient(RestClient.builder(), new ObjectMapper(), props), props);
        assertThatThrownBy(() -> fetcher.fetch(new RepositoryRef("acme", "demo", null)))
                .isInstanceOf(com.cartograph.ingestion.RepositoryLimitException.class);
        assertThat(contentRequests).hasValue(0);
    }
}
