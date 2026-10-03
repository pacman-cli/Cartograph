package com.cartograph.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Rate-limit tuning for the indexing endpoint. Boundaries are per client IP. */
@ConfigurationProperties(prefix = "cartograph.ratelimit")
public class RateLimitProperties {
    private boolean enabled = true;
    private int capacity = 30;
    private int refillPerMinute = 60;

    public boolean enabled() { return enabled; }
    public int capacity() { return capacity; }
    public int refillPerMinute() { return refillPerMinute; }
    public void setEnabled(boolean value) { enabled = value; }
    public void setCapacity(int value) {
        if (value < 1) throw new IllegalArgumentException("Rate limit capacity must be at least 1");
        capacity = value;
    }
    public void setRefillPerMinute(int value) {
        if (value < 1) throw new IllegalArgumentException("Rate limit refill-per-minute must be at least 1");
        refillPerMinute = value;
    }
}
