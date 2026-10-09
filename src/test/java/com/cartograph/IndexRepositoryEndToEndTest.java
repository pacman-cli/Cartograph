package com.cartograph;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cartograph.application.RepositoryFetcher;
import com.cartograph.graph.model.RepositoryRef;
import com.cartograph.graph.model.RepositorySnapshot;
import com.cartograph.graph.model.SourceFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class IndexRepositoryEndToEndTest {
    private static final Path DATABASE = createDatabasePath();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("cartograph.sqlite.path", DATABASE::toString);
    }

    @org.springframework.beans.factory.annotation.Autowired
    MockMvc mvc;

    @org.springframework.beans.factory.annotation.Autowired
    FixtureRepositoryFetcher fetcher;

    @Test
    void indexesFixtureAndUsesPersistedSnapshotOnSecondRequest() throws Exception {
        String request = "{\"repositoryUrl\":\"https://github.com/acme/fixture\"}";

        mvc.perform(post("/api/v1/index").contentType(APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repository").value("acme/fixture"))
                .andExpect(jsonPath("$.commitSha").value("fixture-commit-sha"))
                .andExpect(jsonPath("$.nodes[*].name", hasItem("greet")))
                .andExpect(jsonPath("$.nodes", not(empty())))
                .andExpect(jsonPath("$.edges", not(empty())))
                .andExpect(jsonPath("$.warnings", not(empty())))
                .andExpect(jsonPath("$.warnings[0].code").isString())
                .andExpect(jsonPath("$.warnings[0].message").isString())
                .andExpect(jsonPath("$.metrics.nodes", greaterThan(0)))
                .andExpect(jsonPath("$.metrics.edges", greaterThan(0)));

        mvc.perform(post("/api/v1/index").contentType(APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commitSha").value("fixture-commit-sha"))
                .andExpect(jsonPath("$.nodes", not(empty())))
                .andExpect(jsonPath("$.edges", not(empty())));

        org.junit.jupiter.api.Assertions.assertEquals(
                1, fetcher.fetchCount.get(), "second request must use SQLite cache");
        org.junit.jupiter.api.Assertions.assertEquals(2, fetcher.resolveCommitCount.get());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixtureConfiguration {
        @Bean
        @Primary
        FixtureRepositoryFetcher fixtureRepositoryFetcher() {
            return new FixtureRepositoryFetcher();
        }
    }

    static final class FixtureRepositoryFetcher implements RepositoryFetcher {
        private final AtomicInteger resolveCommitCount = new AtomicInteger();
        private final AtomicInteger fetchCount = new AtomicInteger();

        @Override
        public String resolveCommit(RepositoryRef ref) {
            resolveCommitCount.incrementAndGet();
            return "fixture-commit-sha";
        }

        @Override
        public RepositorySnapshot fetch(RepositoryRef ref) {
            return fetchResolved(ref, resolveCommit(ref));
        }

        @Override
        public RepositorySnapshot fetchResolved(RepositoryRef ref, String sha) {
            org.junit.jupiter.api.Assertions.assertEquals("fixture-commit-sha", sha);
            fetchCount.incrementAndGet();
            try {
                String source = new String(
                        new ClassPathResource("fixtures/simple-ts-repo/main.ts")
                                .getInputStream()
                                .readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
                return new RepositorySnapshot(
                        ref.coordinate(), sha, List.of(new SourceFile("main.ts", source, "typescript")));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static Path createDatabasePath() {
        try {
            return Files.createTempFile("cartograph-e2e-", ".db");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
