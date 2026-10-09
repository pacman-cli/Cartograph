package com.cartograph.ingestion;

import java.util.List;
import java.util.Objects;

/** Immutable file-count and byte limits enforced while downloading a repository. */
public record IndexingLimits(long maxFileCount, long maxTotalBytes, long maxFileBytes) {
    public IndexingLimits {
        if (maxFileCount <= 0 || maxTotalBytes <= 0 || maxFileBytes <= 0) {
            throw new IllegalArgumentException("Indexing limits must be positive");
        }
    }

    public void validateFetchedTree(List<Long> fileSizes) {
        Objects.requireNonNull(fileSizes, "fileSizes");
        if (fileSizes.size() > maxFileCount) {
            throw new RepositoryLimitException(Limit.FILE_COUNT, fileSizes.size(), maxFileCount);
        }
        long total = 0;
        for (Long size : fileSizes) {
            if (size == null || size < 0) throw new IllegalArgumentException("File sizes must be non-negative");
            if (size > maxFileBytes) throw new RepositoryLimitException(Limit.FILE_BYTES, size, maxFileBytes);
            try {
                total = Math.addExact(total, size);
            } catch (ArithmeticException exception) {
                throw new RepositoryLimitException(Limit.TOTAL_BYTES, Long.MAX_VALUE, maxTotalBytes);
            }
        }
        if (total > maxTotalBytes) throw new RepositoryLimitException(Limit.TOTAL_BYTES, total, maxTotalBytes);
    }

    /** Validates tree metadata when individual file sizes are not retained by the fetcher. */
    public void validateFetchedTree(long fileCount, long totalBytes, long largestFileBytes) {
        if (fileCount < 0 || totalBytes < 0 || largestFileBytes < 0) {
            throw new IllegalArgumentException("Tree sizes must be non-negative");
        }
        if (fileCount > maxFileCount) throw new RepositoryLimitException(Limit.FILE_COUNT, fileCount, maxFileCount);
        if (largestFileBytes > maxFileBytes) {
            throw new RepositoryLimitException(Limit.FILE_BYTES, largestFileBytes, maxFileBytes);
        }
        if (totalBytes > maxTotalBytes)
            throw new RepositoryLimitException(Limit.TOTAL_BYTES, totalBytes, maxTotalBytes);
    }

    /** Validate the actual decoded bytes returned for one content response. */
    public long validateFetchedContent(long fileBytes, long downloadedBytes) {
        if (fileBytes < 0 || downloadedBytes < 0) {
            throw new IllegalArgumentException("Content sizes must be non-negative");
        }
        if (fileBytes > maxFileBytes) {
            throw new RepositoryLimitException(Limit.FILE_BYTES, fileBytes, maxFileBytes);
        }
        final long cumulative;
        try {
            cumulative = Math.addExact(downloadedBytes, fileBytes);
        } catch (ArithmeticException exception) {
            throw new RepositoryLimitException(Limit.TOTAL_BYTES, Long.MAX_VALUE, maxTotalBytes);
        }
        if (cumulative > maxTotalBytes) {
            throw new RepositoryLimitException(Limit.TOTAL_BYTES, cumulative, maxTotalBytes);
        }
        return cumulative;
    }

    public void validateTree(List<Long> fileSizes) {
        validateFetchedTree(fileSizes);
    }

    /** Identifies the file-count, cumulative-byte, or single-file-byte limit that was exceeded. */
    public enum Limit {
        FILE_COUNT,
        TOTAL_BYTES,
        FILE_BYTES
    }
}
