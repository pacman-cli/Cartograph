package com.cartograph.ingestion.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cartograph.graph.model.RepositoryRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class GitHubClientTest {
    private HttpServer server;
    private AtomicReference<String> authorization;
    private AtomicReference<String> ifNoneMatch;
    private AtomicReference<URI> requestUri;
    private GitHubClient client;

    @BeforeEach
    void setUp() throws Exception {
        authorization = new AtomicReference<>();
        ifNoneMatch = new AtomicReference<>();
        requestUri = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            ifNoneMatch.set(exchange.getRequestHeaders().getFirst("If-None-Match"));
            requestUri.set(exchange.getRequestURI());
            String path = exchange.getRequestURI().getPath();
            if ("/repos/acme/demo".equals(path)) respond(exchange, 200, "{\"default_branch\":\"main\"}", "\"repo-v1\"");
            else if (path.endsWith("/commits/main")) respond(exchange, 200, "{\"sha\":\"abc123\"}", null);
            else if (path.endsWith("/git/trees/abc123"))
                respond(exchange, 200, "{\"truncated\":false,\"tree\":[]}", null);
            else if (path.endsWith("/contents/src/app.ts")) respond(exchange, 304, "", null);
            else if (path.endsWith("/contents/src/etag.ts"))
                respond(
                        exchange,
                        200,
                        "{\"type\":\"file\",\"size\":1,\"encoding\":\"base64\",\"content\":\"YQ==\"}",
                        "\"content-v1\"");
            else if (path.endsWith("/missing")) respond(exchange, 404, "{}", null);
            else if (path.endsWith("/forbidden")) respond(exchange, 403, "{}", null);
            else if (path.endsWith("/rate-403")) {
                exchange.getResponseHeaders().set("X-RateLimit-Remaining", "0");
                respond(exchange, 403, "{}", null);
            } else if (path.endsWith("/rate-429")) respond(exchange, 429, "{}", null);
            else if (path.endsWith("/commits/release/2026")) respond(exchange, 200, "{\"sha\":\"abc123\"}", null);
            else respond(exchange, 200, "{}", null);
        });
        server.start();
        GitHubProperties properties = new GitHubProperties(
                "secret",
                URI.create("http://localhost:" + server.getAddress().getPort()),
                new com.cartograph.ingestion.IndexingLimits(10, 1000, 100));
        client = new GitHubClient(RestClient.builder(), new ObjectMapper(), properties);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sendsBearerTokenAndAcceptsTypedResponses() {
        var response = client.repository(new RepositoryRef("acme", "demo", null), "\"old\"");
        assertThat(response.body().default_branch()).isEqualTo("main");
        assertThat(response.etag()).isEqualTo("\"repo-v1\"");
        assertThat(authorization).hasValue("Bearer secret");
        assertThat(ifNoneMatch).hasValue("\"old\"");
    }

    @Test
    void mapsNotModifiedAndNotFound() {
        assertThatThrownBy(
                        () -> client.content(new RepositoryRef("acme", "demo", null), "src/app.ts", "abc", "\"old\""))
                .isInstanceOf(GitHubFetchException.class)
                .extracting("kind")
                .isEqualTo(GitHubFetchException.Kind.NOT_MODIFIED);
        assertThatThrownBy(() -> client.repository(new RepositoryRef("acme", "missing", null)))
                .isInstanceOf(GitHubFetchException.class)
                .extracting("kind")
                .isEqualTo(GitHubFetchException.Kind.NOT_FOUND);
    }

    @Test
    void requestsExpectedRefTreeAndContentPathsAndPreservesContentEtag() {
        var ref = new RepositoryRef("acme", "demo", "release/2026");
        client.commit(ref, "release/2026");
        assertThat(requestUri).hasValueSatisfying(uri -> {
            assertThat(uri.getPath()).isEqualTo("/repos/acme/demo/commits/release/2026");
            assertThat(uri.getRawQuery()).isNull();
        });
        client.tree(ref, "abc123");
        assertThat(requestUri).hasValueSatisfying(uri -> {
            assertThat(uri.getPath()).isEqualTo("/repos/acme/demo/git/trees/abc123");
            assertThat(uri.getRawQuery()).isEqualTo("recursive=1");
        });
        var response = client.content(ref, "src/etag.ts", "abc123", "\"old-content\"");
        assertThat(requestUri).hasValueSatisfying(uri -> {
            assertThat(uri.getPath()).isEqualTo("/repos/acme/demo/contents/src/etag.ts");
            assertThat(uri.getRawQuery()).isEqualTo("ref=abc123");
        });
        assertThat(ifNoneMatch).hasValue("\"old-content\"");
        assertThat(response.etag()).isEqualTo("\"content-v1\"");
        assertThat(response.body().decoded()).isEqualTo("a");
    }

    @Test
    void distinguishesForbidden403FromRateLimitedResponses() {
        assertThatThrownBy(() -> client.repository(new RepositoryRef("acme", "forbidden", null)))
                .isInstanceOf(GitHubFetchException.class)
                .extracting("kind")
                .isEqualTo(GitHubFetchException.Kind.FORBIDDEN);
        assertThatThrownBy(() -> client.repository(new RepositoryRef("acme", "rate-403", null)))
                .isInstanceOf(GitHubFetchException.class)
                .extracting("kind")
                .isEqualTo(GitHubFetchException.Kind.RATE_LIMITED);
        assertThatThrownBy(() -> client.repository(new RepositoryRef("acme", "rate-429", null)))
                .isInstanceOf(GitHubFetchException.class)
                .extracting("kind")
                .isEqualTo(GitHubFetchException.Kind.RATE_LIMITED);
    }

    @Test
    void rejectsMalformedUtf8Content() {
        var content = new GitHubClient.ContentDto("file", "base64", "/w==", 1L);
        assertThatThrownBy(content::decodedBytes)
                .isInstanceOf(GitHubFetchException.class)
                .extracting("kind")
                .isEqualTo(GitHubFetchException.Kind.UPSTREAM);
    }

    @Test
    void omitsAuthorizationWhenTokenIsBlank() {
        GitHubProperties properties = new GitHubProperties(
                "",
                URI.create("http://localhost:" + server.getAddress().getPort()),
                new com.cartograph.ingestion.IndexingLimits(10, 1000, 100));
        GitHubClient noToken = new GitHubClient(RestClient.builder(), new ObjectMapper(), properties);
        noToken.repository(new RepositoryRef("acme", "demo", null));
        assertThat(authorization).hasValue(null);
    }

    @Test
    void rejectsOversizedContentLengthBeforeMaterializingBody() {
        GitHubProperties properties = new GitHubProperties(
                "",
                URI.create("http://localhost:" + server.getAddress().getPort()),
                new com.cartograph.ingestion.IndexingLimits(10, 1000, 100));
        properties.setMaxResponseBytes(8);
        GitHubClient bounded = new GitHubClient(RestClient.builder(), new ObjectMapper(), properties);
        assertThatThrownBy(() -> bounded.repository(new RepositoryRef("acme", "demo", null)))
                .isInstanceOf(GitHubFetchException.class)
                .extracting("status")
                .isEqualTo(413);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body, String etag)
            throws java.io.IOException {
        if (etag != null) exchange.getResponseHeaders().set("ETag", etag);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, status == 304 ? -1 : bytes.length);
        if (status != 304) exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
