package com.cartograph;

import com.cartograph.application.IndexRepositoryService;
import com.cartograph.graph.model.RepositoryRef;
import com.cartograph.graph.model.RepositorySnapshot;
import com.cartograph.ingestion.github.RepositorySourceFetcher;
import com.cartograph.ingestion.github.RepositorySourceStrategy;
import com.cartograph.ingestion.github.CloneProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CartographApplicationSourceWiringTest {
    private static final String SHA = "a".repeat(40);

    @Autowired ApplicationContext context;
    @Autowired IndexRepositoryService service;

    @Test
    void productionServiceUsesTheStrategyBeanAsItsSourceBoundary() throws Exception {
        Object injected = field(service, "sourceFetcher");

        assertThat(injected).isSameAs(context.getBean("repositorySourceFetcher"));
        assertThat(injected).isInstanceOf(RepositorySourceStrategy.class);
        assertThat(context.getBean("repositoryFetcher")).isInstanceOf(com.cartograph.ingestion.github.GitHubRepositoryFetcher.class);
    }

    @Test
    void strategyRoutesThresholdAndTruncatedTreesToCloneAdapter() {
        AtomicInteger cloneCalls = new AtomicInteger();
        RepositorySourceFetcher rest = (ref, sha) -> snapshot(ref, sha);
        RepositorySourceFetcher clone = (ref, sha) -> {
            cloneCalls.incrementAndGet();
            return snapshot(ref, sha);
        };
        ClonePropertiesFixture fixture = new ClonePropertiesFixture();

        var thresholdStrategy = new RepositorySourceStrategy(rest, clone, fixture.properties,
                (ref, sha) -> new RepositorySourceStrategy.TreeInspection(10_000, false));
        var truncatedStrategy = new RepositorySourceStrategy(rest, clone, fixture.properties,
                (ref, sha) -> new RepositorySourceStrategy.TreeInspection(1, true));

        thresholdStrategy.fetch(fixture.ref, SHA);
        truncatedStrategy.fetch(fixture.ref, SHA);

        assertThat(cloneCalls).hasValue(2);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = IndexRepositoryService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static RepositorySnapshot snapshot(RepositoryRef ref, String sha) {
        return new RepositorySnapshot(ref.coordinate(), sha, List.of());
    }

    private static final class ClonePropertiesFixture {
        private final RepositoryRef ref = new RepositoryRef("acme", "demo", "main");
        private final CloneProperties properties = new CloneProperties();
    }
}
