# CLAUDE.md

Guide for Claude agents working on this repository. Also read
`docs/spec.md` for the complete functional specification and
`docs/test-plan.md` for the manual test plan (human/application
interaction) covering every feature and edge case — it must be updated
with a new section for every phase/feature added, and with a new entry in
"Known regressions" for every real bug discovered during manual
on-device verification.

This file keeps only the facts still relevant to writing new code. The
full phase-by-phase build log — device reports, root-cause
investigations, superseded design notes — lives in
`docs/phase-history.md`; `docs/implementation-decisions.md` covers
non-obvious technical choices not already spelled out here or in
`docs/spec.md`.

## What this project is

A native, single-user, offline-first Android app for managing video game
reviews (a personal workflow for r/patientgamer). Kotlin + Jetpack Compose +
Material 3, Room, Hilt, ViewModel/StateFlow with unidirectional data flow.

## Progress status by phase

- **Phase 1 — Local MVP**: ✅ CRUD, library with search/filters/sorting,
  detail view, create/edit form, image cover.
- **Phase 2 — Export**: ✅ JSON/CSV for the entire library, Markdown
  compatible with Reddit for a single review, native PDF for a single
  review and for the library in batch.
- **Phase 3 — Library statistics**: ✅ Statistics screen reachable from
  the library: totals/averages, platform/genre distribution, breakdown
  by status.
- **Phase 4 — Google Drive cloud backup**: ✅ manual + automatic backup
  via WorkManager, restore from a list of backups.
- **Phase 5 — Internationalization, theme, and documentation**: ✅ app
  translated IT/EN with an in-app language selector, light/dark/system
  theme persisted with DataStore, documentation reorganized under `docs/`.
- **Phase 6 — Trackable backlog and metadata fetch (TheGamesDB)**: ✅
  two stages — Stage 1: Backlog section (game lists, items with
  status/comments/automatic history/manual reorder, "write a review"
  trigger on completion). Stage 2: "Search online" (TheGamesDB) in the
  backlog and review forms to prefill cover/platform/genre (+
  year/developer for the backlog).
- **Phase 7 — Rebranding, drawer navigation, TheGamesDB search fix**: ✅
  app renamed to ThePatientGamerHelper everywhere, including the
  `applicationId`/Kotlin package; Home screen + hamburger side drawer
  with the 3 main sections + settings; fixed the "TheGamesDB search
  always failed" bug.
- **Phase 8 — Markdown import, backlog export/import, HowLongToBeat, grid
  views**: ✅ review import from Markdown; export/import of the entire
  backlog with its lists, always additive; estimated HowLongToBeat times
  in the backlog and in statistics (inherently fragile — HowLongToBeat
  has no public API); list/grid view for library and backlog. **Later
  revisited** against `docs/reviews-backlog-import-export-spec-v2.md` —
  see that section below for the current, authoritative behavior.
- **DOCX export**: **decided not to implement it**, not merely postponed
  — see "DOCX export" below.

Do not implement features not yet present in this file, `docs/spec.md`,
or `docs/reviews-backlog-import-export-spec-v2.md` unless explicitly
requested in a new session.

## Product decisions already made (do not ask again)

- Rating scale: **0–10 with one decimal** (e.g. 7.3), not stars.
- Review status: enum `IN_CORSO` / `COMPLETATO` / `ABBANDONATO`.
- A single review per game (no replay/rigioco in the MVP).
- Cover image: photo picker + copy into the app's internal storage, no
  runtime permission required (`ActivityResultContracts.PickVisualMedia`).
- Platform and Genre: **many-to-many relationship** with the review,
  modeled as lookup tables with bridge tables, to guarantee consistent
  autocomplete.
- Custom tags: same pattern as Platform/Genre.
- Pros/Cons: relational child table (`review_pro_con`) with a `tipo`
  field (PRO/CONTRO) and `posizione` for ordering, not concatenated strings.
- JSON/CSV export: **always the entire library**, ignoring active UI
  filters (a backup must be complete).
- Batch PDF export: **a single multi-page PDF file** with all reviews
  (not a zip of separate PDFs) — consistent with SAF
  `ACTION_CREATE_DOCUMENT`, which makes the user choose a single
  destination.

## Package/architecture

```
com.marcogn.thepatientgamerhelper
├── data/
│   ├── local/
│   │   ├── entity/      # Room entities (Review, Platform, Genre, Tag, cross-ref, ProCon,
│   │   │                # Backlog* from Phase 6)
│   │   ├── dao/          # Room DAOs, exposed as Flow (ReviewDao, LookupDaos, BacklogDao)
│   │   ├── Converters.kt # TypeConverter for LocalDate/Instant/enum
│   │   └── Migrations.kt # MIGRATION_1_2 (Phase 6: backlog tables), MIGRATION_2_3 (Phase 8:
│   │                      # HowLongToBeat estimate columns on backlog_items), MIGRATION_3_4
│   │                      # (backlog_lists.systemKind), MIGRATION_4_5 (import-export spec v2:
│   │                      # developer/publisher/releaseYear/metadataSource/externalId/
│   │                      # linkedBacklogItemId on reviews) — all additive
│   ├── repository/       # Repository implementations (transactional upsert)
│   ├── export/            # Android I/O for export/import: ExportFileWriter (SAF, write),
│   │                      # ImportFileReader (SAF, read — review Markdown import, backlog
│   │                      # import, review zip import), PdfReviewRenderer (PdfDocument, checks
│   │                      # PdfTemplateProvider before rendering), PdfTemplateProvider/
│   │                      # NoOpPdfTemplateProvider (PDF template seam), ReviewExporter,
│   │                      # ReviewZipExporter/ReviewZipImporter + ReviewZipArchiveBuilder/Reader
│   │                      # (multi-review zip, one front-matter .md per review + covers),
│   │                      # BacklogExporter/BacklogImporter + BacklogExportArchiveBuilder/Reader
│   │                      # (zip data+covers) — all concrete classes injected via Hilt, like
│   │                      # ImageStorage, not an interface/impl abstraction like the
│   │                      # repositories (PdfTemplateProvider is the one deliberate exception)
│   ├── drive/             # Drive REST v3 client (DriveApiClient, HttpURLConnection)
│   │                      # + auth (DriveAuthManager: Credential Manager + AuthorizationClient)
│   ├── backup/            # Backup/restore orchestration: BackupManager, zip archive
│   │                      # (BackupArchiveBuilder/Reader), BackupWorker (WorkManager +
│   │                      # Hilt), BackupScheduler, BackupPreferences (SharedPreferences)
│   ├── settings/          # ThemePreferences (Preferences DataStore), ViewModePreferences
│   │                      # (SharedPreferences, list/grid view for library and backlog)
│   ├── thegamesdb/        # TheGamesDbApiClient (HttpURLConnection, same pattern as
│   │                      # DriveApiClient), TheGamesDbPreferences (SharedPreferences, API key
│   │                      # entered at runtime by the user), GameMetadataSearchCoordinator
│   │                      # (logic shared "search online" between the review form and the
│   │                      # backlog form, also exposes searchHowLongToBeat(), backlog-only)
│   ├── howlongtobeat/     # HowLongToBeatApiClient — HttpURLConnection client for an
│   │                      # unofficial/undocumented endpoint, reverse-engineered technique,
│   │                      # not the same level of reliability as TheGamesDbApiClient/DriveApiClient
│   └── debug/            # DebugSeeder, active only behind BuildConfig.SEED_DEBUG_DATA
├── domain/
│   ├── model/            # Pure domain models (no Android dependencies), including ThemeMode,
│   │                      # Backlog* and GameMetadataSearchResult, HowLongToBeatEstimate/
│   │                      # ViewMode/ImportedBacklog*, ReviewZipImportResult
│   ├── filter/            # Library and backlog filter/sort logic, pure functions, unit-tested
│   ├── stats/             # Pure aggregations: LibraryStatisticsCalculator,
│   │                      # BacklogStatisticsCalculator (counts by status/list, also
│   │                      # computeBacklogTimeEstimateStatistics)
│   ├── export/            # Pure export/import formatting: JSON (kotlinx.serialization),
│   │                      # CSV (manual writer), ReviewBackupMarkdown.kt (front-matter Markdown
│   │                      # format + parser) — no Android import, unit-testable in plain JVM.
│   │                      # The labels remain fixed in Italian (they do not follow the app
│   │                      # language) — the front matter's structural keys are the one
│   │                      # deliberate exception, matching the fixture's English shape. Also
│   │                      # BacklogExportDto.kt (backlog export/import zip payload, format
│   │                      # separate from domain/backup, +recensioneCollegataId round-trip)
│   ├── backup/            # Pure backup format: BackupPayload/BackupReviewDto,
│   │                      # Review<->DTO mapping, file naming — same pattern as domain/export
│   └── repository/        # Repository interfaces (ReviewRepository, LookupRepository,
│                          # BacklogRepository, importLists(), upsertImported())
├── di/                    # Hilt modules (Database, Repository)
└── ui/
    ├── theme/             # Material 3 (Compose) theme + ThemeViewModel (reads ThemePreferences)
    ├── navigation/        # Navigation Compose, type-safe routes (kotlinx.serialization).
    │                      # ModalNavigationDrawer around the NavHost (hamburger drawer with the
    │                      # 3 sections + settings), Destination.Home as startDestination
    ├── home/              # HomeScreen, "what do you want to do?" screen with the 3 main choices
    │                      # (reviews/backlog/statistics)
    ├── library/           # Library screen (list, search, filters, sorting, export). Top bar
    │                      # without the app name/backlog/statistics/settings icons (now in the
    │                      # drawer). List/grid view toggle. Upload icon triggers the multi-review
    │                      # zip import, export menu has a ZIP option
    ├── detail/            # Review detail screen (+ single review export)
    ├── form/              # Create/edit review form (+ "Search online" and prefilling from a
    │                      # backlog item). Upload icon in the top bar imports a front-matter
    │                      # Markdown file as a "replace form content" action
    ├── backlog/            # BacklogScreen (lists + unified search/filter + lightweight
    │                       # aggregate stats), BacklogListDetailScreen (drag-to-reorder),
    │                       # BacklogItemFormScreen, BacklogItemDetailScreen (status/comments/
    │                       # history/abandonment note, also HowLongToBeat estimate). Backlog
    │                       # export/import in BacklogScreen, list/grid view toggle in
    │                       # BacklogListDetailScreen (grid without drag-to-reorder)
    ├── settings/           # Settings screen: theme/language preferences, manual/automatic
    │                       # backup, restore, TheGamesDB API key
    └── common/            # Shared composables (chip input, rating, date picker, cover
                            # thumbnail, ReviewStatus/BacklogItemStatus display, GameSearchDialog,
                            # GameGridTile/ViewModeToggle, etc.)
```

Resources (`app/src/main/res/`): `values/strings.xml` is Italian (the project's
default language), `values-en/strings.xml` the English translation,
`xml/locales_config.xml` lists the supported languages for integration with the
system settings (API 33+).

Guiding rule: **Room is the single source of truth**, exposed via `Flow`. The
ViewModels combine the data flow with local UI state (search query,
selected filters) using `combine()`, producing a single `StateFlow` of UI
state consumed by the Compose UI (UDF pattern: events flow up via lambda,
state flows down via `StateFlow`).

The filter/sort logic lives in `domain/filter` as pure Kotlin
functions (no Android import), to be unit-testable in plain JVM without
needing the Android SDK or Robolectric.

## Phase 2 — Export

- **JSON/CSV**: always over the entire library, **unfiltered** (a backup must
  be complete regardless of the filters active in the UI). Entry
  point: menu in the library's top bar.
- **Markdown**: single review, Reddit-compatible syntax — title as
  `#`, metadata as a bullet list (not a trailing-space hard break, which disappears
  easily in clipboard/editor before reaching Reddit), Pros/Cons/body
  sections only if non-empty. Entry point: menu in the detail view's top bar.
- **PDF**: both single review and entire library into a single
  multi-page file (one file for SAF `ACTION_CREATE_DOCUMENT`, not a zip).
  Native `android.graphics.pdf.PdfDocument` — **not PDFBox nor iText** (iText7
  is AGPL, explicitly excluded from the spec). Pagination via
  `StaticLayout` + `Canvas.translate`/`clipRect` to slice a single
  layout across multiple pages; each review in the batch always starts on a
  new page.
- File saving: **always** the Storage Access Framework
  (`ActivityResultContracts.CreateDocument`), never direct writes to
  external storage — consistent with scoped storage.
- `domain/export` is pure Kotlin (formatting), `data/export` is where the
  Android I/O lives (SAF, `PdfDocument`). `ReviewExporter` is a concrete class
  with `@Inject constructor`, not an interface with a Hilt binding like the
  repositories — it is not a swappable domain abstraction, it is an I/O
  utility (same pattern as `ImageStorage`).
- **`PdfDocument` cannot be meaningfully tested via Robolectric**: PDF
  serialization depends on native code that Robolectric does not provide
  (unlike SQLite/Room, which is well shadowed). `PdfReviewRendererTest`
  only tests `buildReviewText()` (construction of the `SpannableStringBuilder`,
  no real rendering) — if you touch the pagination/page logic,
  verify it by hand on a device/emulator.

## Phase 3 — Library statistics

- Metrics computed: total number of reviews, average rating, total hours
  tracked (sum of `oreGioco`, `null` treated as 0), distribution by
  platform, distribution by genre, percentage breakdown by
  `stato` (`IN_CORSO`/`COMPLETATO`/`ABBANDONATO`).
- `domain/stats/LibraryStatisticsCalculator.kt`: pure function
  `computeLibraryStatistics(List<Review>): LibraryStatistics`, no Android
  import, unit-tested in plain JVM (`domain/model/LibraryStatistics.kt` for
  the models) — same pattern as `domain/filter`.
- The platform/genre distributions **do not** carry a percentage: they are
  many-to-many fields (a review can have multiple platforms/genres), so
  the shares would not add up to 100% and a percentage would be misleading. Only
  the breakdown by `stato` (a single-valued field) exposes a percentage.
  See `docs/implementation-decisions.md`.
- UI: `ui/stats/StatsScreen.kt` (+ `StatsViewModel`, `StatsUiState`),
  reachable from an icon in the library's top bar. Distributions are
  horizontal bars built with native Compose (`Box` +
  `fillMaxWidth(fraction = ...)`), breakdown by status is a segmented
  stacked bar + legend — **no charting dependency introduced** (no Vico):
  with at most a handful of platforms/genres for a single-user library,
  the complexity wasn't justified. Re-evaluate Vico before writing more
  hand-rolled rendering code if the distributions become richer.
- Navigation route: `Destination.Stats` in `ui/navigation/Destinations.kt`.

## Phase 4 — Google Drive cloud backup

- **Authentication/authorization**: two distinct steps. (1) **Credential
  Manager** (`androidx.credentials`) for "Sign in with Google"
  (`GetGoogleIdOption`), with a fallback to `GetSignInWithGoogleOption`
  when `NoCredentialException` is thrown (bottom-sheet flow can't help
  when there are no accounts on the device). (2) **AuthorizationClient**
  (`com.google.android.gms.auth.api.identity`) to request the
  `drive.appdata` scope on that account. Both live in
  `data/drive/DriveAuthManager.kt`. Note: `AuthorizationClient` is
  distributed in the same Maven artifact as the deprecated
  `GoogleSignInClient` (`com.google.android.gms:play-services-auth`) —
  the dependency is unavoidable, but the deprecated class is never
  imported here.
- **Silent background authorization**: `DriveAuthManager.authorize()`
  called with only the `applicationContext` returns a fresh token with no
  UI if consent was already granted — used by `BackupWorker`. If consent
  is invalid, the worker fails silently (`Result.failure()`); the next
  manual backup re-establishes consent.
- **Drive REST API v3**: hand-written client in `data/drive/DriveApiClient.kt`
  using `java.net.HttpURLConnection` — **no dependency on
  `google-api-client`/`google-api-services-drive`** (heavy, brings Guava,
  for just three endpoints).
- **Backup format**: a single ZIP archive (`java.util.zip`) with
  `data.json` (`domain/backup/BackupPayload.kt`) and covers under
  `images/<file-name>`. `domain/backup` is deliberately **separate** from
  `domain/export`: the export format has Italian labels and an absolute
  cover path (not restorable elsewhere); the backup format carries only
  the cover's file name, resolved to a new path at restore time.
- **Restore**: `BackupManager.restoreBackup()` decompresses, deletes all
  local covers, calls `ReviewRepository.replaceAll()` — a single
  transaction that deletes and re-inserts everything, preserving
  `id`/`createdAt`/`updatedAt`. **No merge/conflict handling**: single-user
  app, a restore is a full overwrite.
- **Retention: only the latest backup is kept on Drive.** Each backup is
  a full snapshot, so a single-user app has no use for a growing history
  of them, and the private `appDataFolder` isn't visible/manageable from
  the regular Drive UI (the app is the only way to clean it up).
  `BackupManager.createBackup()` uploads, then calls
  `pruneOldBackups()`, which lists the folder and deletes every file
  except the one just uploaded (best-effort per file via
  `DriveApiClient.deleteBackup()` — `DELETE /drive/v3/files/{id}`; a
  failed deletion doesn't fail the backup that already succeeded, the
  next run just tries again). This applies to both manual and automatic
  (`BackupWorker`) backups, and also cleans up any backlog of old backups
  left over from before this was added — no separate "clean up now"
  action needed, the next backup does it.
- **Automatic backup**: `BackupWorker` (`@HiltWorker`, WorkManager), fixed
  daily cadence (`BackupScheduler`, `NetworkType.CONNECTED`), no UI to
  configure the interval. `ThePatientGamerHelperApplication` implements
  `Configuration.Provider`; the manifest explicitly removes
  `androidx.startup.InitializationProvider` (`tools:node="remove"`) —
  Android Lint's `RemoveWorkManagerInitializer` requires this or the
  build fails.
- **Persisted state**: `BackupPreferences` (`SharedPreferences`) holds the
  automatic-backup toggle and the last backup's outcome.
- **Explicit login, not implicit**: `SettingsUiState.signedInEmail` tracks
  the session's "logged in" state (in-memory only, not persisted — app
  restart requires a new login). A single "Sign in with Google" button
  performs `signIn()` + `authorize()` together; subsequent actions only
  call `authorize()` (silent) via `ensureAccessToken()`.
  `DriveAuthManager.isConfigured()` detects the placeholder OAuth client
  ID and shows `DriveNotConfiguredCard` instead of a generic error.
- **External configuration required**: the OAuth web client ID goes in
  `local.properties` (`DRIVE_OAUTH_WEB_CLIENT_ID`, gitignored, not
  committed) or the `DRIVE_OAUTH_WEB_CLIENT_ID` GitHub Actions secret for
  CI. A companion **Android**-type OAuth client must also exist, with the
  SHA-1 of the *exact keystore* signing the tested build — a mismatch is
  a well-known, silent source of Sign-In failures (see
  `docs/phase-history.md` for the full multi-round debugging story). The
  Google account signing in must be added as a test user (OAuth consent
  screen stays in "Testing" mode), and the Google Drive API must be
  explicitly enabled on the Cloud project, or every Drive call fails with
  `SERVICE_DISABLED` even after a successful sign-in.
- **Release builds are signed with a persistent keystore**, not an
  ephemeral per-run debug one — otherwise the registered SHA-1 goes stale
  on every CI build. Secrets: `RELEASE_KEYSTORE_BASE64`,
  `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
- **Known limitation, not yet addressed**: with the OAuth consent screen
  in "Testing" mode, Google expires the authorization grant after 7 days.
  The interactive login just re-prompts, but the daily `BackupWorker` can
  start failing silently past that window until the user logs in again
  manually. Moving the consent screen to "In production" (optional,
  Google Cloud Console, a `drive.appdata` scope doesn't need full
  verification) would fix this but hasn't been done.
- **Cannot be meaningfully tested via Robolectric**: `HttpURLConnection`
  calls to `googleapis.com`, Credential Manager, and `AuthorizationClient`
  require real network/Play Services. What's unit-tested instead: DTO/JSON
  mapping (`domain/backup/BackupPayloadTest.kt`), the zip archive
  (`data/backup/BackupArchiveTest.kt`, Robolectric) and
  `ReviewRepositoryImpl.replaceAll()` (Robolectric). The auth flow must be
  verified by hand on a device/emulator with Play Services.

Full incident history for this phase (a multi-round "Sign in with Google"
debugging saga, client-ID rotation, persistent-signing setup) is in
`docs/phase-history.md`.

## Phase 5 — Internationalization, theme, and documentation

### IT/EN internationalization

- All screen strings are extracted into string resources:
  `res/values/strings.xml` is Italian (default), `res/values-en/strings.xml`
  the English translation. Keep the two key lists aligned 1:1.
- **`domain/export` stays untouched by localization**: exported file
  labels (Markdown/CSV/JSON/PDF) remain fixed in Italian, hand-written in
  the pure formatters — localizing them would require passing
  `Context`/Android resources into `domain/export`, breaking its nature
  as pure Kotlin testable in the JVM. `ReviewStatus.label()` is the same
  story — the UI instead uses `ReviewStatus.displayName()`
  (`ui/common/ReviewStatusDisplay.kt`, a `@Composable` resolving the
  correct string resource).
- ViewModel-built messages (export/backup outcomes, validation errors)
  can't use `stringResource()` (not `@Composable`): ViewModels inject
  `@ApplicationContext Context` via Hilt and call `context.getString(...)`.
- **In-app language selector**: `AppCompatDelegate.setApplicationLocales()`,
  System/Italian/English in Settings (`ui/settings/AppLanguage.kt`).
  Persistence via `autoStoreLocales`
  (`androidx.appcompat.app.AppLocalesMetadataHolderService` in the
  manifest with `autoStoreLocales = true`) — no custom storage needed.
  `android:localeConfig="@xml/locales_config"` on `<application>`
  integrates with system Settings > App languages (API 33+).
- **`MainActivity` extends `AppCompatActivity`, not `ComponentActivity`** —
  required by the official docs for `setApplicationLocales()` to work with
  Compose at all; with `ComponentActivity` the language change is silently
  ignored (a real bug found during manual verification, see
  `docs/phase-history.md`). As a consequence the theme
  (`res/values/themes.xml`) must descend from `Theme.AppCompat`.
- No manual `recreate()` needed — `setApplicationLocales()` already
  triggers it end-to-end with `AppCompatActivity`.

### Light/dark/system theme

- Three-state preference (`domain/model/ThemeMode.kt`: `SISTEMA`/`CHIARO`/`SCURO`)
  persisted with **Preferences DataStore** (`data/settings/ThemePreferences.kt`)
  — unlike `BackupPreferences`/`TheGamesDbPreferences`/`ViewModePreferences`
  (plain `SharedPreferences`), DataStore was the explicit request here.
- `ui/theme/ThemeViewModel.kt` exposes `themeMode` as a `StateFlow` from
  `ThemePreferences.themeMode` via `stateIn`. Two independent
  `hiltViewModel()` consumers (`MainActivity` and `SettingsScreen`) read
  the same `DataStore` and stay synchronized without a shared scope.

### Documentation reorganization

The functional spec moved to `docs/spec.md`. This file (`CLAUDE.md`),
along with the rest of the documentation, code comments, and commit
history, is English-only — the app's own UI keeps its dual Italian
(default)/English string resources independently of this.

## Phase 6 — Trackable backlog and metadata fetch (TheGamesDB)

Two stages: Stage 1 (backlog) verified before starting Stage 2 (online
search), as requested.

### Stage 1 — Trackable backlog

- **Data model**: `BacklogListEntity`, `BacklogItemEntity` (title, status,
  position, dates, optional `reviewId`, `abandonNote`, `releaseYear`/
  `developer`), `BacklogCommentEntity`, `BacklogHistoryEntryEntity`. The
  item's platform/genre/tag are many-to-many on the **same lookup
  tables** already used by reviews — shared autocomplete pool.
- **Additive migration, not destructive**: `MIGRATION_1_2` only creates
  new tables/indices via raw SQL, never touches
  `reviews`/`platforms`/`genres`/`tags`.
  `fallbackToDestructiveMigration()` is never used — the app is in real
  use, and it would wipe existing reviews.
- **Automatic history**: entirely generated by `BacklogRepositoryImpl` —
  `CREATO` on creation, `CAMBIO_STATO` only on an actual status change,
  `CAMBIO_LISTA` on move, `COMMENTO` per comment,
  `RECENSIONE_COLLEGATA` on linking a review.
- **`dataInizio`/`dataCompletamento` are auto-populated, no manual
  editor**: `updateStatus()` sets them on the relevant transition, never
  overwriting an existing value.
- **`updateStatus()` is distinct from `saveItem()`**: only
  `BacklogItemDetailScreen` calls it, via a dedicated selector — the
  single point that generates history and automatic dates.
- **Reordering**: lists use up/down arrows (few items, no drag needed).
  Items inside a list have real drag-to-reorder
  (`BacklogListDetailScreen.kt`, `Modifier.pointerInput` +
  `detectDragGestures` on a dedicated handle icon, no reorder library).
  Order is written once at `onDragEnd`, not per frame.
- **Unified search/filter**: `BacklogScreen` shows the list of lists by
  default; any active search/filter switches it to a flat cross-list
  results view (`domain/filter/BacklogFilters.kt`/`BacklogFiltering.kt`,
  same pattern as the review library's).
- **"Want to write a review?" trigger**: when `updateStatus()` sets
  `COMPLETATO` on an item with no `reviewId`, the UI offers to navigate
  to `Destination.Form(backlogItemId = itemId)`, which prefills the draft
  from the backlog item and calls `BacklogRepository.linkReview()` on
  save. The cover is duplicated (`ImageStorage.duplicate()`), not shared
  by reference, so deleting one side doesn't affect the other.

### Stage 2 — Automatic cover and metadata fetch (TheGamesDB)

- **The API key is always required** — TheGamesDB requires an `apikey` on
  every request as of their 2026-02-17 policy change; there is no
  anonymous access. The key is entered **at runtime in Settings**
  (`TheGamesDbPreferences`, `SharedPreferences`), never baked into a
  build. With no key set, "Search online" returns an informational
  message instead of calling the API.
- **Hand-written REST client, not Retrofit/Ktor**: same
  `HttpURLConnection` pattern as `DriveApiClient` — four GET endpoints
  don't justify a full HTTP client.
- **In-memory cache of lookups, not persisted**: id→name maps for
  Platforms/Genres/Developers are cached for the process's lifetime to
  avoid extra calls against a public rate limit on the order of a few
  thousand requests/month.
- **Multiple results, no auto-selection**: `GameSearchDialog`
  (`ui/common/`, shared between the review and backlog forms) lists all
  matches with cover/platform/year; the user picks one. The cover is
  downloaded and saved **locally** via `ImageStorage.writeBytes()`.
- **`releaseYear`/`developer` live only on `BacklogItem`, not `Review`**:
  adding them to the mature `Review` schema would have touched every
  export/backup format for two fields that were never part of a review's
  core (rating/pros/cons/text).
- **Silent fallback, never a crash**: `GameMetadataSearchCoordinator`
  turns missing key/no results/network errors into an `Outcome.Message`
  shown in the dialog. Manual entry always remains available.
- **Cannot be meaningfully tested via Robolectric** (real network to
  `api.thegamesdb.net` required). Unit-tested instead:
  `domain/filter/BacklogFilteringTest.kt`,
  `domain/stats/BacklogStatisticsCalculatorTest.kt`.

## Phase 7 — Rebranding, drawer navigation, TheGamesDB search fix

### ThePatientGamerHelper rebranding

- Renamed **everywhere**, including `applicationId`/Kotlin package:
  `com.marcogn.gamereviewer` → `com.marcogn.thepatientgamerhelper`. No
  data migration for existing installations (a different `applicationId`
  is a different app to Android); a Drive OAuth client re-registration is
  needed for the new `applicationId`+SHA-1. See
  `docs/implementation-decisions.md` for what was intentionally left
  unchanged (repo name, export file prefix).
- Renamed files/classes accordingly (`ThePatientGamerHelperNavGraph.kt`,
  `ThePatientGamerHelperApplication.kt`,
  `data/local/ThePatientGamerHelperDatabase.kt` with
  `DATABASE_NAME = "the_patient_gamer_helper.db"`).

### Navigation: Home chooser + hamburger drawer

- `Destination.Home` (`ui/navigation/Destinations.kt`) is now the graph's
  `startDestination`. `ThePatientGamerHelperNavGraph.kt` wraps the entire
  `NavHost` in a `ModalNavigationDrawer`, `drawerState` hoisted at the
  graph level. Drawer entries navigate with
  `popUpTo(Destination.Home) { saveState = true }` +
  `launchSingleTop = true` + `restoreState = true` to avoid backstack
  accumulation. Every screen receives only an `onMenuClick: () -> Unit`
  lambda, never the drawer state itself (UDF).
- `SettingsScreen` is reachable only from the drawer (not one of the 3
  main sections) — its `onBack` must use the same
  `popUpTo`/`restoreState` pattern (not a bare `popBackStack()`) to
  preserve `SettingsViewModel`'s in-memory login state across visits, and
  needs an explicit `BackHandler` since the system back gesture bypasses
  a screen's custom `onBack` by default. See `docs/phase-history.md` for
  the device report that uncovered this — the same "system back skips a
  screen's custom logic" trap recurs in Phase 8's review form fix.

### TheGamesDB search always-failed fix

`GameMetadataSearchCoordinator.search()` used to replace every exception
(network, HTTP, parsing) with the same fixed generic message, discarding
the real detail. It now logs (`Log.w`) and appends the exception's
message to what's shown in the dialog, so a future failure is
diagnosable (e.g. "HTTP 401: ..." for an invalid key). Several additional
defensive fixes (headers, timeouts, filter syntax) were also applied —
see `docs/phase-history.md` for the detail and which parts were verified
vs. best-effort.

## Phase 8 — Markdown import, backlog export/import, HowLongToBeat, grid views

The single-review Markdown import format from this phase was later
**superseded** by the front-matter format in "Reviews/backlog
import-export spec v2" below — that section is the current, authoritative
behavior for review Markdown import/export. What's still current from
this phase:

### Backlog export/import with its lists

- Format **deliberately separate** from `domain/backup` (Phase 4): this
  is a file the user explicitly shares/merges via SAF, not a safety
  restore, so import is **always additive** — new lists, new items, new
  ids, even importing the same file twice (not idempotent, an accepted
  trade-off over ambiguous title-based merging). `reviewId` is discarded
  on import (see the v2 section below for the current best-effort
  round-trip). Covers get a new UUID file name on import, never reusing
  the original.
- Entry point: upload/download icons in `BacklogScreen`'s top bar.
  Export always covers the entire backlog.

### Estimated HowLongToBeat times in the backlog

- **No public API exists** — verified before implementing. Every
  unofficial integration re-derives the current search endpoint from
  HowLongToBeat's frontend JS bundle at runtime, because the path changes
  on every deploy; there is no stable contract to implement against.
  `data/howlongtobeat/HowLongToBeatApiClient.kt` does the same (homepage →
  bundle → endpoint → `init` → search with auth headers, manual redirect
  following for 307/308, a browser User-Agent). **This is inherently more
  fragile than `TheGamesDbApiClient`/`DriveApiClient`** and can break
  again without notice if HowLongToBeat changes its frontend — check logs
  under tag `HowLongToBeatClient` first if estimates go missing again,
  and see `docs/phase-history.md` for the full multi-round debugging story
  (redirect handling, auth headers, regex must require `method: "POST"`
  on the matched `fetch()`, User-Agent).
- **Always fails silently**: every error becomes `null` in
  `GameMetadataSearchCoordinator.searchHowLongToBeat()` and a
  human-readable message in `BacklogItemFormUiState.hltbMessage` — never
  a propagated exception, never blocking the existing "search online"
  flow.
- `hltbMainStoryHours`/`hltbMainExtraHours`/`hltbCompletionistHours` live
  **only on `BacklogItem`**, same precedent as `releaseYear`/`developer`.
  Only `BacklogItemFormViewModel` calls `searchHowLongToBeat()`, using the
  exact title of the chosen TheGamesDB result.

### Statistics: estimated backlog time

`domain/stats/BacklogStatisticsCalculator.computeBacklogTimeEstimateStatistics()`
sums estimated hours across all backlog items with **at least one**
HowLongToBeat field set, regardless of status, plus `itemsWithEstimate`.
Shown in `StatsScreen` only if at least one item has an estimate.

### List/grid views for reviews and backlog

- `domain/model/ViewMode.kt` (`LIST`/`GRID`) + `data/settings/ViewModePreferences.kt`
  (`SharedPreferences`, two flags: library view, backlog view).
- `ui/common/GameGridTile.kt` — covers keep their real aspect ratio
  (`ContentScale.FillWidth`, no forced ratio) inside a
  `LazyVerticalStaggeredGrid` (`StaggeredGridCells.Adaptive`,
  `@OptIn(ExperimentalFoundationApi::class)`) so tiles of different
  heights sit without wasted space; the "no cover" placeholder stays a
  fixed 2:3 ratio. `ui/common/ViewModeToggle.kt` is shared between
  `LibraryScreen` and `BacklogListDetailScreen`.
- **The backlog grid does not support drag-to-reorder** — only the list
  view does; extending the drag gesture to 2D wasn't worth it for a
  cosmetic benefit.
- `BacklogScreen` (the list-of-lists/cross-list search view) does **not**
  get the toggle — the grid applies where games are browsed, not where
  lists themselves are browsed.

## Reviews/backlog import-export spec v2

`docs/reviews-backlog-import-export-spec-v2.md` (with fixtures under
`docs/examples/`) is the **authoritative** behavior document for
single-review Markdown export/import, multi-review ZIP export/import, and
the backlog's `reviewId` round-trip — superseding the original Phase 8
implementation where they diverge. Current behavior:

- **Single-review Markdown** is a YAML front-matter block (id, title,
  platforms/genres as arrays, tags, score, status, dates, hoursPlayed,
  coverImage, developer/publisher/releaseYear/metadataSource/externalId/
  linkedBacklogItemId, createdAt/updatedAt) followed by the same
  Pros/Cons/free-text body as before
  (`domain/export/ReviewBackupMarkdown.kt`, replacing the deleted
  `ReviewMarkdownFormatter`/`ReviewMarkdownParser` pair). The six new
  fields on `Review`/`ReviewEntity` (`MIGRATION_4_5`) exist purely for
  round-trip fidelity — the create/edit form never edits them, `save()`
  always preserves whatever a review already had.
- **Import is a form-level "replace content" action**
  (`ReviewFormViewModel.importMarkdown()`, upload icon in the form's top
  bar), not a library-level "always create new" action. The file's `id`
  is parsed but never applied — the review being edited keeps its own
  identity.
- **Multi-review ZIP export/import** (`data/export/ReviewZipArchive.kt`/
  `ReviewZipExporter.kt`/`ReviewZipImporter.kt`, upload/download icons in
  the library's top bar) is content-atomic: any malformed `.md` blocks
  the whole batch with every failing file name + reason reported; images
  are always best-effort. `ReviewRepository.upsertImported()` does the
  upsert-by-id-from-front-matter on success — additive, preserves
  id/createdAt/updatedAt, distinct from both `save()` and the
  Drive-restore-only `replaceAll()`.
- **Backlog export/import kept its existing Italian-labeled, array-based
  schema** — not rewritten to match the fixture's shape, since the
  fixture's JSON *structure* (lists→items→comments/history) already
  matches. The one addition: a best-effort `reviewId` round-trip
  (`recensioneCollegataId`, default `null` so older files still decode),
  relinked on import only if a review with that id already exists on the
  importing device.
- **`PdfTemplateProvider` seam** added to `PdfReviewRenderer` (`PdfModule`
  Hilt `@Binds`, interface + `NoOpPdfTemplateProvider`) — no real
  template exists yet, `currentTemplate()` always returns `null`, every
  render still falls back to the existing plain layout.
- `domain/backup/BackupPayload.kt` (`BackupReviewDto`) also carries the
  same six new Review fields (default `null`, old backups still decode).
- **Not touched**: JSON/CSV whole-library export.

Full reasoning for the choices above (including three conflicts resolved
by asking rather than guessing) is in `docs/implementation-decisions.md`,
"Reviews/backlog import-export spec v2".

## DOCX export — why it was not implemented

Explicitly removed from the roadmap (not "postponed" or "optional"): no
lightweight, mature DOCX writer exists for Android — Apache POI depends
on `java.awt` (unavailable on Android) and bloats the APK, and available
Kotlin wrappers (e.g. DocxKtm) are still built on docx4j with the same
heavy dependencies. Hand-writing OOXML XML via a ZIP remains a
theoretical future option, but with Markdown (readable sharing) and
JSON/CSV (portable raw data) already covered, there's no use case that
justifies it. Do not reconsider without an explicit request and a
concrete reason.

## Known gotchas

Durable lessons worth checking before writing similar code, discovered
the hard way (full context in `docs/phase-history.md`):

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
./gradlew testDebugUnitTest   # JVM unit tests (domain + repository logic)
./gradlew connectedDebugAndroidTest  # instrumented tests (requires device/emulator)
./gradlew lint                # Android Lint
```

Real builds are verified by the GitHub Actions workflows in
`.github/workflows/` (`android-ci.yml` on every push/PR;
`build-apk.yml`/the release workflow for signed release APKs) — an
isolated sandbox may have no network access to `dl.google.com`
(required for the Android Gradle Plugin and AndroidX/Compose/Room/Hilt
artifacts), in which case `./gradlew` cannot run locally; check with
`curl` before assuming otherwise, and rely on CI for real build
verification in that case.

Testing approach: pure JVM unit tests cover `domain/filter`, `domain/model`,
`domain/export`, `domain/stats`; Room DAOs are tested via Robolectric as
JVM unit tests (no emulator needed). Anything requiring real network,
Play Services, or native PDF rendering (Drive, TheGamesDB, HowLongToBeat,
`PdfDocument` pagination) can't be meaningfully exercised by
Robolectric and needs manual on-device verification instead — see
`docs/test-plan.md`.

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

DOCX export: **permanently out of scope** (a decision made, not merely
postponed — see dedicated section above), do not reconsider without an
explicit request. User authentication/multi-account: out of scope, Phase 4
uses OAuth only for Drive authorization, it does not introduce an
application-level account concept.
