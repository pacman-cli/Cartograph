# Cartograph — Detailed Implementation Plan & Feature Tracker

> **Companion document.** Strategy, positioning, tech-stack rationale, and high-level architecture live in
> [`cartograph-architecture-and-implementation-plan.md`](./cartograph-architecture-and-implementation-plan.md) (referenced below as **Strategy §n**).
> This document is the **execution layer**: every feature broken out into trackable units with IDs, priorities, effort, dependencies, and acceptance criteria.
>
> Working name: **Cartograph**. Version of this doc: **v1.0** (2026-09-27).

---

## 0. How to Use This Doc

### 0.1 Status legend

| Icon | Status | Meaning |
|---|---|---|
| ⬜ | Not Started | No work begun |
| 🟡 | In Progress | Actively being built |
| 🔵 | In Review | PR open / awaiting review |
| ✅ | Done | Meets Definition of Done (§6.9) |
| 🚫 | Blocked | Waiting on a dependency or decision (note the blocker in the card) |
| ❌ | Cut | Intentionally dropped (note the reason) |

### 0.2 Priority & effort

| Priority | Meaning |
|---|---|
| **P0** | Launch blocker — the phase's exit gate fails without it |
| **P1** | Should-have — noticeably weakens the phase if missing, but shippable without |
| **P2** | Nice-to-have — quality-of-life or forward-looking |
| **P3** | Later — revisit at the next phase gate |

| Effort | Size |
|---|---|
| XS | < 0.5 day |
| S | < 1 day |
| M | 1–3 days |
| L | 3–7 days |
| XL | > 1 week |

### 0.3 Feature ID convention

`F{phase}.{nn}` — e.g. `F0.23` is the 23rd feature of Phase 0. IDs are permanent: never renumber, only add. Dependencies always reference IDs, so re-planning never breaks the graph.

### 0.4 Update workflow (the tracking contract)

1. When you start a feature: flip status to 🟡 in the master tracker (§1) and on the card.
2. When a PR opens: 🔵. When merged **and** acceptance criteria are verified: ✅.
3. Any deviation from scope (new sub-task, cut criterion) gets a line in the card's **Notes**.
4. Every status change appends one line to the changelog (§9): `YYYY-MM-DD · F0.23 ⬜→✅ · note`.
5. **Minimal-first:** a feature touched in an earlier wave in minimal form stays 🟡 (In Progress) until *every* acceptance criterion on its card passes. The CSV `wave` column records the wave where the **full** criteria are met — Wave-1 vertical slices therefore leave features 🟡, never ✅.

---

## 1. Master Feature Tracker

**Totals:** 90 features — Phase 0: 52 · Phase 1: 11 · Phase 2: 14 · Phase 3: 13.
A machine-readable copy lives in [`cartograph-feature-tracker.csv`](./cartograph-feature-tracker.csv) (importable into Notion / Linear / GitHub Projects).

### 1.1 Phase 0 — MVP (Weeks 1–4) · 52 features

| ID | Feature | Epic | P | Effort | Depends on | Status |
|---|---|---|---|---|---|---|
| F0.01 | Monorepo scaffold & tooling | 0.1 Foundation | P0 | S | — | 🟡 |
| F0.02 | Shared types & API contracts | 0.1 Foundation | P0 | S | F0.01 | 🟡 |
| F0.03 | CI pipeline | 0.1 Foundation | P0 | S | F0.01 | ⬜ |
| F0.04 | Deploy pipeline & environments | 0.1 Foundation | P0 | S | F0.01 | ⬜ |
| F0.05 | Secrets & config management | 0.1 Foundation | P0 | S | F0.01 | 🟡 |
| F0.06 | Error tracking & product analytics | 0.1 Foundation | P1 | S | F0.04 | ⬜ |
| F0.07 | URL input & validation | 0.2 Ingestion | P0 | S | F0.02 | 🟡 |
| F0.08 | GitHub API client | 0.2 Ingestion | P0 | M | F0.02 | 🟡 |
| F0.09 | Shallow-clone fallback | 0.2 Ingestion | P0 | M | F0.08 | ⬜ |
| F0.10 | Repo guardrails & size caps | 0.2 Ingestion | P0 | S | F0.08 | 🟡 |
| F0.11 | Language detection | 0.2 Ingestion | P0 | S | F0.08 | 🟡 |
| F0.12 | Async indexing jobs & queue | 0.2 Ingestion | P0 | L | F0.08 | ⬜ |
| F0.13 | Indexing progress UI | 0.2 Ingestion | P0 | M | F0.12 | ⬜ |
| F0.14 | Friendly error states | 0.2 Ingestion | P0 | S | F0.10, F0.12 | 🟡 |
| F0.15 | tree-sitter runtime integration | 0.3 Parsing & Graph | P0 | M | F0.01 | 🟡 |
| F0.16 | TypeScript/JavaScript extractor | 0.3 Parsing & Graph | P0 | L | F0.15 | 🟡 |
| F0.17 | Python extractor | 0.3 Parsing & Graph | P0 | L | F0.15 | ⬜ |
| F0.18 | Symbol & module resolution | 0.3 Parsing & Graph | P0 | L | F0.16, F0.17 | 🟡 |
| F0.19 | Edge extraction | 0.3 Parsing & Graph | P0 | M | F0.18 | 🟡 |
| F0.20 | Confidence scoring | 0.3 Parsing & Graph | P0 | M | F0.19 | 🟡 |
| F0.21 | Web-framework route extraction | 0.3 Parsing & Graph | P1 | L | F0.18 | ⬜ |
| F0.22 | Graph normalization & stable IDs | 0.3 Parsing & Graph | P0 | M | F0.19 | 🟡 |
| F0.23 | SQLite graph store | 0.3 Parsing & Graph | P0 | M | F0.22 | 🟡 |
| F0.24 | Golden-file parser test suite | 0.3 Parsing & Graph | P0 | M | F0.23 | 🟡 |
| F0.25 | React Flow canvas shell | 0.4 Diagram Viewer | P0 | S | F0.02 | ⬜ |
| F0.26 | Auto-layout engine | 0.4 Diagram Viewer | P0 | M | F0.25 | ⬜ |
| F0.27 | Node design system | 0.4 Diagram Viewer | P0 | M | F0.25 | ⬜ |
| F0.28 | Edge rendering (solid vs dashed) | 0.4 Diagram Viewer | P0 | M | F0.25 | ⬜ |
| F0.29 | Legend & filters | 0.4 Diagram Viewer | P0 | S | F0.28 | ⬜ |
| F0.30 | Click-to-highlight neighborhood | 0.4 Diagram Viewer | P0 | M | F0.25 | ⬜ |
| F0.31 | GitHub deep links | 0.4 Diagram Viewer | P0 | S | F0.27 | ⬜ |
| F0.32 | Hover tooltips | 0.4 Diagram Viewer | P1 | S | F0.27 | ⬜ |
| F0.33 | Command-palette symbol search | 0.4 Diagram Viewer | P1 | M | F0.25 | ⬜ |
| F0.34 | Large-graph performance | 0.4 Diagram Viewer | P0 | L | F0.25 | ⬜ |
| F0.35 | Mobile-friendly basics | 0.4 Diagram Viewer | P2 | S | F0.25 | ⬜ |
| F0.36 | Permalink URL scheme & share state | 0.5 Sharing | P0 | S | F0.02 | ⬜ |
| F0.37 | OG social-preview images | 0.5 Sharing | P0 | M | F0.36 | ⬜ |
| F0.38 | SEO & discoverability | 0.5 Sharing | P0 | S | F0.36 | ⬜ |
| F0.39 | URL-hack redirect entry | 0.5 Sharing | P0 | S | F0.36 | ⬜ |
| F0.40 | Browser extension | 0.5 Sharing | P1 | M | F0.36 | ⬜ |
| F0.41 | Landing page | 0.5 Sharing | P0 | M | F0.37 | ⬜ |
| F0.42 | Context assembler v1 | 0.6 Ask / Chat | P0 | L | F0.23 | ⬜ |
| F0.43 | Embedding pipeline (semantic search) | 0.6 Ask / Chat | P1 | M | F0.23 | ⬜ |
| F0.44 | Chat UI (streaming) | 0.6 Ask / Chat | P0 | M | F0.42 | ⬜ |
| F0.45 | Grounded-answer contract | 0.6 Ask / Chat | P0 | M | F0.42 | ⬜ |
| F0.46 | Prompt-injection & abuse hardening | 0.6 Ask / Chat | P0 | M | F0.42 | ⬜ |
| F0.47 | LLM cost controls | 0.6 Ask / Chat | P0 | M | F0.44 | ⬜ |
| F0.48 | Commit-SHA cache layer | 0.7 Cache & Hygiene | P0 | M | F0.23 | 🟡 |
| F0.49 | Popular-repo pre-warm | 0.7 Cache & Hygiene | P2 | S | F0.48 | ⬜ |
| F0.50 | Rate limiting | 0.7 Cache & Hygiene | P0 | S | F0.04 | ⬜ |
| F0.51 | Health checks & server metrics | 0.7 Cache & Hygiene | P1 | S | F0.04 | ⬜ |
| F0.52 | Terms, privacy & attribution | 0.7 Cache & Hygiene | P0 | S | — | ⬜ |

### 1.2 Phase 1 — Trace Mode (Weeks 4–7) · 11 features

| ID | Feature | Epic | P | Effort | Depends on | Status |
|---|---|---|---|---|---|---|
| F1.01 | Entry-point registry | 1.1 Trace Engine | P0 | M | F0.21 | ⬜ |
| F1.02 | Forward path tracing | 1.1 Trace Engine | P0 | L | F1.01 | ⬜ |
| F1.03 | Reverse impact tracing | 1.1 Trace Engine | P0 | M | F1.02 | ⬜ |
| F1.04 | Trace animation & step-through | 1.1 Trace Engine | P0 | L | F1.02 | ⬜ |
| F1.05 | LLM trace narration | 1.1 Trace Engine | P0 | M | F1.02 | ⬜ |
| F1.06 | Public REST API + API keys | 1.2 API & Embeds | P1 | M | F0.23 | ⬜ |
| F1.07 | API rate tiers & docs | 1.2 API & Embeds | P1 | S | F1.06 | ⬜ |
| F1.08 | README architecture badge | 1.2 API & Embeds | P0 | M | F0.37 | ⬜ |
| F1.09 | Embeddable read-only viewer | 1.2 API & Embeds | P2 | M | F0.36 | ⬜ |
| F1.10 | Demo-repo curation & pre-index | 1.3 Launch | P0 | S | F0.48 | ⬜ |
| F1.11 | Show HN / Product Hunt launch kit | 1.3 Launch | P0 | S | F1.10, F1.04 | ⬜ |

### 1.3 Phase 2 — Depth (Months 2–4) · 14 features

| ID | Feature | Epic | P | Effort | Depends on | Status |
|---|---|---|---|---|---|---|
| F2.01 | GitHub OAuth & accounts | 2.1 Multi-Repo | P0* | M | Gate C | ⬜ |
| F2.02 | Workspace model & UI | 2.1 Multi-Repo | P1 | L | F2.01 | ⬜ |
| F2.03 | Cross-repo import resolution | 2.1 Multi-Repo | P1 | XL | F2.02 | ⬜ |
| F2.04 | Kafka contract extraction | 2.2 Stitching | P1 | L | F2.02 | ⬜ |
| F2.05 | Queue & broker contract extraction | 2.2 Stitching | P2 | M | F2.04 | ⬜ |
| F2.06 | Stitched-edge visualization | 2.2 Stitching | P1 | M | F2.04 | ⬜ |
| F2.07 | Neo4j migration (dual-write & cutover) | 2.3 Graph Store | P1 | XL | Gate C | ⬜ |
| F2.08 | GitHub App scaffold | 2.4 PR Bot | P1 | M | F2.01 | ⬜ |
| F2.09 | Structural graph diff | 2.4 PR Bot | P1 | L | F2.08 | ⬜ |
| F2.10 | PR comment renderer | 2.4 PR Bot | P1 | M | F2.09 | ⬜ |
| F2.11 | Go extractor | 2.5 Languages | P1 | L | F0.15 | ⬜ |
| F2.12 | Java/Kotlin extractor | 2.5 Languages | P2 | XL | F0.15 | ⬜ |
| F2.13 | Rust extractor | 2.5 Languages | P2 | L | F0.15 | ⬜ |
| F2.14 | Local/open-weights LLM adapter | 2.6 Self-Host Model | P2 | L | F0.42 | ⬜ |

\* F2.01 is P0 *within Phase 2* — auth is a prerequisite for everything else in Phase 2.

### 1.4 Phase 3 — Ecosystem (Months 4+) · 13 features

| ID | Feature | Epic | P | Effort | Depends on | Status |
|---|---|---|---|---|---|---|
| F3.01 | VS Code extension core & repo matching | 3.1 VS Code | P2 | M | — | ⬜ |
| F3.02 | Hover call-graph popover | 3.1 VS Code | P2 | M | F3.01 | ⬜ |
| F3.03 | In-editor trace panel | 3.1 VS Code | P3 | L | F3.02 | ⬜ |
| F3.04 | JetBrains plugin core | 3.2 JetBrains | P3 | L | — | ⬜ |
| F3.05 | JetBrains trace panel | 3.2 JetBrains | P3 | M | F3.04 | ⬜ |
| F3.06 | Docker Compose self-host bundle | 3.3 Self-Host | P2 | L | — | ⬜ |
| F3.07 | Self-host docs, upgrades & license | 3.3 Self-Host | P2 | M | F3.06 | ⬜ |
| F3.08 | Stripe billing & plans | 3.4 SaaS Tier | P2 | L | F2.01 | ⬜ |
| F3.09 | Private-repo indexing | 3.4 SaaS Tier | P2 | L | F2.01, F3.08 | ⬜ |
| F3.10 | Team workspaces & seats | 3.4 SaaS Tier | P3 | L | F2.02, F3.08 | ⬜ |
| F3.11 | SSO (SAML/OIDC) | 3.4 SaaS Tier | P3 | L | F3.10 | ⬜ |
| F3.12 | Audit logs | 3.5 Enterprise | P3 | M | F2.01 | ⬜ |
| F3.13 | SOC 2 readiness path | 3.5 Enterprise | P3 | L | — | ⬜ |

---

## 2. Phase 0 — MVP (Weeks 1–4): the viral surface

**Phase goal (from Strategy §6):** something a stranger can try in 20 seconds and want to screenshot.

### Epic 0.1 — Foundation & Infrastructure

Everything else sits on this. Kept deliberately small — no infra beyond what a first-time visitor can see.

#### F0.01 — Monorepo scaffold & tooling `P0 · S`
**Status:** 🟡 — Java 17/Maven backend scaffold merged; planned multi-app tooling and frontend remain unbuilt. Spring Boot override applies to this slice.
**Story:** As a developer on this project, I want a ready monorepo so every feature lands in the right place from day one.
**Sub-tasks:** pnpm workspaces with `apps/web` (Next.js + TS + Tailwind), `apps/api` (Node.js + TypeScript — route handlers + indexing worker; Strategy §7 amendment), `packages/parser`, `packages/graph`, `packages/context-assembler`, `packages/shared-types`; lint/format config; conventional-commit hook; `.editorconfig`; README skeleton.
**Acceptance criteria:**
- [ ] `pnpm dev` boots web + api concurrently with one command.
- [ ] All packages import `shared-types` without copy-pasted type definitions.
- [ ] Repo structure matches Strategy §8 exactly.
**Depends on:** — · **Notes:** Directory names are load-bearing — later features reference them.

#### F0.02 — Shared types & API contracts `P0 · S`
**Status:** 🟡 — Java domain records, ports, and validated REST DTOs exist; cross-client contracts and CI drift checks remain.
**Story:** As a full-stack dev, I want one source of truth for API types so the API can't drift from the client.
**Sub-tasks:** hand-authored TS types in `packages/shared-types` — single language after the 2026-10-01 stack consolidation, so no cross-language codegen layer; `Symbol`, `Edge`, `GraphSnapshot`, `IndexJob`, `ApiError` types; versioned API envelope (`{ data, meta }`); runtime validation at the API boundary (zod or equivalent).
**Acceptance criteria:**
- [ ] Web and api import every shared type from `shared-types` (no duplicated definitions).
- [ ] A deliberately breaking type change fails typecheck in CI.
**Depends on:** F0.01.

#### F0.03 — CI pipeline `P0 · S`
**Sub-tasks:** GitHub Actions: lint + typecheck + unit tests per package; run on PRs and main; caching for pnpm and pip.
**Acceptance criteria:**
- [ ] PR with failing test/lint cannot merge (branch protection on).
- [ ] CI completes < 5 min.
**Depends on:** F0.01.

#### F0.04 — Deploy pipeline & environments `P0 · S`
**Sub-tasks:** Vercel for `apps/web` (prod + preview per PR); Fly.io for `apps/api` + parsing workers; staging env; deploy-on-merge to main.
**Acceptance criteria:**
- [ ] Merge to main deploys both apps with zero manual steps.
- [ ] Every PR gets a preview URL.
- [ ] Rollback documented and tested once.
**Depends on:** F0.01.

#### F0.05 — Secrets & config management `P0 · S`
**Status:** 🟡 — Environment-backed GitHub token and example configuration exist; deployment secret management/scanning not verified.
**Sub-tasks:** `.env.example` as the canonical config surface; secrets in Vercel/Fly dashboards only; config validation at boot (fail fast on missing var).
**Acceptance criteria:**
- [ ] Fresh clone + `.env.example` → app boots locally with no tribal knowledge.
- [ ] No secret value ever committed (secret-scanning enabled).
**Depends on:** F0.01.

#### F0.06 — Error tracking & product analytics `P1 · S`
**Sub-tasks:** Sentry (web + api); PostHog with a documented event-naming convention (`object_verb`, lowercase); no PII in payloads.
**Acceptance criteria:**
- [ ] An exception in staging shows in Sentry with stack + release tag.
- [ ] Event taxonomy doc exists; first events (`repo_indexed`, `diagram_viewed`) fire.
**Depends on:** F0.04. **Notes:** Full event list in §6.7.

### Epic 0.2 — Repo Ingestion

#### F0.07 — URL input & validation `P0 · S`
**Status:** 🟡 — Backend normalization and rejection tests pass; homepage input and full feature fixtures remain.
**Story:** As a visitor, I paste a GitHub URL and Cartograph accepts `github.com/owner/repo`, `/tree/branch`, `?tab=readme`-polluted URLs, and rejects everything else politely.
**Sub-tasks:** URL parser → `{owner, repo, branch?, ref?}`; normalize GitHub UI variants; homepage input + `/owner/repo` route handling; inline validation errors.
**Acceptance criteria:**
- [ ] 20 real-world GitHub URL formats in a fixture table parse correctly.
- [ ] Non-GitHub or malformed input shows a helpful inline error (never a 500).
**Depends on:** F0.02.

#### F0.08 — GitHub API client `P0 · M`
**Status:** 🟡 — Bounded retry/backoff, primary/secondary limit handling, timeouts, validated responses, bounded ETag reuse, and single-resolution SHA-pinned fetching implemented with offline regression tests. Prior unauthenticated live smoke check passed. Live authenticated acceptance is blocked on server-side `GITHUB_TOKEN`; large-repo clone routing remains dependent on F0.09. No completion claimed.
**Story:** As the ingestion pipeline, I need a resilient GitHub REST client: repo metadata, recursive git tree, file contents, latest commit SHA.
**Sub-tasks:** typed client with ETag conditional requests; rate-limit accounting (primary + secondary limits) with exponential backoff; optional `GITHUB_TOKEN` (server-side only) to raise limits; per-repo fetch plan (tree API for small repos, clone for big ones).
**Acceptance criteria:**
- [ ] Handles 403 rate-limit responses gracefully with Retry-After, never crashes a job.
- [ ] Integration test against a public repo hits the happy path with and without a token.
**Depends on:** F0.02.

#### F0.09 — Shallow-clone fallback `P0 · M`
**Status:** 🟡 — Configuration, strategy selection, exact-SHA local clone integration, bounded process execution, source filtering, cleanup, timeout/interruption, and a deterministic 10,001-file routing fixture are covered by offline tests. The complete Java 17 verification suite passes after the README configuration table is synchronized. F0.09 remains In Progress because the large fixture proves clone selection without indexing all generated files, and no public 10k-file live indexing run is claimed.
**Sub-tasks:** `git clone --depth 1` path for repos above the tree-API threshold (set in config); blob materialization from the clone; deterministic workspace temp-dir lifecycle + cleanup; clone timeout.
**Acceptance criteria:**
- [ ] A repo > 10k files indexes via clone path in CI (pinned test repo). **Not yet evidenced:** the local 10,001-file fixture verifies strategy selection without parsing the generated files.
- [x] No temp workspace leaks after success, failure, or timeout (cleanup verified in test).
**Depends on:** F0.08.

#### F0.10 — Repo guardrails & size caps `P0 · S`
**Status:** 🟡 — File/count/byte guards and HTTP 413 tested; full card acceptance has not been verified.
**Story:** As an operator, I need hard free-tier caps so a monorepo can't blow up compute (Strategy §11, risk #1).
**Sub-tasks:** limits in config: max files (start: 5,000), max repo blob size (start: 50 MB), max single-file size; binary/vendored dir skip-list (`node_modules`, `dist`, `.min.js`, lockfiles); pre-flight check before any heavy work.
**Acceptance criteria:**
- [ ] Oversized repos are rejected in < 5s with the friendly "too big" state (F0.14), before cloning/parsing starts.
- [ ] Caps configurable per env without redeploy.
**Depends on:** F0.08.

#### F0.11 — Language detection `P0 · S`
**Status:** 🟡 — JS/TS extension selection and unsupported-file warnings exist; broader detection remains.
**Sub-tasks:** extension → language map; manifest sniffing (`package.json`, `tsconfig.json`, `requirements.txt`, `pyproject.toml`, `Pipfile`); emits ordered `language_set` per repo (drives which extractors run and forms part of cache keys, F0.48).
**Acceptance criteria:**
- [ ] Mixed TS+Python repo detected as both; extractor list matches.
- [ ] Repos in neither supported language get the "unsupported yet" state.
**Depends on:** F0.08.

#### F0.12 — Async indexing jobs & queue `P0 · L`
**Story:** As a user with a large repo, I get a job that progresses in the background instead of a browser spinner that dies at 30s.
**Sub-tasks:** `IndexJob` state machine (`queued → fetching → parsing → building → done | failed`) with per-stage timestamps; worker pool (start: simple in-process workers on Fly.io, no Kafka — Strategy §7 note); retry policy (2 retries, backoff, poison-job quarantine); job status API; stale-job reaper.
**Acceptance criteria:**
- [ ] Kill the worker mid-job → job recovers or fails cleanly, never hangs forever.
- [ ] 20 concurrent index requests don't starve the API process (load test documented).
- [ ] Job status endpoint reflects every stage transition within 2s.
**Depends on:** F0.08. **Notes:** Deliberately simple v1; swap for a real queue only when concurrency demands (Strategy §7 hosting row).

#### F0.13 — Indexing progress UI `P0 · M`
**Story:** As a user, I see live progress (stage indicator + queue position) and the page auto-transitions to the diagram when done.
**Sub-tasks:** polling or SSE from job status API; animated stage stepper; elapsed time; auto-redirect on done; "email me" is explicitly out of scope for v1.
**Acceptance criteria:**
- [ ] Progress reflects real job stages (not fake timers).
- [ ] Done → diagram renders without a manual reload.
**Depends on:** F0.12.

#### F0.14 — Friendly error states `P0 · S`
**Status:** 🟡 — Structured HTTP errors implemented; designed UI states and retry affordances remain.
**Story:** Every failure mode has a designed page: repo not found · private · too large · rate-limited · unsupported language · internal error — each with a next-step suggestion.
**Sub-tasks:** error taxonomy enum shared across API/UI (via F0.02); designed states with illustration-level polish; retry affordances where sensible.
**Acceptance criteria:**
- [ ] Each taxonomy member has a reachable designed state (e2e test per state).
- [ ] No raw stack trace or generic 500 is ever user-visible.
**Depends on:** F0.10, F0.12.

### Epic 0.3 — AST Parsing & Graph Building

The moat (Strategy §2, §3). Every feature here serves "accurate, not hallucinated."

#### F0.15 — tree-sitter runtime integration `P0 · M`
**Status:** 🟡 — Native JS/TS grammars tested on Java 17; Python, throughput benchmark, and timeout acceptance remain.
**Sub-tasks:** grammar loading for TS/JS + Python; native vs WASM decision documented; parse-error tolerance (never fail the whole repo on one broken file — collect per-file parse-error stats); per-file parse timeout; benchmark baseline (files/sec).
**Acceptance criteria:**
- [ ] File with syntax errors still yields partial symbols; repo-level indexing completes.
- [ ] Throughput measured and recorded (target: ≥ 2k files/min/core for TS+Python mix).
**Depends on:** F0.01.

#### F0.16 — TypeScript/JavaScript extractor `P0 · L`
**Status:** 🟡 — AST definitions, imports/exports, and direct calls tested; arrows and broader constructs remain unsupported or unresolved.
**Sub-tasks:** extract function declarations, arrow fns, class declarations, methods, object-literal methods; imports/exports/re-exports (`export * from`, barrel files, `export default`); call expressions incl. `await`, chained calls (`a.b().c()`), `new` expressions; dynamic `import()` (flagged low-confidence); tagged-template + JSX component references (P1 sub-task).
**Acceptance criteria:**
- [ ] Fixture file with ≥ 30 constructs produces a hand-verified symbol/edge list (100% match).
- [ ] Chained and method calls attribute to the receiver's method when resolvable, else flagged unresolved.
**Depends on:** F0.15.

#### F0.17 — Python extractor `P0 · L`
**Sub-tasks:** `def`/`async def`, classes, methods; imports (absolute, relative, `from x import y as z`); calls incl. attribute calls; decorators (routed to F0.21 for web frameworks); `__init__.py` re-export handling.
**Acceptance criteria:**
- [ ] Fixture file ≥ 30 constructs, hand-verified 100% match.
- [ ] Relative imports inside a package resolve to the right module symbols.
**Depends on:** F0.15.

#### F0.18 — Symbol & module resolution `P0 · L`
**Status:** 🟡 — Same-file lexical scope resolution exists; cross-file aliases/barrels and repo-wide resolution remain.
**Story:** Turn per-file AST facts into a repo-wide symbol table: which import refers to which real definition.
**Sub-tasks:** module-path resolution (TS path aliases via `tsconfig.json` `paths`, JS `main`/`exports` fields; Python package layout with `__init__.py`); alias/import-rename tracking; export-following through barrels; same-name disambiguation (prefer same-package, then same-repo; else unresolved); unresolved calls counted, not faked.
**Acceptance criteria:**
- [ ] Barrel re-export chain (3 hops) resolves to the defining file.
- [ ] `import { db } from './db'` in two packages resolves to two distinct symbols.
- [ ] Unresolved-ratio metric emitted per repo (feeds §6.3 honesty stats).
**Depends on:** F0.16, F0.17. **Notes:** This is the hardest feature in Phase 0 — schedule it early, protect it.

#### F0.19 — Edge extraction `P0 · M`
**Status:** 🟡 — Basic calls/imports with locations round-trip through SQLite; full edge-kind and multi-callsite acceptance remain.
**Sub-tasks:** typed edges per Strategy §5: `calls`, `imports`, `extends`, `implements`, `instantiates`, `handles_route`; dedup at (from, to, kind); edge provenance (file + line of the call site) stored in `meta` for deep-linking.
**Acceptance criteria:**
- [ ] Every edge kind produced by at least one fixture; round-trips through the store.
- [ ] Duplicate (from,to,kind) tuples collapse to one edge with multiple call sites in `meta`.
**Depends on:** F0.18.

#### F0.20 — Confidence scoring `P0 · M`
**Status:** 🟡 — Proven edges carry confidence; full scoring rules, unresolved-ratio statistics, and UI remain.
**Story:** The honesty feature — every edge carries a confidence, and the UI renders solid vs dashed accordingly (Strategy §5).
**Sub-tasks:** scoring rules (full table in §6.3): direct static = 1.00; cross-file resolved import = 0.95; barrel re-export = 0.85; dynamic `import()`/`importlib` with literal = 0.60; duck-typed/`getattr`/`any`-typed = 0.40; threshold constant `DASHED_BELOW = 0.50` defined in exactly one place; per-repo unresolved-ratio stat.
**Acceptance criteria:**
- [ ] Each rule has a unit test; threshold constant imported (not redefined) by UI + parser.
- [ ] Repo stats panel can display "% of call sites resolved statically".
**Depends on:** F0.19.

#### F0.21 — Web-framework route extraction `P1 · L`
**Story:** Entry points matter: `Route → Handler` edges for Express, Fastify, NestJS (JS/TS); Flask, FastAPI, Django (Python).
**Sub-tasks:** decorator/route-registration detection per framework (literal route strings; dynamic/parameterized routes flagged heuristic); `Route` symbol kind with `method + path` metadata; `handles_route` edges; unsupported framework → graceful skip (no crash, no fake routes).
**Acceptance criteria:**
- [ ] One fixture repo per framework yields the correct route table (verified by hand).
- [ ] Routes with computed paths (variables) render as inferred/heuristic, never as proven literals.
**Depends on:** F0.18. **Notes:** Feeds Trace Mode entry points (F1.01); P1 because the graph ships without it, but Trace Mode is weak without it.

#### F0.22 — Graph normalization & stable IDs `P0 · M`
**Status:** 🟡 — Deterministic SHA-256 IDs include snapshot and source location under the Spring spec; full multi-language and golden acceptance remain.
**Sub-tasks:** stable deterministic symbol IDs: `sha1(owner/repo/sha/relative_path/qualified_name)` (stable across re-indexes of the same commit); orphan policy (keep, flagged); self-loop policy (keep, render differently); multi-language graph merge; graph-level stats (node/edge counts by kind, unresolved ratio).
**Acceptance criteria:**
- [ ] Re-indexing the same SHA produces a byte-identical graph JSON (golden test).
- [ ] Symbol IDs survive file reorderings (same commit, different fetch order).
**Depends on:** F0.19.

#### F0.23 — SQLite graph store `P0 · M`
**Status:** 🟡 — Transactional snapshot storage and reads tested in one SQLite database; planned per-snapshot files/blob storage and 2k-file benchmark remain.
**Sub-tasks:** schema per §6.1 (column-level spec below); per-(repo, sha) snapshot DB file; writer/reader API in `packages/graph`; snapshots pushed to blob storage; reader loads snapshot by key.
**Acceptance criteria:**
- [ ] Snapshot for a 2k-file repo loads into the reader in < 500ms.
- [ ] Store is disposable: delete + re-index reproduces an equivalent graph (ID-stable from F0.22).
**Depends on:** F0.22.

#### F0.24 — Golden-file parser test suite `P0 · M`
**Status:** 🟡 — Small checked-in TS fixture and offline tests exist; real OSS golden corpus, precision/recall thresholds, and CI gate remain.
**Story:** The accuracy gate. Curated sample repos with hand-verified expected graphs; CI fails if accuracy regresses — this is how "accurate, not hallucinated" stays true.
**Sub-tasks:** 6–10 small real OSS repos (2–4 per language, incl. one with a web framework); expected-graph JSONs reviewed by hand; CI computes precision/recall vs thresholds (start: precision ≥ 0.95, recall ≥ 0.90 on calls; tune after first real-world run); accuracy dashboard numbers emitted per release.
**Acceptance criteria:**
- [ ] CI blocks merge on accuracy regression below thresholds.
- [ ] Adding a new golden repo is a documented 30-minute procedure.
**Depends on:** F0.23. **Notes:** See testing strategy §6.4.

### Epic 0.4 — Diagram Viewer

#### F0.25 — React Flow canvas shell `P0 · S`
**Sub-tasks:** React Flow wrapper component with zoom/pan/controls/minimap/background; loads graph JSON from the API; empty + loading + error states (reuse F0.14 taxonomy).
**Acceptance criteria:**
- [ ] Any graph snapshot renders; pan/zoom at 60fps on a 500-node graph.
**Depends on:** F0.02.

#### F0.26 — Auto-layout engine `P0 · M`
**Sub-tasks:** dagre or ELK integration; top-to-bottom + left-right toggle; cluster-by-folder grouping option; stable layout (same graph → same layout, no jitter on re-render); layout stored in URL so shares look identical (ties to F0.36).
**Acceptance criteria:**
- [ ] 1k-node graph lays out in < 3s (budget table §6.5).
- [ ] Toggle LR/TB re-layouts without losing selection.
**Depends on:** F0.25.

#### F0.27 — Node design system `P0 · M`
**Sub-tasks:** node types: file, function, class/method, route, cluster/group; kind icons + language color chips; long-name truncation with tooltip; selected/hover/dimmed visual states; route nodes visually distinct (they become trace entry points).
**Acceptance criteria:**
- [ ] All five kinds render distinctly; states visible in a Storybook-style gallery page.
**Depends on:** F0.25.

#### F0.28 — Edge rendering (solid vs dashed) `P0 · M`
**Story:** The differentiator made visible: statically proven edges solid, inferred edges dashed — legend explains it (Strategy §5).
**Sub-tasks:** confidence → style mapping via the single `DASHED_BELOW` constant from F0.20; edge-kind styling (calls vs imports vs extends); arrowheads; self-loops rendered as curved loops; animated-flow variant reserved for Trace Mode (F1.04).
**Acceptance criteria:**
- [ ] A fixture graph with mixed confidences renders solid/dashed exactly per the constant.
- [ ] Edge hover shows kind + confidence + call-site file:line.
**Depends on:** F0.25.

#### F0.29 — Legend & filters `P0 · S`
**Sub-tasks:** always-visible legend (solid/dashed/edge kinds); filters: edge kind, confidence band, language; filter state persists in URL.
**Acceptance criteria:**
- [ ] Toggling "inferred edges" off hides exactly the dashed set.
**Depends on:** F0.28.

#### F0.30 — Click-to-highlight neighborhood `P0 · M`
**Story:** Click any node → its direct callers/callees stay lit, everything else dims (Phase 0 checklist, Strategy §6).
**Sub-tasks:** 1-hop neighborhood query (client-side from loaded graph); in/out direction distinction (color-coded); Esc / click-away to clear; deep-linkable selected node (F0.36).
**Acceptance criteria:**
- [ ] Selection works on 1k-node graphs with no perceptible lag.
- [ ] Shared URL restores the same selection.
**Depends on:** F0.25.

#### F0.31 — GitHub deep links `P0 · S`
**Story:** Click a node's "open in GitHub" affordance → exact file + line range on GitHub (Phase 0 checklist).
**Sub-tasks:** `github.com/owner/repo/blob/{sha}/{path}#L{start}-L{end}` builder using edge/symbol provenance; per-node + per-tooltip action.
**Acceptance criteria:**
- [ ] Deep link lands on the correct lines for 20 sampled symbols across languages.
**Depends on:** F0.27.

#### F0.32 — Hover tooltips `P1 · S`
**Sub-tasks:** deferred tooltip (~300ms) with signature, path, line range, in/out edge counts; escape hatch on touch devices (tap = select, tooltip suppressed).
**Acceptance criteria:**
- [ ] Tooltip never blocks clicks on adjacent nodes (hit-testing verified).
**Depends on:** F0.27.

#### F0.33 — Command-palette symbol search `P1 · M`
**Sub-tasks:** ⌘K palette; fuzzy search over qualified names + file paths; result → pans/zooms to node and selects it; recent searches.
**Acceptance criteria:**
- [ ] Finding any symbol by name takes < 3 interactions on a 1k-node graph.
**Depends on:** F0.25.

#### F0.34 — Large-graph performance `P0 · L`
**Story:** Graphs must stay interactive at realistic repo sizes — a laggy diagram kills the "wow."
**Sub-tasks:** node-count adaptive detail (LOD: labels/edges simplify beyond thresholds); cap visible nodes with "zoom to expand" clusters (folder-level first); edge virtualization; measure with CI perf test (pan/zoom frame time on a 2k-node / 5k-edge fixture).
**Acceptance criteria:**
- [ ] Interactive (no dropped-frame stalls > 100ms) at 2k nodes / 5k edges on a mid laptop.
- [ ] Repos above render-cap degrade gracefully into folder clusters, never blank.
**Depends on:** F0.25. **Notes:** Budget table §6.5; if React Flow hits its ceiling, the custom-renderer escape hatch in Strategy §7 applies — but only after this feature proves the ceiling is real.

#### F0.35 — Mobile-friendly basics `P2 · S`
**Sub-tasks:** pinch-zoom/tap-select from React Flow defaults; bottom-sheet panel instead of side panel; acceptable OG-image-quality rendering on small screens.
**Acceptance criteria:**
- [ ] Manual pass on iOS Safari + Android Chrome: no broken layout, all core interactions possible.
**Depends on:** F0.25.

### Epic 0.5 — Sharing & Distribution

#### F0.36 — Permalink URL scheme & share state `P0 · S`
**Story:** `cartograph.dev/{owner}/{repo}` always resolves to the latest indexed default-branch graph; `cartograph.dev/{owner}/{repo}/at/{sha}` pins a commit; UI state (selection, filters, layout) lives in the URL so shares reproduce exactly.
**Sub-tasks:** routing + canonical URL logic; share-button (copy URL) with toast; state serialization in query params.
**Acceptance criteria:**
- [ ] Open → select node → copy URL → incognito window shows identical view.
- [ ] Pinned-SHA link never silently redirects to latest.
**Depends on:** F0.02.

#### F0.37 — OG social-preview images `P0 · M`
**Story:** Shared links on Twitter/Discord/Slack show a real graph thumbnail + repo stats — critical for the viral loop (Strategy §6 Phase 0).
**Sub-tasks:** server-rendered PNG via satori + resvg (or equivalent); cached per (repo, sha); layout: repo name, stars, language chips, simplified graph silhouette; fallback image for failed renders.
**Acceptance criteria:**
- [ ] OG image renders < 1.5s on cache miss; instant on hit.
- [ ] Twitter card validator + Discord link unfurl both show the image correctly.
**Depends on:** F0.36.

#### F0.38 — SEO & discoverability `P0 · S`
**Sub-tasks:** per-repo meta/title tags ("Call graph of owner/repo"); sitemap with pre-indexed popular repos; robots policy; JSON-LD structured data (SoftwareSourceCode).
**Acceptance criteria:**
- [ ] `site:` search intent verifiable; sitemap submitted; Lighthouse SEO ≥ 95 on repo pages.
**Depends on:** F0.36.

#### F0.39 — URL-hack redirect entry `P0 · S`
**Story:** Copy GitDiagram's proven growth mechanic: `cartograph.dev/https://github.com/owner/repo` works, and the README teaches the domain-swap trick (Strategy §10).
**Sub-tasks:** URL-in-URL route parsing; redirect to canonical permalink; the landing explains the trick in one line.
**Acceptance criteria:**
- [ ] Pasting a full GitHub URL after the domain works from a cold browser.
**Depends on:** F0.36.

#### F0.40 — Browser extension `P1 · M`
**Story:** One click from a GitHub repo page to its Cartograph graph (Strategy §6 Phase 0 "one-click browser extension").
**Sub-tasks:** MV3 extension; content-script button on `github.com/{owner}/{repo}`; opens `cartograph.dev/{owner}/{repo}`; no permissions beyond tabs; Chrome Web Store listing; Firefox build from same codebase.
**Acceptance criteria:**
- [ ] Works on repo root, subfolder, and file pages (maps to repo).
- [ ] Store review passed.
**Depends on:** F0.36.

#### F0.41 — Landing page `P0 · M`
**Story:** GIF-first, zero-jargon hero, "try it in 20s" with 3 pre-indexed demo repos (Strategy §10 item 1).
**Sub-tasks:** hero GIF of the graph + (later) Trace Mode; one-line value prop; "How is this different from GitDiagram?" honesty section (solid vs dashed framing); FAQ; demo links.
**Acceptance criteria:**
- [ ] Page communicates the value without scrolling past 2 screens; demo links work.
- [ ] Lighthouse performance ≥ 90 mobile.
**Depends on:** F0.37.

### Epic 0.6 — Ask / Chat

#### F0.42 — Context assembler v1 `P0 · L`
**Story:** The component that keeps LLM answers honest and cheap: pull the *relevant subgraph* + code snippets, never the whole repo (Strategy §4 component 6).
**Sub-tasks:** seed selection (matched symbol from question, else embedding hit from F0.43); k-hop subgraph expansion with token-budget-aware truncation; code-snippet extraction (symbol bodies within budget); prompt template with strict "answer only from context" contract + citation markers; token accounting logged per request.
**Acceptance criteria:**
- [ ] Prompt size bounded: p95 < 8k tokens regardless of repo size.
- [ ] Question about an unindexed topic → assembler returns "insufficient context" rather than padding.
**Depends on:** F0.23.

#### F0.43 — Embedding pipeline (semantic search) `P1 · M`
**Sub-tasks:** chunk = symbol summary (signature + docstring + 1-hop neighbor names); embedding model choice documented; sqlite-vec index inside the snapshot; semantic search endpoint used by assembler when symbol-match fails.
**Acceptance criteria:**
- [ ] "find the code that handles refunds" returns the right file in a test repo's top-3.
- [ ] Index build adds < 20% to indexing time.
**Depends on:** F0.23.

#### F0.44 — Chat UI (streaming) `P0 · M`
**Sub-tasks:** chat panel beside the diagram; SSE token streaming; per-repo conversation history (client-held in v1); suggested starter questions; stop button.
**Acceptance criteria:**
- [ ] First token < 2s p75 (budget §6.5); streaming never blocks the diagram interactions.
**Depends on:** F0.42.

#### F0.45 — Grounded-answer contract `P0 · M`
**Story:** Every answer cites what it's based on: clickable references to graph nodes or file/line snippets. No citations → "I couldn't find this in the graph."
**Sub-tasks:** citation format in LLM output (structured markers → rendered links); renderer links citations to nodes (selects them, F0.30) or GitHub (F0.31); refusal path when context is insufficient.
**Acceptance criteria:**
- [ ] Sampled answers over 3 repos: ≥ 90% of factual claims carry a working citation.
- [ ] Adversarial question outside context yields a refusal, not a hallucination.
**Depends on:** F0.42.

#### F0.46 — Prompt-injection & abuse hardening `P0 · M`
**Story:** Repo content is untrusted input. Code/README text must never be able to steer the assistant beyond answering questions about the repo.
**Sub-tasks:** system prompt isolates repo content as quoted data; injection test suite (malicious comments/READMEs in a fixture repo); no side-effect tools in the ask path; output filtering for obvious exfiltration patterns; per-IP question throttling beyond F0.50 defaults.
**Acceptance criteria:**
- [ ] Injection fixture repo cannot make the assistant ignore instructions or leak the system prompt.
- [ ] Test suite runs in CI against the live prompt template.
**Depends on:** F0.42.

#### F0.47 — LLM cost controls `P0 · M`
**Story:** Popularity must not equal bankruptcy (Strategy §11 risk #3).
**Sub-tasks:** per-IP daily question budget; per-repo response caching keyed (repo, sha, question-hash); cheap-model routing for simple queries (classification heuristic); budget-exceeded → graceful "graph-only" mode (answers from graph data, no LLM); daily spend dashboard + alarm.
**Acceptance criteria:**
- [ ] Median ask costs < $0.02 (measured, logged).
- [ ] Exceeding budget degrades to graph-only answers, never a hard error.
**Depends on:** F0.44. **Notes:** Cost model §6.6.

### Epic 0.7 — Caching & API Hygiene

#### F0.48 — Commit-SHA cache layer `P0 · M`
**Status:** 🟡 — SQLite repository+SHA reuse tested end-to-end; full cache-layer acceptance remains unverified.
**Story:** Repeat visits to a popular repo are instant and free (Strategy §4 component 8).
**Sub-tasks:** Redis/KV layer keyed `(repo, commit_sha, language_set)`; hit → serve snapshot pointer (no re-index); explicit re-index action (busts cache); stale-branch handling; cache-hit-rate metric.
**Acceptance criteria:**
- [ ] Second request for same repo+SHA returns in < 300ms p95.
- [ ] New commit on default branch triggers fresh index automatically.
**Depends on:** F0.23.

#### F0.49 — Popular-repo pre-warm `P2 · S`
**Sub-tasks:** curated list + cron pre-index; pre-warm on demand when a repo trends (request spike heuristic).
**Acceptance criteria:**
- [ ] Top-20 curated repos always cache-warm; hit rate tracked.
**Depends on:** F0.48.

#### F0.50 — Rate limiting `P0 · S`
**Sub-tasks:** IP-based limits per endpoint class (index: expensive, graph: cheap, ask: separate budget via F0.47); 429 + `Retry-After`; abuse-pattern logging; allowlist for the badge/OG endpoints (they get hot).
**Acceptance criteria:**
- [ ] Load test: abusive client gets limited before infra strain; normal users unaffected.
**Depends on:** F0.04.

#### F0.51 — Health checks & server metrics `P1 · S`
**Sub-tasks:** `/health` (liveness) + `/health/ready` (deps); metrics: indexing duration by stage, parse-error rate, unresolved-ratio (avg), cache hit rate, queue depth, LLM tokens/day; uptime monitoring on both apps.
**Acceptance criteria:**
- [ ] Dashboard answers "is indexing healthy right now?" in one glance.
**Depends on:** F0.04.

#### F0.52 — Terms, privacy & attribution `P0 · S`
**Sub-tasks:** Terms; Privacy (what's stored: public repo data, derived graphs; no private repos in v1); repo-content attribution policy; takedown contact; MIT license on the core (Strategy §10 item 6).
**Acceptance criteria:**
- [ ] Pages linked in footer; license file present at launch.
**Depends on:** —.

---

## 3. Phase 1 — Trace Mode (Weeks 4–7): the screenshot moment

**Phase goal (Strategy §6):** the signature "wow" feature; launch vehicle for Show HN / Product Hunt.

### Epic 1.1 — Trace Engine

#### F1.01 — Entry-point registry `P0 · M`
**Story:** Trace Mode needs starting points: every HTTP route (F0.21), CLI `main`s, message handlers, and top-level exported symbols, presented as a pickable list.
**Sub-tasks:** entry-point detection and ranking (routes first, then mains, then exports by reference count); entry-point picker UI grouped by type; free-text symbol search as an entry.
**Acceptance criteria:**
- [ ] Fixture repos expose their routes as traceable entries; unknown frameworks degrade to exports-only.
**Depends on:** F0.21.

#### F1.02 — Forward path tracing `P0 · L`
**Story:** Pick an entry → enumerate the real call paths through the graph (this is the product's signature interaction and it only works because the graph is real — Strategy §3).
**Sub-tasks:** path enumeration (bounded k-shortest-simple-paths; default k=5, depth cap configurable); cycle handling (visited-set, cycles rendered as loop-back markers); path ranking (edge confidence-weighted; prefer statically proven); per-path node/edge list API.
**Acceptance criteria:**
- [ ] On a golden repo, the top path for a route matches the hand-verified execution order.
- [ ] Cyclic graphs (recursive/mutual recursion) trace without hanging; loops visibly marked.
- [ ] Trace of a 50-node path returns < 1s server-side.
**Depends on:** F1.01. **Notes:** Confidence-weighting means inferred edges route lower — trace stays honest by construction.

#### F1.03 — Reverse impact tracing `P0 · M`
**Story:** "What breaks if I change this?" — select any symbol → all transitive callers light up (Strategy §6 Phase 1).
**Sub-tasks:** transitive-caller traversal (with depth/size caps + "show more"); impact list sidebar (grouped by file, sorted by distance); exportable impact list (copy as markdown); mixed forward+reverse mode (callers above, callees below).
**Acceptance criteria:**
- [ ] Impact of a hot utility symbol (100+ callers) caps and paginates without freezing UI.
- [ ] Results include confidence breakdown (how much is proven vs inferred).
**Depends on:** F1.02.

#### F1.04 — Trace animation & step-through `P0 · L`
**Story:** The money shot: watch the request path light up node-by-node across the graph (Strategy §6 Phase 1) — designed to be recorded as a GIF.
**Sub-tasks:** staggered node/edge activation along the traced path; play/pause/replay/speed control; step-through mode (next/prev hop with narration sync F1.05); path-list sidebar (each path clickable); dimming non-path elements; animation respects reduced-motion preference.
**Acceptance criteria:**
- [ ] Smooth on a 500-node graph (animation is transform/opacity-only, no re-layout).
- [ ] GIF-recordable at 30fps without dropped frames (manual QC on demo repos).
**Depends on:** F1.02.

#### F1.05 — LLM trace narration `P0 · M`
**Story:** Plain-English narration of the *actual* traced path: "this request hits `AuthMiddleware`, then `UserController.login`, which calls `UserService.verify`…" (Strategy §6 Phase 1). The LLM narrates verified structure — never invents it.
**Sub-tasks:** narration prompt fed only the traced path + symbol summaries (bounded); streamed alongside animation; sentence-level sync option (current sentence ↔ current node highlight); refusal when path contains unresolved edges ("part of this path is inferred").
**Acceptance criteria:**
- [ ] Narration references only symbols present in the path (automated check over fixture set).
- [ ] Narration cost per trace < $0.01 median.
**Depends on:** F1.02.

### Epic 1.2 — Public API & Embeds

#### F1.06 — Public REST API + API keys `P1 · M`
**Story:** Programmatic access: `GET /v1/repos/{owner}/{repo}/graph`, `/trace?entry=...`, `/impact?symbol=...` returning the same JSON the UI consumes (Strategy §6 Phase 1).
**Sub-tasks:** key issuance + hashed storage; auth middleware; OpenAPI spec published; response schemas reuse F0.02 contracts; usage logging per key.
**Acceptance criteria:**
- [ ] A stranger can go from docs page to a working `curl` in < 5 minutes.
**Depends on:** F0.23.

#### F1.07 — API rate tiers & docs `P1 · S`
**Sub-tasks:** anonymous vs keyed limits; rate-limit headers on every response; docs page with limits, examples, changelog.
**Acceptance criteria:**
- [ ] Limits enforced and documented; over-limit returns 429 with guidance.
**Depends on:** F1.06.

#### F1.08 — README architecture badge `P0 · M`
**Story:** The star-history.com-style badge: embeds turn every adopting README into free compounding distribution (Strategy §6 Phase 1, §10 item 4).
**Sub-tasks:** SVG badge endpoint (cached, hot-path exempt from rate limits — see F0.50); badge shows repo name + live stats; markdown snippet generator on the repo page; badge click-through to permalink; batched render caching to survive README-embed traffic spikes.
**Acceptance criteria:**
- [ ] Badge SVG loads < 100ms p95 (it will be embedded in high-traffic READMEs).
- [ ] Traffic-shape test: 100 concurrent badge fetches don't degrade the main API.
**Depends on:** F0.37.

#### F1.09 — Embeddable read-only viewer `P2 · M`
**Sub-tasks:** iframe + `<script>` embed of the viewer (no chat, no edit); allowlisted origin params; lazy-load postMessage sizing.
**Acceptance criteria:**
- [ ] Embed works in a docs site (mkdocs/docusaurus test page) with no console errors.
**Depends on:** F0.36.

### Epic 1.3 — Launch

#### F1.10 — Demo-repo curation & pre-index `P0 · S`
**Story:** 5–10 recognizable OSS repos (a popular Express app, a Flask app, etc.) pre-indexed, accuracy hand-checked, cache-warm — the launch-day showcase (Strategy §10 item 3).
**Sub-tasks:** repo selection (recognizable + graph-photogenic + framework coverage); manual accuracy review per repo (this is a QA gate, not a formality — fix parser gaps before launch); pre-warm via F0.49; freshness check script.
**Acceptance criteria:**
- [ ] Every demo repo: no wrong edges found in 15-minute human review; instant load.
**Depends on:** F0.48.

#### F1.11 — Show HN / Product Hunt launch kit `P0 · S`
**Story:** Same-day coordinated launch (Strategy §10 item 3): assets, copy, feedback channels ready before F1.10 signs off.
**Sub-tasks:** launch GIFs (trace animation featured); post copy for HN/PH/Twitter; comment-response rota; feedback channel (GitHub Discussions + in-app widget); launch-day monitoring dashboard (errors, queue depth, LLM spend).
**Acceptance criteria:**
- [ ] Dry run: assets posted to staging; monitoring dashboard live; rollback plan for the API documented.
**Depends on:** F1.10, F1.04.

---

## 4. Phase 2 — Depth (Months 2–4): the original CodeMonk vision

> **Gate C applies (Strategy §11, risk #6):** Phase 2 infra work (F2.01–F2.07) does **not** start until Phase 0/1 metrics hit thresholds — see §7, Gate C. Lighter-weight cards from here; full cards written at phase kickoff.

### Epic 2.1 — Auth & Multi-Repo Workspaces

| ID | Feature | Story & acceptance criteria (summary) | P · Effort | Depends |
|---|---|---|---|---|
| F2.01 | **GitHub OAuth & accounts** | Log in with GitHub; minimal scopes (public data only in this phase); session layer on API. **AC:** login/logout/revocation flows tested; no private-repo tokens accepted yet (explicitly rejected with explanation). | P0 (in-phase) · M | Gate C |
| F2.02 | **Workspace model & UI** | Create a workspace; link N repos; workspace-scoped combined views; workspace = the unit for stitching (F2.04). **AC:** workspace with 3 repos renders selectable per-repo or combined graph; membership persists across sessions. | P1 · L | F2.01 |
| F2.03 | **Cross-repo import resolution** | Resolve imports across linked repos (published-package deps pinned by version, git submodules); produce inter-repo edges marked `cross_repo`. **AC:** two linked repos where A imports B's package show A→B edges with correct pinned-version provenance; version-mismatch = inferred edge + warning. | P1 · XL | F2.02 |

### Epic 2.2 — Cross-Service Contract Stitching

| ID | Feature | Story & acceptance criteria (summary) | P · Effort | Depends |
|---|---|---|---|---|
| F2.04 | **Kafka contract extraction** | Extract topics + producer + consumer sites from Kafka client usage/config across workspace repos. **AC:** fixture multi-repo workspace yields correct topic→{producers, consumers} map, hand-verified; config-only topics (yml) also detected. | P1 · L | F2.02 |
| F2.05 | **Queue & broker contract extraction** | RabbitMQ, SQS, Redis streams — same contract model as F2.04. **AC:** one fixture per broker; contracts normalize into the same stitched-edge representation. | P2 · M | F2.04 |
| F2.06 | **Stitched-edge visualization** | "Service A emits `PaymentProcessedEvent` → consumed by B, C" as a distinct edge type in the viewer, clearly labeled contract-level (not a call). **AC:** stitched edges visually distinct + filterable; legend updated; clicking shows both producer and consumer code. | P1 · M | F2.04 |

### Epic 2.3 — Graph Store Evolution

| ID | Feature | Story & acceptance criteria (summary) | P · Effort | Depends |
|---|---|---|---|---|
| F2.07 | **Neo4j migration (dual-write & cutover)** | Migrate when cross-repo traversal outgrows SQLite snapshots (Strategy §4 component 4). Dual-write → backfill → parity-check → cutover. **AC:** query-parity suite (same answers SQLite vs Neo4j on golden workspaces) passes; rollback plan executable in < 10 min; single-repo mode still served from snapshots (no regression). | P1 · XL | Gate C |

### Epic 2.4 — PR Diagram Bot

| ID | Feature | Story & acceptance criteria (summary) | P · Effort | Depends |
|---|---|---|---|---|
| F2.08 | **GitHub App scaffold** | App install flow, webhook receiver, least-privilege permissions (contents: read, PRs: write). **AC:** install on a test org; webhook signature verification; replay-safe processing. | P1 · M | F2.01 |
| F2.09 | **Structural graph diff** | Diff graph(base SHA) vs graph(head SHA): added/removed/changed symbols and edges, classified (new call path, removed handler, changed route…). **AC:** fixture PR with known structural changes produces the expected diff classes; comment-only PRs produce empty diff (bot stays quiet). | P1 · L | F2.08 |
| F2.10 | **PR comment renderer** | Bot comments: updated diagram image + human-readable structural-change summary; edits its own comment on force-push (never spams). **AC:** force-push updates the existing comment; "no structural change" → no comment; comment includes both base/head permalink links. | P1 · M | F2.09 |

### Epic 2.5 — Language Expansion

| ID | Feature | Story & acceptance criteria (summary) | P · Effort | Depends |
|---|---|---|---|---|
| F2.11 | **Go extractor** | Functions, methods, interfaces, goroutine-fed call sites (static subset), package resolution; golden repo included. **AC:** golden-repo thresholds met (same bar as F0.24). | P1 · L | F0.15 |
| F2.12 | **Java/Kotlin extractor** | Classes/interfaces, overloads, packages, build-file (Maven/Gradle) module resolution. **AC:** golden-repo thresholds met. | P2 · XL | F0.15 |
| F2.13 | **Rust extractor** | Modules, traits, impls, workspace/crate resolution. **AC:** golden-repo thresholds met. | P2 · L | F0.15 |

### Epic 2.6 — Self-Hosted Model Option

| ID | Feature | Story & acceptance criteria (summary) | P · Effort | Depends |
|---|---|---|---|---|
| F2.14 | **Local/open-weights LLM adapter** | LLM-provider abstraction + adapter for a self-hosted open-weights model; eval harness comparing answer quality vs Claude on a fixed question set. **AC:** eval harness produces comparable quality scores; adapter passes the F0.45 grounding contract and F0.46 injection suite. | P2 · L | F0.42 |

---

## 5. Phase 3 — Ecosystem (Months 4+): IDE, self-host, SaaS

### Epic 3.1 — VS Code Extension

| ID | Feature | Story & acceptance criteria (summary) | P · Effort | Depends |
|---|---|---|---|---|
| F3.01 | **Extension core & repo matching** | Detect current workspace's repo; fetch its graph; auth token entry (keyed API). **AC:** opens a graph view for the active repo; unmatched repos → clear guidance. | P2 · M | — |
| F3.02 | **Hover call-graph popover** | Hover a function → mini callers/callees popover inline (Strategy §6 Phase 3). **AC:** popover < 300ms on cached graphs; click-through opens the full viewer. | P2 · M | F3.01 |
| F3.03 | **In-editor trace panel** | Run Trace Mode inside the IDE; results link to files/lines. **AC:** a traced route opens each hop at the right line via `revealDefinition`. | P3 · L | F3.02 |

### Epic 3.2 — JetBrains Plugin

| ID | Feature | Story & acceptance criteria (summary) | P · Effort | Depends |
|---|---|---|---|---|
| F3.04 | **JetBrains plugin core** | Parity with F3.01 + F3.02 (IntelliJ platform, Kotlin plugin). **AC:** parity checklist passes on IntelliJ IDEA. | P3 · L | — |
| F3.05 | **JetBrains trace panel** | Parity with F3.03. **AC:** trace from editor → hop navigation works. | P3 · M | F3.04 |

### Epic 3.3 — Self-Host Bundle

| ID | Feature | Story & acceptance criteria (summary) | P · Effort | Depends |
|---|---|---|---|---|
| F3.06 | **Docker Compose self-host bundle** | One command up: api + worker + web + db + redis; works against GitHub Enterprise via base-URL config (Strategy §6 Phase 3, enterprise-friendly). **AC:** fresh machine → `docker compose up` → index a repo end-to-end; GHE smoke test documented. | P2 · L | — |
| F3.07 | **Self-host docs, upgrades & license** | Upgrade path, health checks, backup/restore for snapshots, license clarity (core MIT, enterprise add-ons clearly scoped). **AC:** documented upgrade between two tagged versions succeeds without data loss. | P2 · M | F3.06 |

### Epic 3.4 — Hosted SaaS Tier

| ID | Feature | Story & acceptance criteria (summary) | P · Effort | Depends |
|---|---|---|---|---|
| F3.08 | **Stripe billing & plans** | Free / Pro / Team; usage metering (indexing minutes, ask queries, repo size ceiling); paywalls without breaking the free core (Strategy §6 Phase 3: core stays free). **AC:** plan changes prorate; free tier limits unchanged from public product; metering matches Stripe records in reconciliation test. | P2 · L | F2.01 |
| F3.09 | **Private-repo indexing** | Encrypted clone handling, minimal scoped tokens, auto-delete guarantees (retention config), private-repo isolation (never cached publicly, never in OG/badge flows). **AC:** private repo graphs never appear in shared caches/OG images (test suite proves isolation); token revocation stops all access. | P2 · L | F2.01, F3.08 |
| F3.10 | **Team workspaces & seats** | Seats, roles (admin/member/viewer), shared workspaces on hosted tier. **AC:** role matrix enforced API-side; seat count drives billing events. | P3 · L | F2.02, F3.08 |
| F3.11 | **SSO (SAML/OIDC)** | Enterprise-tier SSO. **AC:** one SAML + one OIDC IdP tested end-to-end; SCIM out of scope unless a customer asks. | P3 · L | F3.10 |

### Epic 3.5 — Enterprise Readiness

| ID | Feature | Story & acceptance criteria (summary) | P · Effort | Depends |
|---|---|---|---|---|
| F3.12 | **Audit logs** | Admin-visible event log (logins, index events, permission changes) on hosted + self-host. **AC:** log entries immutable, exportable, documented schema. | P3 · M | F2.01 |
| F3.13 | **SOC 2 readiness path** | Policies, access controls, vendor reviews, evidence collection — scoped to the hosted tier. **AC:** readiness checklist maintained; gaps tracked as issues; external audit only when a paying customer requires it. | P3 · L | — |

---

## 6. Cross-Cutting Specs

### 6.1 Data model v1 (column-level)

```sql
-- One SQLite snapshot per (repo_id, sha); blobs on object storage
Repo        (id PK, owner, name, default_branch, last_indexed_sha,
             size_bucket, language_set JSON, indexed_at)

File        (id PK, repo_id FK→Repo, path, language, loc, blob_sha)
            UNIQUE(repo_id, path)

Symbol      (id PK, file_id FK→File,
             kind CHECK IN ('function','method','class','module','route','entrypoint'),
             name, qualified_name, signature,
             start_line, end_line, is_exported BOOL)
            UNIQUE(repo_id, qualified_name)  -- via join; index (repo_id, qualified_name)

Edge        (id PK, from_symbol FK→Symbol, to_symbol FK→Symbol,
             kind CHECK IN ('calls','imports','extends','implements',
                            'handles_route','instantiates','publishes','consumes','cross_repo'),
             confidence REAL,        -- rules in §6.3
             resolution CHECK IN ('static','inferred','heuristic'),
             meta JSON)              -- call sites: [{file, line}]

EmbeddingChunk (id PK, symbol_id FK→Symbol, summary_text, vector BLOB)

IndexJob    (id PK, repo_id, sha, status, stage, attempts, error,
             queued_at, started_at, finished_at)
```

- **`publishes`/`consumes`/`cross_repo`** edge kinds are reserved now (Phase 2: F2.03, F2.04) so v1 schemas don't migrate.
- **Stable IDs:** `sha1(owner/repo/sha/rel_path/qualified_name)` — see F0.22. Never use row autoincrement IDs in URLs or APIs.
- **Counts to expose per repo:** files, symbols, edges by kind, % statically resolved (the honesty stat).

### 6.2 API surface v1

| Method & path | Purpose | Notes |
|---|---|---|
| `POST /api/index` | Kick off indexing `{github_url}` | Returns job id, or 302 to cached permalink |
| `GET /api/jobs/{id}` | Job status/stage (poll or SSE) | Powers F0.13 |
| `GET /api/graph?owner&repo[&sha]` | Graph snapshot JSON (nodes/edges/stats) | Cached by F0.48 |
| `GET /api/graph/neighbors?symbol_id&hops=1` | Neighborhood for highlight (F0.30) | Client-side fallback if graph already loaded |
| `POST /api/ask` | Grounded Q&A | SSE stream; budget-gated (F0.47) |
| `GET /api/og/{owner}/{repo}.png` | OG preview image | Cached; hot-path |
| `GET /api/badge/{owner}/{repo}.svg` | README badge | Cached; hot-path; rate-limit exempt (F0.50) |
| `GET /v1/repos/{owner}/{repo}/graph` | Public API graph | Keys (F1.06); Phase 1 |
| `GET /v1/repos/{owner}/{repo}/trace` | Public API trace | `?entry=route:POST /login` etc. |
| `GET /v1/repos/{owner}/{repo}/impact` | Public API impact | `?symbol=...` |
| `GET /health`, `GET /health/ready` | Liveness / readiness | F0.51 |

### 6.3 Confidence model (the honesty spec)

| Resolution situation | Confidence | Rendered |
|---|---|---|
| Direct call, same file, statically typed/obvious receiver | 1.00 | solid |
| Cross-file via resolved import (exact module path) | 0.95 | solid |
| Resolved through re-export barrel (≤ 3 hops) | 0.85 | solid |
| Dynamic `import()` / `importlib` with literal string | 0.60 | dashed |
| Duck-typed / `getattr` / `any`-typed receiver | 0.40 | dashed |
| Framework magic (decorator-registered handler, DI container) | 0.40–0.60 | dashed |
| Unresolved call site | — (edge dropped) | counted in repo's unresolved-ratio stat |

- Single source of truth: `DASHED_BELOW = 0.50` — parser, store, and UI all import it (F0.20).
- Repo stats panel shows "% of call sites statically resolved" — surfaced, never hidden. This stat is also a parser-quality dashboard metric (F0.24, F0.51).
- Rule changes require a golden-suite (F0.24) re-run in the PR.

### 6.4 Testing strategy

1. **Golden repos (the accuracy gate, F0.24):** 6–10 curated repos, hand-verified expected graphs; CI computes precision/recall. Start: precision ≥ 0.95, recall ≥ 0.90 on `calls` edges. Thresholds are phase-gate inputs, tune after first real-world data.
2. **Fixture unit tests:** per-extractor construct fixtures (F0.16/F0.17) — 100% match expected.
3. **Fuzz/property:** parser never crashes on arbitrary bytes; per-file errors are contained.
4. **E2E (Playwright):** paste URL → diagram → select → deep-link → ask → trace (the full user journey), plus every F0.14 error state.
5. **Perf tests in CI:** layout budget (F0.26), render budget (F0.34), cache latency (F0.48).
6. **Injection suite:** prompt-injection fixtures run against the live prompt template (F0.46).
7. **Manual QA ritual per release:** 15-minute human accuracy review of the demo repos (F1.10) — the LLM-free check that keeps marketing claims honest.

### 6.5 Performance budgets (backing Strategy §12's "< 30 seconds")

| Stage | Budget (p75, repo ≤ 2k files) |
|---|---|
| Fetch (tree API / clone) | ≤ 8s |
| Parse (all languages) | ≤ 10s |
| Graph build + store | ≤ 3s |
| Layout | ≤ 3s |
| First graph paint | ≤ 3s |
| **Total time-to-first-diagram** | **< 30s** (target < 15s) |
| Cached graph load (p95) | < 300ms |
| Ask first token (p75) | < 2s |
| Render interactivity | no stall > 100ms at 2k nodes / 5k edges |

### 6.6 Cost model (v1 assumptions, revisit monthly)

- LLM: Sonnet-class; ask prompt bounded ≤ 8k tokens (F0.42) → median ask < $0.02; trace narration < $0.01; per-IP daily budget with graph-only degradation (F0.47); response caching keyed (repo, sha, question-hash).
- Infra: Vercel (web) + one small Fly.io VM (api + workers) + Redis + blob storage for snapshots ≈ low tens of $/month pre-scale.
- GitHub API: token-backed budget; tree-API-first strategy keeps clone load low; ETags cut redundant calls.
- Alarm thresholds: daily LLM spend ×2 of 7-day median; queue depth > 50; indexing p95 > 2× budget.

### 6.7 Analytics event taxonomy (initial)

`repo_indexed`, `index_failed`, `diagram_viewed`, `node_selected`, `github_link_opened`, `filter_toggled`, `share_copied`, `ask_sent`, `ask_answered`, `trace_started`, `trace_completed`, `badge_viewed`, `badge_snippet_copied`, `extension_opened`, `urlhack_landing`.
North-star funnel for Gate B/C: `diagram_viewed → trace_started` (target ≥ 40%, Strategy §12) and `badge_snippet_copied` (distribution proxy).

### 6.8 Security & abuse posture

- **Untrusted code = untrusted input:** parsing in workers with per-file timeouts; no code execution from parsed content; clone path traversal/zip-bomb guards.
- **SSRF:** only `github.com` (later: configured GHE host) origins allowed for fetch/clone; no following arbitrary URLs from repo content.
- **Prompt injection:** repo content is quoted data in prompts; no side-effect tools in the ask path; injection suite in CI (F0.46).
- **No private repos in v1 at all** — no token-entry UI exists to abuse; private indexing arrives only with F3.09's isolation guarantees.
- **Rate limits** (F0.50) with CAPTCHA escalation on sustained abuse; badge/OG hot paths exempt but cached aggressively.

### 6.9 Definition of Done (applies to every feature card)

1. Code reviewed via PR; CI green (lint, types, tests).
2. All card acceptance criteria verified — a criterion that can't be verified gets rewritten until it can.
3. User-visible changes: analytics event emitted + error states handled.
4. Golden-suite run if parser/resolution/confidence logic changed.
5. Docs touched when relevant (README, ARCHITECTURE, API docs).
6. Interactive features meet the accessibility baseline (§6.10).
7. Master tracker (§1) + card status updated; changelog (§9) line appended.

### 6.10 Accessibility baseline (applies to every interactive feature)

1. **Keyboard:** the canvas (pan/zoom/select via React Flow defaults), ⌘K palette, chat panel, and all filters are fully operable without a mouse; visible focus states everywhere.
2. **Reduced motion:** Trace Mode animation (F1.04), skeletons, and transitions respect `prefers-reduced-motion`.
3. **Contrast & labels:** node/edge colors meet WCAG AA contrast against the canvas background; icon-only controls carry `aria-label`s; the legend (F0.29) is readable as text.
4. **Screen-reader landmarks:** diagram region, chat region, and status messages (indexing progress, errors) are announced.

---

## 7. Milestone Gates

| Gate | When | Exit criteria (measurable) |
|---|---|---|
| **A — MVP done** | end of Phase 0 | Stranger: paste URL → correct interactive diagram < 30s; golden suite green at thresholds; all 14 F0.14 error states reachable; 20 friendly-user smoke sessions with zero P0 bugs open; F0.52 live |
| **B — Trace launch** | end of Phase 1 | Trace Mode live on all demo repos (F1.10 QC passed); badge shipped and embedded in ≥ 3 friendly repos; Show HN submitted; baseline metrics captured for the funnel in §6.7 |
| **C — Depth authorization** (the anti-scope-creep gate, Strategy §11) | before any Phase 2 infra (F2.01–F2.07) | ≥ 1,000 GitHub stars **and** ≥ 40% of diagram viewers use Trace Mode **and** ≥ 50 badge embeds. **Not met →** next 2 weeks go to growth/accuracy work, not infra. Re-check weekly. |
| **D — Monetization authorization** | before F3.08–F3.11 | ≥ 200 private-repo waitlist signups or ≥ 3 inbound enterprise conversations; self-host bundle (F3.06) shipped as the enterprise beachhead |

---

## 8. Risk Register (expanded)

| # | Risk | Trigger signal (what we watch) | Mitigation | Contingency if it fires |
|---|---|---|---|---|
| 1 | Large monorepos blow up compute | Queue depth > 50; indexing p95 > 2× budget (F0.51 alarms) | Hard caps (F0.10), async jobs (F0.12), friendly queue UI (F0.13) | Degrade to file-level graph (skip symbol resolution) for oversized repos — still useful, still honest |
| 2 | Dynamic-language accuracy disappoints | Unresolved-ratio > 30% on popular repos (per-repo stat, F0.20/F0.51) | Confidence scoring + dashed edges + honesty framing (§6.3) | Pull LSP-based resolution forward from Strategy §7's v2 column; publish an accuracy dashboard; lean into the honesty narrative |
| 3 | LLM costs scale with popularity | Daily spend ×2 of 7-day median (F0.47 alarm) | Bounded context (F0.42), response cache, per-IP budgets (F0.47) | Graph-only answer mode default-on for unauthenticated traffic |
| 4 | GitDiagram-style clones copy the URL hack | Competitive launches (manual watch) | Moat = parsing accuracy + Trace Mode, not the trick (Strategy §11) | Ship the "solid vs dashed" accuracy comparison page; double down on golden-suite-derived accuracy claims |
| 5 | Scope creep back toward "CodeMonk-heavy" | Phase 2 work started while Gate C unmet | Gate C enforced in weekly review (§7) | Hard stop: reassign to growth backlog; phase gate is a rule, not a suggestion |
| 6 | tree-sitter misses framework magic (decorators, DI, route registries) | Golden-suite misses concentrated in framework files (F0.24 triage) | Per-framework extractors (F0.21), heuristic edges marked inferred | Publish a "supported frameworks" table; unsupported = routes come from config files only, clearly labeled |
| 7 | Trace Mode underused (the wow doesn't wow) | `trace_started / diagram_viewed` < 20% two weeks post-launch (§6.7 funnel) | Entry-point picker defaults, suggested traces (F1.01) | Auto-trace the most-trafficked route by default on load; onboarding tooltip; revisit entry-point ranking |
| 8 | Badge/OG hot-path traffic degrades the API | Badge p95 latency rising (F0.51) | Rate-limit exemption + aggressive caching (F0.48/F0.50/F1.08) | Serve badge/OG from edge/static layer; pre-render top-100 |
| 9 | React Flow ceiling hit before F0.34 lands | Frame-time CI test regressing (F0.34) | LOD + clustering + caps (F0.34) | Custom WebGL renderer (Strategy §7's stated escape hatch) — only with Gate C metrics proving demand for huge graphs |
| 10 | Solo/small-team burnout on a 90-feature plan | Changelog velocity stalls; 🚫 statuses pile up | Effort-sized cards, strict phase gates, P0-first ordering (§1) | Cut P2s without ceremony (that's what the column is for); shrink Phase 0 scope to the true viral core: F0.07–F0.31, F0.36–F0.41, F0.48, F0.50 |

---

## 9. Changelog

Tracking log — append one line per status change (see §0.4).

2026-10-17 · F0.09 remains 🟡 · documented clone settings, local Git prerequisite, fallback conditions, and exact verification commands; synchronized the README configuration table; Java 17 suite measured 274 tests before the documentation-only correction. Full large-fixture clone/index evidence is still open.

2026-10-03 · F0.08 remains 🟡 · resilience and pinned-fetch implementation passes combined 141-test suite; token-backed live acceptance and large-repo clone routing still open. Do not start the next feature until this feature's remaining scope is resolved.

2026-10-02 integration: Spring Boot backend merged into main; `mvn test` on Java 17 passed 60 tests. This is a backend-only subset, not completion of the original Wave 0/1 exit gates. Waves remain planned full-acceptance targets. No original feature is newly marked Done.
2026-10-02 live smoke check: indexed public `sindresorhus/is` through the REST endpoint (19 files seen, 5 parsed, 242 nodes, 487 edges); repeated request returned the same SQLite-cached snapshot. Full live acceptance remains open.

- 2026-10-02 · F0.01 ⬜→🟡 · Maven/Spring backend scaffold only.
- 2026-10-02 · F0.02 ⬜→🟡 · Java domain/API contracts only.
- 2026-10-02 · F0.05 ⬜→🟡 · Local environment configuration only.
- 2026-10-02 · F0.07 ⬜→🟡 · Backend URL validation only.
- 2026-10-02 · F0.08 ⬜→🟡 · Offline-tested GitHub adapter.
- 2026-10-02 · F0.10 ⬜→🟡 · Bounded fetch/size guards.
- 2026-10-02 · F0.11 ⬜→🟡 · JS/TS extension selection.
- 2026-10-02 · F0.14 ⬜→🟡 · HTTP errors, no UI.
- 2026-10-02 · F0.15 ⬜→🟡 · JS/TS native runtime only.
- 2026-10-02 · F0.16 ⬜→🟡 · Limited AST extraction.
- 2026-10-02 · F0.18 ⬜→🟡 · Same-file scope resolution only.
- 2026-10-02 · F0.19 ⬜→🟡 · Basic located edges only.
- 2026-10-02 · F0.20 ⬜→🟡 · Proven-edge confidence only.
- 2026-10-02 · F0.22 ⬜→🟡 · Snapshot-scoped deterministic IDs.
- 2026-10-02 · F0.23 ⬜→🟡 · SQLite adapter, no scale benchmark.
- 2026-10-02 · F0.24 ⬜→🟡 · Small fixture, no OSS accuracy gate.
- 2026-10-02 · F0.48 ⬜→🟡 · SQLite SHA reuse only.

```
2026-09-27 · doc created · v1.0 · 90 features tracked (52 / 11 / 14 / 13 across Phases 0–3)
2026-10-01 · v1.1 · stack consolidated to TypeScript end-to-end (F0.01/F0.02 wording, F0.02 & F0.04 effort M→S, §7 rows, §8 comment) · minimal-first convention added (§0.4) · accessibility baseline added (§6.10, DoD item 6)
```
