package com.cartograph.api;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

/** Registers the indexing-endpoint rate limiter. */
@Configuration
public class RateLimitConfiguration implements WebMvcConfigurer {
    private final ObjectProvider<RateLimitInterceptor> interceptor;

    public RateLimitConfiguration(ObjectProvider<RateLimitInterceptor> interceptor) {
        this.interceptor = interceptor;
    }

    @Bean
    RateLimiter indexingRateLimiter(RateLimitProperties properties) {
        return new RateLimiter(properties.capacity(), properties.refillPerMinute(), Clock.systemUTC());
    }

    @Bean
    RateLimitInterceptor rateLimitInterceptor(RateLimiter limiter, RateLimitProperties properties) {
        return new RateLimitInterceptor(limiter, properties);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        RateLimitInterceptor rateLimitInterceptor = interceptor.getIfAvailable();
        if (rateLimitInterceptor != null) {
            registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/v1/index");
        }
    }
}
