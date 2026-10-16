# Task 2 Report: Bounded process execution and workspace cleanup

## Scope

Implemented only the Task 2 process/workspace contracts and their focused tests:

- `GitProcessRunner` uses `ProcessBuilder` with a fixed argument vector and working directory.
- Process stdout and stderr are drained concurrently and captured up to the configured byte bound.
- Start failures map to `CLONE_UNAVAILABLE`; non-zero exits map to `CLONE_FAILED` with a bounded `ProcessResult` cause; timeout and interruption terminate the process and map to `CLONE_TIMEOUT`.
- Interrupted execution restores the Java interrupt flag.
- `CloneWorkspace` creates a unique `Files.createTempDirectory` root, exposes an absent clone destination, validates containment including symlink ancestors, and deletes recursively without following symlinks.
- Cleanup is idempotent, time-bounded, and supports adding cleanup failures as suppressed diagnostics to an existing operation failure.

No clone strategy or fetcher was added.

## Verification

Focused command:

```text
mvn -Dtest='com.cartograph.ingestion.github.GitProcessRunnerTest,com.cartograph.ingestion.github.CloneWorkspaceTest' test
```

Result: **PASS** — 9 tests, 0 failures, 0 errors.

Additional checks:

- `git diff --check`: **PASS**
- `mvn test`: **FAIL**, 254 tests executed with one pre-existing/configuration-contract failure:
  `ConfigHonestyTest.readmeTableMatchesApplicationYmlUnderCartographNamespace` reports the `cartograph.github.clone` key missing from the README config table. That documentation/config mismatch is outside Task 2's allowed files and was not changed.

## Commit

The implementation is committed as:

```text
feat(ingestion): add bounded clone process lifecycle
```

## Concerns

- The full suite remains blocked by the Task 1 configuration documentation mismatch described above; resolving it would require touching files outside this task's ownership.
- Cleanup failures are retained as suppressed exceptions when an original operation failure is supplied; standalone cleanup reports `CLONE_FAILED`.

## Review fixes

- `GitProcessRunner` now applies one operation deadline to process completion, output-reader futures, and executor termination. Timed reader waits cancel readers, close pipes, terminate the process tree, and raise `CLONE_TIMEOUT`.
- Added a deterministic inherited-pipe test where a child keeps stdout open after its parent exits.
- `CloneWorkspace` cleanup now uses nanosecond-aware joins, including positive sub-millisecond durations. Timeout/interruption paths interrupt the worker, wait a bounded 50 ms grace period, and attach safe diagnostics containing the workspace root, timeout, and cleanup status. A timed-out worker is reported as remaining in progress with retry unsupported.
- Added deterministic cleanup timeout diagnostics and suppression coverage.

Verification after fixes:

- Focused Task 2 tests: **PASS** — 11 tests, 0 failures, 0 errors.
- `git diff --check`: **PASS**.
