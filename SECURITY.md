# Security Policy

## Supported versions

Cartograph is pre-1.0. Only the **latest commit on `main`** and the **latest tagged
release** receive security fixes — there are no long-lived support branches yet. If
you are running an older tag, upgrade before reporting or expecting a patch.

## Reporting a vulnerability

Please report privately via **GitHub's private vulnerability reporting**
(Repository → Security → Report a vulnerability). Do not open a public issue for
anything you believe is exploitable.

Include what makes a report actionable:

- The affected endpoint or component (e.g. `POST /api/v1/index`)
- A minimal reproduction (request, config, and repository URL involved)
- Your assessment of impact
- Whether the issue depends on a non-default configuration

## What to expect

- We aim to acknowledge reports within a few days, but as a small pre-1.0 project
  there is no guaranteed SLA — we'd rather be honest than optimistic.
- Fixes land on `main` first; credit goes to the reporter unless they prefer otherwise.

## Out of scope

- Regular bugs and usability problems — please use the public issue tracker for those.
- Reports that only apply to intentionally insecure local configurations.

## Handling secrets

`GITHUB_TOKEN` (and any other credential) must **never** be included in reports,
issue reports, screenshots, or logs. If you believe a token leaked, revoke it in
your GitHub settings first, then report the leak mechanics separately.
