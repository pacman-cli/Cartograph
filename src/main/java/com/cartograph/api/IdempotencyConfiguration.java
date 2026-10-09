package com.cartograph.api;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the idempotency cache. */
@Configuration
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyConfiguration {
    @Bean
    IdempotencyStore idempotencyStore(IdempotencyProperties properties) {
        return new IdempotencyStore(properties.maxEntries(), properties.ttlSeconds(), Clock.systemUTC());
    }
}
