# CLAUDE.md

Guide for Claude agents working on this repository. Also read `docs/spec.md` (functional specification)
and `docs/test-plan.md` (manual test plan: update it with a new section for every phase/feature, and with
a new entry in "Known regressions" for every real bug found during on-device verification).

This file keeps only what every task needs: layout, commands, rules that still bind, status. Everything
else lives where it can be found when needed:

- `docs/phase-history.md` — phase-by-phase build log, device reports, root causes, and the passages this
  file used to carry (package tree with per-file notes, Phase 2–9 and spec-v2 sections, release process).
- `docs/implementation-decisions.md` — non-obvious technical choices and their reasons.
- `docs/drive-setup.md` — Google Drive backup design notes and the external OAuth/signing setup.
- `docs/reviews-backlog-import-export-spec-v2.md` — authoritative review/backlog import-export behavior.
- `REVIEW.md` — what every review checks.

## What this project is

A native, single-user, offline-first Android app for managing video game reviews (a personal workflow for
r/patientgamer). Kotlin + Jetpack Compose + Material 3, Room, Hilt, ViewModel/StateFlow with
unidirectional data flow.

## Status

Phases 1–8 are done (CRUD library, export, statistics, Drive backup, IT/EN + theme, trackable backlog +
TheGamesDB, rebranding + drawer, Markdown/backlog import-export, HowLongToBeat, grid views). Phase 9
(in-app updates + Info screen, `docs/plan-phase-9-updates.md`) is released as 1.1.0; its device checks
(`docs/test-plan.md` §7.5) run against the next release. DOCX export is **decided against** (below).

Do not implement features not present in this file, `docs/spec.md` or the import-export spec v2 unless
explicitly requested in a new session.

## Product decisions already made (do not ask again)

- Rating scale **0–10 with one decimal**, not stars. Review status enum `IN_CORSO`/`COMPLETATO`/`ABBANDONATO`.
- A single review per game (no replay in the MVP).
- Cover image: photo picker + copy into internal storage, no runtime permission.
- Platform, Genre, custom Tags: **many-to-many** via lookup tables + bridge tables (consistent autocomplete).
- Pros/Cons: child table `review_pro_con` with `tipo` (PRO/CONTRO) and `posizione`, not concatenated strings.
- JSON/CSV export: **always the entire library**, ignoring UI filters. Batch PDF: **one multi-page file**.
- `releaseYear`/`developer`/HowLongToBeat hours live on `BacklogItem` (reviews got `developer`/`publisher`/
  `releaseYear`/`metadataSource`/`externalId`/`linkedBacklogItemId` only for round-trip, spec v2).
- Backup/restore covers **reviews and the whole backlog**; restore is a full replace (reviews first, then
  backlog), ids and timestamps preserved. Only the latest Drive backup is kept. Backlog and ZIP imports are
  additive.
- Only the Drive authorization uses OAuth; no application-level accounts.

## Package layout

```
com.marcogn.thepatientgamerhelper
├── data/    local (Room entities, DAOs, Converters, Migrations) · repository (impls) · export (SAF I/O,
│            PdfReviewRenderer, ExportLabelsProvider, zip import/export) · drive · backup (BackupManager,
│            BackupWorker) · settings · thegamesdb · howlongtobeat · update (Phase 9) · image · debug
├── domain/  model · filter · stats · export (pure formatters, ExportLabels) · backup · update ·
│            howlongtobeat · repository (interfaces) — pure Kotlin, no `android.*` import, JVM-tested
├── di/      Hilt modules
└── ui/      theme · navigation (drawer + type-safe routes, Home is the start) · home · library · detail ·
             form · backlog · stats · settings · update (UpdateDialogHost) · about (Info) · common
```

The full tree with per-file notes is in `docs/phase-history.md`. Resources: `values/strings.xml` is Italian
(default), `values-en/strings.xml` the English translation, `xml/locales_config.xml` the supported languages.

Guiding rule: **Room is the single source of truth**, exposed via `Flow`; ViewModels `combine()` it with
local UI state into one `StateFlow` consumed by Compose (events up via lambdas, state down). Filter/sort,
statistics, formatting and update logic are pure functions in `domain/` so they run as plain JVM tests.

## Rules that still bind, by area

**Database.** Room migrations are additive and numbered (`data/local/Migrations.kt`); never
`fallbackToDestructiveMigration()` (real data). Never `@Insert(onConflict = REPLACE)` as the edit path of a row
others reference with `ON DELETE CASCADE`/`SET NULL`: SQLite deletes the row first and fires those actions
(#63). `upsertReview`/`upsertItem` insert with IGNORE and `@Update` on conflict.

**Export / import.** Files are written/read through SAF only (`CreateDocument`/`OpenDocument`). PDF only via
native `android.graphics.pdf.PdfDocument` (no PDFBox/iText; PDF pagination is device-verified, Robolectric
can't run `PdfDocument`). Human-readable export labels (CSV header and status values, PDF captions,
suggested file names) follow the **app language**: `domain/export` stays pure by taking an `ExportLabels`
value, built in `data/export` from string resources (`ExportLabelsProvider`/`toExportLabels()`); defaults
are Italian. The file **formats** are language-independent and must not change: front-matter keys and
`## Pros`/`## Cons`/`## Review` headings of the Markdown, JSON field names, backup and backlog-archive
structure — so a file exported in either language imports in the other. Markdown/ZIP/backlog import
behavior: the spec v2.

**Backup (Drive).** Hand-written `DriveApiClient` over `HttpURLConnection`; auth in two steps (Credential
Manager sign-in, `AuthorizationClient` for `drive.appdata`); `BackupWorker` (WorkManager, daily) needs the
manifest to remove `androidx.startup.InitializationProvider`. `domain/backup` is deliberately separate from
`domain/export`. External setup (OAuth client IDs, SHA-1 of the signing keystore, test users, persistent
release keystore): `docs/drive-setup.md`.

**Networking.** All HTTP clients are hand-rolled `HttpURLConnection`, no new HTTP dependency. Third-party
sites with anti-bot filtering (TheGamesDB, HowLongToBeat) get a desktop-browser User-Agent; the GitHub update
client sends the app name instead, as GitHub's API asks. TheGamesDB needs the user's API key (Settings).
HowLongToBeat has no public API: the client is fragile by nature (logs under `HowLongToBeatClient`). Network
failures become user-visible messages or silent fallbacks, never crashes.

**Backlog.** History and automatic dates are generated only by `BacklogRepositoryImpl` (`updateStatus()` is
the single point; `saveItem()` doesn't touch status). Completing an item without a review offers
`Destination.Form(backlogItemId)`, which duplicates the cover and links the review on save. The backlog grid
has no drag-to-reorder.

**Updates / Info (Phase 9).** `UpdateManager.state` (`Idle`, `Checking`, `Available`, `Downloading`,
`ReadyToInstall`, `Installing`, `Failed`) is the single source of truth; `ui/update/UpdateDialogHost` maps it
to dialogs. `install()` returns `InstallStart.PermissionRequired` the first time: launch its intent and call
`install()` again only if `ApkInstaller.canInstall()`. A check never sets `Failed`. Startup check is
release-builds-only, once per process, per `UpdatePreferences.frequency` (default Weekly). Install is always
with the system confirmation. Info is `Destination.About`, drawer-only, same back pattern as Settings.

**UI and navigation.** `MainActivity` stays an `AppCompatActivity` (required for
`setApplicationLocales()`; the theme descends from `Theme.AppCompat`). Screens with custom back logic have an
explicit `BackHandler` (the system back gesture bypasses `onBack`); drawer-only screens (Settings, Info) leave
with `popUpTo(Home){saveState}`/`launchSingleTop`/`restoreState` so ViewModel state survives. Navigation taps
are gated on the entry being `RESUMED`. Theme (`ThemeMode`) is persisted with Preferences DataStore; view
mode, backup, update and API-key preferences use `SharedPreferences`.

**Strings.** No hardcoded user-visible text: Compose uses `stringResource()`, ViewModels
`@ApplicationContext Context.getString()`, and every key exists in **both** `values/` and `values-en/`
(keep the two lists aligned 1:1). Parser error messages in `domain/export` are still fixed Italian.

**Statistics.** Platform/genre distributions carry no percentage (many-to-many, shares wouldn't sum to 100);
only the status breakdown does. Charts are plain Compose, no charting dependency.

## DOCX export — decided against

Explicitly removed from the roadmap: no lightweight, mature DOCX writer exists for Android (Apache POI needs
`java.awt`; the Kotlin wrappers sit on docx4j). Markdown + JSON/CSV cover the use cases. Do not reconsider
without an explicit request and a concrete reason.

## Known gotchas

Durable lessons discovered the hard way (full context in `docs/phase-history.md`):

- **The system back gesture/button bypasses a screen's custom `onBack`
  lambda by default** — Compose Navigation's own `OnBackPressedCallback`
  does a bare `popBackStack()` unless you add an explicit `BackHandler`.
  Any screen with custom back-button logic (save-as-draft, preserve
  ViewModel state across a drawer round-trip, etc.) needs one. Hit twice
  independently (`SettingsScreen` in Phase 7, `ReviewFormScreen` in
  Phase 8) before the pattern was recognized.
- `FlowRow`/`FlowColumn` (Compose Foundation) require an explicit
  `@OptIn(ExperimentalLayoutApi::class)` on this BOM — treated as a
  **build error**, not a warning.
- `Json.encodeToString(value)` **without**
  `import kotlinx.serialization.encodeToString` resolves to the wrong
  two-argument overload and fails with a misleading type error. Always
  import it explicitly for the short form.
- `kotlinx.serialization`'s default value on a field only covers a
  **missing** key, not an explicit JSON `null` — an API returning
  `"field": null` still crashes decoding unless the type is nullable
  (`List<T>?`) and/or `coerceInputValues = true` is set. Bit both
  `TheGamesDbApiClient` (`genres`/`developers`) in production.
- `HttpURLConnection`'s default `followRedirects` does not reliably
  follow redirects on POST requests, with real gaps around HTTP 308
  (which, unlike 301/302, must preserve method and body per RFC 7538) —
  follow redirects manually for any POST-based client talking to a
  server you don't control.
- A reverse-engineered scraping regex should require the *specific*
  marker that makes a match unambiguous (e.g. `method: "POST"` in the
  same `fetch()` options block), not just a loose URL pattern — a looser
  regex silently matched the wrong `fetch()` call in the HowLongToBeat
  bundle and produced a plausible-but-wrong endpoint.
- Sites with anti-bot/anti-scraping measures can reject a non-browser
  `User-Agent` with a misleading, unrelated-looking error (TheGamesDB
  returned "invalid API key" for a valid key, solely because of the
  User-Agent) — use a realistic desktop browser User-Agent for any
  hand-written HTTP client hitting a third-party site.
- Android Lint's `RemoveWorkManagerInitializer` fails the build if
  `Application` implements `androidx.work.Configuration.Provider` without
  explicitly removing `androidx.startup.InitializationProvider` from the
  manifest (`tools:node="remove"`, requires `xmlns:tools`).
- `PdfDocument` under Robolectric throws `IllegalStateException` in its
  page lifecycle — a Robolectric shadow limitation, not an app bug; PDF
  pagination changes must be verified by hand on a device/emulator.

## Build/test commands

```bash
./gradlew assembleDebug       # debug APK build
./gradlew testDebugUnitTest   # JVM unit tests (domain + repository logic, Robolectric)
./gradlew connectedDebugAndroidTest  # instrumented tests (device/emulator)
./gradlew lint                # Android Lint
./gradlew testDebugUnitTest createDebugUnitTestCoverageReport -Pcoverage  # JaCoCo report
```

CI (`.github/workflows/`, callers of the Android kit's reusable workflows, `docs/ci.md`) verifies real builds;
in a cloud session the SessionStart hook installs the Android SDK, and if `./gradlew` can't run (no network
to `dl.google.com`/plugin repositories) rely on CI. Pure JVM tests cover `domain/*`; Room and repositories run
under Robolectric (`@Config(sdk = [26])` on every Robolectric class). Anything needing real network, Play
Services or native PDF rendering (Drive, TheGamesDB, HowLongToBeat, the update flow, `PdfDocument`) is
verified by hand: `docs/test-plan.md`.

Claude Code tooling lives in `.claude/` (kit skills `verify`, `new-plan`, `next-phase`, `close-phase`,
`steward`; the `architecture-reviewer` agent; the SDK hook — `docs/claude.md`). Bugs and ideas found outside
the current task become GitHub issues (bug/task templates), not notes in this file.

## Changelog

`CHANGELOG.md` is the release-notes source of truth: **every user-visible change gets an entry under
`## [Unreleased]` when it is made.** Entry convention: a top-level bullet with a bold one-line summary,
`- **Summary.** detail`, nested `  - ` bullets for detail — `release.yml` publishes exactly those bold
lead-ins (or a bullet's first sentence) as the GitHub Release body. Cutting a release is a deliberate,
human-directed action: run the `Release` workflow (manual `workflow_dispatch`) with the new `x.y.z`; it
validates, renames `[Unreleased]`, bumps `versionCode`/`versionName`, builds, signs, publishes and only then
commits the bump to `main`. Details: `docs/ci.md` and the "Changelog and release process" copy in
`docs/phase-history.md`.

## Code conventions

- No mock data in the final UI: all screens read from Room via
  repositories. The demo data seed only exists in `data/debug/DebugSeeder.kt`
  and is only active if `BuildConfig.SEED_DEBUG_DATA == true` (`debug`
  build only).
- Dates: native `java.time.LocalDate` / `java.time.Instant` (available without
  desugaring from API 26, which is already our `minSdk`).
- Review/lookup entity IDs: `String` (UUID) for reviews; the
  lookup tables (Platform/Genre/Tag) use auto-generated `Long` with a
  `UNIQUE` constraint on the normalized name (trim + lowercase for comparison).
- Do not introduce new dependencies without an explicit request or a
  genuine need: if related needs come up, flag them instead of
  implementing them. No dependency has been added purely for convenience
  across any phase — charting, Drive/TheGamesDB/HowLongToBeat HTTP
  clients, PDF rendering, and backlog drag-to-reorder are all hand-rolled
  on top of what was already a dependency. Every dependency actually
  added was explicitly requested (Credential Manager, AuthorizationClient,
  WorkManager, `googleid`, `androidx.hilt:hilt-work`,
  `androidx.datastore:datastore-preferences`, `androidx.appcompat`) or a
  direct, unavoidable consequence of one of those.
- PDF export: only native `android.graphics.pdf.PdfDocument`. No
  Apache PDFBox nor iText7 (iText7 is AGPL, explicitly excluded).
- No hardcoded strings in the screens: every visible text in `ui/`
  goes through `stringResource()` (Compose) or `context.getString()` (ViewModel,
  via `@ApplicationContext Context` injected with Hilt), with a corresponding
  entry in `values/strings.xml` **and** `values-en/strings.xml`. The
  two key lists must be kept aligned: if you add a string in
  one language, add it right away to the other too instead of leaving a
  silent fallback to Italian.

## What NOT to do until explicitly requested

DOCX export (permanently out of scope, see above). User authentication/multi-account: out of scope.
