package com.cartograph.ingestion.github;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

/** Per-client LRU of validated wire bodies. No credentials appear in keys or persisted state. */
final class GitHubResponseCache {
    record Entry(byte[] body, String etag, long weight) {}

    private final int maxEntries;
    private final long maxBytes;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(16, .75f, true);
    private long bytes;

    GitHubResponseCache(int maxEntries, long maxBytes) {
        this.maxEntries = maxEntries;
        this.maxBytes = maxBytes;
    }

    Entry get(String path) {
        return entries.get(path);
    }

    void remove(String path) {
        Entry old = entries.remove(path);
        if (old != null) bytes -= old.weight();
    }

    void put(String path, byte[] body, String etag) {
        remove(path);
        if (maxEntries == 0 || etag == null || etag.isBlank() || etag.length() > 1024) return;
        long weight = (long) body.length
                + path.getBytes(StandardCharsets.UTF_8).length
                + etag.getBytes(StandardCharsets.UTF_8).length;
        if (weight > maxBytes) return;
        while (entries.size() >= maxEntries || bytes > maxBytes - weight) {
            remove(entries.keySet().iterator().next());
        }
        entries.put(path, new Entry(body, etag, weight));
        bytes += weight;
    }
}
