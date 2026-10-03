/**
 * Repository ingestion concerns shared across adapters: GitHub URL
 * normalization ({@code GitHubUrlNormalizer}) and indexing guardrails
 * ({@code IndexingLimits}, {@code RepositoryLimitException}).
 *
 * <p>Boundary rules: no parsing and no persistence here.
 */
package com.cartograph.ingestion;
