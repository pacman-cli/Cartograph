# Final review fix report

## Fixes

- Added stable `ApiExceptionHandler` mappings for every `CloneFailureException.Kind`: unavailable and disabled return 503, timeout returns 504, and clone/commit-verification failures return 502. Responses use allowlisted codes and messages and never expose causes, command output, credentials, or raw repository URLs.
- Added controller regression coverage for all five clone failure kinds, including safe-response assertions.
- Replaced eager `Files.walk(...).toList()` path collection with incremental `walkFileTree` traversal. Ignored directories are pruned, symbolic-link paths are skipped, supported-file candidates are counted before reading, per-file and cumulative byte limits are checked before source materialization, and accepted source files are sorted deterministically after bounded collection.
- Added `finally` cleanup for the generated 10,001-file local repository fixture.

F0.09 remains **In Progress**. Deferred test-only Git assumptions remain unchanged.

## Verification

Commands were run with Java 17:

```text
mvn -Dtest='com.cartograph.api.IndexControllerTest,com.cartograph.ingestion.github.ShallowCloneRepositoryFetcherTest,com.cartograph.ingestion.github.ShallowCloneIntegrationTest' test
mvn validate
mvn test
mvn javadoc:javadoc
git diff --check
```
