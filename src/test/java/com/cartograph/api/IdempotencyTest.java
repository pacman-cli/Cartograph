package com.cartograph.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cartograph.application.IndexRepositoryService;
import com.cartograph.graph.model.GraphMetrics;
import com.cartograph.graph.model.GraphSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyTest {
    private static final Path DATABASE = createDatabasePath();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("cartograph.sqlite.path", DATABASE::toString);
        registry.add("cartograph.idempotency.max-entries", () -> "1");
    }

    private static Path createDatabasePath() {
        try {
            return Files.createTempFile("cartograph-idempotency-", ".db");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    MockMvc mvc;

    @MockBean
    IndexRepositoryService service;

    private String body(String repo) {
        return "{\"repositoryUrl\":\"https://github.com/acme/" + repo + "\"}";
    }

    private GraphSnapshot snapshot() {
        return new GraphSnapshot(
                "acme/widgets",
                "sha-1",
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                new GraphMetrics(0, 0, 0, 1, 1));
    }

    @Test
    void retryWithSameKeyReplaysTheOriginalResponseWithoutReindexing() throws Exception {
        when(service.index("https://github.com/acme/widgets")).thenReturn(snapshot());

        MvcResult first = mvc.perform(post("/api/v1/index")
                        .header(IdempotencyFilter.HEADER, "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("widgets")))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(IdempotencyFilter.REPLAYED_HEADER))
                .andReturn();

        MvcResult second = mvc.perform(post("/api/v1/index")
                        .header(IdempotencyFilter.HEADER, "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("widgets")))
                .andExpect(status().isOk())
                .andExpect(header().string(IdempotencyFilter.REPLAYED_HEADER, "true"))
                .andReturn();

        assertThat(second.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
        verify(service, times(1)).index("https://github.com/acme/widgets");
    }

    @Test
    void sameKeyWithDifferentBodyIsAConflict() throws Exception {
        when(service.index(Mockito.anyString())).thenReturn(snapshot());

        mvc.perform(post("/api/v1/index")
                        .header(IdempotencyFilter.HEADER, "key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("widgets")))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/index")
                        .header(IdempotencyFilter.HEADER, "key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("other")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void requestsWithoutHeaderExecuteEveryTime() throws Exception {
        when(service.index(Mockito.anyString())).thenReturn(snapshot());

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/index")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("widgets")))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist(IdempotencyFilter.REPLAYED_HEADER));
        }
        verify(service, times(2)).index(Mockito.anyString());
    }

    @Test
    void evictedKeysExecuteAgain() throws Exception {
        when(service.index(Mockito.anyString())).thenReturn(snapshot());

        mvc.perform(post("/api/v1/index")
                        .header(IdempotencyFilter.HEADER, "evict-me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("widgets")))
                .andExpect(status().isOk());
        // capacity is 1: this second key evicts the first
        mvc.perform(post("/api/v1/index")
                        .header(IdempotencyFilter.HEADER, "other-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("widgets")))
                .andExpect(status().isOk());

        MvcResult reExecuted = mvc.perform(post("/api/v1/index")
                        .header(IdempotencyFilter.HEADER, "evict-me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("widgets")))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(IdempotencyFilter.REPLAYED_HEADER))
                .andReturn();
        assertThat(reExecuted.getResponse().getStatus()).isEqualTo(200);
        verify(service, times(3)).index(Mockito.anyString());
    }
}
