package com.cartograph.ingestion.github;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins eviction behavior under both configured bounds:
 * {@code cache-max-entries} and {@code cache-max-bytes}.
 */
class GitHubResponseCacheEvictionTest {
    private byte[] body(int bytes) {
        return "b".repeat(bytes).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void evictsLeastRecentlyUsedWhenEntryCountIsExceeded() {
        GitHubResponseCache cache = new GitHubResponseCache(2, 1_000_000);
        cache.put("/a", body(10), "\"etag-a\"");
        cache.put("/b", body(10), "\"etag-b\"");
        cache.put("/c", body(10), "\"etag-c\"");

        assertNull(cache.get("/a"));
        assertNotNull(cache.get("/b"));
        assertNotNull(cache.get("/c"));
    }

    @Test
    void touchingAnEntryProtectsItFromEviction() {
        GitHubResponseCache cache = new GitHubResponseCache(2, 1_000_000);
        cache.put("/a", body(10), "\"etag-a\"");
        cache.put("/b", body(10), "\"etag-b\"");
        assertNotNull(cache.get("/a"));
        cache.put("/c", body(10), "\"etag-c\"");

        assertNotNull(cache.get("/a"));
        assertNull(cache.get("/b"));
        assertNotNull(cache.get("/c"));
    }

    @Test
    void byteCeilingEvictsOldestUntilTheNewEntryFits() {
        GitHubResponseCache cache = new GitHubResponseCache(100, 1_000);
        cache.put("/a", body(400), "\"etag-a\"");
        cache.put("/b", body(400), "\"etag-b\"");
        cache.put("/c", body(400), "\"etag-c\"");

        assertNull(cache.get("/a"));
        assertNotNull(cache.get("/b"));
        assertNotNull(cache.get("/c"));
    }

    @Test
    void oversizedSingleEntryIsNotCachedAndEvictsNothing() {
        GitHubResponseCache cache = new GitHubResponseCache(10, 1_000);
        cache.put("/small", body(50), "\"etag-small\"");
        cache.put("/huge", body(5_000), "\"etag-huge\"");

        assertNull(cache.get("/huge"));
        assertNotNull(cache.get("/small"));
    }

    @Test
    void blankOrMissingEtagsAreNeverCached() {
        GitHubResponseCache cache = new GitHubResponseCache(10, 1_000_000);
        cache.put("/null-etag", body(10), null);
        cache.put("/blank-etag", body(10), "   ");

        assertNull(cache.get("/null-etag"));
        assertNull(cache.get("/blank-etag"));
    }

    @Test
    void replacingAnEntryDoesNotDoubleCountItsBytes() {
        GitHubResponseCache cache = new GitHubResponseCache(3, 10_000);
        cache.put("/a", body(10), "\"etag-a-1\"");
        cache.put("/a", body(300), "\"etag-a-2\"");
        cache.put("/b", body(10), "\"etag-b\"");
        cache.put("/c", body(10), "\"etag-c\"");

        assertEquals("\"etag-a-2\"", cache.get("/a").etag());
        assertNotNull(cache.get("/b"));
        assertNotNull(cache.get("/c"));
    }
}
