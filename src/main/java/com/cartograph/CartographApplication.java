package com.cartograph;

import com.cartograph.application.GraphSnapshotRepository;
import com.cartograph.application.SourceParser;
import com.cartograph.graph.GraphBuilder;
import com.cartograph.ingestion.GitHubUrlNormalizer;
import com.cartograph.ingestion.github.CloneProperties;
import com.cartograph.ingestion.github.CloneSourceWalker;
import com.cartograph.ingestion.github.GitHubClient;
import com.cartograph.ingestion.github.GitHubProperties;
import com.cartograph.ingestion.github.GitHubRepositoryFetcher;
import com.cartograph.ingestion.github.GitProcessRunner;
import com.cartograph.ingestion.github.RepositorySourceStrategy;
import com.cartograph.ingestion.github.ShallowCloneRepositoryFetcher;
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
    GitHubRepositoryFetcher repositoryFetcher(GitHubClient client, GitHubProperties properties) {
        return new GitHubRepositoryFetcher(client, properties);
    }

    @Bean
    GitProcessRunner gitProcessRunner() {
        return new GitProcessRunner();
    }

    @Bean
    CloneProperties cloneProperties(GitHubProperties properties) {
        return properties.clone();
    }

    @Bean
    CloneSourceWalker cloneSourceWalker(GitHubProperties properties) {
        return new CloneSourceWalker(properties.limits());
    }

    @Bean
    ShallowCloneRepositoryFetcher shallowCloneRepositoryFetcher(
            CloneProperties properties, GitProcessRunner runner, CloneSourceWalker walker) {
        return new ShallowCloneRepositoryFetcher(properties, runner, walker);
    }

    @Bean
    RepositorySourceStrategy repositorySourceFetcher(
            GitHubRepositoryFetcher rest, ShallowCloneRepositoryFetcher clone, CloneProperties properties) {
        return new RepositorySourceStrategy(rest, clone, properties);
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
        if (parent != null) Files.createDirectories(parent);
        return properties.initializeDataSourceBuilder().build();
    }

    public static void main(String[] args) {
        SpringApplication.run(CartographApplication.class, args);
    }
}
