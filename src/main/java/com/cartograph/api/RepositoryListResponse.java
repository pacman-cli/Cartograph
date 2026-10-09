package com.cartograph.api;

import com.cartograph.application.GraphSnapshotRepository.RepositorySummary;
import java.util.List;

/** Wire shape for the repository-discovery endpoint. */
public record RepositoryListResponse(List<RepositorySummary> repositories, int count) {}
