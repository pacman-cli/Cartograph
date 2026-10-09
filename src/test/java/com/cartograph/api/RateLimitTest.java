package com.cartograph.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cartograph.application.IndexRepositoryService;
import com.cartograph.graph.model.GraphMetrics;
import com.cartograph.graph.model.GraphSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RateLimitTest {
    private static final Path DATABASE = createDatabasePath();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("cartograph.sqlite.path", DATABASE::toString);
        registry.add("cartograph.ratelimit.capacity", () -> "2");
        registry.add("cartograph.ratelimit.refill-per-minute", () -> "2");
    }

    private static Path createDatabasePath() {
        try {
            return Files.createTempFile("cartograph-ratelimit-", ".db");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    MockMvc mvc;

    @MockBean
    IndexRepositoryService service;

    private String body() {
        return "{\"repositoryUrl\":\"https://github.com/acme/widgets\"}";
    }

    @Test
    void limitsIndexRequestsPerClientAndKeepsOtherClientsUnaffected() throws Exception {
        when(service.index("https://github.com/acme/widgets"))
                .thenReturn(new GraphSnapshot(
                        "acme/widgets", "sha", List.of(), List.of(), List.of(), new GraphMetrics(0, 0, 0, 0)));

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/index")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", "203.0.113.10")
                            .content(body()))
                    .andExpect(status().isOk());
        }

        mvc.perform(post("/api/v1/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.10")
                        .content(body()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").exists());

        mvc.perform(post("/api/v1/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "198.51.100.77")
                        .content(body()))
                .andExpect(status().isOk());
    }

    @Test
    void leavesHealthEndpointUnlimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }
    }
}
