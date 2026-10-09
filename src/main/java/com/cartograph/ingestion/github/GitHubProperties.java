package com.cartograph.ingestion.github;

import com.cartograph.ingestion.IndexingLimits;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration owned by the GitHub adapter; the token is never part of a request DTO. */
@ConfigurationProperties(prefix = "cartograph.github")
public class GitHubProperties {
    private String token = "";
    private URI baseUrl = URI.create("https://api.github.com");
    private long maxFiles = 10_000;
    private long maxTotalBytes = 1_073_741_824;
    private long maxFileBytes = 10_485_760;
    private long maxResponseBytes = 33_554_432;
    private int maxAttempts = 3;
    private long retryBackoffMillis = 250;
    private long maxRetrySleepMillis = 5_000;
    private int connectTimeoutMillis = 5_000;
    private int readTimeoutMillis = 15_000;
    private int cacheMaxEntries = 256;
    private long cacheMaxBytes = 16_777_216;

    public GitHubProperties() {}

    public GitHubProperties(String token, URI baseUrl, IndexingLimits limits) {
        this.token = token == null ? "" : token;
        this.baseUrl = baseUrl == null ? URI.create("https://api.github.com") : baseUrl;
        this.maxFiles = limits.maxFileCount();
        this.maxTotalBytes = limits.maxTotalBytes();
        this.maxFileBytes = limits.maxFileBytes();
    }

    public String token() {
        return token;
    }

    public URI baseUrl() {
        return baseUrl;
    }

    public IndexingLimits limits() {
        return new IndexingLimits(maxFiles, maxTotalBytes, maxFileBytes);
    }

    public long maxResponseBytes() {
        return maxResponseBytes;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public long retryBackoffMillis() {
        return retryBackoffMillis;
    }

    public long maxRetrySleepMillis() {
        return maxRetrySleepMillis;
    }

    public int connectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public int readTimeoutMillis() {
        return readTimeoutMillis;
    }

    public int cacheMaxEntries() {
        return cacheMaxEntries;
    }

    public long cacheMaxBytes() {
        return cacheMaxBytes;
    }

    public void setMaxAttempts(int value) {
        if (value < 1 || value > 10) throw new IllegalArgumentException("GitHub max attempts must be between 1 and 10");
        maxAttempts = value;
    }

    public void setRetryBackoffMillis(long value) {
        if (value < 1) throw new IllegalArgumentException("GitHub retry backoff must be positive");
        retryBackoffMillis = value;
    }

    public void setMaxRetrySleepMillis(long value) {
        if (value < 0) throw new IllegalArgumentException("GitHub retry sleep budget must not be negative");
        maxRetrySleepMillis = value;
    }

    public void setConnectTimeoutMillis(int value) {
        if (value < 1) throw new IllegalArgumentException("GitHub connect timeout must be positive");
        connectTimeoutMillis = value;
    }

    public void setReadTimeoutMillis(int value) {
        if (value < 1) throw new IllegalArgumentException("GitHub read timeout must be positive");
        readTimeoutMillis = value;
    }

    public void setCacheMaxEntries(int value) {
        if (value < 0) throw new IllegalArgumentException("GitHub cache entries must not be negative");
        cacheMaxEntries = value;
    }

    public void setCacheMaxBytes(long value) {
        if (value < 0) throw new IllegalArgumentException("GitHub cache bytes must not be negative");
        cacheMaxBytes = value;
    }

    public void setToken(String token) {
        this.token = token == null ? "" : token;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void setMaxFiles(long value) {
        this.maxFiles = value;
    }

    public void setMaxTotalBytes(long value) {
        this.maxTotalBytes = value;
    }

    public void setMaxFileBytes(long value) {
        this.maxFileBytes = value;
    }

    public void setMaxResponseBytes(long value) {
        this.maxResponseBytes = value;
    }
}
