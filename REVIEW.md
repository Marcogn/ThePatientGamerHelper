# REVIEW.md

What a review of a ThePatientGamerHelper change must look at. Rules come from
`CLAUDE.md`, `docs/spec.md`, `docs/reviews-backlog-import-export-spec-v2.md`
and `docs/implementation-decisions.md`; each was checked against the code when
this file was written.

## What Important means here

A bug, lost or altered user data (reviews, backlog, covers, Drive backups), a
broken migration, a release that can't install over the previous one, or a
break of a rule below. Everything else is a Nit.

## Always check

- `domain/` stays pure Kotlin: no `android.*` import. Human-readable export
  labels (CSV header, PDF captions, file names) reach `domain/export` as an
  `ExportLabels` value built from string resources in `data/export`; the file
  formats (front-matter keys, JSON field names, backup structure) never change
  with the language.
- Room migrations are additive and numbered (`data/local/Migrations.kt`);
  never `fallbackToDestructiveMigration()`.
- No `@Insert(onConflict = REPLACE)` as the edit path of a row that other
  rows reference with `ON DELETE CASCADE` or `SET NULL` (reviews → pros/cons,
  cross-refs, `backlog_items.reviewId`; backlog items → comments, history,
  cross-refs): SQLite deletes the row first and fires those actions. Use
  `@Update`, or rewrite every dependent row in the same transaction.
  `ReviewDao.upsertReview`/`BacklogDao.upsertItem` insert with IGNORE and
  `@Update` on conflict (#63).
- Backup/restore covers reviews and the whole backlog; restore is a full
  replace (reviews before backlog), ids and timestamps preserved. Backlog and
  ZIP imports are additive; JSON/CSV export is always the whole library.
- File I/O through SAF only; PDF only via `android.graphics.pdf.PdfDocument`
  (no PDFBox/iText).
- HTTP clients are hand-rolled `HttpURLConnection` with a desktop browser
  User-Agent for third-party sites (TheGamesDB, HowLongToBeat); the GitHub
  update client sends the app's name instead, as GitHub's API requires;
  network failures become messages, never crashes.
- No hardcoded user-visible strings; every new key in both
  `values/strings.xml` (Italian) and `values-en/strings.xml`.
- `MainActivity` stays an `AppCompatActivity`; screens with custom back logic
  have a `BackHandler`.
- Robolectric test classes carry `@Config(sdk = [26])`.
- No new dependency without an explicit request; no DOCX export.
- A user-visible change has a `CHANGELOG.md` `[Unreleased]` entry
  (`- **Summary.** detail`).

## Verification bar

Findings name `file:line` and a concrete path from a real caller or input to
the failure.

## Do not report

Style already consistent with the surrounding code, formatting, generated
Room code, `docs/phase-history.md` text, missing tests for pure UI layout,
HowLongToBeat endpoint fragility (known and documented).

## Shape

Important first, then Nits; one line each with `file:line`, the rule or bug,
and the fix in a sentence.
