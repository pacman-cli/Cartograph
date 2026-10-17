# Contributing to Cartograph

First off — **thank you**. Cartograph is built wave-by-wave in public, and every contribution moves a real roadmap feature forward. This guide gets you from zero to merged PR.

## 🧭 Ways to contribute

| If you… | Start here |
|---|---|
| Want a small, guided task | [`good first issue`](https://github.com/pacman-cli/Cartograph/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22) |
| Have domain experience (parsing, graphs, Spring, React Flow) | [`help wanted`](https://github.com/pacman-cli/Cartograph/issues?q=is%3Aissue+is%3Aopen+label%3A%22help+wanted%22) |
| Found a bug indexing a real repo | [Open a bug report](https://github.com/pacman-cli/Cartograph/issues/new?template=bug_report.yml) |
| Have an idea for the product | [Start a discussion](https://github.com/pacman-cli/Cartograph/discussions) before opening a feature PR |
| Want to improve docs | Doc PRs are first-class contributions — no issue needed |

## 🛠️ Development setup

**Requirements:** Java 17 (Temurin or Homebrew `openjdk@17`), Maven 3.8+. Node/pnpm/Python are *not* needed — the backend skeleton is pure JVM.

```bash
git clone https://github.com/YOUR_USERNAME/Cartograph.git
cd Cartograph

# macOS with Homebrew OpenJDK
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:$PATH"

mvn test          # full offline suite — must pass before any PR
mvn spotless:apply   # format Java code before committing
mvn spring-boot:run
```

Optional: `export GITHUB_TOKEN=…` to raise GitHub rate limits when testing live indexing. Never commit tokens — `.env.example` documents the local config surface.

### Architecture rules of the road

Cartograph is a **hexagonal (ports & adapters)** service. To keep it that way:

- The `application` package holds the use case and **port interfaces** — it must not import Spring Web, GitHub clients, or SQLite types.
- New external technology = new adapter in `ingestion/`, `parsing/`, or `persistence/` implementing a port.
- New language support = a new extractor under `parsing/` implementing `SourceParser`.
- Errors crossing the API boundary must use the stable `{ code, message }` contract — never leak stack traces.

## 🌿 Git workflow

1. **Fork** the repo (or branch directly if you're a collaborator).
2. **Branch** from `main` using one of:
   - `feat/<feature-id>-short-name` — e.g. `feat/F0.17-python-extractor`
   - `fix/short-description` — e.g. `fix/normalize-trailing-slash`
   - `docs/short-description`
3. **Commit** using [Conventional Commits](https://www.conventionalcommits.org/):
   ```
   feat(parsing): add Python extractor skeleton
   fix(api): reject blank repository URLs with 400
   docs: add viewer architecture diagram
   test(persistence): cover snapshot upsert races
   ```
4. **Push** and open a PR against `main`, linking the issue it closes (`Closes #123`).

### PR checklist

- [ ] `mvn test` passes locally (CI runs it too — PRs must be green)
- [ ] New code has tests; bug fixes include a regression test
- [ ] Ports remain free of HTTP/database implementation details; follow the existing Spring service registration pattern
- [ ] Error responses use the stable `{ code, message }` contract
- [ ] Docs updated if you added config, endpoints, or behavior
- [ ] If you completed a feature's **full acceptance criteria**, update `cartograph-feature-tracker.csv` (status + wave) in the same PR
- [ ] PR description explains *what* and *why*, not just *how*

## 🏷️ How the tracker works

[`cartograph-feature-tracker.csv`](cartograph-feature-tracker.csv) is the source of truth — 90+ features with permanent IDs (`F{phase}.{nn}`), priorities, dependencies, and waves. Issues reference these IDs. If your PR fully completes a feature card's acceptance criteria, flip its status; partial work stays 🟡 In Progress.

| Symbol | Meaning |
|---|---|
| ⬜ | Not started |
| 🟡 | In progress |
| ✅ | Complete (full acceptance criteria) |
| 🚫 | Deliberately excluded |
| ❌ | Dropped (documented reason required) |

## 🎃 Hacktoberfest

`good first issue` and `help wanted` are contributor-discovery labels, not guarantees of Hacktoberfest eligibility. Check the event's current rules and repository participation before relying on a PR for credit. Submit useful, tested changes; explain and verify any AI-assisted code as you would your own. Comment on an issue before starting so contributors can coordinate.

## 🔍 Review process

- Review timing depends on maintainer availability; include reproduction steps and test results to make review easier.
- CI must be green; a maintainer will help if your PR has conflicts or failing tests.
- Reviews may be iterative — that's normal, not rejection.

## 🏅 Recognition

Contributions of every kind are credited in the README contributors table — code,
documentation, ideas, tests, infrastructure, and bug reports. The table follows the
[all-contributors specification](https://allcontributors.org/docs/en/specification)
(`.all-contributorsrc`); a maintainer updates it as part of merging meaningful work,
or you can add yourself in your PR.

## 💬 Questions?

Ask in [Discussions](https://github.com/pacman-cli/Cartograph/discussions) or inside the issue you're working on — no question is too small. See also our [Code of Conduct](CODE_OF_CONDUCT.md).
