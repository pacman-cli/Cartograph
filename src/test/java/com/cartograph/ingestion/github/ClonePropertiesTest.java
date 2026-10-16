package com.cartograph.ingestion.github;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ClonePropertiesTest {
    @Test
    void usesSafeCloneDefaults() {
        var properties = new CloneProperties();

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.fileThreshold()).isEqualTo(10_000);
        assertThat(properties.timeoutSeconds()).isEqualTo(120);
        assertThat(properties.cleanupTimeoutSeconds()).isEqualTo(10);
        assertThat(properties.executable()).isEqualTo("git");
    }

    @Test
    void rejectsNonPositiveThresholdAndTimeouts() {
        var properties = new CloneProperties();

        assertThatIllegalArgumentException().isThrownBy(() -> properties.setFileThreshold(0));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setFileThreshold(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setTimeoutSeconds(0));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setTimeoutSeconds(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setCleanupTimeoutSeconds(0));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setCleanupTimeoutSeconds(-1));
    }

    @Test
    void rejectsBlankExecutableNames() {
        var properties = new CloneProperties();

        assertThatIllegalArgumentException().isThrownBy(() -> properties.setExecutable(null));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setExecutable(""));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setExecutable("   "));
    }

    @Test
    void retainsValidationForExistingGitHubRetrySettings() {
        var properties = new GitHubProperties();

        assertThatIllegalArgumentException().isThrownBy(() -> properties.setMaxAttempts(0));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setMaxAttempts(11));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setRetryBackoffMillis(0));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setMaxRetrySleepMillis(-1));
    }

    @Test
    void exposesTypedFailuresWithSafeMessages() {
        for (var kind : CloneFailureException.Kind.values()) {
            var exception = new CloneFailureException(kind);

            assertThat(exception.kind()).isEqualTo(kind);
            assertThat(exception).hasMessageNotContaining("https://");
            assertThat(exception).hasMessageNotContaining("token");
            assertThat(exception).hasMessageNotContaining("stdout");
        }
    }
}
