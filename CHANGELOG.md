# Changelog

All notable changes to ThePatientGamerHelper are documented in this file.
The format is loosely based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning follows the app's `versionName` in `app/build.gradle.kts`.

## [Unreleased]

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
