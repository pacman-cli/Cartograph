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

## Review-fix follow-up — 2026-10-17

Replaced the aggregate clone configuration row with one README row per bindable
leaf. Updated `ConfigHonestyTest` to recursively discover YAML leaves at any
indentation depth, so namespace rows such as `cartograph.github.clone` are no
longer accepted as documented configuration. Added clone default assertions.

Verification: `mvn -Dtest=ConfigHonestyTest test` passed (2 tests); full `mvn
validate`, `mvn test` (274 tests, 0 failures, 0 errors, 0 skipped), `mvn
javadoc:javadoc`, and `git diff --check` all passed with Java 17. Non-fatal Maven
output included the expected private Flyway metadata 401 and existing deprecated
`MockBean` warnings.
