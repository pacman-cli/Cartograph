package com.cartograph.ingestion;

/** Reports which configured repository size limit prevented indexing. */
public final class RepositoryLimitException extends RuntimeException {
    private final IndexingLimits.Limit limit;
    private final long observedValue;
    private final long configuredLimit;

    public RepositoryLimitException(IndexingLimits.Limit limit, long observedValue, long configuredLimit) {
        super("Repository " + limit + " limit exceeded: observed " + observedValue + ", limit " + configuredLimit);
        this.limit = limit;
        this.observedValue = observedValue;
        this.configuredLimit = configuredLimit;
    }

    public IndexingLimits.Limit limit() {
        return limit;
    }

    public IndexingLimits.Limit violatedLimit() {
        return limit;
    }

    public long observedValue() {
        return observedValue;
    }

    public long observed() {
        return observedValue;
    }

    public long configuredLimit() {
        return configuredLimit;
    }
}
