package com.cartograph.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Idempotent retries for the indexing endpoint: a client retrying a timed-out
 * request with the same {@code Idempotency-Key} receives the original
 * response instead of triggering a second index. Keys reused with a
 * different body are rejected as a conflict. Responses that are not 2xx are
 * not stored, so a failed attempt can be retried. No header — no change in
 * behavior.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IdempotencyFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyFilter.class);
    static final String HEADER = "Idempotency-Key";
    static final String REPLAYED_HEADER = "Idempotency-Replayed";
    private static final String CONFLICT_BODY =
            "{\"code\":\"IDEMPOTENCY_CONFLICT\",\"message\":\"This Idempotency-Key was already used with a different request body.\"}";

    private final IdempotencyStore store;

    public IdempotencyFilter(IdempotencyStore store) {
        this.store = store;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !"/api/v1/index".equals(request.getRequestURI())
                || request.getHeader(HEADER) == null
                || request.getHeader(HEADER).isBlank();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] body = request.getInputStream().readAllBytes();
        String bodyHash = sha256(body);
        HttpServletRequest wrappedRequest = new CachedBodyRequest(request, body);

        var lookup = store.lookup(request.getHeader(HEADER), bodyHash);
        switch (lookup.outcome()) {
            case REPLAY -> {
                var entry = lookup.entry();
                response.setHeader(REPLAYED_HEADER, "true");
                response.setStatus(entry.status());
                response.setContentType(entry.contentType());
                response.getOutputStream().write(entry.body());
                return;
            }
            case CONFLICT -> {
                response.setStatus(409);
                response.setContentType("application/json");
                response.getOutputStream().write(CONFLICT_BODY.getBytes(StandardCharsets.UTF_8));
                return;
            }
            case MISS -> {}
        }

        ContentCachingResponseWrapper responseCache = new ContentCachingResponseWrapper(response);
        chain.doFilter(wrappedRequest, responseCache);
        if (responseCache.getStatus() >= 200 && responseCache.getStatus() < 300) {
            store.store(
                    request.getHeader(HEADER),
                    bodyHash,
                    new IdempotencyStore.Entry(
                            responseCache.getStatus(),
                            responseCache.getContentType() == null
                                    ? "application/json"
                                    : responseCache.getContentType(),
                            responseCache.getContentAsByteArray()));
        }
        responseCache.copyBodyToResponse();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class CachedBodyRequest extends jakarta.servlet.http.HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() {
            var source = new java.io.ByteArrayInputStream(body);
            return new jakarta.servlet.ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(jakarta.servlet.ReadListener listener) {}

                @Override
                public int read() {
                    return source.read();
                }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
