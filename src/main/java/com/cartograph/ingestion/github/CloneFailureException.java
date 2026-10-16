package com.cartograph.ingestion.github;

import java.util.Objects;

/** Safe, classified failure raised by the shallow-clone fallback. */
public class CloneFailureException extends RuntimeException {
    /** Categories used by the clone adapter to map local and repository failures. */
    public enum Kind {
        CLONE_UNAVAILABLE,
        CLONE_TIMEOUT,
        CLONE_FAILED,
        CLONE_COMMIT_MISMATCH,
        CLONE_DISABLED
    }

    private final Kind kind;

    public CloneFailureException(Kind kind) {
        this(kind, null);
    }

    public CloneFailureException(Kind kind, Throwable cause) {
        super(safeMessage(Objects.requireNonNull(kind, "kind must not be null")), cause);
        this.kind = kind;
    }

    public Kind kind() { return kind; }

    private static String safeMessage(Kind kind) {
        return switch (kind) {
            case CLONE_UNAVAILABLE -> "Repository clone is unavailable";
            case CLONE_TIMEOUT -> "Repository clone timed out";
            case CLONE_FAILED -> "Repository clone failed";
            case CLONE_COMMIT_MISMATCH -> "Repository clone commit did not match the requested revision";
            case CLONE_DISABLED -> "Repository clone fallback is disabled";
        };
    }
}
