package com.cartograph.graph.model;

/** Relationship categories supported by the repository graph. */
public enum EdgeKind {
    /** A graph element contains another element. */
    CONTAINS,
    /** One function or method calls another. */
    CALLS,
    /** A source file imports another module. */
    IMPORTS,
    /** A symbol references another symbol. */
    REFERENCES,
    /** A type extends a base type. */
    EXTENDS,
    /** A type implements an interface. */
    IMPLEMENTS,
    /** A dependency whose precise relation is unavailable. */
    DEPENDS_ON
}
