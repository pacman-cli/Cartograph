package com.cartograph.ingestion.github;

import com.cartograph.graph.model.RepositoryRef;
import com.cartograph.graph.model.RepositorySnapshot;

/** Source boundary for materializing one already-resolved repository commit. */
@FunctionalInterface
public interface RepositorySourceFetcher {
    RepositorySnapshot fetch(RepositoryRef ref, String resolvedCommitSha);
}
