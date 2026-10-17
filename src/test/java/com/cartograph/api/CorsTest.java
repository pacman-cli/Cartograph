package com.cartograph.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

@SpringBootTest
@AutoConfigureMockMvc
class CorsTest {
    private static final Path DATABASE = createDatabasePath();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("cartograph.sqlite.path", DATABASE::toString);
        registry.add("cartograph.cors.allowed-origins[0]", () -> "https://viewer.example.org");
    }

    private static Path createDatabasePath() {
        try {
            return Files.createTempFile("cartograph-cors-", ".db");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void preflightFromAllowedOriginIsAccepted() throws Exception {
        mvc.perform(options("/api/v1/index")
                        .header("Origin", "https://viewer.example.org")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://viewer.example.org"));
    }

    @Test
    void actualRequestFromAllowedOriginCarriesAllowOrigin() throws Exception {
        mvc.perform(post("/api/v1/index")
                        .header("Origin", "https://viewer.example.org")
                        .contentType("application/json")
                        .content("{\"repositoryUrl\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://viewer.example.org"));
    }

    @Test
    void disallowedOriginGetsNoCorsHeaders() throws Exception {
        mvc.perform(options("/api/v1/index")
                        .header("Origin", "https://evil.example.org")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
