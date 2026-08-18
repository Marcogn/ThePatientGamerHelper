# Changelog

All notable changes to ThePatientGamerHelper are documented in this file.
The format is loosely based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning follows the app's `versionName` in `app/build.gradle.kts`.

## [Unreleased]

### Fixed

- HowLongToBeat estimate lookups still failed after the previous fix
  (#45), now with an "HTTP 404" reported in the app's error message
  instead of silently missing. Root cause, confirmed by fetching the
  real site: HowLongToBeat's build moved to Turbopack, so its bundle no
  longer has the `_app-*.js` chunk this client looked for specifically
  — it now scans every same-origin script the homepage references
  instead, same fallback strategy the actively-maintained
  ScrappyCocco/HowLongToBeat-PythonAPI reference uses. Also added the
  `Referer` header to every request, not just the final search POST:
  confirmed against the real site that the `/init` endpoint alone
  403s ("Access Denied") without it, unlike the static chunk requests.

## [1.0.3] - 2026-08-18

### Changed

- The `Release` GitHub Actions workflow now cuts the version itself:
  the manual dispatch form takes a `version` input (validated to be
  greater than the current `versionName`, no auto-computed default),
  and the workflow renames `CHANGELOG.md`'s `[Unreleased]` section,
  bumps `versionCode`/`versionName` in `app/build.gradle.kts`, and
  pushes that commit straight to `main` before building/signing/
  publishing — no more manually editing those two files by hand before
  triggering a release. The push authenticates with a `RELEASE_PUSH_TOKEN`
  fine-grained PAT (repo-scoped, `Contents: read/write`) instead of the
  default `GITHUB_TOKEN`, needed to actually bypass `main`'s branch
  protection: the default token's `github-actions[bot]` identity isn't
  covered by an "admins bypass required pull requests" exception the
  way a repo admin's own PAT is.

### Fixed

- HowLongToBeat estimate lookups (backlog "search online") always came
  back empty, even when the endpoint/auth extraction succeeded. Diffed
  this client against the actively-maintained
  ScrappyCocco/HowLongToBeat-PythonAPI reference and found the actual
  cause: the key/value pair pulled from the `/init` response must also
  be injected as an extra property in the search request's JSON body,
  not just sent as the `x-hp-key`/`x-hp-val` headers — HowLongToBeat's
  anti-bot check inspects both. Also fixed the bundle-derived search
  path to match the reference's shape (truncate to the first path
  segment, no forced trailing slash) instead of a variant that could
  point at a nonexistent route.

## [1.0.1] - 2026-08-17

### Fixed

- Drawer "Backlog" navigation could land on a stale review/edit-form
  screen instead of the Backlog list after cancelling the backlog's
  "write a review" flow, self-correcting only on a second tap. Caused by
  an unnecessary `saveState = true` on a back-stack pop that was meant
  to discard that back stack, not preserve it for later restoration.

### Changed

- Google Drive backup retention: only the most recent backup is now
  kept in the private appDataFolder. Every successful backup (manual or
  automatic) deletes older ones, including any pile left over from
  before this change.

## [1.0.0] - 2026-08-17

First public release.

This is the project's initial milestone, so rather than duplicate a
feature-by-feature list here, see the [README](README.md) for the full,
up-to-date description of what the app does — review library, trackable
backlog, online metadata search, statistics, multi-format export, Google
Drive backup, Italian/English localization, light/dark/system theme, and
list/grid views are all included in this release.

For the detailed development history behind this release, see
[`docs/phase-history.md`](docs/phase-history.md).
