# Cartograph F0.09 Shallow-Clone Fallback

**Date:** 2026-10-09  
**Status:** Design approved; specification review pending  
**Feature:** F0.09 — Shallow-clone fallback

## Goal

Add a bounded Git-based ingestion path for repositories that are too large for the GitHub recursive tree/blob API or whose tree response is truncated. The fallback must preserve the resolved commit identity, enforce existing repository limits, and clean up all temporary workspaces.

## User-visible outcome

A public repository that cannot be indexed safely through GitHub's tree API can still produce a complete source snapshot through the same `POST /api/v1/index` flow. The returned graph contract does not change. Clone failures are structured errors rather than partial or misleading graphs.

## Scope and non-goals

### In scope

- Configurable clone enablement and file-count threshold.
- Strategy selection from repository metadata/tree conditions.
- Shallow clone of an exact resolved commit.
- Bounded process execution and output capture.
- Safe workspace creation, source walking, and cleanup.
- Existing source filtering and file/byte limits during clone walking.
- Unit, integration, lifecycle, and cleanup tests.

### Out of scope

- Async jobs or queues.
- Docker or OS-level sandboxing.
- Authentication or private repository support.
- Python parser implementation.
- Cross-file symbol resolution.
- Viewer changes.
- Automatic retries specific to clone failures.

## Architecture

The application continues to resolve a branch or URL ref to a commit SHA before fetching sources. A source strategy then receives the original normalized repository reference and the immutable SHA:

```text
normalize URL
  → resolve ref to SHA
  → choose REST/tree or shallow-clone strategy
  → fetch exact SHA
  → apply source filters and limits
  → parse/build/persist unchanged
```

Introduce these boundaries:

```java
interface RepositorySourceFetcher {
    RepositorySnapshot fetch(RepositoryRef ref, String resolvedCommitSha);
}
```

The REST/tree adapter and shallow-clone adapter implement this boundary. The application service remains responsible for cache lookup and passes the resolved SHA exactly once. Existing graph building and SQLite persistence do not know which source strategy produced the snapshot.

### Components

- `RepositorySourceStrategy`: selects REST or clone mode from threshold/truncation state.
- `GitProcessRunner`: starts a fixed argument list, applies timeouts, bounds output, handles exit status, and restores interruption.
- `CloneWorkspace`: creates a unique temporary root and deletes it recursively without following links outside the root.
- `ShallowCloneRepositoryFetcher`: clones, checks out/verifies the exact SHA, walks eligible files, enforces limits, and returns a `RepositorySnapshot`.

No component may construct a shell command string or execute repository code.

## Configuration

Add settings under `cartograph.github.clone`:

```yaml
enabled: true
file-threshold: 10000
timeout-seconds: 120
cleanup-timeout-seconds: 10
executable: git
```

The implementation may use the repository's established property binding style, but these defaults and meanings are fixed. Invalid values fail configuration validation rather than silently disabling safety. The executable defaults to `git`; tests may inject a local executable or fake runner.

Strategy behavior:

- Below threshold and with a complete tree response: use REST/tree mode.
- At or above threshold: use clone mode when enabled.
- Truncated tree: use clone mode when enabled.
- Threshold/truncation requiring clone while clone is disabled: return a typed upstream/configuration error; never use partial tree data.

## Clone lifecycle

1. Create a unique temporary root and an absent clone destination.
2. Run a fixed-argument command equivalent to:

   ```text
   git clone --depth 1 --no-tags --single-branch <validated-repository-url> <destination>
   ```

3. Materialize or checkout the resolved SHA in detached state.
4. Verify the repository's checked-out commit equals the resolved SHA.
5. Walk files without following symlinks outside the destination.
6. Apply supported-language and generated/vendor/binary filtering.
7. Enforce file-count, per-file, cumulative-byte, and configured response limits before parsing.
8. Read bounded source contents and return a snapshot with the resolved SHA.
9. Delete the temporary root in a `finally` path.

Clone and checkout processes have hard timeouts. Process stdout/stderr capture is bounded and must not allow unbounded memory growth.

## Failure contract

Use typed failures that map to stable API errors without exposing command output or credentials:

- Git executable unavailable → `CLONE_UNAVAILABLE`.
- Timeout → `CLONE_TIMEOUT`.
- Non-zero clone or checkout → `CLONE_FAILED`.
- SHA mismatch → `CLONE_COMMIT_MISMATCH`.
- Existing repository limits → `REPOSITORY_LIMIT_EXCEEDED`.
- Clone disabled for a required fallback → `CLONE_DISABLED` or an equivalent stable upstream/configuration code.

On interruption, terminate the process, restore the Java interrupt flag, attempt cleanup, and propagate a typed failure. If cleanup fails, retain the original failure and log cleanup diagnostics. Never return a partial snapshot after a clone/read/verification failure.

## Test strategy

### Unit tests

- REST versus clone strategy selection for threshold and truncated-tree conditions.
- Fixed argument construction and exact SHA handling.
- Invalid configuration rejection.
- Symlink/path containment checks.
- File-count, per-file, cumulative-byte, and filtering behavior.
- Non-zero exit, timeout, interruption, and bounded output behavior.
- Cleanup after success, clone failure, timeout, interruption, and cleanup failure.

### Integration tests

Create a temporary local Git repository in the test, with at least two commits and a moving branch. Resolve the first commit, invoke clone mode, and verify:

- the first commit is indexed rather than the branch tip;
- returned repository and SHA identity are correct;
- source files are deterministic;
- no temporary workspace remains.

The tests must not require GitHub access, a token, or a network connection.

### Large-fixture test

Use a deterministic local fixture or generated repository with more than 10,000 files to exercise clone strategy selection and cleanup. The test must avoid parsing all generated files when measuring clone behavior; it should verify bounded selection and resource cleanup separately from parser accuracy.

## Acceptance criteria

F0.09 is complete only when:

1. A repository at or above the configured threshold selects clone mode.
2. A truncated tree selects clone mode.
3. Clone mode indexes the exact resolved commit SHA.
4. A deterministic fixture with more than 10,000 files exercises clone mode in verification.
5. No temporary workspace remains after success, failure, timeout, or interruption.
6. Clone and checkout have hard timeouts and bounded process output.
7. Existing source/file/byte limits are enforced before parsing.
8. No shell command string or repository code execution is used.
9. `mvn test` passes on Java 17.
10. The tracker, feature card, changelog, and `AGENTS.md` state only verified behavior.

## Rollout and compatibility

The default public API remains `POST /api/v1/index`. Existing REST/tree indexing remains the default below the threshold. The clone path is additive and can be disabled through configuration for environments without Git. No database schema change is required.
