package com.cartograph.graph.model;

/** Repository-relative source path, text content, and parser language identifier. */
public record SourceFile(String path, String content, String language) {}
