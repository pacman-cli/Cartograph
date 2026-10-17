# F0.09 Shallow-Clone Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a bounded shallow-clone ingestion strategy that indexes the exact resolved commit when GitHub tree responses are truncated or repositories exceed the configured tree threshold, with deterministic cleanup and no partial snapshots.

**Architecture:** Keep the existing cache-first application flow and introduce a source-fetch strategy boundary. The REST/tree fetcher remains the small-repository path; a new clone fetcher uses an injected process runner and workspace manager, applies the existing filtering and limits, and returns the same `RepositorySnapshot` contract. All clone work is pinned to a resolved SHA and isolated from shell execution and repository code execution.

**Tech Stack:** Java 17, Spring Boot configuration properties, `ProcessBuilder` through an injected runner, Java NIO temporary directories/file walking, Git CLI, JUnit 5, Mockito/Spring tests, and the existing Maven build.

---

## Task 1: Define clone configuration and failure contracts

**Files:**
- Create: `src/main/java/com/cartograph/ingestion/github/CloneProperties.java`
- Create: `src/main/java/com/cartograph/ingestion/github/CloneFailureException.java`
- Modify: `src/main/java/com/cartograph/ingestion/github/GitHubProperties.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/cartograph/ingestion/github/ClonePropertiesTest.java`

- [ ] **Step 1: Write configuration tests**

Test defaults for enabled clone mode, a 10,000-file threshold, 120-second process timeout, 10-second cleanup timeout, and `git` executable. Test that zero/negative timeout and threshold values, blank executable names, and invalid retry-style settings are rejected.

- [ ] **Step 2: Implement `CloneProperties`**

Bind `cartograph.github.clone` with validated fields:

```java
boolean enabled = true;
long fileThreshold = 10_000;
long timeoutSeconds = 120;
long cleanupTimeoutSeconds = 10;
String executable = "git";
```

Expose immutable accessors and validated setters consistent with the repository's existing configuration-property classes.

- [ ] **Step 3: Define typed clone failures**

Add failure kinds `CLONE_UNAVAILABLE`, `CLONE_TIMEOUT`, `CLONE_FAILED`, `CLONE_COMMIT_MISMATCH`, and `CLONE_DISABLED`. The exception must expose the kind and a safe public message without including command output, repository credentials, or raw URLs.

- [ ] **Step 4: Register configuration**

Make the properties discoverable through the existing `@ConfigurationPropertiesScan` setup and add the documented YAML defaults.

- [ ] **Step 5: Run focused tests**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:$PATH"
mvn -Dtest='com.cartograph.ingestion.github.ClonePropertiesTest' test
```

Expected: all configuration and validation tests pass.

---

## Task 2: Build bounded process execution and workspace cleanup

**Files:**
- Create: `src/main/java/com/cartograph/ingestion/github/GitProcessRunner.java`
- Create: `src/main/java/com/cartograph/ingestion/github/CloneWorkspace.java`
- Create: `src/main/java/com/cartograph/ingestion/github/ProcessResult.java`
- Test: `src/test/java/com/cartograph/ingestion/github/GitProcessRunnerTest.java`
- Test: `src/test/java/com/cartograph/ingestion/github/CloneWorkspaceTest.java`

- [ ] **Step 1: Write process-runner tests**

Use temporary executable scripts created by the tests to verify: fixed argument ordering, working-directory selection, successful exit, non-zero exit, bounded stdout/stderr capture, timeout termination, and interrupted execution restoring the interrupt flag.

- [ ] **Step 2: Implement `GitProcessRunner`**

Accept a `List<String>` argument vector, working directory, timeout, and bounded output limit. Use `ProcessBuilder` directly, never `sh -c` or a concatenated command string. Capture output up to a fixed bound, terminate on timeout, and map failures to typed clone exceptions.

- [ ] **Step 3: Write workspace lifecycle tests**

Test unique root creation, destination creation rules, recursive deletion after nested files, symlink handling, cleanup timeout behavior, and cleanup failure reporting without replacing the original operation failure.

- [ ] **Step 4: Implement `CloneWorkspace`**

Create a unique temporary root with `Files.createTempDirectory`. Resolve paths against the root, reject paths that escape it, do not follow symlinks while walking/deleting, and expose idempotent cleanup that can be called from `finally` blocks.

- [ ] **Step 5: Run focused tests**

```bash
mvn -Dtest='com.cartograph.ingestion.github.GitProcessRunnerTest,com.cartograph.ingestion.github.CloneWorkspaceTest' test
```

Expected: all lifecycle and timeout tests pass without leaving temporary directories.

---

## Task 3: Add strategy selection to the repository source boundary

**Files:**
- Create: `src/main/java/com/cartograph/ingestion/github/RepositorySourceStrategy.java`
- Create: `src/main/java/com/cartograph/ingestion/github/RepositorySourceFetcher.java`
- Modify: `src/main/java/com/cartograph/application/RepositoryFetcher.java`
- Modify: `src/main/java/com/cartograph/ingestion/github/GitHubRepositoryFetcher.java`
- Modify: `src/main/java/com/cartograph/application/IndexRepositoryService.java`
- Test: `src/test/java/com/cartograph/ingestion/github/RepositorySourceStrategyTest.java`
- Test: `src/test/java/com/cartograph/application/IndexRepositoryServiceTest.java`

- [ ] **Step 1: Write strategy-selection tests**

Test that a complete tree below the threshold uses the REST fetcher, a tree at/above the threshold uses clone mode, a truncated tree uses clone mode, and clone-disabled fallback returns `CLONE_DISABLED` without parsing partial tree data.

- [ ] **Step 2: Define the exact-SHA source port**

Add a source-fetching contract equivalent to:

```java
RepositorySnapshot fetch(RepositoryRef ref, String resolvedCommitSha);
```

Keep existing direct `fetch` and `fetchResolved` compatibility behavior until all callers migrate.

- [ ] **Step 3: Refactor REST fetching to the source port**

Make the existing GitHub REST fetcher accept an already-resolved SHA and return a snapshot for that SHA without resolving a branch or calling metadata again. Preserve existing guardrails and warnings.

- [ ] **Step 4: Update application orchestration**

Resolve the commit once, check SQLite, then pass the same SHA to the strategy. Ensure cache hits do not invoke either REST content fetching or clone processes.

- [ ] **Step 5: Run focused tests**

```bash
mvn -Dtest='com.cartograph.application.IndexRepositoryServiceTest,com.cartograph.ingestion.github.RepositorySourceStrategyTest,com.cartograph.ingestion.github.GitHubRepositoryFetcherTest' test
```

Expected: existing REST tests and new strategy tests pass.

---

## Task 4: Implement the shallow-clone repository fetcher

**Files:**
- Create: `src/main/java/com/cartograph/ingestion/github/ShallowCloneRepositoryFetcher.java`
- Create: `src/main/java/com/cartograph/ingestion/github/CloneSourceWalker.java`
- Modify: `src/main/java/com/cartograph/ingestion/github/GitHubFetchException.java` or the typed clone failure mapping as needed
- Modify: `src/main/java/com/cartograph/CartographApplication.java`
- Test: `src/test/java/com/cartograph/ingestion/github/ShallowCloneRepositoryFetcherTest.java`

- [ ] **Step 1: Write clone command and exact-SHA tests**

Use a fake `GitProcessRunner` to assert the clone command contains `--depth 1`, `--no-tags`, `--single-branch`, the validated GitHub URL, and a temporary destination. Assert that the checkout/verification path uses the resolved 40-character SHA and rejects mismatches.

- [ ] **Step 2: Implement clone orchestration**

Create a workspace, run clone, run detached checkout or exact-SHA verification, and always clean up in `finally`. Map executable absence, non-zero exit, timeout, interruption, and SHA mismatch to typed failures.

- [ ] **Step 3: Write source-walker tests**

Create a local directory fixture containing supported JS/TS/Python files, generated/vendor directories, binary files, symlinks, oversized files, and nested paths. Assert only eligible files are read, warnings are emitted for skipped files, and limits stop processing before parsing.

- [ ] **Step 4: Implement `CloneSourceWalker`**

Walk with `Files.walk`/`walkFileTree` without following links. Apply path filters and supported-language detection before reading bytes. Use existing `IndexingLimits` for candidate count, per-file bytes, and cumulative bytes. Return deterministic path ordering and a `RepositorySnapshot` carrying the exact SHA and `filesSeen` count.

- [ ] **Step 5: Register production beans**

Register clone properties, process runner, workspace factory, clone fetcher, source strategy, and any adapter needed by the application. Keep dependencies out of the graph/parser domain packages.

- [ ] **Step 6: Run focused tests**

```bash
mvn -Dtest='com.cartograph.ingestion.github.ShallowCloneRepositoryFetcherTest,com.cartograph.ingestion.github.GitProcessRunnerTest,com.cartograph.ingestion.github.CloneWorkspaceTest' test
```

Expected: clone success, failure, timeout, cleanup, SHA, filtering, and limit tests pass.

---

## Task 5: Add local two-commit integration coverage

**Files:**
- Create: `src/test/java/com/cartograph/ingestion/github/ShallowCloneIntegrationTest.java`
- Create: `src/test/resources/clone-fixture/README.md`
- Modify: `src/test/java/com/cartograph/IndexRepositoryEndToEndTest.java`

- [ ] **Step 1: Create a temporary Git repository in the test**

Use the real local Git executable to initialize a temporary repository, create two commits, and move the branch after recording the first commit SHA. Skip the test with a clear assumption only when Git is unavailable in the test environment; do not skip when Git is present and the behavior fails.

- [ ] **Step 2: Verify exact-commit indexing**

Invoke clone mode against the local fixture, assert the first commit SHA and source contents are returned, then verify the moving branch tip is not indexed.

- [ ] **Step 3: Verify lifecycle cleanup**

Assert the workspace registry is empty after success, clone failure, timeout, and an interrupted operation.

- [ ] **Step 4: Verify large-fixture routing**

Generate more than 10,000 small files in a deterministic local repository, configure the threshold below that count, invoke the strategy, and assert clone mode is selected without parsing every generated file.

- [ ] **Step 5: Run integration tests**

```bash
mvn -Dtest='com.cartograph.ingestion.github.ShallowCloneIntegrationTest,com.cartograph.IndexRepositoryEndToEndTest' test
```

Expected: exact SHA, moving-branch, cleanup, and threshold routing tests pass offline.

---

## Task 6: Complete documentation, tracker, and full verification

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `cartograph-feature-tracker.csv`
- Modify: `cartograph-detailed-implementation-plan.md`
- Modify: `cartograph-execution-plan.md` only if the verified implementation changes sequencing

- [ ] **Step 1: Document verified configuration and commands**

Document clone settings, local Git prerequisite, the fallback conditions, and the exact Maven test command. Do not claim public 10k-file live indexing unless it was actually run.

- [ ] **Step 2: Update F0.09 status conservatively**

Keep `Not Started` until all acceptance criteria pass. Change to `In Progress` during implementation and only to `Done` after the large-fixture, cleanup, timeout, and exact-SHA evidence exists. Add a dated changelog entry with the measured test count.

- [ ] **Step 3: Run the complete check**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:$PATH"
mvn validate
mvn test
mvn javadoc:javadoc
git diff --check
```

Expected: all commands pass; report the test count and any non-fatal warnings exactly.

- [ ] **Step 4: Review changed files and commit**

Run `git status --short` and `git diff --stat`, confirm no generated database, target output, token, or temporary workspace is staged, then commit with:

```bash
git add <intended-files>
git commit -m "feat(ingestion): add shallow clone fallback"
```

## Plan self-review

- Every F0.09 spec section maps to Tasks 1–6: configuration, process bounds, workspace cleanup, strategy selection, exact-SHA clone, source walking, failures, tests, and docs.
- The REST/tree path remains intact and the application cache remains SHA-first.
- No task adds async jobs, authentication, viewer code, Python parsing, or cross-file resolution.
- All process execution is through an argument list and bounded runner; no shell command is constructed.
- Test interfaces and names are consistent across tasks: `RepositorySourceFetcher.fetch(ref, commitSha)`, `GitProcessRunner`, `CloneWorkspace`, and `ShallowCloneRepositoryFetcher`.
