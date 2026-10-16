# Task 6 report

## Status

Complete with F0.09 conservatively marked **In Progress**. Documentation now records
the clone defaults, local Git prerequisite, threshold/truncated-tree fallback rules,
cleanup and timeout bounds, and the exact Java 17 verification commands. F0.08
remains **In Progress**. No public 10k-file live indexing claim was added.

## Commit

`feat(ingestion): add shallow clone fallback` (final commit recorded by Git)

## Tests

- `mvn validate` — passed.
- `mvn test` — passed: 274 tests, 0 failures, 0 errors, 0 skipped.
- `mvn javadoc:javadoc` — passed.
- `git diff --check` — passed.
- Clone evidence includes exact-SHA local integration, success/failure/timeout/interruption
  cleanup checks, bounded process tests, and a deterministic 10,001-file fixture that
  verifies clone strategy selection without parsing all generated files.

## Concerns

- The remaining F0.09 acceptance gap is a full large-fixture clone/index run; the
  existing 10,001-file test intentionally verifies routing only. F0.09 is therefore
  not marked Done.
- Non-fatal verification output included expected Spring test-context discovery
  warnings, the OpenJDK class-sharing warning, and intentional error/404 logs from
  negative-path tests.
