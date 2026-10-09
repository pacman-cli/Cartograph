package com.cartograph;

import com.cartograph.application.GraphSnapshotRepository;
import com.cartograph.application.RepositoryFetcher;
import com.cartograph.application.SourceParser;
import com.cartograph.graph.GraphBuilder;
import com.cartograph.ingestion.GitHubUrlNormalizer;
import com.cartograph.ingestion.github.GitHubClient;
import com.cartograph.ingestion.github.GitHubProperties;
import com.cartograph.ingestion.github.GitHubRepositoryFetcher;
import com.cartograph.parsing.javascript.JavaScriptTypeScriptParser;
import com.cartograph.persistence.sqlite.SQLiteGraphSnapshotRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Spring Boot entry point and wiring for the Cartograph adapters and application ports. */
@SpringBootApplication
@org.springframework.boot.context.properties.ConfigurationPropertiesScan
public class CartographApplication {

    @Bean
    GitHubUrlNormalizer gitHubUrlNormalizer() {
        return new GitHubUrlNormalizer();
    }

    @Bean
    GitHubClient gitHubClient(
            org.springframework.web.client.RestClient.Builder builder,
            com.fasterxml.jackson.databind.ObjectMapper mapper,
            GitHubProperties properties) {
        return new GitHubClient(builder, mapper, properties);
    }

    @Bean
    RepositoryFetcher repositoryFetcher(GitHubClient client, GitHubProperties properties) {
        return new GitHubRepositoryFetcher(client, properties);
    }

    @Bean
    SourceParser sourceParser() {
        return new JavaScriptTypeScriptParser();
    }

    @Bean
    GraphBuilder graphBuilder(SourceParser parser) {
        return new GraphBuilder(parser);
    }

    @Bean
    GraphSnapshotRepository graphSnapshotRepository(
            DataSource dataSource, @Value("${cartograph.persistence.retention-per-repo:10}") int retentionPerRepo) {
        return new SQLiteGraphSnapshotRepository(
                dataSource, new com.fasterxml.jackson.databind.ObjectMapper(), retentionPerRepo);
    }

    @Bean
    @ConfigurationProperties("spring.datasource")
    DataSource dataSource(DataSourceProperties properties, @Value("${cartograph.sqlite.path}") String sqlitePath)
            throws IOException {
        Path database = Path.of(sqlitePath).toAbsolutePath();
        Path parent = database.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return properties.initializeDataSourceBuilder().build();
    }

    /**
     * Launches the Spring Boot service.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(CartographApplication.class, args);
    }
}
