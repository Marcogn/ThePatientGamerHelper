# Changelog

All notable changes to ThePatientGamerHelper are documented in this file.
The format is loosely based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning follows the app's `versionName` in `app/build.gradle.kts`.

## [Unreleased]

- **Fixed rapid navigation taps landing on the wrong screen, and gave screen transitions a modern, faster feel.**
  Navigating quickly (e.g. Home → Reviews → back → Backlog done in rapid succession) could
  register the tap on the screen still fading out underneath instead of the intended
  destination — reported live on-device, reproducible whenever two navigation actions happened
  within the transition window. Root cause: `navigation-compose` 2.8.5's default transition is a
  700ms crossfade during which both the outgoing and incoming screens stay composed and
  clickable at the same time, so a fast second tap could hit whichever one happened to occupy
  that screen coordinate. `ThePatientGamerHelperNavGraph` now (1) declares explicit, faster
  slide+fade transitions (300ms, Material-style directional push/pop) instead of the default
  crossfade, and (2) gates every `navigate()`/`popBackStack()` call behind a check that the
  *specific* `NavBackStackEntry` triggering it has actually reached `RESUMED` — the officially
  documented signal that its own transition has fully settled — so a stray tap on a screen still
  mid-transition is ignored rather than misrouted. Also addresses the general "navigation feels
  slow" complaint, since 300ms directional transitions read as snappier than the previous
  generic 700ms fade.
- **Fixed a `release.yml` bug that could burn a version number on a failed signed build.** The
  workflow used to commit and push the `versionName`/`versionCode` bump and the cut
  `CHANGELOG.md` section to `main` *before* attempting the signed build. Found on the sibling
  HackDex-Tracker project, which hit this for real on its first release run: a signing failure
  left `main` permanently bumped with no release ever published, and every retry then failed
  immediately because `[Unreleased]` was already empty. This project's releases have all
  succeeded so far, but the same latent bug applied here. Reordered so the commit/push only
  happens after `gh release create` actually succeeds; a build/signing failure now leaves `main`
  untouched and the same version can simply be re-run. See `docs/implementation-decisions.md`.

## [1.0.5] - 2026-08-20

### Fixed

- **Existing covers weren't retroactively shrunk by the previous backup-size fix, so on-device/backup size stayed just as large.**
  The previous session's downsample/compress fix (900px longest edge,
  JPEG quality 85) only ever applied to *newly* picked/downloaded covers
  going forward — every cover already on disk before updating, or
  restored from an old Drive backup taken before backups' covers were
  compressed either, stayed exactly as large as before. Confirmed by a
  real device report: after updating and adding only a few new games,
  the backup was still ~40MB. `ImageStorage.recompressOversizedCovers()`
  now runs once at every app startup (alongside the existing orphan
  sweep) and recompresses any cover file still above the target
  dimension or an oversized-for-a-compressed-JPEG file size, overwriting
  it in place at its existing path — no `Review`/`BacklogItem` row needs
  updating, and files already compliant are left untouched.
- **HowLongToBeat estimates ported to GameNative's working approach.**
  The client used to re-derive HowLongToBeat's current search endpoint at
  runtime by scanning its homepage JS bundles for a regex match — the
  recurring source of every previous HowLongToBeat breakage. It now hits
  the fixed `/api/bleed` endpoint pair used by
  [GameNative's `HltbService`](https://github.com/utkarshdalal/GameNative),
  a confirmed-working reference, with the same request shape, an
  auth-token retry on 401/403, and Levenshtein-distance best-match
  selection (extracted into a new unit-tested `domain/howlongtobeat/HltbMatcher.kt`)
  replacing the previous "exact match or first result" logic.

### Changed

- **Release notes always show only the changelog's significant-change
  highlights, never the full section.** `.github/workflows/release.yml`'s
  "Extract changelog section for this version" step used to hand the
  whole cut section to `gh release create` verbatim, no matter its
  length (`v1.0.4`'s own entry was a long wall of text). It now always
  extracts just the bold lead-in of each top-level bullet (or, for a
  bullet with no bold lead-in, its first sentence), with no length-based
  judgment call — a section with no top-level bullets falls back to the
  whole section instead, a structural fallback rather than a size cutoff.
  Either way the notes always end with a link back to this file's
  matching section. Same change applied to CoverDex's and
  3DSAppManager's `release.yml`, and each project's `CLAUDE.md` now
  documents the bullet/heading convention its own release notes depend
  on, so the highlights are always ready in this file rather than
  computed by trimming or reflowing prose at release time.

## [1.0.4] - 2026-08-18

### Fixed

- **Google Drive backup/restore silently excluded the entire backlog.**
  Only the review library was ever written to `data.json` or restored —
  every backlog list, item, comment and history entry was permanently
  unrecoverable from a Drive backup, with no warning anywhere that this
  was the case. A backup now also captures every backlog list with its
  items (including comments, history, and each item's link back to its
  review), and restoring a backup fully overwrites the local backlog the
  same way it already did the review library, preserving every id and
  timestamp. Old backups taken before this change still restore fine
  (with an empty backlog, since they never had one to restore).
- Google Drive backups and on-device storage were growing far larger than
  the library warranted, all traced back to TheGamesDB cover handling:
  - Every backup zipped the *entire* `covers/` folder, including files
    left behind by an abandoned form and — before the fix above — every
    backlog item's cover despite the backup format never restoring it.
    Backups now only include covers actually referenced by what's being
    backed up (reviews and, now, backlog items alike).
  - Covers picked from the photo library or downloaded from TheGamesDB
    were saved to disk at full/original resolution (TheGamesDB's box art
    can be several MB each). They're now downsampled and re-encoded
    before being written, with no visible quality loss at any size the
    app displays a cover.
  - The "cerca online" result list loaded TheGamesDB's full-resolution
    box art just to show a 48dp row icon, which Coil then disk-cached in
    full — a likely major contributor to overall app storage growth
    across many searches. It now uses TheGamesDB's small "thumb" crop for
    the list, falling back to the full image only if that's unavailable.
  - Replacing or removing a cover in the review/backlog item form used to
    delete the previous file from disk immediately, before the change
    was even saved — cancelling afterwards left the review pointing at a
    deleted file. Deletion is now deferred to a startup sweep
    (`CoverImageReconciler`) that reclaims any cover file no longer
    referenced by a review or backlog item, so cancelling never breaks
    an existing reference.
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
