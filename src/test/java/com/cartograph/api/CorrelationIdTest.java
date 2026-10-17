package com.cartograph.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class CorrelationIdTest {
    private static final Path DATABASE = createDatabasePath();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("cartograph.sqlite.path", DATABASE::toString);
    }

    private static Path createDatabasePath() {
        try {
            return Files.createTempFile("cartograph-correlation-", ".db");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void generatesAndEchoesACorrelationId() throws Exception {
        MvcResult result = mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andReturn();
        assertThat(result.getResponse().getHeader("X-Request-Id")).isNotBlank();
    }

    @Test
    void reusesASuppliedRequestId() throws Exception {
        mvc.perform(get("/actuator/health").header("X-Request-Id", "my-trace-id-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "my-trace-id-123"));
    }

    @Test
    void errorBodiesCarryTheCorrelationId() throws Exception {
        mvc.perform(get("/definitely-not-a-route").header("X-Request-Id", "trace-abc"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.correlationId").value("trace-abc"));
    }

    @Test
    void generatedIdsAreUniquePerRequest() throws Exception {
        MvcResult first = mvc.perform(get("/actuator/health")).andReturn();
        MvcResult second = mvc.perform(get("/actuator/health")).andReturn();
        assertThat(first.getResponse().getHeader("X-Request-Id"))
                .isNotEqualTo(second.getResponse().getHeader("X-Request-Id"));
    }
}
