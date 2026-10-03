/**
 * Adapter implementing the {@code RepositoryFetcher} port against the GitHub
 * REST API: URL-to-ref resolution, bounded retries with backoff
 * ({@code GitHubRetryPolicy}), connect/read timeouts, response validation,
 * ETag reuse, and a bounded response cache ({@code GitHubResponseCache}).
 *
 * <p>Boundary rules: this package owns all GitHub I/O; the application core
 * only sees {@code RepositorySnapshot} values. The token is configuration,
 * never part of a request DTO.
 */
package com.cartograph.ingestion.github;
