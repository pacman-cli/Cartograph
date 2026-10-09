package com.cartograph.ingestion;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class IndexingLimitsTest {
    @Test
    void allowsValuesAtEveryLimit() {
        IndexingLimits limits = new IndexingLimits(2, 100, 60);
        assertDoesNotThrow(() -> limits.validateFetchedTree(List.of(40L, 60L)));
    }

    @Test
    void rejectsTooManyFilesWithTypedDetails() {
        RepositoryLimitException error = assertThrows(
                RepositoryLimitException.class,
                () -> new IndexingLimits(2, 100, 100).validateFetchedTree(List.of(1L, 2L, 3L)));
        assertEquals(IndexingLimits.Limit.FILE_COUNT, error.limit());
        assertEquals(3L, error.observedValue());
    }

    @Test
    void rejectsTotalBytesBeforeContentDownload() {
        RepositoryLimitException error = assertThrows(
                RepositoryLimitException.class,
                () -> new IndexingLimits(3, 100, 100).validateFetchedTree(List.of(60L, 41L)));
        assertEquals(IndexingLimits.Limit.TOTAL_BYTES, error.limit());
        assertEquals(101L, error.observedValue());
    }

    @Test
    void rejectsIndividualFileLimit() {
        RepositoryLimitException error = assertThrows(
                RepositoryLimitException.class,
                () -> new IndexingLimits(3, 1000, 50).validateFetchedTree(List.of(51L)));
        assertEquals(IndexingLimits.Limit.FILE_BYTES, error.limit());
        assertEquals(51L, error.observedValue());
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new IndexingLimits(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new IndexingLimits(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new IndexingLimits(1, 1, 0));
    }

    @org.junit.jupiter.api.Test
    void fileCountBoundary_atLimitAccepted_oneOverRejected() {
        IndexingLimits limits = new IndexingLimits(3, 1000, 100);
        assertDoesNotThrow(() -> limits.validateFetchedTree(List.of(10L, 10L, 10L)));
        RepositoryLimitException error = assertThrows(
                RepositoryLimitException.class, () -> limits.validateFetchedTree(List.of(10L, 10L, 10L, 10L)));
        assertEquals(IndexingLimits.Limit.FILE_COUNT, error.limit());
        assertEquals(4L, error.observedValue());
        assertEquals(3L, error.configuredLimit());
    }

    @org.junit.jupiter.api.Test
    void fileBytesBoundary_atLimitAccepted_oneOverRejected() {
        IndexingLimits limits = new IndexingLimits(100, 1000, 100);
        assertDoesNotThrow(() -> limits.validateFetchedTree(List.of(100L)));
        RepositoryLimitException error =
                assertThrows(RepositoryLimitException.class, () -> limits.validateFetchedTree(List.of(101L)));
        assertEquals(IndexingLimits.Limit.FILE_BYTES, error.limit());
        assertEquals(101L, error.observedValue());
    }

    @org.junit.jupiter.api.Test
    void totalBytesBoundary_atLimitAccepted_oneOverRejected() {
        IndexingLimits limits = new IndexingLimits(100, 1000, 600);
        assertDoesNotThrow(() -> limits.validateFetchedTree(List.of(500L, 500L)));
        RepositoryLimitException error =
                assertThrows(RepositoryLimitException.class, () -> limits.validateFetchedTree(List.of(500L, 501L)));
        assertEquals(IndexingLimits.Limit.TOTAL_BYTES, error.limit());
        assertEquals(1001L, error.observedValue());
    }

    @org.junit.jupiter.api.Test
    void cumulativeDownloadBoundary_viaValidateFetchedContent() {
        IndexingLimits limits = new IndexingLimits(100, 1000, 100);
        assertEquals(100L, limits.validateFetchedContent(100L, 0L));
        assertEquals(1000L, limits.validateFetchedContent(100L, 900L));
        RepositoryLimitException byFile =
                assertThrows(RepositoryLimitException.class, () -> limits.validateFetchedContent(101L, 0L));
        assertEquals(IndexingLimits.Limit.FILE_BYTES, byFile.limit());
        RepositoryLimitException byTotal =
                assertThrows(RepositoryLimitException.class, () -> limits.validateFetchedContent(1L, 1000L));
        assertEquals(IndexingLimits.Limit.TOTAL_BYTES, byTotal.limit());
    }

    @org.junit.jupiter.api.Test
    void eachSingleCapSatisfiedButCumulativeTotalStillRejected() {
        IndexingLimits limits = new IndexingLimits(3, 100, 100);
        RepositoryLimitException error =
                assertThrows(RepositoryLimitException.class, () -> limits.validateFetchedTree(List.of(40L, 40L, 40L)));
        assertEquals(IndexingLimits.Limit.TOTAL_BYTES, error.limit());
        assertEquals(120L, error.observedValue());
    }
}
