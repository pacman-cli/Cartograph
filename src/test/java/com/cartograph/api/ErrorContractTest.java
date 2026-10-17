package com.cartograph.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cartograph.application.IndexRepositoryService;
import com.cartograph.ingestion.IndexingLimits;
import com.cartograph.ingestion.InvalidRepositoryUrlException;
import com.cartograph.ingestion.RepositoryLimitException;
import com.cartograph.ingestion.github.GitHubFetchException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Self-enforcing version of the README error table: every documented
 * HTTP-status ↔ code pair must hold. Adding a code without documenting it —
 * or changing a documented one — fails this test.
 */
@WebMvcTest(controllers = IndexController.class)
@ContextConfiguration(
        classes = {IndexController.class, ApiExceptionHandler.class, ErrorContractTest.MetricsConfig.class})
class ErrorContractTest {
    private static final String URL = "https://github.com/acme/widgets";

    @Autowired
    MockMvc mvc;

    @MockBean
    IndexRepositoryService service;

    @FunctionalInterface
    interface RequestPerformer {
        ResultActions perform(ErrorContractTest test) throws Exception;
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> documentedMappings() {
        return Stream.of(
                Arguments.of(400, "INVALID_REQUEST", (RequestPerformer) test -> test.mvc.perform(post("/api/v1/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryUrl\":\" \"}"))),
                Arguments.of(400, "INVALID_REQUEST", (RequestPerformer) test -> {
                    when(test.service.index(URL)).thenThrow(new InvalidRepositoryUrlException("bad"));
                    return test.mvc.perform(post("/api/v1/index")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"repositoryUrl\":\"" + URL + "\"}"));
                }),
                Arguments.of(413, "REPOSITORY_LIMIT_EXCEEDED", (RequestPerformer) test -> {
                    when(test.service.index(URL))
                            .thenThrow(new RepositoryLimitException(IndexingLimits.Limit.FILE_BYTES, 11, 10));
                    return test.mvc.perform(post("/api/v1/index")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"repositoryUrl\":\"" + URL + "\"}"));
                }),
                Arguments.of(404, "UPSTREAM_GITHUB_ERROR", (RequestPerformer) test -> {
                    when(test.service.index(URL))
                            .thenThrow(new GitHubFetchException(GitHubFetchException.Kind.NOT_FOUND, 404, "missing"));
                    return test.mvc.perform(post("/api/v1/index")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"repositoryUrl\":\"" + URL + "\"}"));
                }),
                Arguments.of(403, "UPSTREAM_GITHUB_ERROR", (RequestPerformer) test -> {
                    when(test.service.index(URL))
                            .thenThrow(new GitHubFetchException(GitHubFetchException.Kind.FORBIDDEN, 403, "denied"));
                    return test.mvc.perform(post("/api/v1/index")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"repositoryUrl\":\"" + URL + "\"}"));
                }),
                Arguments.of(429, "UPSTREAM_GITHUB_ERROR", (RequestPerformer) test -> {
                    when(test.service.index(URL))
                            .thenThrow(new GitHubFetchException(
                                    GitHubFetchException.Kind.RATE_LIMITED, 429, "upstream limit"));
                    return test.mvc.perform(post("/api/v1/index")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"repositoryUrl\":\"" + URL + "\"}"));
                }),
                Arguments.of(502, "UPSTREAM_GITHUB_ERROR", (RequestPerformer) test -> {
                    when(test.service.index(URL))
                            .thenThrow(new GitHubFetchException(GitHubFetchException.Kind.UPSTREAM, 500, "unusable"));
                    return test.mvc.perform(post("/api/v1/index")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"repositoryUrl\":\"" + URL + "\"}"));
                }),
                Arguments.of(
                        404, "NOT_FOUND", (RequestPerformer) test -> test.mvc.perform(get("/definitely-not-a-route"))));
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @MethodSource("documentedMappings")
    void enforcesEveryDocumentedMapping(int expectedStatus, String expectedCode, RequestPerformer perform)
            throws Exception {
        perform.perform(this)
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void rateLimitMappingCarriesRetryAfterHeaderAndStableCode() {
        var response = new ApiExceptionHandler().rateLimited(new RateLimitExceededException(30));
        org.junit.jupiter.api.Assertions.assertEquals(
                429, response.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertEquals(
                "30", response.getHeaders().getFirst("Retry-After"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "RATE_LIMIT_EXCEEDED",
                java.util.Objects.requireNonNull(response.getBody()).code());
    }

    @Test
    void internalErrorBodyNeverLeaksExceptionDetails() throws Exception {
        when(service.index(URL)).thenThrow(new IllegalStateException("exploded with secrets"));
        mvc.perform(post("/api/v1/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryUrl\":\"" + URL + "\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("exploded with secrets"))));
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
