package com.cartograph.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor guarding the expensive indexing endpoint with a per-client
 * token bucket. Only {@code POST /api/v1/index} is limited; health and
 * unknown routes stay unlimited. Interceptor exceptions flow through the
 * {@code ApiExceptionHandler}, keeping the stable {@code {code, message}}
 * error contract.
 */
public final class RateLimitInterceptor implements HandlerInterceptor {
    private final RateLimiter limiter;
    private final RateLimitProperties properties;

    public RateLimitInterceptor(RateLimiter limiter, RateLimitProperties properties) {
        this.limiter = limiter;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.enabled() || !"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        limiter.tryAcquire(clientKey(request)).ifPresent(retryAfterSeconds -> {
            throw new RateLimitExceededException(retryAfterSeconds);
        });
        return true;
    }

    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
