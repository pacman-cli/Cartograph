package com.cartograph.api;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS for the API surface. {@code allowed-origins} is empty by default,
 * which disables CORS processing entirely — byte-identical to pre-CORS
 * behavior. Origins are added per deployment for the graph viewer (F0.25).
 */
@ConfigurationProperties(prefix = "cartograph.cors")
public class CorsProperties {
    private List<String> allowedOrigins = new ArrayList<>();

    public List<String> allowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> value) {
        allowedOrigins = value == null ? new ArrayList<>() : value;
    }
}
