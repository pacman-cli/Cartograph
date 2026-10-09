package com.cartograph.api;

import com.cartograph.ingestion.InvalidRepositoryUrlException;
import com.cartograph.ingestion.RepositoryLimitException;
import com.cartograph.ingestion.github.GitHubFetchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps request, upstream, and unexpected failures to the API's stable error response. */
@RestControllerAdvice
public final class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({
        InvalidRepositoryUrlException.class,
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class
    })
    ResponseEntity<ApiErrorResponse> badRequest(Exception ignored) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The repository URL is invalid.");
    }

    @ExceptionHandler(RepositoryLimitException.class)
    ResponseEntity<ApiErrorResponse> limit(RepositoryLimitException ignored) {
        return response(
                HttpStatus.PAYLOAD_TOO_LARGE, "REPOSITORY_LIMIT_EXCEEDED", "Repository exceeds indexing limits.");
    }

    @ExceptionHandler(GitHubFetchException.class)
    ResponseEntity<ApiErrorResponse> github(GitHubFetchException exception) {
        HttpStatus status =
                switch (exception.kind()) {
                    case NOT_FOUND -> HttpStatus.NOT_FOUND;
                    case FORBIDDEN -> HttpStatus.FORBIDDEN;
                    case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
                    case NOT_MODIFIED, UPSTREAM -> HttpStatus.BAD_GATEWAY;
                };
        return response(status, "UPSTREAM_GITHUB_ERROR", "Unable to access the GitHub repository.");
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ApiErrorResponse> rateLimited(RateLimitExceededException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(exception.retryAfterSeconds()))
                .body(new ApiErrorResponse(
                        "RATE_LIMIT_EXCEEDED", "Too many indexing requests. Retry after the indicated delay."));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class, ResourceNotFoundException.class})
    ResponseEntity<ApiErrorResponse> notFound(Exception ignored) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> internal(Exception exception) {
        LOG.error(
                "Unhandled indexing failure correlationId={}",
                org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY),
                exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An internal error occurred.");
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message));
    }
}
