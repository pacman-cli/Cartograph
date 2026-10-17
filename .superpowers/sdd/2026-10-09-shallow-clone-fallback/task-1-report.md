# Task 1 Report: Clone configuration and failure contracts

## Status

Implemented the Task 1 scope only: clone configuration defaults and validation, typed clone failures, nested registration under the existing GitHub configuration, YAML defaults, and focused tests. Process execution and strategy selection were not implemented.

## Files changed

- `src/main/java/com/cartograph/ingestion/github/CloneProperties.java`
  - Added clone settings with the required defaults.
  - Added validated setters for positive thresholds/timeouts and non-blank executable names.
- `src/main/java/com/cartograph/ingestion/github/CloneFailureException.java`
  - Added the five required failure kinds.
  - Added stable, safe messages that do not include command output, credentials, or repository URLs.
  - Added optional cause preservation without exposing cause data through the public message.
- `src/main/java/com/cartograph/ingestion/github/GitHubProperties.java`
  - Added the nested `clone` property while preserving the existing GitHub fields and retry validation.
- `src/main/resources/application.yml`
  - Added the documented `cartograph.github.clone` defaults.
- `src/test/java/com/cartograph/ingestion/github/ClonePropertiesTest.java`
  - Added default, validation, existing retry-setting, and safe failure-contract tests.

## Verification

Focused command required by the brief:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:$PATH"
mvn -q -Dtest='com.cartograph.ingestion.github.ClonePropertiesTest' test
```

Result: passed.

Additional verification:

- `mvn -q test` compiled and ran the suite, but failed at the existing `ConfigHonestyTest` because the new YAML keys are not yet documented in `README.md`. Updating `README.md` is outside this task's owned-file list.
- `git diff --check`: passed.

## Self-review

- Existing `cartograph.github` properties, defaults, and retry setters remain intact.
- Existing `@ConfigurationPropertiesScan` remains unchanged; clone settings are exposed as a nested configuration property on the existing `GitHubProperties` bean.
- No process execution, shell command construction, workspace cleanup, or strategy selection was added.
- No credentials, raw repository URLs, or command output are accepted as the public clone failure message.

## Concern

The full suite's `ConfigHonestyTest` requires corresponding `README.md` table entries for the new `cartograph.github.clone.*` keys. That file was intentionally not modified because the brief limits ownership to the listed files.
