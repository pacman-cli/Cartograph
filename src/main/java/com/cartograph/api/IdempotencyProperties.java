package com.cartograph.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bounds of the in-memory idempotency cache for replayed indexing requests. */
@ConfigurationProperties(prefix = "cartograph.idempotency")
public class IdempotencyProperties {
    private long ttlSeconds = 86_400;
    private int maxEntries = 1_000;

    public long ttlSeconds() {
        return ttlSeconds;
    }

    public int maxEntries() {
        return maxEntries;
    }

    public void setTtlSeconds(long value) {
        if (value < 1) throw new IllegalArgumentException("ttl-seconds must be at least 1");
        ttlSeconds = value;
    }

    public void setMaxEntries(int value) {
        if (value < 1) throw new IllegalArgumentException("max-entries must be at least 1");
        maxEntries = value;
    }
}
