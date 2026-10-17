package com.cartograph.api;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Driving-adapter metrics for the indexing endpoint: request count and
 * duration, visible under {@code /actuator/metrics}. The application core
 * stays framework-free — instrumentation lives at the adapter boundary.
 */
@Component
public class IndexingMetrics {
    private final MeterRegistry registry;
    private final AtomicLong requests = new AtomicLong();

    public IndexingMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.counter("cartograph.index.total");
        registry.timer("cartograph.index.duration");
    }

    /** Executes the call, recording the request count and duration. */
    public <T> T recordIndex(Supplier<T> call) {
        requests.incrementAndGet();
        registry.counter("cartograph.index.total").increment();
        return registry.timer("cartograph.index.duration").record(call::get);
    }

    long totalRequests() {
        return requests.get();
    }
}
