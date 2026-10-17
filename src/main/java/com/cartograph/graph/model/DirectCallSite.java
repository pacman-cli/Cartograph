package com.cartograph.graph.model;

/** A direct function call and the source location at which it occurs. */
public record DirectCallSite(String targetName, SourceLocation location) {}
