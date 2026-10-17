package com.cartograph.ingestion.github;

import java.util.Objects;

/** Bounded result of an external process invocation. */
public final class ProcessResult extends Exception {
    private final int exitCode;
    private final String stdout;
    private final String stderr;

    public ProcessResult(int exitCode, String stdout, String stderr) {
        super("Process exited with status " + exitCode);
        this.exitCode = exitCode;
        this.stdout = Objects.requireNonNull(stdout, "stdout must not be null");
        this.stderr = Objects.requireNonNull(stderr, "stderr must not be null");
    }

    public int exitCode() {
        return exitCode;
    }

    public String stdout() {
        return stdout;
    }

    public String stderr() {
        return stderr;
    }
}
