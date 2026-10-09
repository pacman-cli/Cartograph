package com.cartograph.api;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded, TTL-aware store for completed idempotent requests. Synchronized:
 * indexing is expensive, so lock contention on the cache is negligible.
 * Eviction is oldest-first by insertion and by expiry.
 */
public final class IdempotencyStore {
    /** Outcome of {@link #lookup}. */
    public enum Outcome {
        MISS,
        REPLAY,
        CONFLICT
    }

    public record Entry(int status, String contentType, byte[] body) {}

    private record Cached(long storedAtMillis, String bodyHash, Entry entry) {}

    private final int maxEntries;
    private final long ttlMillis;
    private final Clock clock;
    private final Map<String, Cached> entries = new LinkedHashMap<>(16, .75f, true);

    public IdempotencyStore(int maxEntries, long ttlSeconds, Clock clock) {
        this.maxEntries = maxEntries;
        this.ttlMillis = ttlSeconds * 1_000;
        this.clock = clock;
    }

    /**
     * Looks up the key. REPLAY carries the stored response for an identical
     * body; CONFLICT means the key was used with a different body.
     */
    public synchronized Record lookup(String key, String bodyHash) {
        evictExpired();
        Cached cached = entries.get(key);
        if (cached == null) return new Record(Outcome.MISS, null);
        if (!cached.bodyHash().equals(bodyHash)) return new Record(Outcome.CONFLICT, null);
        touch(key, cached);
        return new Record(Outcome.REPLAY, cached.entry());
    }

    /** Stores a completed response; oldest entries are evicted beyond max-entries. */
    public synchronized void store(String key, String bodyHash, Entry entry) {
        evictExpired();
        touch(key, new Cached(clockMillis(), bodyHash, entry));
        while (entries.size() > maxEntries) {
            String eldest = entries.keySet().iterator().next();
            entries.remove(eldest);
        }
    }

    private void touch(String key, Cached cached) {
        entries.remove(key);
        entries.put(key, cached);
    }

    private void evictExpired() {
        long now = clockMillis();
        entries.entrySet().removeIf(next -> now - next.getValue().storedAtMillis() > ttlMillis);
    }

    private long clockMillis() {
        return clock.instant().toEpochMilli();
    }

    /** Immutable lookup result: outcome plus the stored entry when REPLAY. */
    public record Record(Outcome outcome, Entry entry) {}
}
