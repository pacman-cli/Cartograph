# AGENTS.md

## Repository status

- This repository contains an offline-tested backend walking skeleton on `main`, not the full Wave 1 viewer or completed Wave 0 deployment/CI gates.
- The current implementation override is Java 17 + Maven + Spring Boot 3.5.6 (the earlier TypeScript/Next.js architecture remains planning context, not the active scaffold).
- The application entry point is `com.cartograph.CartographApplication`; the offline test suite runs with `mvn test`.

## Verified local commands

Use Homebrew's Java 17 before running Maven:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:$PATH"
mvn spring-boot:run
mvn test
```

For the complete local verification gate, use Java 17 and run:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:$PATH"
mvn validate
mvn test
mvn javadoc:javadoc
git diff --check
```

The offline suite does not require GitHub access or a token. Shallow-clone fallback
tests use the local `git` executable and temporary repositories. Clone mode is
enabled by default for a tree at or above 10,000 files or a truncated tree; it is
bounded to a 120-second clone/checkout timeout and a 10-second cleanup timeout,
and can be disabled with `cartograph.github.clone.enabled=false`. With fallback
disabled, a required clone path fails instead of indexing partial tree data.

The HTTP endpoint is `POST /api/v1/index` with a JSON `repositoryUrl`. SQLite persists snapshots at `./data/cartograph.db` by default; set `cartograph.sqlite.path` to override it.

## Source of truth

- Start with `README.md`; it links the strategy, feature plan, execution plan, and tracker in intended reading order.
- `cartograph-feature-tracker.csv` is the machine-readable status/dependency/wave source of truth. Feature IDs are permanent (`F{phase}.{nn}`); update status and wave there first, then keep the feature-plan card and changelog consistent.
- Use `cartograph-execution-plan.md` for current sequencing and cut lines. Each wave must end in a runnable demo; do not silently reduce a P0 acceptance criterion.
- The architecture document's original FastAPI/Python table and its TypeScript amendment are planning references; the active walking-skeleton implementation uses the Spring Boot override above.

## Planned boundaries

- Preserve the planned product boundaries when later work expands beyond this scaffold; the current vertical slice is intentionally contained in the Spring Boot application.
- The core vertical slice is URL → fetch → parse → resolve/normalize → SQLite snapshot → rendered graph. Test this walking skeleton before UI polish or later infrastructure.
- v1 deliberately excludes Kafka, multi-agent orchestration, and Neo4j; defer them to the gated Phase 2+ work unless the plans are explicitly revised.

## Operational constraints

- The current scaffold requires Java 17 and Maven. Node, pnpm, and Python remain future planning assumptions and are not required by the current tests.
- Planned deployment targets are Vercel for `apps/web` and Fly.io for `apps/api`/indexing workers. Do not provision or deploy until the relevant scaffold and credentials exist.
- `.env.example` is intended to become the canonical local configuration surface; never commit secrets. Planned external secrets belong in Vercel/Fly dashboards.
