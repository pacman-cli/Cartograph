package com.cartograph.ingestion.github;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpHeaders;

/** GitHub REST best practices: never shorten a server-requested delay to fit a budget. */
final class GitHubRetryPolicy {
    private GitHubRetryPolicy() {}

    static long delayMillis(HttpHeaders headers, boolean rateLimited, int attempt, long backoffMillis, Clock clock) {
        Long retryAfter = retryAfter(headers.getFirst(HttpHeaders.RETRY_AFTER), clock);
        Long reset = "0".equals(headers.getFirst("X-RateLimit-Remaining"))
                ? epochDelay(headers.getFirst("X-RateLimit-Reset"), clock)
                : null;
        if (retryAfter != null || reset != null) {
            return Math.max(
                    exponential(backoffMillis, attempt),
                    Math.max(retryAfter == null ? 0 : retryAfter, reset == null ? 0 : reset));
        }
        return exponential(rateLimited ? Math.max(60_000, backoffMillis) : backoffMillis, attempt);
    }

    static long exponential(long base, int attempt) {
        try {
            return Math.multiplyExact(base, 1L << (attempt - 1));
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static Long retryAfter(String value, Clock clock) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.matches("[0-9]+")) {
            try {
                return Math.multiplyExact(Long.parseLong(trimmed), 1000);
            } catch (ArithmeticException | NumberFormatException exception) {
                return Long.MAX_VALUE;
            }
        }
        try {
            return until(
                    ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME)
                            .toInstant(),
                    clock);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static Long epochDelay(String value, Clock clock) {
        if (value == null) return null;
        try {
            return until(Instant.ofEpochSecond(Long.parseLong(value.trim())), clock);
        } catch (RuntimeException exception) {
            // An overflowing future timestamp is still a request not to retry now.
            return value.trim().matches("[0-9]+") ? Long.MAX_VALUE : null;
        }
    }

    private static long until(Instant instant, Clock clock) {
        try {
            return Math.max(0, Duration.between(clock.instant(), instant).toMillis());
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }
}
