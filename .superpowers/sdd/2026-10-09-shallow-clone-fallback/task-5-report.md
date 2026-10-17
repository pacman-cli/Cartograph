# Task 5 report

## Status

Complete. Added offline local-Git integration coverage for exact commit materialization, moving branch protection, cleanup on success/failure/timeout/interruption, and deterministic large-tree clone routing.

## Verification

- `mvn -Dtest='com.cartograph.ingestion.github.ShallowCloneIntegrationTest,com.cartograph.IndexRepositoryEndToEndTest' test` — passed (6 tests, 0 failures, 0 errors, 0 skipped).
- `git diff --check` — passed.

## Notes

- Tests use the real local `git` executable and skip only when `git --version` is unavailable.
- The local remote URL is translated only inside the test runner so production URL validation and command construction remain exercised without network access.
