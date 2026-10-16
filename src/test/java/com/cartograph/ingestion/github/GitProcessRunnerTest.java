package com.cartograph.ingestion.github;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitProcessRunnerTest {
    @Test
    void executesFixedArgumentsInWorkingDirectory() throws Exception {
        Path directory = Files.createTempDirectory("runner-test-");
        Path script = script(directory, "printf '%s|%s|%s' \"$1\" \"$2\" \"$(pwd)\"\n");

        ProcessResult result = new GitProcessRunner().run(
                List.of(script.toString(), "first", "second"), directory, Duration.ofSeconds(2), 256);

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).startsWith("first|second|").contains(directory.toString());
    }

    @Test
    void mapsNonZeroExitAndBoundsBothStreams() throws Exception {
        Path directory = Files.createTempDirectory("runner-test-");
        Path script = script(directory, "printf 'out%.0s' {1..100}; printf 'err%.0s' {1..100} >&2; exit 7\n");

        assertThatThrownBy(() -> new GitProcessRunner().run(
                List.of(script.toString()), directory, Duration.ofSeconds(2), 20))
                .isInstanceOf(CloneFailureException.class)
                .satisfies(error -> {
                    var failure = (CloneFailureException) error;
                    assertThat(failure.kind()).isEqualTo(CloneFailureException.Kind.CLONE_FAILED);
                    assertThat(failure.getCause()).isInstanceOf(ProcessResult.class);
                    var result = (ProcessResult) failure.getCause();
                    assertThat(result.stdout()).hasSizeLessThanOrEqualTo(20);
                    assertThat(result.stderr()).hasSizeLessThanOrEqualTo(20);
                });
    }

    @Test
    void terminatesTimedOutProcess() throws Exception {
        Path directory = Files.createTempDirectory("runner-test-");
        Path marker = directory.resolve("finished");
        Path script = script(directory, "sleep 30; touch \"" + marker + "\"\n");

        assertThatThrownBy(() -> new GitProcessRunner().run(
                List.of(script.toString()), directory, Duration.ofMillis(100), 100))
                .isInstanceOf(CloneFailureException.class)
                .extracting(error -> ((CloneFailureException) error).kind())
                .isEqualTo(CloneFailureException.Kind.CLONE_TIMEOUT);
        assertThat(marker).doesNotExist();
    }

    @Test
    void restoresInterruptFlagWhenInterrupted() throws Exception {
        Path directory = Files.createTempDirectory("runner-test-");
        Path script = script(directory, "sleep 30\n");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                new GitProcessRunner().run(List.of(script.toString()), directory, Duration.ofSeconds(10), 100);
            } catch (Throwable error) {
                failure.set(error);
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            }
        });
        thread.start();
        Thread.sleep(100);
        thread.interrupt();
        thread.join(3000);

        assertThat(failure.get()).isInstanceOf(CloneFailureException.class);
        assertThat(((CloneFailureException) failure.get()).kind())
                .isEqualTo(CloneFailureException.Kind.CLONE_TIMEOUT);
        assertThat(thread.isAlive()).isFalse();
    }

    private static Path script(Path directory, String body) throws Exception {
        Path script = Files.createTempFile(directory, "command-", ".sh");
        Files.writeString(script, "#!/bin/sh\n" + body);
        script.toFile().setExecutable(true);
        return script;
    }
}
