package com.cartograph.ingestion;

import com.cartograph.application.IndexRepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Indexes the configured popular repositories after startup, sequentially
 * and best-effort: a failure is logged and the remaining repositories are
 * still attempted. Runs on a daemon thread so readiness is never delayed.
 */
@Component
@EnableConfigurationProperties(PrewarmProperties.class)
public class PrewarmRunner implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(PrewarmRunner.class);
    private final IndexRepositoryService service;
    private final PrewarmProperties properties;

    public PrewarmRunner(IndexRepositoryService service, PrewarmProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled() || properties.repositories().isEmpty()) {
            return;
        }
        Thread worker = new Thread(this::prewarmAll, "cartograph-prewarm");
        worker.setDaemon(true);
        worker.start();
    }

    private void prewarmAll() {
        LOG.info("Pre-warming {} repositories", properties.repositories().size());
        int succeeded = 0;
        for (String repository : properties.repositories()) {
            try {
                service.index("https://github.com/" + repository.strip());
                succeeded++;
            } catch (RuntimeException e) {
                LOG.warn("Pre-warm failed for {}: {}", repository, e.getMessage());
            }
        }
        LOG.info(
                "Pre-warm finished: {}/{} repositories hot",
                succeeded,
                properties.repositories().size());
    }
}
