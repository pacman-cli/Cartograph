package com.cartograph.ingestion.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.cartograph.graph.model.RepositoryRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubClientResilienceTest {
    private static final String ROOT = "https://api.github.test/repos/acme/";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final GitHubProperties properties = new GitHubProperties();
    private final List<Long> sleeps = new ArrayList<>();
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.test");
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();

    private GitHubClient client() {
        return new GitHubClient(builder.build(), new ObjectMapper(), properties, CLOCK, sleeps::add);
    }

    private RepositoryRef ref(String name) {
        return new RepositoryRef("acme", name, null);
    }

    @Test
    void retriesServerErrorsExponentiallyAndStopsAtAttemptBound() {
        for (int i = 0; i < 3; i++)
            server.expect(requestTo(ROOT + "demo"))
                    .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("secret upstream body"));
        assertThatThrownBy(() -> client().repository(ref("demo")))
                .isInstanceOf(GitHubFetchException.class)
                .hasMessage("GitHub API returned HTTP 503")
                .hasNoCause();
        assertThat(sleeps).containsExactly(250L, 500L);
        server.verify();
    }

    @Test
    void retriesTransportFailureThenRecoversWithoutExposingTransportDetails() {
        server.expect(requestTo(ROOT + "demo")).andRespond(request -> {
            throw new IOException("private URL secret");
        });
        server.expect(requestTo(ROOT + "demo"))
                .andRespond(withSuccess("{\"default_branch\":\"main\"}", MediaType.APPLICATION_JSON));
        assertThat(client().repository(ref("demo")).body().default_branch()).isEqualTo("main");
        assertThat(sleeps).containsExactly(250L);
        server.verify();
    }

    @Test
    void transportExhaustionHasSafeTypedError() {
        properties.setMaxAttempts(2);
        for (int i = 0; i < 2; i++)
            server.expect(requestTo(ROOT + "demo")).andRespond(request -> {
                throw new IOException("token=secret");
            });
        assertThatThrownBy(() -> client().repository(ref("demo")))
                .isInstanceOf(GitHubFetchException.class)
                .hasMessage("GitHub transport request failed")
                .hasNoCause();
        assertThat(sleeps).containsExactly(250L);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "Thu, 1 Jan 2026 00:00:02 GMT"})
    void honorsSecondsAndHttpDateRetryAfter(String retryAfter) {
        server.expect(requestTo(ROOT + "demo"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", retryAfter));
        server.expect(requestTo(ROOT + "demo"))
                .andRespond(withSuccess("{\"default_branch\":\"main\"}", MediaType.APPLICATION_JSON));
        assertThat(client().repository(ref("demo")).body().default_branch()).isEqualTo("main");
        assertThat(sleeps).containsExactly(2000L);
        server.verify();
    }

    @Test
    void primaryResetWinsOverShorterRetryAfter() {
        server.expect(requestTo(ROOT + "demo"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .header("X-RateLimit-Remaining", "0")
                        .header("X-RateLimit-Reset", "1767225603")
                        .header("Retry-After", "1"));
        server.expect(requestTo(ROOT + "demo"))
                .andRespond(withSuccess("{\"default_branch\":\"main\"}", MediaType.APPLICATION_JSON));
        client().repository(ref("demo"));
        assertThat(sleeps).containsExactly(3000L);
        server.verify();
    }

    @Test
    void ordinaryForbiddenWithResetHeaderIsNeverRetried() {
        server.expect(requestTo(ROOT + "demo"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .header("X-RateLimit-Remaining", "4999")
                        .header("X-RateLimit-Reset", "1767225603")
                        .body("{\"message\":\"Resource not accessible by integration\"}"));
        assertThatThrownBy(() -> client().repository(ref("demo")))
                .isInstanceOf(GitHubFetchException.class)
                .extracting("kind")
                .isEqualTo(GitHubFetchException.Kind.FORBIDDEN);
        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    void longPrimaryResetAndLong503RetryAfterAreNotRetriedEarly() {
        server.expect(requestTo(ROOT + "primary"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .header("X-RateLimit-Remaining", "0")
                        .header("X-RateLimit-Reset", "1767225660"));
        server.expect(requestTo(ROOT + "unavailable"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "60"));
        GitHubClient client = client();
        assertThatThrownBy(() -> client.repository(ref("primary")))
                .isInstanceOf(GitHubFetchException.class)
                .extracting("kind")
                .isEqualTo(GitHubFetchException.Kind.RATE_LIMITED);
        assertThatThrownBy(() -> client.repository(ref("unavailable")))
                .isInstanceOf(GitHubFetchException.class)
                .extracting("kind")
                .isEqualTo(GitHubFetchException.Kind.UPSTREAM);
        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    void missingOrMalformedRateDelayUsesConservativeDefault() {
        for (String repo : List.of("missing", "malformed"))
            server.expect(requestTo(ROOT + repo))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                            .header("Retry-After", repo.equals("missing") ? "" : "not-a-date"));
        GitHubClient client = client();
        for (String repo : List.of("missing", "malformed"))
            assertThatThrownBy(() -> client.repository(ref(repo)))
                    .isInstanceOf(GitHubFetchException.class)
                    .extracting("kind")
                    .isEqualTo(GitHubFetchException.Kind.RATE_LIMITED);
        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"60", "999999999999999999999999999999"})
    void neverShortensLongServerDelayToFitBudget(String delay) {
        server.expect(requestTo(ROOT + "demo"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", delay));
        assertThatThrownBy(() -> client().repository(ref("demo")))
                .isInstanceOf(GitHubFetchException.class)
                .extracting("kind")
                .isEqualTo(GitHubFetchException.Kind.RATE_LIMITED);
        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    void secondaryLimitWithoutHeaderWaitsAtLeastMinuteAndBacksOff() {
        properties.setMaxRetrySleepMillis(180_000);
        for (int i = 0; i < 3; i++)
            server.expect(requestTo(ROOT + "demo"))
                    .andRespond(withStatus(HttpStatus.FORBIDDEN)
                            .body("{\"message\":\"You have exceeded a secondary rate limit.\"}"));
        assertThatThrownBy(() -> client().repository(ref("demo")))
                .isInstanceOf(GitHubFetchException.class)
                .extracting("kind")
                .isEqualTo(GitHubFetchException.Kind.RATE_LIMITED);
        assertThat(sleeps).containsExactly(60_000L, 120_000L);
        server.verify();
    }

    @Test
    void boundsTotalSleepEvenWithAttemptsRemaining() {
        properties.setMaxAttempts(10);
        properties.setMaxRetrySleepMillis(600);
        for (int i = 0; i < 2; i++)
            server.expect(requestTo(ROOT + "demo")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(() -> client().repository(ref("demo"))).isInstanceOf(GitHubFetchException.class);
        assertThat(sleeps).containsExactly(250L);
        server.verify();
    }

    @Test
    void interruptedBackoffPreservesInterruptAndStops() {
        server.expect(requestTo(ROOT + "demo")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        GitHubClient client = new GitHubClient(builder.build(), new ObjectMapper(), properties, CLOCK, millis -> {
            throw new InterruptedException();
        });
        try {
            assertThatThrownBy(() -> client.repository(ref("demo")))
                    .isInstanceOf(GitHubFetchException.class)
                    .hasMessage("GitHub request interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        server.verify();
    }

    @Test
    void revalidatesEtagAndReturnsCachedBodyFor304() {
        server.expect(requestTo(ROOT + "demo"))
                .andExpect(headerDoesNotExist("If-None-Match"))
                .andRespond(withSuccess("{\"default_branch\":\"main\",\"ignored\":true}", MediaType.APPLICATION_JSON)
                        .header("ETag", "\"v1\""));
        server.expect(requestTo(ROOT + "demo"))
                .andExpect(header("If-None-Match", "\"v1\""))
                .andRespond(withStatus(HttpStatus.NOT_MODIFIED));
        GitHubClient client = client();
        var initial = client.repository(ref("demo"));
        assertThat(client.repository(ref("demo"))).isEqualTo(initial);
        server.verify();
    }

    @Test
    void mismatchedExplicitEtagCannotReturnWrongCachedBody() {
        server.expect(requestTo(ROOT + "demo"))
                .andRespond(withSuccess("{\"default_branch\":\"main\"}", MediaType.APPLICATION_JSON)
                        .header("ETag", "\"v1\""));
        server.expect(requestTo(ROOT + "demo"))
                .andExpect(header("If-None-Match", "\"other\""))
                .andRespond(withStatus(HttpStatus.NOT_MODIFIED));
        GitHubClient client = client();
        client.repository(ref("demo"));
        assertThatThrownBy(() -> client.repository(ref("demo"), "\"other\""))
                .isInstanceOf(GitHubFetchException.class)
                .extracting("kind")
                .isEqualTo(GitHubFetchException.Kind.NOT_MODIFIED);
        server.verify();
    }

    @Test
    void freshClientDoesNotReuseAnotherClientsPrivateCache() {
        properties.setToken("first-identity");
        GitHubClient first = client();
        properties.setToken("second-identity");
        GitHubClient second = client();
        server.expect(requestTo(ROOT + "demo"))
                .andExpect(header("Authorization", "Bearer first-identity"))
                .andRespond(withSuccess("{\"default_branch\":\"main\"}", MediaType.APPLICATION_JSON)
                        .header("ETag", "\"v1\""));
        server.expect(requestTo(ROOT + "demo"))
                .andExpect(header("Authorization", "Bearer second-identity"))
                .andExpect(headerDoesNotExist("If-None-Match"))
                .andRespond(withSuccess("{\"default_branch\":\"different\"}", MediaType.APPLICATION_JSON));
        first.repository(ref("demo"));
        assertThat(second.repository(ref("demo")).body().default_branch()).isEqualTo("different");
        server.verify();
    }

    @Test
    void evictsLeastRecentlyUsedCacheEntryAtConfiguredCount() {
        properties.setCacheMaxEntries(1);
        for (String repo : List.of("one", "two", "one")) {
            server.expect(requestTo(ROOT + repo))
                    .andExpect(headerDoesNotExist("If-None-Match"))
                    .andRespond(withSuccess("{\"default_branch\":\"main\"}", MediaType.APPLICATION_JSON)
                            .header("ETag", "\"v1\""));
        }
        GitHubClient client = client();
        for (String repo : List.of("one", "two", "one")) client.repository(ref(repo));
        server.verify();
    }

    @Test
    void responsesTooLargeForCacheAreStillUsableButNotRetained() {
        properties.setCacheMaxBytes(1);
        for (int i = 0; i < 2; i++)
            server.expect(requestTo(ROOT + "demo"))
                    .andExpect(headerDoesNotExist("If-None-Match"))
                    .andRespond(withSuccess("{\"default_branch\":\"main\"}", MediaType.APPLICATION_JSON)
                            .header("ETag", "\"v1\""));
        GitHubClient client = client();
        client.repository(ref("demo"));
        client.repository(ref("demo"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "null",
                "{\"default_branch\":null}",
                "{\"default_branch\":\" \"}",
                "{\"default_branch\":\"main\"} {}",
                "{\"private-token\":"
            })
    void rejectsInvalidRepositoryPayloadWithoutLeakingBody(String body) {
        server.expect(requestTo(ROOT + "demo")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client().repository(ref("demo")))
                .isInstanceOf(GitHubFetchException.class)
                .hasMessage("Invalid GitHub response")
                .hasNoCause();
        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"truncated\":false}",
                "{\"tree\":[]}",
                "{\"truncated\":false,\"tree\":[null]}",
                "{\"truncated\":false,\"tree\":[{\"path\":\"a.ts\",\"type\":\"blob\",\"sha\":\"abc\"}]}",
                "{\"truncated\":false,\"tree\":[{\"type\":\"blob\",\"size\":1,\"sha\":\"abc\"}]}"
            })
    void rejectsMissingTreeFields(String body) {
        server.expect(requestTo(ROOT + "demo/git/trees/abc?recursive=1"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client().tree(ref("demo"), "abc"))
                .isInstanceOf(GitHubFetchException.class)
                .extracting("kind")
                .isEqualTo(GitHubFetchException.Kind.UPSTREAM);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"encoding\":\"base64\",\"content\":\"YQ==\"}",
                "{\"type\":\"file\",\"size\":1,\"encoding\":\"base64\",\"content\":null}",
                "{\"type\":\"file\",\"size\":1,\"encoding\":\"base64\",\"content\":\"Y!Q==\"}"
            })
    void rejectsInvalidContentBeforeCachingIt(String body) {
        String url = ROOT + "demo/contents/a.ts?ref=abc";
        server.expect(requestTo(url))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON).header("ETag", "\"bad\""));
        server.expect(requestTo(url))
                .andExpect(headerDoesNotExist("If-None-Match"))
                .andRespond(withSuccess(
                        "{\"type\":\"file\",\"size\":1,\"encoding\":\"base64\",\"content\":\"YQ==\"}",
                        MediaType.APPLICATION_JSON));
        GitHubClient client = client();
        assertThatThrownBy(() -> client.content(ref("demo"), "a.ts", "abc", null))
                .isInstanceOf(GitHubFetchException.class)
                .hasNoCause();
        assertThat(client.content(ref("demo"), "a.ts", "abc", null).body().decoded())
                .isEqualTo("a");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Y!Q==", "Y Q==", "YQ", "YR==", "/w=="})
    void rejectsInvalidOrNonCanonicalBase64AndInvalidUtf8(String encoded) {
        assertThatThrownBy(() -> new GitHubClient.ContentDto("file", "base64", encoded, 1L).decodedBytes())
                .isInstanceOf(GitHubFetchException.class)
                .hasNoCause();
    }

    @Test
    void permitsGithubLineWrappingAndEmptyFileButRejectsSizeMismatch() {
        assertThat(new GitHubClient.ContentDto("file", "base64", "YQ==\r\n", 1L).decoded())
                .isEqualTo("a");
        assertThat(new GitHubClient.ContentDto("file", "base64", "", 0L).decoded())
                .isEmpty();
        assertThatThrownBy(() -> new GitHubClient.ContentDto("file", "base64", "YQ==", 2L).decodedBytes())
                .isInstanceOf(GitHubFetchException.class);
        assertThatThrownBy(() -> new GitHubClient.ContentDto("file", "base64", null, 0L).decodedBytes())
                .isInstanceOf(GitHubFetchException.class);
    }

    @Test
    void boundsBodiesWithoutContentLength() {
        properties.setMaxResponseBytes(8);
        server.expect(requestTo(ROOT + "demo"))
                .andRespond(withSuccess("{\"default_branch\":\"main\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client().repository(ref("demo")))
                .isInstanceOf(GitHubFetchException.class)
                .extracting("status")
                .isEqualTo(413);
        server.verify();
    }
}
