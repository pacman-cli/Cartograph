package com.cartograph.ingestion.github;

/** Configuration for the shallow-clone fallback used by the GitHub adapter. */
public class CloneProperties {
    private boolean enabled = true;
    private long fileThreshold = 10_000;
    private long timeoutSeconds = 120;
    private long cleanupTimeoutSeconds = 10;
    private String executable = "git";

    public boolean enabled() {
        return enabled;
    }

    public long fileThreshold() {
        return fileThreshold;
    }

    public long timeoutSeconds() {
        return timeoutSeconds;
    }

    public long cleanupTimeoutSeconds() {
        return cleanupTimeoutSeconds;
    }

    public String executable() {
        return executable;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public void setFileThreshold(long value) {
        if (value <= 0) throw new IllegalArgumentException("Clone file threshold must be positive");
        fileThreshold = value;
    }

    public void setTimeoutSeconds(long value) {
        if (value <= 0) throw new IllegalArgumentException("Clone timeout must be positive");
        timeoutSeconds = value;
    }

    public void setCleanupTimeoutSeconds(long value) {
        if (value <= 0) throw new IllegalArgumentException("Clone cleanup timeout must be positive");
        cleanupTimeoutSeconds = value;
    }

    public void setExecutable(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Clone executable must not be blank");
        }
        executable = value;
    }
}
