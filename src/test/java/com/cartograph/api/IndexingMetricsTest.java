package com.cartograph.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cartograph.application.IndexRepositoryService;
import com.cartograph.graph.model.GraphMetrics;
import com.cartograph.graph.model.GraphSnapshot;
import io.micrometer.core.instrument.MeterRegistry;
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
class IndexingMetricsTest {
    private static final Path DATABASE = createDatabasePath();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("cartograph.sqlite.path", DATABASE::toString);
    }

    private static Path createDatabasePath() {
        try {
            return Files.createTempFile("cartograph-metrics-", ".db");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    MeterRegistry registry;

    @MockBean
    IndexRepositoryService service;

    @Test
    void recordsCountAndDurationPerIndexRequest() throws Exception {
        when(service.index("https://github.com/acme/widgets"))
                .thenReturn(new GraphSnapshot(
                        "acme/widgets", "sha", List.of(), List.of(), List.of(), new GraphMetrics(0, 0, 0, 0)));

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/index")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"repositoryUrl\":\"https://github.com/acme/widgets\"}"))
                    .andExpect(status().isOk());
        }

        org.junit.jupiter.api.Assertions.assertEquals(
                2.0, registry.counter("cartograph.index.total").count());
        org.junit.jupiter.api.Assertions.assertEquals(
                2.0, registry.timer("cartograph.index.duration").count());
    }
}
