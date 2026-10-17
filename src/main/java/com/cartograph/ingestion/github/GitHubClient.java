package com.cartograph.ingestion.github;

import com.cartograph.graph.model.RepositoryRef;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** Small typed boundary around the GitHub REST API. DTOs intentionally stay adapter-local. */
public class GitHubClient {
    private final RestClient http;
    private final ObjectMapper mapper;
    private final GitHubProperties properties;
    private final Clock clock;
    private final Sleeper sleeper;
    private final GitHubResponseCache cache;
    // Freeze credential scope: configuration changes cannot reuse another identity's cache.
    private final String token;

    /** Delay hook used between retry attempts; implementations may be interrupted. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    public GitHubClient(RestClient.Builder builder, ObjectMapper mapper, GitHubProperties properties) {
        this(builder, mapper, properties, Clock.systemUTC(), Thread::sleep);
    }

    public GitHubClient(
            RestClient.Builder builder,
            ObjectMapper mapper,
            GitHubProperties properties,
            Clock clock,
            Sleeper sleeper) {
        this(configuredClient(builder, properties), mapper, properties, clock, sleeper);
    }

    public GitHubClient(RestClient http, ObjectMapper mapper, GitHubProperties properties) {
        this(http, mapper, properties, Clock.systemUTC(), Thread::sleep);
    }

    /** A supplied RestClient owns its transport timeouts; the builder overload configures them. */
    public GitHubClient(
            RestClient http, ObjectMapper mapper, GitHubProperties properties, Clock clock, Sleeper sleeper) {
        this.http = http;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
        this.sleeper = sleeper;
        this.token = properties.token();
        this.cache = new GitHubResponseCache(properties.cacheMaxEntries(), properties.cacheMaxBytes());
    }

    private static RestClient configuredClient(RestClient.Builder builder, GitHubProperties properties) {
        var transport = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMillis()))
                .build();
        var factory = new JdkClientHttpRequestFactory(transport);
        factory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMillis()));
        return builder.clone()
                .requestFactory(factory)
                .baseUrl(properties.baseUrl().toString())
                .build();
    }

    public Response<RepositoryDto> repository(RepositoryRef ref) {
        return repository(ref, null);
    }

    public Response<RepositoryDto> repository(RepositoryRef ref, String etag) {
        return get(
                "/repos/" + ref.owner() + "/" + ref.repository(),
                RepositoryDto.class,
                etag,
                properties.maxResponseBytes());
    }

    public Response<CommitDto> commit(RepositoryRef ref, String requestedRef) {
        return get(
                "/repos/" + ref.owner() + "/" + ref.repository() + "/commits/" + encodePath(requestedRef),
                CommitDto.class,
                null,
                properties.maxResponseBytes());
    }

    public Response<TreeDto> tree(RepositoryRef ref, String sha) {
        return get(
                "/repos/" + ref.owner() + "/" + ref.repository() + "/git/trees/" + encodePath(sha) + "?recursive=1",
                TreeDto.class,
                null,
                properties.maxResponseBytes());
    }

    public Response<ContentDto> content(RepositoryRef ref, String path, String sha, String etag) {
        long encodedContentLimit = Math.min(
                properties.maxResponseBytes(),
                encodedContentLimit(properties.limits().maxFileBytes()));
        return get(
                "/repos/" + ref.owner() + "/" + ref.repository() + "/contents/" + encodePath(path) + "?ref="
                        + encodePath(sha),
                ContentDto.class,
                etag,
                encodedContentLimit);
    }

    // Serial requests avoid amplifying secondary limits and protect the LRU.
    private synchronized <T> Response<T> get(String path, Class<T> type, String etag, long responseLimit) {
        GitHubResponseCache.Entry cached = cache.get(path);
        String conditionalEtag = etag != null && !etag.isBlank() ? etag : cached == null ? null : cached.etag();
        long slept = 0;
        for (int attempt = 1; ; attempt++) {
            try {
                return exchange(path, type, conditionalEtag, responseLimit, cached, attempt);
            } catch (Retryable failure) {
                if (attempt >= properties.maxAttempts() || failure.delay > properties.maxRetrySleepMillis() - slept) {
                    throw failure.safeError;
                }
                pause(failure.delay);
                slept += failure.delay;
            } catch (ResourceAccessException exception) {
                long delay = GitHubRetryPolicy.exponential(properties.retryBackoffMillis(), attempt);
                if (attempt >= properties.maxAttempts() || delay > properties.maxRetrySleepMillis() - slept) {
                    throw new GitHubFetchException(
                            GitHubFetchException.Kind.UPSTREAM, 0, "GitHub transport request failed");
                }
                pause(delay);
                slept += delay;
            }
        }
    }

    private void pause(long millis) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GitHubFetchException(GitHubFetchException.Kind.UPSTREAM, 0, "GitHub request interrupted");
        }
    }

    private <T> Response<T> exchange(
            String path,
            Class<T> type,
            String etag,
            long responseLimit,
            GitHubResponseCache.Entry cached,
            int attempt) {
        return http.get()
                .uri(path)
                .headers(headers -> {
                    headers.set(HttpHeaders.ACCEPT, "application/vnd.github+json");
                    headers.set(HttpHeaders.USER_AGENT, "Cartograph");
                    if (!token.isBlank()) headers.setBearerAuth(token);
                    if (etag != null && !etag.isBlank()) headers.set(HttpHeaders.IF_NONE_MATCH, etag);
                })
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    String responseEtag = response.getHeaders().getFirst(HttpHeaders.ETAG);
                    if (status == 304) {
                        if (cached != null && cached.etag().equals(etag) && cached.body().length <= responseLimit) {
                            return new Response<>(decode(cached.body(), type, status), cached.etag());
                        }
                        throw new GitHubFetchException(
                                GitHubFetchException.Kind.NOT_MODIFIED,
                                status,
                                "GitHub response was not modified without a matching cached body");
                    }
                    if (status == 404)
                        throw new GitHubFetchException(
                                GitHubFetchException.Kind.NOT_FOUND, status, "GitHub resource was not found");
                    boolean rateLimited = status == 429
                            || (status == 403
                                    && (isRateLimited403(response.getHeaders())
                                            || secondaryLimitBody(response.getBody())));
                    if (rateLimited || status >= 500) {
                        var kind = rateLimited
                                ? GitHubFetchException.Kind.RATE_LIMITED
                                : GitHubFetchException.Kind.UPSTREAM;
                        var error = new GitHubFetchException(
                                kind,
                                status,
                                rateLimited ? "GitHub API rate limit exceeded" : "GitHub API returned HTTP " + status);
                        throw new Retryable(
                                error,
                                GitHubRetryPolicy.delayMillis(
                                        response.getHeaders(),
                                        rateLimited,
                                        attempt,
                                        properties.retryBackoffMillis(),
                                        clock));
                    }
                    if (status == 403) {
                        throw new GitHubFetchException(
                                GitHubFetchException.Kind.FORBIDDEN, status, "GitHub API permission denied");
                    }
                    if (status < 200 || status >= 300)
                        throw new GitHubFetchException(
                                GitHubFetchException.Kind.UPSTREAM, status, "GitHub API returned HTTP " + status);
                    byte[] bytes = readBounded(
                            response.getBody(), response.getHeaders().getContentLength(), responseLimit);
                    T body = decode(bytes, type, status);
                    cache.put(path, bytes, responseEtag);
                    return new Response<>(body, responseEtag);
                });
    }

    private <T> T decode(byte[] bytes, Class<T> type, int status) {
        try {
            T body = mapper.readerFor(type)
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(bytes);
            validate(body);
            return body;
        } catch (IOException | IllegalArgumentException exception) {
            // Parser exceptions can contain response fragments: never attach them as causes.
            throw new GitHubFetchException(GitHubFetchException.Kind.UPSTREAM, status, "Invalid GitHub response");
        }
    }

    private static void validate(Object body) {
        if (body instanceof RepositoryDto repository) {
            requireText(repository.default_branch());
        } else if (body instanceof CommitDto commit) {
            requireText(commit.sha());
        } else if (body instanceof TreeDto tree) {
            if (tree.truncated() == null || tree.tree() == null) throw new IllegalArgumentException();
            for (TreeEntry entry : tree.tree()) {
                if (entry == null) throw new IllegalArgumentException();
                requireText(entry.path());
                requireText(entry.sha());
                if (!List.of("blob", "tree", "commit").contains(entry.type() == null ? "" : entry.type()))
                    throw new IllegalArgumentException();
                if (("blob".equals(entry.type()) && entry.size() == null) || (entry.size() != null && entry.size() < 0))
                    throw new IllegalArgumentException();
            }
        } else if (body instanceof ContentDto content) {
            content.decodedBytes();
        } else {
            throw new IllegalArgumentException();
        }
    }

    private static void requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException();
    }

    private static final class Retryable extends RuntimeException {
        private final GitHubFetchException safeError;
        private final long delay;

        Retryable(GitHubFetchException safeError, long delay) {
            super(safeError.getMessage());
            this.safeError = safeError;
            this.delay = delay;
        }
    }

    private static long encodedContentLimit(long maxFileBytes) {
        try {
            long base64Bytes = Math.multiplyExact(Math.addExact(maxFileBytes, 2) / 3, 4);
            // GitHub's wrapped content includes JSON-escaped newlines every 60 base64 characters.
            long wrappingBytes = Math.multiplyExact(Math.addExact(base64Bytes, 59) / 60, 2);
            return Math.addExact(Math.addExact(base64Bytes, wrappingBytes), 4096);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static byte[] readBounded(InputStream body, long contentLength, long maxBytes) throws IOException {
        if (maxBytes <= 0) throw new IllegalArgumentException("Maximum response size must be positive");
        if (contentLength > maxBytes)
            throw new GitHubFetchException(
                    GitHubFetchException.Kind.UPSTREAM, 413, "GitHub response exceeded the configured response limit");
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream((int) Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = body.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes)
                throw new GitHubFetchException(
                        GitHubFetchException.Kind.UPSTREAM,
                        413,
                        "GitHub response exceeded the configured response limit");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static boolean isRateLimited403(HttpHeaders headers) {
        return "0".equals(headers.getFirst("X-RateLimit-Remaining")) || headers.containsKey("Retry-After");
    }

    private boolean secondaryLimitBody(InputStream body) throws IOException {
        // Error bodies never escape the adapter or enter the cache. Inspect only a bounded prefix.
        byte[] bytes = body.readNBytes((int) Math.min(properties.maxResponseBytes(), 16_384));
        try {
            String message = mapper.readTree(bytes).path("message").asText("").toLowerCase(Locale.ROOT);
            return message.contains("secondary rate limit")
                    || message.contains("abuse detection")
                    || message.contains("api rate limit exceeded");
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private static String encodePath(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%2F", "/");
    }

    /**
     * Body and ETag returned by a GitHub REST request.
     *
     * @param <T> response body type
     * @param body decoded response body
     * @param etag entity tag that can be reused for a conditional request
     */
    public record Response<T>(T body, String etag) {}

    /** Repository metadata returned by GitHub, including its default branch name. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RepositoryDto(String default_branch) {}

    /** Commit metadata returned by GitHub, including the resolved commit SHA. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommitDto(String sha) {}

    /** Recursive Git tree response, including GitHub's truncation flag and returned entries. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TreeDto(Boolean truncated, List<TreeEntry> tree) {}

    /** Git tree item with a path, object type, optional size, and object SHA. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TreeEntry(String path, String type, Long size, String sha) {}

    /**
     * Base64-encoded GitHub file-content response.
     *
     * @param type GitHub content type, expected to be {@code file}
     * @param encoding GitHub content encoding, expected to be {@code base64}
     * @param content base64 content, optionally wrapped in newline characters
     * @param size reported decoded byte count
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentDto(String type, String encoding, String content, Long size) {
        /**
         * Validates the metadata, Base64 encoding, reported byte count, and UTF-8 content.
         *
         * @return decoded file bytes
         * @throws GitHubFetchException if the response metadata or content is invalid
         */
        public byte[] decodedBytes() {
            if (!"file".equals(type) || !"base64".equals(encoding) || content == null || size == null || size < 0) {
                throw new GitHubFetchException(
                        GitHubFetchException.Kind.UPSTREAM, 200, "Invalid GitHub content metadata");
            }
            try {
                // GitHub wraps base64 with newlines; reject other characters rather than silently dropping them.
                String compact = content.replace("\r", "").replace("\n", "");
                byte[] bytes = Base64.getDecoder().decode(compact);
                if (!Base64.getEncoder().encodeToString(bytes).equals(compact) || bytes.length != size) {
                    throw new IllegalArgumentException();
                }
                // Decode strictly so replacement characters can never enter a snapshot.
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(bytes));
                return bytes;
            } catch (java.io.IOException | IllegalArgumentException exception) {
                throw new GitHubFetchException(
                        GitHubFetchException.Kind.UPSTREAM, 200, "Invalid base64 or UTF-8 GitHub content");
            }
        }

        /**
         * Returns the validated content as UTF-8 text.
         *
         * @return decoded file content
         * @throws GitHubFetchException if the response metadata or content is invalid
         */
        public String decoded() {
            return new String(decodedBytes(), StandardCharsets.UTF_8);
        }
    }
}
