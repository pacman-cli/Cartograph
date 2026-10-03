package com.cartograph.api;

/** Requested resource does not exist locally; maps to the stable 404 contract. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
