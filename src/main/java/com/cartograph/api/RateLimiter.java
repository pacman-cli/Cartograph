package com.cartograph.api;

import java.time.Clock;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal in-process token bucket, one bucket per client key. Buckets start
 * full and refill continuously at {@code refillPerMinute} tokens per minute.
 * State is per instance — a multi-instance deployment shares state through a
 * server DB or queue instead (see ADR 0001).
 */
public final class RateLimiter {
    private final int capacity;
    private final double refillPerNano;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private static final class Bucket {
        double tokens;
        long lastRefillNanos;
        boolean allowed;
        long retryAfterSeconds;
    }

    public RateLimiter(int capacity, int refillPerMinute, Clock clock) {
        if (capacity < 1 || refillPerMinute < 1) {
            throw new IllegalArgumentException("capacity and refillPerMinute must be at least 1");
        }
        this.capacity = capacity;
        this.refillPerNano = refillPerMinute / 60_000_000_000.0;
        this.clock = clock;
    }

    /**
     * Consumes one token for the key if available.
     *
     * @return empty when allowed; present with the seconds until the next
     *         token when the client is over the limit
     */
    public OptionalLong tryAcquire(String key) {
        var instant = clock.instant();
        long now = instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
        Bucket bucket = buckets.compute(key, (ignored, existing) -> {
            Bucket b = existing == null ? new Bucket() : existing;
            if (existing == null) {
                b.tokens = capacity;
                b.lastRefillNanos = now;
            } else if (now > b.lastRefillNanos) {
                b.tokens = Math.min(capacity, b.tokens + (now - b.lastRefillNanos) * refillPerNano);
                b.lastRefillNanos = now;
            }
            if (b.tokens >= 1) {
                b.tokens -= 1;
                b.allowed = true;
            } else {
                double deficit = 1 - b.tokens;
                b.allowed = false;
                b.retryAfterSeconds = Math.max(1, (long) Math.ceil(deficit / refillPerNano / 1_000_000_000.0));
            }
            return b;
        });
        return bucket.allowed ? OptionalLong.empty() : OptionalLong.of(bucket.retryAfterSeconds);
    }
}
