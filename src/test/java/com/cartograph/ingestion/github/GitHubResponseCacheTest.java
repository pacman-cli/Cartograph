package com.cartograph.ingestion.github;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GitHubResponseCacheTest {
    @Test
    void evictsByByteBudgetInAccessOrderAndDropsEntriesWithoutEtags() {
        var cache = new GitHubResponseCache(10, 12);
        cache.put("a", new byte[3], "v1"); // Six bytes including key and validator.
        cache.put("b", new byte[3], "v1");
        assertThat(cache.get("a")).isNotNull();
        cache.put("c", new byte[3], "v1");
        assertThat(cache.get("b")).isNull();
        assertThat(cache.get("a")).isNotNull();
        assertThat(cache.get("c")).isNotNull();
        cache.put("a", new byte[3], null);
        assertThat(cache.get("a")).isNull();
    }

    @Test
    void oversizedValidatorAndDisabledCacheRetainNothing() {
        var cache = new GitHubResponseCache(10, 10_000);
        cache.put("a", new byte[1], "v".repeat(1025));
        assertThat(cache.get("a")).isNull();
        var disabled = new GitHubResponseCache(0, 10_000);
        disabled.put("a", new byte[1], "v1");
        assertThat(disabled.get("a")).isNull();
    }
}
