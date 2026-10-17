package com.cartograph.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class RateLimiterTest {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-03T12:00:00Z"));

    @Test
    void freshBucketStartsFullAndRejectsWhenEmpty() {
        RateLimiter limiter = new RateLimiter(3, 60, clock);
        assertTrue(limiter.tryAcquire("client").isEmpty());
        assertTrue(limiter.tryAcquire("client").isEmpty());
        assertTrue(limiter.tryAcquire("client").isEmpty());
        OptionalLong rejected = limiter.tryAcquire("client");
        assertTrue(rejected.isPresent());
        assertTrue(rejected.getAsLong() >= 1);
    }

    @Test
    void idleTimeRefillsButNeverExceedsCapacity() {
        RateLimiter limiter = new RateLimiter(2, 60, clock);
        assertTrue(limiter.tryAcquire("client").isEmpty());
        assertTrue(limiter.tryAcquire("client").isEmpty());
        assertTrue(limiter.tryAcquire("client").isPresent());
        clock.advanceSeconds(3600);
        assertTrue(limiter.tryAcquire("client").isEmpty());
        assertTrue(limiter.tryAcquire("client").isEmpty());
        assertTrue(limiter.tryAcquire("client").isPresent());
    }

    @Test
    void refillRestoresExactlyOneTokenAtTheRightMoment() {
        RateLimiter limiter = new RateLimiter(1, 60, clock);
        assertTrue(limiter.tryAcquire("client").isEmpty());
        clock.advanceNanos(999_000_000L);
        assertTrue(limiter.tryAcquire("client").isPresent());
        clock.advanceNanos(1_000_000L);
        assertTrue(limiter.tryAcquire("client").isEmpty());
    }

    @Test
    void retryAfterReflectsTheOutstandingDeficit() {
        RateLimiter limiter = new RateLimiter(1, 6, clock);
        assertTrue(limiter.tryAcquire("client").isEmpty());
        clock.advanceSeconds(2);
        OptionalLong rejected = limiter.tryAcquire("client");
        assertTrue(rejected.isPresent());
        assertEquals(8, rejected.getAsLong());
    }

    @Test
    void keysAreIndependentlyLimited() {
        RateLimiter limiter = new RateLimiter(1, 60, clock);
        assertTrue(limiter.tryAcquire("203.0.113.10").isEmpty());
        assertTrue(limiter.tryAcquire("203.0.113.10").isPresent());
        assertTrue(limiter.tryAcquire("198.51.100.77").isEmpty());
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(0, 60, clock));
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(1, 0, clock));
    }

    /** Advances deterministically; no sleeping in these tests. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advanceSeconds(long seconds) {
            advanceNanos(seconds * 1_000_000_000L);
        }

        void advanceNanos(long nanos) {
            now = now.plusNanos(nanos);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void hammeringOneKeyFromManyThreadsAllowsExactlyCapacity() throws Exception {
        RateLimiter limiter = new RateLimiter(100, 60, clock);
        int threads = 200;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        var start = new java.util.concurrent.CountDownLatch(1);
        var allowed =
                java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());
        try {
            var futures = java.util.stream.IntStream.range(0, threads)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        return limiter.tryAcquire("shared-key").isEmpty();
                    }))
                    .toList();
            start.countDown();
            for (var future : futures) {
                if (future.get(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    allowed.add(Integer.toString(allowed.size()));
                }
            }
            assertEquals(100, allowed.size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void multiKeyHammerPreservesPerKeyCounts() throws Exception {
        RateLimiter limiter = new RateLimiter(1, 60, clock);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(50);
        var start = new java.util.concurrent.CountDownLatch(1);
        var keys = java.util.stream.IntStream.range(0, 50)
                .mapToObj(i -> "key-" + i)
                .toList();
        try {
            var futures = keys.stream()
                    .flatMap(key -> java.util.stream.IntStream.range(0, 4)
                            .mapToObj(i -> pool.submit(() -> {
                                start.await();
                                return limiter.tryAcquire(key).isEmpty();
                            })))
                    .toList();
            start.countDown();
            long allowed = 0;
            for (var future : futures) {
                if (future.get(5, java.util.concurrent.TimeUnit.SECONDS)) allowed++;
            }
            assertEquals(50, allowed);
        } finally {
            pool.shutdownNow();
        }
    }
}
