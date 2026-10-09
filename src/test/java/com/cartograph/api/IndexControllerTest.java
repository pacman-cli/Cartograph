package com.cartograph.api;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.cartograph.application.GraphSnapshotRepository;
import com.cartograph.application.IndexRepositoryService;
import com.cartograph.graph.model.GraphMetrics;
import com.cartograph.graph.model.GraphSnapshot;
import com.cartograph.ingestion.IndexingLimits;
import com.cartograph.ingestion.InvalidRepositoryUrlException;
import com.cartograph.ingestion.RepositoryLimitException;
import com.cartograph.ingestion.github.GitHubFetchException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = IndexController.class)
@ContextConfiguration(
        classes = {IndexController.class, ApiExceptionHandler.class, IndexControllerTest.MetricsConfig.class})
class IndexControllerTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @MockBean
    IndexRepositoryService service;

    @Test
    void mapsUnknownRouteToStructuredNotFound() throws Exception {
        mvc.perform(get("/unknown-route"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"code":"NOT_FOUND","message":"The requested resource was not found."}
                        """));
        verifyNoInteractions(service);
    }

    @Test
    void mapsUnknownActuatorRouteToStructuredNotFound() throws Exception {
        mvc.perform(get("/actuator/nope"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"code":"NOT_FOUND","message":"The requested resource was not found."}
                        """));
        verifyNoInteractions(service);
    }

    @Test
    void mapsMissingHandlerToStructuredNotFound() throws Exception {
        MockMvc withoutResourceHandlers = standaloneSetup(new IndexController(
                        service, new IndexingMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        withoutResourceHandlers
                .perform(get("/unknown-route"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"code":"NOT_FOUND","message":"The requested resource was not found."}
                        """));
        verifyNoInteractions(service);
    }

    @Test
    void returnsStoredSnapshotWithoutIndexing() throws Exception {
        when(service.snapshot("acme/widgets"))
                .thenReturn(Optional.of(new GraphSnapshot(
                        "acme/widgets", "sha", List.of(), List.of(), List.of(), new GraphMetrics(0, 0, 0, 0))));

        mvc.perform(get("/api/v1/repositories/acme/widgets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repository").value("acme/widgets"))
                .andExpect(jsonPath("$.commitSha").value("sha"));
        verify(service, never()).index(anyString());
    }

    @Test
    void listsRepositoriesWithoutIndexing() throws Exception {
        when(service.repositories())
                .thenReturn(List.of(
                        new GraphSnapshotRepository.RepositorySummary("acme/widgets", "sha-2", "2026-10-03 10:00:00"),
                        new GraphSnapshotRepository.RepositorySummary("acme/other", "sha-9", "2026-10-03 09:00:00")));

        mvc.perform(get("/api/v1/repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.repositories[0].repository").value("acme/widgets"))
                .andExpect(jsonPath("$.repositories[1].commitSha").value("sha-9"));
        verify(service, never()).index(anyString());
    }

    @Test
    void clampsPaginationBounds() throws Exception {
        when(service.repositories())
                .thenReturn(List.of(
                        new GraphSnapshotRepository.RepositorySummary("acme/widgets", "sha-2", "2026-10-03 10:00:00")));

        mvc.perform(get("/api/v1/repositories").param("limit", "5000").param("offset", "-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.repositories.length()").value(1));
    }

    @Test
    void mapsUnknownRepositorySnapshotToStructuredNotFound() throws Exception {
        when(service.snapshot("acme/unknown")).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/repositories/acme/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void indexesRepositoryWithoutCallingGitHub() throws Exception {
        when(service.index("https://github.com/acme/widgets"))
                .thenReturn(new GraphSnapshot(
                        "acme/widgets", "sha", List.of(), List.of(), List.of(), new GraphMetrics(0, 0, 0, 0)));

        mvc.perform(post("/api/v1/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new IndexRepositoryRequest("https://github.com/acme/widgets"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repository").value("acme/widgets"))
                .andExpect(jsonPath("$.commitSha").value("sha"))
                .andExpect(jsonPath("$.metrics.filesSeen").value(0))
                .andExpect(jsonPath("$.metrics.filesParsed").value(0))
                .andExpect(jsonPath("$.metrics.nodes").value(0))
                .andExpect(jsonPath("$.metrics.edges").value(0));
    }

    @Test
    void rejectsBlankUrl() throws Exception {
        mvc.perform(post("/api/v1/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryUrl\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void mapsMalformedUrlToStructuredBadRequest() throws Exception {
        when(service.index("not-a-url")).thenThrow(new InvalidRepositoryUrlException("bad"));
        mvc.perform(post("/api/v1/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryUrl\":\"not-a-url\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void mapsUnexpectedIllegalArgumentExceptionToInternalError() throws Exception {
        when(service.index("https://github.com/acme/widgets"))
                .thenThrow(new IllegalArgumentException("unexpected internal failure"));

        mvc.perform(post("/api/v1/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryUrl\":\"https://github.com/acme/widgets\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An internal error occurred."));
    }

    @Test
    void mapsGitHubRateLimitToTooManyRequests() throws Exception {
        when(service.index("https://github.com/acme/widgets"))
                .thenThrow(new GitHubFetchException(GitHubFetchException.Kind.RATE_LIMITED, 429, "rate limited"));

        mvc.perform(post("/api/v1/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryUrl\":\"https://github.com/acme/widgets\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("UPSTREAM_GITHUB_ERROR"));
    }

    @Test
    void mapsRepositoryLimitToPayloadTooLarge() throws Exception {
        when(service.index("https://github.com/acme/widgets"))
                .thenThrow(new RepositoryLimitException(IndexingLimits.Limit.FILE_BYTES, 11, 10));

        mvc.perform(post("/api/v1/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryUrl\":\"https://github.com/acme/widgets\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("REPOSITORY_LIMIT_EXCEEDED"));
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class MetricsConfig {
        @org.springframework.context.annotation.Bean
        io.micrometer.core.instrument.MeterRegistry meterRegistry() {
            return new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        }

        @org.springframework.context.annotation.Bean
        IndexingMetrics indexingMetrics(io.micrometer.core.instrument.MeterRegistry registry) {
            return new IndexingMetrics(registry);
        }
    }
}
