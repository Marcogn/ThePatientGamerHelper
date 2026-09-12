# Next steps

Short backlog of work that is genuinely still open, according to the
project's own documentation. Every entry cites its source — nothing here
is invented. DOCX export is deliberately **not** listed: `CLAUDE.md`,
"DOCX export — why it was not implemented", marks it as permanently out
of scope, not pending work.

## 1. Google Drive OAuth consent screen stuck in "Testing"

The OAuth consent screen for Drive backup is in Google's "Testing"
publish status, which expires the underlying authorization grant after 7
days. The interactive "Sign in with Google" flow is unaffected (it just
re-prompts), but the daily automatic `BackupWorker` relies on silent
`authorize()` calls — past 7 days without an interactive re-login it
starts failing silently (`Result.failure()`, by design) until the user
opens Settings and logs in again.

Known remedy, not yet applied: move the consent screen to "In
production" in the Google Cloud Console. `drive.appdata` is a
non-sensitive scope, so this should not require Google's full manual
security review. This is a manual console action, not a code change.

- Source: `CLAUDE.md`, "Phase 4 — Google Drive cloud backup", "Known
  limitation, not yet addressed".
- Source: `docs/phase-history.md` lines 139–149, "Known consequence of
  staying in 'Testing', not yet addressed".

## 2. SET-39 — long-running test for the 7-day expiry, not yet reproduced

`docs/test-plan.md` has a dedicated manual test case for the limitation
above: leave the app installed with automatic backup enabled and no
manual login for more than a week, then confirm Settings shows the
automatic worker's failure (not a crash or a stuck "in progress" state),
and that a manual login re-establishes it.

The test plan itself flags it as unresolved either way: "If this never
actually happens in practice, downgrade/remove this item."

- Source: `docs/test-plan.md` lines 1181–1191, **SET-39 (edge,
  long-running — not yet reproduced)**.

## 3. Manual on-device verifications still pending

`docs/test-plan.md`'s "Known regressions" log (section 10) records
several fixes that were made by code review rather than confirmed
on-device, each explicitly flagged as still needing real-device
confirmation before being considered closed:

- **FORM-33b** (and FORM-34b, FORM-39b/c, SET-16c) — the cover
  image storage/backup bloat fix (2026-08-18 entry): re-verify FORM-33b
  specifically — editing a review, replacing its cover, then cancelling
  without saving must leave the *original* cover intact, not broken.
  Source: `docs/test-plan.md` lines 1508–1513.
- **SET-24b** — the backlog round trip through a Google Drive
  backup/restore (2026-08-18 follow-up entry): back up a backlog with
  lists/items/comments/history/review links, mutate it, restore, and
  confirm everything comes back exactly as it was. Source:
  `docs/test-plan.md` lines 1514–1527.
- **SET-16e** — the follow-up cover-bloat regression (2026-08-20 entry):
  confirm that *pre-existing* cover files (not just newly written ones)
  are actually shrunk, since the first fix only affected new writes.
  Source: `docs/test-plan.md` lines 1541–1549.
- **REG-15** — the rapid-navigation misclick fix (2026-08-21 entry):
  confirm fast Home/Library/Backlog taps in succession land on the
  intended screen, not one still fading out underneath. Source:
  `docs/test-plan.md` lines 1550–1557.
- The HowLongToBeat fixed-endpoint port (2026-08-20 entry) also still
  needs a real HTTP round trip against `howlongtobeat.com` confirmed
  on-device — no test-plan item number is attached to this one, it is
  noted directly in the log entry. Source: `docs/test-plan.md` lines
  1528–1540.

All of the corresponding checklist items (FORM-33b, FORM-34b, FORM-39b,
FORM-39c, SET-16c, SET-16d, SET-24b, SET-16e, REG-15) are still unchecked
(`- [ ]`) in `docs/test-plan.md` as of this writing.

## 4. Proposed improvement (approved by the user, 2026-09-12)

Not yet planned in any phase document — a new proposal raised during a
cross-repo audit and approved for the backlog. Still needs its own design
pass before implementation.

- **Yearly recap ("wrapped").** Extends the existing `StatsScreen`/
  `LibraryStatisticsCalculator` (Phase 3) with a per-year breakdown —
  games completed, hours tracked, average rating — reusing the same data
  and rendering approach (no new charting dependency, same hand-rolled
  Compose bars) rather than introducing a separate feature.
