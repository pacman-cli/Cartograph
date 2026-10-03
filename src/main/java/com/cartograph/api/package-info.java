/**
 * REST adapter for Cartograph: {@code IndexController} exposes the indexing
 * use case, request/response records carry the wire shape, and
 * {@code ApiExceptionHandler} maps every failure to the stable
 * {@code {code, message}} contract.
 *
 * <p>Boundary rules: no business logic here — this package delegates to the
 * {@code com.cartograph.application} use case and must not import GitHub
 * clients, parsers, or persistence types.
 */
package com.cartograph.api;
