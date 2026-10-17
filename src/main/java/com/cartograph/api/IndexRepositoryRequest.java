package com.cartograph.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Validated input for the repository indexing endpoint.
 *
 * @param repositoryUrl public GitHub repository URL to normalize and index
 */
public record IndexRepositoryRequest(
        @NotBlank(message = "repositoryUrl must not be blank")
        String repositoryUrl) {}
