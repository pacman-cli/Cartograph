package com.cartograph.ingestion.github;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.cartograph.graph.model.RepositoryRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

class GitHubClientTimeoutTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timesOutWhenHeadersOrBodyStall(boolean sendHeaders) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var release = new CountDownLatch(1);
        server.createContext("/", exchange -> {
            try {
                if (sendHeaders) {
                    exchange.sendResponseHeaders(200, 100);
                    exchange.getResponseBody().write('{');
                    exchange.getResponseBody().flush();
                }
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            var properties = new GitHubProperties();
            properties.setBaseUrl(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
            properties.setReadTimeoutMillis(100);
            properties.setConnectTimeoutMillis(100);
            properties.setMaxAttempts(1);
            var client = new GitHubClient(
                    RestClient.builder(), new ObjectMapper(), properties, Clock.systemUTC(), millis -> {
                        throw new AssertionError("Unexpected retry");
                    });
            assertTimeoutPreemptively(
                    Duration.ofSeconds(2),
                    () -> assertThatThrownBy(() -> client.repository(new RepositoryRef("acme", "demo", null)))
                            .isInstanceOf(GitHubFetchException.class)
                            .hasMessage("GitHub transport request failed")
                            .hasNoCause());
        } finally {
            release.countDown();
            server.stop(0);
        }
    }
}
