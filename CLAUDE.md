# CLAUDE.md

Guide for Claude agents working on this repository. Also read
`docs/spec.md` for the complete functional specification and
`docs/test-plan.md` for the manual test plan (human/application
interaction) covering every feature and edge case — it must be
updated with a new section for every phase/feature added, and with
a new entry in "Known regressions" for every real bug discovered during
manual on-device verification.

## What this project is

A native, single-user, offline-first Android app for managing video game
reviews (a personal workflow for r/patientgamer). Kotlin + Jetpack Compose +
Material 3, Room, Hilt, ViewModel/StateFlow with unidirectional data flow.

## Progress status by phase

- **Phase 1 — Local MVP**: ✅ completed (CRUD, library with
  search/filters/sorting, detail view, create/edit form, image cover).
- **Phase 2 — Export**: ✅ completed (JSON/CSV for the entire library, Markdown
  compatible with Reddit for a single review, native PDF for a single
  review and for the library in batch). See dedicated section below.
- **Phase 3 — Library statistics**: ✅ completed (new Statistics screen
  reachable from the library: totals/averages, platform/genre
  distribution, breakdown by status). See dedicated section below.
- **Phase 4 — Google Drive cloud backup**: ✅ completed (manual + automatic
  backup via WorkManager, restore from a list of backups). See dedicated
  section below.
- **Phase 5 — Internationalization, theme, and documentation**: ✅ completed
  (app translated IT/EN with an in-app language selector, light/dark/system
  theme persisted with DataStore, documentation reorganized under `docs/`).
  See dedicated section below.
- **Phase 6 — Trackable backlog and metadata fetch (TheGamesDB)**: ✅
  completed in two stages — Stage 1: new Backlog section (game
  lists, items with status/comments/automatic history/manual reorder,
  "write a review" trigger on completion). Stage 2: "Search online"
  button (TheGamesDB) in the backlog and review forms to
  prefill cover/platform/genre (+ year/developer for the
  backlog). See dedicated section below.
- **Phase 7 — Rebranding, drawer navigation, TheGamesDB search fix**: ✅
  completed (app renamed to ThePatientGamerHelper everywhere, including
  the `applicationId`/Kotlin package; new Home screen "what do you want to
  do?" + hamburger side drawer with the 3 main sections + settings;
  fix for the "TheGamesDB search always failed" bug). See dedicated section
  below.
- **Phase 8 — Markdown import, backlog export/import, HowLongToBeat, grid
  views**: ✅ completed (review import from Markdown, the reverse
  of the existing export; export/import of the entire backlog with its
  lists, always additive, ZIP format separate from the Drive backup; estimated
  HowLongToBeat times in the backlog form/detail and in statistics,
  an integration that is inherently fragile because HowLongToBeat has no
  public API — see below; list/grid view for library and
  backlog). See dedicated section below and
  `docs/implementation-decisions.md` for the full reasoning. **Revisited**
  against `docs/reviews-backlog-import-export-spec-v2.md`: single-review
  Markdown export/import switched to a front-matter format and moved to a
  form-level "replace content" action, multi-review ZIP export/import
  added, backlog export/import gained a `reviewId` round-trip, a PDF
  template seam was added — see the "Reviews/backlog import-export spec
  v2" subsection below.
- **DOCX export**: **decided not to implement it**, not merely postponed. See
  "DOCX export — why it was not implemented" below.

With Phases 6-8 the roadmap has been further extended beyond the original one (see
`docs/spec.md`). Do not implement features not yet present in this
file or in the spec unless the user explicitly requests it in a
new session.

## Product decisions already made (do not ask again)

- Rating scale: **0–10 with one decimal** (e.g. 7.3), not stars.
- Review status: enum `IN_CORSO` / `COMPLETATO` / `ABBANDONATO`.
- A single review per game (no replay/rigioco in the MVP).
- Cover image: **implemented** in Phase 1 (photo picker + copy into
  the app's internal storage, no runtime permission required thanks to
  `ActivityResultContracts.PickVisualMedia`).
- Platform and Genre: **many-to-many relationship** with the review (a
  game can be released on multiple platforms / have multiple genres), modeled as
  lookup tables with bridge tables, to guarantee consistent autocomplete.
- Custom tags: same pattern as Platform/Genre (lookup table +
  bridge table), for model consistency and autocomplete.
- Pros/Cons: relational child table (`review_pro_con`) with a `tipo`
  field (PRO/CONTRO) and `posizione` for ordering, not concatenated strings.
- JSON/CSV export: **always the entire library**, ignoring the filters active
  in the UI (a backup must be complete).
- Batch PDF export: **a single multi-page PDF file** with all
  reviews (not a zip of separate PDFs) — consistent with SAF
  `ACTION_CREATE_DOCUMENT`, which makes the user choose a single destination.

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
│   │                      # ImportFileReader (Phase 8, SAF, read — used by review Markdown
│   │                      # import, backlog import, and review zip import), PdfReviewRenderer
│   │                      # (PdfDocument, checks PdfTemplateProvider before rendering),
│   │                      # PdfTemplateProvider/NoOpPdfTemplateProvider (import-export spec v2
│   │                      # PDF template seam), ReviewExporter, ReviewZipExporter/
│   │                      # ReviewZipImporter + ReviewZipArchiveBuilder/Reader (import-export
│   │                      # spec v2: multi-review zip, one front-matter .md per review +
│   │                      # covers), BacklogExporter/BacklogImporter + BacklogExportArchiveBuilder/
│   │                      # Reader (Phase 8, zip data+covers) — all concrete classes injected
│   │                      # via Hilt, like ImageStorage, not an interface/impl abstraction like
│   │                      # the repositories (PdfTemplateProvider is the one deliberate
│   │                      # exception — see its own section)
│   ├── drive/             # Drive REST v3 client (DriveApiClient, HttpURLConnection)
│   │                      # + auth (DriveAuthManager: Credential Manager + AuthorizationClient)
│   ├── backup/            # Backup/restore orchestration: BackupManager, zip archive
│   │                      # (BackupArchiveBuilder/Reader), BackupWorker (WorkManager +
│   │                      # Hilt), BackupScheduler, BackupPreferences (SharedPreferences)
│   ├── settings/          # ThemePreferences (Phase 5, Preferences DataStore), ViewModePreferences
│   │                      # (Phase 8, SharedPreferences, list/grid view for library and backlog)
│   ├── thegamesdb/        # Phase 6, Stage 2: TheGamesDbApiClient (HttpURLConnection, same
│   │                      # pattern as DriveApiClient), TheGamesDbPreferences (SharedPreferences,
│   │                      # API key entered at runtime by the user), GameMetadataSearchCoordinator
│   │                      # (logic shared "search online" between the review form and the
│   │                      # backlog form, Phase 8: also exposes searchHowLongToBeat(), backlog-only)
│   ├── howlongtobeat/     # Phase 8: HowLongToBeatApiClient — HttpURLConnection client for an
│   │                      # unofficial/undocumented endpoint, reverse-engineered technique
│   │                      # (see dedicated section below), not the same level of reliability
│   │                      # as TheGamesDbApiClient/DriveApiClient
│   └── debug/            # DebugSeeder, active only behind BuildConfig.SEED_DEBUG_DATA
├── domain/
│   ├── model/            # Pure domain models (no Android dependencies), including ThemeMode,
│   │                      # Backlog* and GameMetadataSearchResult (Phase 6), HowLongToBeatEstimate/
│   │                      # ViewMode/ImportedBacklog* (Phase 8), ReviewZipImportResult
│   │                      # (import-export spec v2, multi-review zip import outcome)
│   ├── filter/            # Library and backlog filter/sort logic, pure functions, unit-tested
│   ├── stats/             # Pure aggregations: LibraryStatisticsCalculator (Phase 3),
│   │                      # BacklogStatisticsCalculator (Phase 6, counts by status/list;
│   │                      # Phase 8: also computeBacklogTimeEstimateStatistics)
│   ├── export/            # Pure export/import formatting: JSON (kotlinx.serialization),
│   │                      # CSV (manual writer), ReviewBackupMarkdown.kt (import-export spec v2:
│   │                      # front-matter Markdown format + parser, replaced the Phase 8
│   │                      # ReviewMarkdownFormatter/Parser pair) — no Android import,
│   │                      # unit-testable in plain JVM. The labels remain fixed in Italian
│   │                      # (see Phase 5, they do not follow the app language) — the v2 front
│   │                      # matter's structural keys are the one deliberate exception, matching
│   │                      # the fixture's English shape. Phase 8: also BacklogExportDto.kt
│   │                      # (backlog export/import zip payload, format separate from
│   │                      # domain/backup — see dedicated section below; import-export spec v2:
│   │                      # +recensioneCollegataId round-trip)
│   ├── backup/            # Pure backup format: BackupPayload/BackupReviewDto,
│   │                      # Review<->DTO mapping, file naming — same pattern as domain/export
│   └── repository/        # Repository interfaces (ReviewRepository, LookupRepository,
│                          # BacklogRepository from Phase 6, + importLists() from Phase 8,
│                          # + upsertImported() from import-export spec v2)
├── di/                    # Hilt modules (Database, Repository)
└── ui/
    ├── theme/             # Material 3 (Compose) theme + ThemeViewModel (Phase 5, reads ThemePreferences)
    ├── navigation/        # Navigation Compose, type-safe routes (kotlinx.serialization).
    │                      # Phase 7: ModalNavigationDrawer around the NavHost (hamburger
    │                      # drawer with the 3 sections + settings), Destination.Home
    │                      # as startDestination
    ├── home/              # Phase 7: HomeScreen, "what do you want to do?" screen with the 3
    │                      # main choices (reviews/backlog/statistics)
    ├── library/           # Library screen (list, search, filters, sorting, export).
    │                      # Phase 7: no longer startDestination, top bar without the app name/
    │                      # backlog/statistics/settings icons (now in the drawer). Phase 8:
    │                      # list/grid view toggle. Import-export spec v2: the top bar's upload
    │                      # icon now triggers the multi-review zip import (not single-file
    │                      # Markdown import anymore, see ui/form/), export menu gained a ZIP option
    ├── detail/            # Review detail screen (+ single review export)
    ├── form/              # Create/edit review form (+ "Search online" and prefilling
    │                      # from a backlog item, Phase 6). Import-export spec v2: upload icon in
    │                      # the top bar imports a front-matter Markdown file as a "replace form
    │                      # content" action (moved here from ui/library/, see dedicated section)
    ├── backlog/            # Phase 6: BacklogScreen (lists + unified search/filter + lightweight
    │                       # aggregate stats), BacklogListDetailScreen (drag-to-reorder),
    │                       # BacklogItemFormScreen, BacklogItemDetailScreen (status/comments/
    │                       # history/abandonment note, Phase 8: also HowLongToBeat estimate).
    │                       # Phase 8: backlog export/import in BacklogScreen, list/grid view toggle
    │                       # in BacklogListDetailScreen (grid without
    │                       # drag-to-reorder)
    ├── settings/           # Settings screen: theme/language preferences (Phase 5), manual/automatic
    │                       # backup, restore, TheGamesDB API key (Phase 6)
    └── common/            # Shared composables (chip input, rating, date picker, cover
                            # thumbnail, ReviewStatus/BacklogItemStatus display, GameSearchDialog
                            # Phase 6, GameGridTile/ViewModeToggle Phase 8, etc.)
```

Resources (`app/src/main/res/`): `values/strings.xml` is Italian (the project's
default language), `values-en/strings.xml` the English translation,
`xml/locales_config.xml` lists the supported languages for integration with the
system settings (API 33+). See the "Phase 5" section below for details.

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
  verify it by hand in Android Studio with a device/emulator.

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
  the breakdown by `stato` (a single-valued field) exposes a percentage, as
  required by the spec. See `docs/implementation-decisions.md`.
- UI: new screen `ui/stats/StatsScreen.kt` (+ `StatsViewModel`,
  `StatsUiState`), reachable from an icon in the library's top bar
  (`ui/library/LibraryScreen.kt`). The distributions are horizontal bars
  built with native Compose (`Box` + `fillMaxWidth(fraction = ...)`), the
  breakdown by status is a segmented stacked bar + legend — **no
  new charting dependency introduced** (no Vico): with at most a
  handful of platforms/genres for a single-user library, the complexity
  of a charting library did not seem justified. If in the future the
  distributions were to become richer (e.g. pie charts, trends over
  time), re-evaluate Vico before writing more hand-rolled rendering code.
- Navigation route: `Destination.Stats` in
  `ui/navigation/Destinations.kt`, wired in `ThePatientGamerHelperNavGraph.kt`.

## Phase 4 — Google Drive cloud backup

- **Authentication/authorization**: two distinct steps, as per spec
  section 6.
  1. **Credential Manager** (`androidx.credentials`) for the "Sign in
     with Google" flow (`GetGoogleIdOption`), to let the user
     choose/confirm their Google account.
  2. **AuthorizationClient** (`com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient`)
     to request the `drive.appdata` scope on that account.
  Both live in `data/drive/DriveAuthManager.kt`.
  - **Note on `play-services-auth`**: the explicit requirement was "not
    `GoogleSignInClient`/`play-services-auth` because it's deprecated". In practice
    `AuthorizationClient` (package `com.google.android.gms.auth.api.identity`,
    **not** `com.google.android.gms.auth.api.signin`) is distributed precisely
    in the Maven artifact `com.google.android.gms:play-services-auth` — there is no
    separate artifact. The deprecated part is the class
    `GoogleSignInClient`/`GoogleSignInOptions` (package `...auth.api.signin`),
    not the entire artifact: it is never imported here. The Gradle
    dependency is therefore necessary, but the code never touches the deprecated API —
    a choice verified against the Android Identity Services documentation
    cited in the spec, not a free interpretation. If you'd prefer to avoid
    that Maven artifact entirely purely on principle, let me know: it is
    the only known way to obtain a Drive access token with the
    modern `AuthorizationClient`.
  - **Silent background authorization**: `DriveAuthManager.authorize()`
    called with only the `applicationContext` (no Activity) returns
    a fresh token with no UI if consent was already granted
    previously — that is what `BackupWorker` uses for automatic backups. If
    consent is not (or no longer) valid, the worker fails silently
    (`Result.failure()`, no crash, no prompt): the next time the
    user opens Settings and does a manual backup, the interactive flow
    re-establishes consent.
- **Drive REST API v3**: hand-written client in
  `data/drive/DriveApiClient.kt` using `java.net.HttpURLConnection` — **no
  dependency on `google-api-client`/`google-api-services-drive`** (Google's
  official Java client), which brings along Guava and a heavy
  dependency graph for just three endpoints (multipart upload, list,
  download). Consistent with CLAUDE.md's explicit guidance not to
  add dependencies without a real need.
- **Backup format**: a single ZIP archive (`java.util.zip`, no
  dependency) with `data.json` (the entire library, DTO in
  `domain/backup/BackupPayload.kt`) and the covers under `images/<file-name>`.
  `domain/backup` is deliberately **separate** from `domain/export`
  (Phase 2): the Phase 2 export is a user-facing format with labels in
  Italian and an absolute path for the cover (not reusable for a
  restore on another device/installation); the backup format instead
  carries only the cover's file name (`coverImageFileName`), resolved to
  a new absolute path at restore time.
- **Restore**: `BackupManager.restoreBackup()` downloads the archive,
  decompresses it, deletes all local covers
  (`ImageStorage.clearAll()`) and calls `ReviewRepository.replaceAll()` — a
  new repository method that, in a single transaction, entirely
  deletes `reviews`+lookup (`platforms`/`genres`/`tags`, with cascade on
  cross-ref and pro/con) and re-inserts each review preserving
  `id`/`createdAt`/`updatedAt` from the backup (unlike `save()`, designed
  for the form and not for a restore). **No merge/conflict handling**:
  it is a single-user app, a restore is a full overwrite, as per
  explicit request.
- **Automatic backup**: `BackupWorker` (`@HiltWorker`, WorkManager) with a
  fixed daily cadence (`BackupScheduler`, 24h,
  `NetworkType.CONNECTED`) — no UI to configure the interval, same
  "don't over-engineer" principle already applied in Phase 3.
  `ThePatientGamerHelperApplication` implements `Configuration.Provider` to inject
  `HiltWorkerFactory`; the manifest explicitly removes
  `androidx.startup.InitializationProvider`
  (`tools:node="remove"`) — necessary because Android Lint
  (`RemoveWorkManagerInitializer`) requires it when `Application`
  implements `Configuration.Provider`, otherwise the build fails (see
  the "Real bugs found only thanks to CI" section below).
- **Persisted state**: `BackupPreferences` (a simple `SharedPreferences`,
  no DataStore for just three flags) holds the "automatic backup" toggle and
  the outcome of the last backup (timestamp/error), written by
  `BackupManager.createBackup()` so both the manual button and the
  periodic worker update the same state shown in Settings.
- **UI**: new screen `ui/settings/SettingsScreen.kt` (+
  `SettingsViewModel`, `SettingsUiState`), reachable from a gear
  icon in the library's top bar. The interactive consent
  flow (`AuthorizationResult.hasResolution() == true`) uses the same
  pattern already in use for exports (`rememberLauncherForActivityResult`),
  with a bridge `StateFlow<IntentSenderRequest?>` +
  `CompletableDeferred<ActivityResult>` in the ViewModel to suspend the
  authorization coroutine until the user responds to the consent prompt.
  Route: `Destination.Settings` in `ui/navigation/Destinations.kt`.
- **Explicit login, not implicit**: the first version triggered
  `signIn()` (Credential Manager bottom sheet) on every single action
  (backup, list backups, restore) — functional but confusing, the user
  saw the account picker with no clear entry point. Now
  `SettingsUiState.signedInEmail` tracks the "logged in" state of the
  session (in-memory only in the ViewModel, not persisted to disk — no
  refresh token saved: an app restart requires a new login, a deliberate
  choice to avoid introducing credential storage). A single "Sign in
  with Google" button (`onLoginClick`) performs `signIn()` + `authorize()`
  together; as long as `signedInEmail == null` the UI shows only that button and
  hides backup/restore (the Preferences and TheGamesDB sections remain
  visible, they don't depend on Drive). Subsequent actions
  (`onBackupNow`/`onRefreshBackups`/`onRestore`) only call
  `authorize()` (silent once the scope has been granted, no further
  picker) via `ensureAccessToken()`, no longer `signIn()`.
  `DriveAuthManager.isConfigured()` exposes whether `google_oauth_web_client_id`
  is still at its placeholder value: if so, the screen shows directly in-app
  (`DriveNotConfiguredCard`, localized IT/EN strings) a card that
  explains what is missing and where it needs to be configured — instead of a generic
  error after pressing login. The OAuth client ID remains the only thing
  that **must** be supplied outside the app's own runtime UI: it is the
  app's one-time registration on Google Cloud Console (tied to SHA-1 +
  `applicationId`), not a per-user piece of data — no Google API allows
  creating it from code at runtime, so it cannot be moved behind a login
  button. (It is, however, **not** committed to the repository — see the
  "Client ID kept out of version control" note below.)
- **External configuration required** (out of scope for these
  changes): the "Web application" OAuth client ID created in Google Cloud
  Console (project with the consent screen in testing mode, Android
  client ID with the SHA-1 of the signing certificate) must be set as
  `DRIVE_OAUTH_WEB_CLIENT_ID=<id>` in the repo-root `local.properties`
  (gitignored, not committed — see below). If absent, `app/build.gradle.kts`
  falls back to the placeholder `[TO_COMPLETE]` and `DriveAuthManager`
  throws `DriveNotConfiguredException` with an explicit message instead of
  attempting sign-in.
- **Cannot be meaningfully tested via Robolectric**: the `HttpURLConnection`
  calls to `googleapis.com`, Credential Manager, and
  `AuthorizationClient` require real network/Play Services — same discussion
  already made for `PdfDocument` in Phase 2. What is instead unit-tested: the
  DTO/JSON mapping (`domain/backup/BackupPayloadTest.kt`), the zip archive
  (`data/backup/BackupArchiveTest.kt`, Robolectric with a real `ImageStorage`)
  and `ReviewRepositoryImpl.replaceAll()`
  (`data/repository/ReviewRepositoryImplTest.kt`, Robolectric). The
  authentication/authorization flow must be verified by hand on a device/emulator with
  Play Services, after configuring the OAuth client.

### Device report: "Sign in with Google" button does nothing (no error, no bottom sheet)

Reported after the user filled in a real `google_oauth_web_client_id` and
rebuilt: tapping "Accedi con Google" produces no visible effect at all —
no account picker, no snackbar, no crash. Reviewed the whole flow
(`DriveAuthManager.signIn()`/`authorize()`, `SettingsViewModel.onLoginClick()`,
`SettingsScreen`'s `GoogleLoginCard`) line by line against the current
official Credential Manager "Sign in with Google" implementation guide
(fetched during this session, since Google's Identity APIs have already
proven to shift under this project — see the Phase 6/8 TheGamesDB/
HowLongToBeat sections): the code matches the documented pattern
(`GetGoogleIdOption` + `setFilterByAuthorizedAccounts(false)` +
`CredentialManager.getCredential(activityContext, request)`, an Activity
`LocalContext.current` from inside `setContent {}`), and `onLoginClick`'s
`try/catch` in `runBusy` does surface `e.message` (or a fallback string)
via a snackbar on any thrown exception — so a literal "nothing, not even
an error" is not explained by a bug in the code path checked so far.

**Leading hypothesis, external to the code**: per the same official guide,
"missing or incorrect SHA-1 [fingerprint]" registered as a companion
**Android** OAuth client (as opposed to the "Web application" client
configured via `local.properties`, see the bullet above)
is documented as a common cause of exactly this kind of *silent* failure —
distinct from the Drive `AuthorizationClient` scope consent (step two),
which already has its own configured-vs-not branch
(`DriveNotConfiguredCard`). Two concrete things worth checking on the
Google Cloud Console project before assuming a code bug: (1) that an
**Android**-type OAuth client also exists there (not just the Web one),
registered with `com.marcogn.thepatientgamerhelper` and the SHA-1 of
*the exact keystore used to build the tested APK*; (2) that a debug build
was not tested against a SHA-1 registered only for the release keystore
(or vice versa) — the two have different fingerprints and Google matches
strictly.

Instead of guessing a code fix for a cause that isn't code, added
diagnostics so the next report would be conclusive either way:
`DriveAuthManager.signIn()`/`authorize()` log (`Log.w`/`Log.i`, tag
`DriveAuthManager`) and wrap any `GetCredentialException`/`ApiException`
into a message that includes the exception's `type`/`statusCode`. Also
gave the login `Button` a visible `CircularProgressIndicator` while
`isBusy` (it previously just disabled itself with no other feedback).

**Confirmed by the diagnostics, and not the SHA-1 hypothesis above**: the
user reported back the exact message surfaced by the new snackbar —
**"No credentials available"** — which is `NoCredentialException`
(a subtype of `GetCredentialException`), not the SHA-1/DEVELOPER_ERROR
symptom the leading hypothesis predicted. Per the same official Google
guide (re-checked for this specific exception type), `NoCredentialException`
from the bottom-sheet flow (`GetGoogleIdOption`) is the **documented**
trigger for falling back to the button-style flow
(`GetSignInWithGoogleOption`) — Google lists "no Google accounts on the
device" explicitly as one of the three conditions where the bottom-sheet
flow can't help and the button flow is needed instead. `signIn()` now
tries the bottom sheet first and, only on `NoCredentialException`, retries
with `GetSignInWithGoogleOption` (same `serverClientId`, no other config
needed — `GetSignInWithGoogleOption` has shipped in the already-present
`googleid` artifact since 1.1.0, this project is on 1.1.1). Both options
resolve to the same `GoogleIdTokenCredential` response type, so the
parsing after the try/catch didn't need to change. **Still worth checking
on the test device**: Settings > Accounts has at least one Google account
added (the button flow can prompt to add one where the bottom sheet
can't, but an emulator image with no Play Store at all won't have either
provider) — if `NoCredentialException` persists even from the button flow,
that's the next thing to verify. **Do not assume fully resolved** until
the user confirms the picker now appears and login completes.

**Follow-up, progress and a newly confirmed root cause**: the button-flow
fallback worked — the account picker now appears and the user can select
an account (real progress from the previous two rounds, where nothing
reached this point at all). But choosing an account now fails with
`GetCredentialCancellationException`, message **"\[16\] Account reauth
failed"**. Researched externally (this exact message, verbatim, turns up
in multiple independent Google Sign-In bug reports — a Flutter issue and
others) rather than guessing: it is the **documented symptom of a SHA-1
fingerprint mismatch** on the companion **Android** OAuth client — i.e.
exactly the leading hypothesis from the very first "does nothing" report,
now corroborated by a second, more specific piece of evidence instead of
just a documentation cross-reference. This is external Google Cloud
Console configuration, not app code: the fix is registering (or
correcting) the Android-type OAuth client's SHA-1 to match the *exact*
keystore that signed the APK actually installed on the test device/phone —
concretely, either `~/.android/debug.keystore` for an Android-Studio-run
build (get its SHA-1 via the Gradle "signingReport" task, or `keytool -list
-v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass
android -keypass android`), or the CI runner's own ephemeral debug
keystore for an `app-debug.apk` downloaded from `build-apk.yml` (its SHA-1
is **not** the same as any developer's local one — AGP generates a fresh
random debug keystore per machine when none exists, it is not a fixed
well-known value — so it must be read off the actual APK, e.g.
`apksigner verify --print-certs app-debug.apk` or `keytool -printcert
-jarfile app-debug.apk`, not assumed). **Still not confirmed end-to-end**:
this explains the specific error message per external reports, but has not
yet been verified against this project's actual Google Cloud Console state
nor confirmed fixed by the user.

### Client ID kept out of version control

Follow-up request, unrelated to whether the "does nothing" report above
turns out to be fixed: the repository is public, and the OAuth web client
ID had been committed verbatim in `res/values/drive_config.xml` (added
while configuring Drive, see PR history). The user separately deleted and
regenerated that client ID in Google Cloud Console specifically to
invalidate the one already exposed in git history — a git history rewrite
(BFG/`git filter-repo` + force-push) was **explicitly declined** as
overkill for a value Google itself doesn't require to be kept
confidential (it ends up baked into the APK and visible in network
requests either way); regenerating it was judged sufficient.

Going forward, the client ID is no longer a committed resource at all:
`app/build.gradle.kts` reads `DRIVE_OAUTH_WEB_CLIENT_ID` from the
repo-root `local.properties` (already gitignored, same file Android
Studio uses for `sdk.dir`) and injects it via `resValue("string",
"google_oauth_web_client_id", ...)` in `defaultConfig` — falling back to
the placeholder `[TO_COMPLETE]` when the value is absent from every
source. `res/values/drive_config.xml` was deleted — keeping both the XML
resource and the generated one would have been a duplicate resource build
error, not just redundant. Each developer machine needs its own
`local.properties` line: `DRIVE_OAUTH_WEB_CLIENT_ID=<web client id>`.

**CI needs the same value too**: `local.properties` is gitignored and
therefore never checked out on a GitHub Actions runner, but
`build-apk.yml`'s `assembleDebug` is what produces the installable
`app-debug.apk` artifact the user actually side-loads onto a device — if
CI built with only the placeholder, every APK downloaded from there would
show "Drive not configured" regardless of what's in a developer's local
file. So `driveOAuthWebClientId()` checks a `DRIVE_OAUTH_WEB_CLIENT_ID`
**environment variable** first, before falling back to
`local.properties`; both `build-apk.yml` and `android-ci.yml` pass it to
the `assembleDebug`/`assembleDebug`-invoking step from a
`DRIVE_OAUTH_WEB_CLIENT_ID` **GitHub Actions repository secret**
(Settings > Secrets and variables > Actions > New repository secret, same
name), which must be added once, by hand, on GitHub — no tool available
in these sessions can create a repository secret from code, same
"one-time app registration, not settable from a runtime login button"
kind of external step already true for the client ID itself.

## Phase 5 — Internationalization, theme, and documentation

### IT/EN internationalization

- All screen strings (phases 1-4) have been extracted into
  string resources: `res/values/strings.xml` is Italian (the project's
  default language), `res/values-en/strings.xml` the English
  translation. The two key lists are kept aligned 1:1 — if you add
  a string in one, add it to the other too.
- **`domain/export` is not touched by this phase**: the labels used
  in exported files (Markdown/CSV/JSON/PDF) remain fixed in Italian,
  hand-written in the pure formatters. The request was to "internationalize
  the screens", not the content of generated files, and localizing them
  would have required passing `Context`/Android resources into `domain/export`,
  breaking its nature as pure Kotlin testable in the JVM (see Phase 2). The
  file the user exports and perhaps pastes on Reddit therefore stays in
  Italian regardless of the language chosen for the app — intentional
  behavior, not a forgotten inconsistency.
- **`ReviewStatus.label()` (in `domain/model`) was not touched** for the
  same reason: it is also used by `ReviewMarkdownFormatter`/`PdfReviewRenderer`
  in `domain`/`data/export`. For the UI there is instead
  `ReviewStatus.displayName()` in `ui/common/ReviewStatusDisplay.kt`, a
  `@Composable` that resolves the correct string resource — the screens always use
  `displayName()`, never `label()`.
- Messages built in the ViewModels (export/backup outcomes, form
  validation errors) cannot use `stringResource()` (it is not
  `@Composable`): the ViewModels that generate them inject
  `@ApplicationContext Context` via Hilt and call `context.getString(...)`
  — a pattern already adopted in `LibraryViewModel`, `DetailViewModel`,
  `ReviewFormViewModel`, `SettingsViewModel`.
- **In-app language selector**: `AppCompatDelegate.setApplicationLocales()`
  (the AndroidX per-app language API, working as a backport from API 26, not only
  from API 33+), three options in Settings — System/Italian/English
  (`ui/settings/AppLanguage.kt`). Persistence is automatic thanks to
  `autoStoreLocales` (see below), no custom storage.
- **`autoStoreLocales`**: enabled by adding to `AndroidManifest.xml` the
  `<service android:name="androidx.appcompat.app.AppLocalesMetadataHolderService">`
  with `<meta-data android:name="autoStoreLocales" android:value="true" />`
  — this is the documented AndroidX mechanism to persist the choice without
  writing `SharedPreferences`/DataStore by hand. `android:localeConfig="@xml/locales_config"`
  on the `<application>` tag (with `res/xml/locales_config.xml` listing `it`/`en`)
  is the platform-side complement for integration with system
  Settings > App languages on API 33+.
- **`MainActivity` extends `AppCompatActivity`, not `ComponentActivity`**:
  explicitly required by the official documentation to use
  `AppCompatDelegate.setApplicationLocales()` with Compose — *"If you're using
  Compose with setApplicationLocales, you must extend your activity from
  AppCompatActivity. Otherwise, setting the app locale won't work."* With
  `ComponentActivity` the language change produces no error at all, it is just
  silently ignored (a real bug discovered during manual verification on
  device, see `docs/implementation-decisions.md` for the full
  account, including two wrong fix attempts before this one). It does not
  introduce View/XML: `setContent {}` remains the sole UI entry point,
  `AppCompatActivity` only serves as a hook for `AppCompatDelegate`'s
  lifecycle. As a consequence the Android theme under
  `res/values/themes.xml` must descend from `Theme.AppCompat` (here
  `Theme.AppCompat.DayNight.NoActionBar`) — `AppCompatActivity` throws
  a runtime exception if the theme is not compatible.
- No manual `recreate()`: with `AppCompatActivity`,
  `setApplicationLocales()` already triggers it end-to-end on its own (both on API 33+
  and below). `ui/settings/AppLanguage.kt` simply calls
  `setApplicationLocales()`.
- New dependency: `androidx.appcompat:appcompat` — necessary for
  `AppCompatDelegate`/`AppCompatActivity`/`AppLocalesMetadataHolderService`;
  it does not introduce XML layouts nor other APIs from the old View-based UI.

### Light/dark/system theme

- A three-state preference (`domain/model/ThemeMode.kt`: `SISTEMA` by default,
  `CHIARO`, `SCURO`) persisted with **Preferences DataStore**
  (`data/settings/ThemePreferences.kt`) — unlike
  `BackupPreferences` (Phase 4, `SharedPreferences`, justified there by "just three
  simple flags"), here the explicit request was DataStore.
- `ui/theme/ThemeViewModel.kt` exposes `themeMode` as a `StateFlow` read from
  `ThemePreferences.themeMode` (`Flow`, single source of truth) via
  `stateIn`. Two independent consumption points, both via
  `hiltViewModel()`: the root `ThePatientGamerHelperApp` in `MainActivity.kt` (decides
  whether to apply `darkTheme = true/false` to `ThePatientGamerHelperTheme`, respecting
  `isSystemInDarkTheme()` when the mode is `SISTEMA`) and `SettingsScreen`
  (to show/change the selection). They are two different `ViewModel`
  instances but read the same `DataStore`, so they stay synchronized
  without needing a shared scope — same "Room/DataStore
  as single source of truth via Flow" principle already in use across the rest of the app.
- `ThePatientGamerHelperTheme` (`ui/theme/Theme.kt`) did not change signature:
  it already accepts `darkTheme: Boolean`, it is only the caller in `MainActivity`
  that now computes it from `ThemeMode` instead of always using the default
  `isSystemInDarkTheme()`.
- New dependency: `androidx.datastore:datastore-preferences`.

### Documentation reorganization

- The functional spec, previously `spec-app-recensioni-videogiochi.md` at the
  root, was moved to `docs/spec.md` (roadmap updated to
  reflect the completion of Phase 5).
- **Note on this very translation pass**: `CLAUDE.md` used to remain
  Italian-only at this point in the project's history, on the reasoning that
  it is operational for the agent, not documentation aimed at repository
  readers — unlike a since-removed `docs/en/` translation tree that existed
  for a while alongside the Italian-first docs. That reasoning has since been
  reversed by explicit user request: this file, along with the rest of the
  documentation, code comments, and other agent-facing files, is now
  English-only, eliminating the separate translation tree entirely. The
  app's own UI is unaffected by this and keeps its dual Italian (default)/
  English string resources (`values/strings.xml`/`values-en/strings.xml`) as
  described throughout this document.

## Phase 6 — Trackable backlog and metadata fetch (TheGamesDB)

Two stages, developed in sequence (Stage 1 verified before starting
Stage 2, as requested).

### Stage 1 — Trackable backlog

- **Data model**: `BacklogListEntity` (lists created/renamed/deleted/
  reordered by the user), `BacklogItemEntity` (title, status, position,
  dates, optional `reviewId`, `abandonNote`, `releaseYear`/`developer` —
  the latter two populated only from Stage 2, see below),
  `BacklogCommentEntity`, `BacklogHistoryEntryEntity`. The item's platform/genre/tag
  are many-to-many **on the same lookup tables** already used
  by reviews (`platforms`/`genres`/`tags`, new cross-refs
  `backlog_item_*_cross_ref`) — the same autocomplete pool shared between backlog and
  reviews, consistent with the rest of the data model.
- **Additive migration, not destructive**: `ThePatientGamerHelperDatabase` goes from
  `version = 1` to `version = 2`. `fallbackToDestructiveMigration()` was
  **not** used: the app is already in real use (see this file's intro),
  a `fallbackToDestructiveMigration()` would have wiped existing
  reviews on the first launch after the update. `MIGRATION_1_2`
  in `data/local/Migrations.kt` only creates the new tables/indices via raw
  SQL, it does not touch `reviews`/`platforms`/`genres`/`tags`.
- **Automatic history**: entirely generated by `BacklogRepositoryImpl`,
  not from manual input — `CREATO` on item creation, `CAMBIO_STATO` only
  when the status actually changes (not on every write of `abandonNote`),
  `CAMBIO_LISTA` when the item is moved (`moveItem`), `COMMENTO` on
  every comment added, `RECENSIONE_COLLEGATA` when the item is
  linked to a review. The `detail` field carries a payload
  interpretable by the UI depending on the type (e.g. the status name for
  `CAMBIO_STATO`, the destination list's name for `CAMBIO_LISTA`) —
  see `ui/backlog/BacklogHistoryDisplay.kt`.
- **`dataInizio`/`dataCompletamento` auto-populated, no manual editor**:
  the spec lists these two fields as optional on the item but does not ask for a
  UI control to set them by hand (unlike the review, which has
  explicit `DatePickerField`s). The only sensible way to set them is
  therefore automatically on the status transition:
  `BacklogRepository.updateStatus()` sets `startDate` the first time
  the status moves to `IN_CORSO` (if not already set) and `completedDate` the
  first time it moves to `COMPLETATO`, never overwriting a value
  already present (so an item that goes back to "in progress" after being
  completed does not lose its original completion date).
- **`updateStatus()` distinct from `saveItem()`**: the spec lists "status
  change via selector" as a feature separate from item CRUD. The
  create/edit form (`BacklogItemFormScreen`) never touches the status;
  only `BacklogItemDetailScreen` does, via a dedicated selector that
  calls `updateStatus()` — a single point that generates history and automatic
  dates, instead of duplicating that logic in the form too.
- **Reordering**: lists (typically few) are reordered with up/down
  arrows on `BacklogScreen` — no drag-and-drop for a list of
  that order of magnitude. Items inside a list (potentially
  numerous, "useful for prioritizing" per the spec) instead have real
  drag-to-reorder, hand-implemented in `BacklogListDetailScreen.kt` with
  `Modifier.pointerInput` + `detectDragGestures` on a dedicated "handle"
  icon (not on the whole row, to avoid conflicts between the drag
  gesture and the click that opens the detail view) — **no reorder
  library added**, consistent with the "do not introduce dependencies without
  need" section further below. The final order is written only once at the end of the
  gesture (`onDragEnd`), not on every frame.
- **Unified search/filter**: `BacklogScreen` normally shows the list of
  lists (with item count and a lightweight aggregate view by
  status/list); as soon as a text search or a filter (list, status,
  platform, genre) is active, the same screen switches to a flat
  cross-list results list (each row shows which list it
  belongs to) — the same search+filter structure as the review library
  (`domain/filter/BacklogFilters.kt`/`BacklogFiltering.kt`, pure functions,
  same pattern as `LibraryFilters`/`LibraryFiltering`), but without
  introducing a separate screen just for search.
- **"Want to write a review?" trigger**: when `updateStatus()`
  sets `COMPLETATO` and the item does not yet have a `reviewId`,
  `BacklogItemDetailViewModel` exposes a one-shot event that the UI
  intercepts to show the confirmation dialog. On confirmation, it navigates to
  `Destination.Form(backlogItemId = itemId)` — `Destination.Form` now has a
  second optional parameter, used only on creation (ignored if
  `reviewId` is already set). `ReviewFormViewModel` prefills the draft from
  the `BacklogItem` (title/platforms/genres/dates/cover) and, on a successful
  save, calls `BacklogRepository.linkReview()` to close the
  loop (it also records the `RECENSIONE_COLLEGATA` history entry). The
  cover image is **not** shared by reference between the backlog item and
  the review: `ImageStorage.duplicate()` (a new method) copies the file to a
  new name, so deleting the review afterwards does not make the
  cover shown in the backlog disappear (or vice versa) — two independent files with the
  same initial content.

### Stage 2 — Automatic cover and metadata fetch (TheGamesDB)

- **The API key is always required, it was not a choice**: before
  implementing, I verified online (as requested) the current
  limits/requirements of TheGamesDB's public API. The result, different
  from the initial assumption ("shouldn't be needed, ES-DE doesn't
  need it"): **as of 02/17/2026 TheGamesDB changed its policy and now requires
  an `apikey` on every request**, public or private — anonymous access no
  longer exists. ES-DE/Skyscraper don't ask for a *personal* key
  because they embed their own (public, shared, rate-limited) key in
  their source code, but that key still exists. Not having a
  reliable way to retrieve its current literal value, I explicitly asked
  the user how to proceed instead of guessing or pasting in
  a key found online without certainty.
- **Runtime configuration, not a build placeholder**: unlike the
  Drive OAuth client ID (Phase 4, `res/values/drive_config.xml`,
  `[TO_COMPLETE]` replaced before the build), the TheGamesDB API key is
  a field the user fills in **inside the app** (a new section in
  Settings, `TheGamesDbPreferences`, `SharedPreferences` — the same
  minimal pattern as `BackupPreferences`, not DataStore). No build
  contains a key, real or placeholder: as long as the field is empty, the
  "Search online" button triggers `GameMetadataSearchCoordinator`, which
  returns an informational message instead of calling the API — never a
  crash, never a build that fails to compile due to a missing key.
- **Hand-written REST client, not Retrofit/Ktor**: the original request
  cited Retrofit/Ktor as an example ("if not already present"), but the project
  had already solved the same problem in Phase 4 (`DriveApiClient`) with a
  minimal `HttpURLConnection` + `kotlinx.serialization` client. I followed
  the same pattern for `TheGamesDbApiClient` instead of introducing a new
  HTTP dependency: just four GET endpoints (search +
  Platforms/Genres/Developers) do not justify a full HTTP client,
  consistent with "do not introduce dependencies without need" already applied three
  times before (Drive, PDF, statistics charting). **No new
  dependency added in Phase 6.**
- **In-memory cache of lookups, not persisted**: `TheGamesDbApiClient`
  keeps in memory (for the process's lifetime) the id→name maps of
  Platforms/Genres/Developers, populated on first use and reused for
  subsequent searches within the same app session. With a public
  rate limit on the order of a few thousand requests per month, avoiding three
  extra lookup calls on every single search was a deliberate
  choice, not a premature optimization.
- **Multiple results, no auto-selection**: `Games/ByGameName`
  (optionally filtered by platform, inferred from the first platform tag
  already entered in the form, to disambiguate remasters/regional editions) can
  return multiple matches; `GameSearchDialog` (a composable shared
  between `ReviewFormScreen` and `BacklogItemFormScreen`, `ui/common/`) lists them
  all with cover/platform/year, the user picks one. On selection, the
  cover is downloaded and saved **locally** with
  `ImageStorage.writeBytes()` (the same storage used for manually uploaded
  covers) — never just the remote URL.
- **`releaseYear`/`developer` only on `BacklogItem`, not on `Review`**: the
  spec asked for saving "platform, genre, year, developer" as
  useful metadata. Platform and genre are already existing fields on both
  models; year and developer are not. I chose to add them **only** to
  `BacklogItemEntity`/`BacklogItem` (useful as cataloging data even before
  actually having played the game) and to **not** extend `ReviewEntity`/`Review`:
  doing so would have required touching a mature schema with five phases of
  functionality already built on top of it (JSON/CSV/PDF/Markdown export, backup
  DTOs, statistics), for two bibliographic fields that were never
  part of the core of a review (rating/pros/cons/text). The online search
  in the review form therefore stays limited to
  title/platform/genre/cover, like the rest of the form.
- **Silent fallback, never a crash**: `GameMetadataSearchCoordinator`
  centralizes the logic shared between the two forms — missing key, no
  results, network/HTTP errors all become an `Outcome.Message`
  text shown in the dialog, never a propagated exception. The
  existing manual flow (typing the fields by hand) always remains available
  below, unchanged.
- **Cannot be meaningfully tested via Robolectric**: same discussion
  already made for Drive in Phase 4 — real `HttpURLConnection` calls to
  `api.thegamesdb.net` require a real network. What is instead unit-tested is the
  pure parts added in Phase 6: `domain/filter/BacklogFilteringTest.kt` and
  `domain/stats/BacklogStatisticsCalculatorTest.kt`, same pattern as
  `LibraryFilteringTest`/`LibraryStatisticsCalculatorTest`.

**Build status**: as with Phase 5, this change was written and
reviewed statically line by line (parenthesis balance, imports,
1:1 correspondence of `strings.xml` IT/EN keys, consistency of the
Room `@Relation`/`@Junction`/`@ForeignKey` signatures) but **not yet
verified on CI at the time of writing this note** — the same isolated
sandbox with no access to `dl.google.com` described under "Known
sandbox environment limitation" was also in effect for this session. Check the
status of the checks on the relevant PR before considering it green.

## Phase 7 — Rebranding, drawer navigation, TheGamesDB search fix

Three distinct requests in the same session, treated as a single
coordinated change.

### ThePatientGamerHelper rebranding

- App renamed **everywhere**, including `applicationId`/Kotlin package (not
  just the display name): `com.marcogn.gamereviewer` →
  `com.marcogn.thepatientgamerhelper`. Choice explicitly confirmed
  by the user after being informed of the two concrete consequences (no
  data migration for existing installations — a different `applicationId` is
  a different app to Android; the Drive OAuth client will need to be re-registered for
  the new `applicationId`+SHA1 once configured). See
  `docs/implementation-decisions.md`, Phase 7 section, for the full
  detail of the reasoning and of what was intentionally
  left unchanged (the GitHub repository name, the export file prefix in
  `domain/export/ExportFileNaming.kt`).
- Renamed files/classes: `GameReviewerNavGraph.kt` →
  `ThePatientGamerHelperNavGraph.kt`, `GameReviewerApplication.kt` →
  `ThePatientGamerHelperApplication.kt`,
  `data/local/GameReviewerDatabase.kt` →
  `data/local/ThePatientGamerHelperDatabase.kt` (with
  `DATABASE_NAME = "the_patient_gamer_helper.db"`, changed by hand because
  snake_case was not caught by the rename `sed`).
- `app_name` (`values/strings.xml`/`values-en/strings.xml`): now
  `"ThePatientGamerHelper"` in both languages (previously "Recensioni
  Videogiochi"/"Game Reviews").

### Navigation: Home chooser + hamburger drawer

- New `Destination.Home` (`ui/navigation/Destinations.kt`), now the
  graph's `startDestination` — replacing `Destination.Library` as the
  first screen shown. `ui/home/HomeScreen.kt`: top bar with a hamburger icon
  + "what do you want to do?" text + three cards (Reviews/Backlog/Statistics,
  each with icon/title/subtitle/chevron), no mock data — only
  navigation, no reading from Room.
- `ThePatientGamerHelperNavGraph.kt` wraps the entire `NavHost` in a
  `ModalNavigationDrawer` (Material 3), with `drawerState` hoisted at the
  graph level. The drawer entries (Reviews, Backlog, Statistics,
  then a divider and Settings at the bottom) navigate with
  `popUpTo(Destination.Home) { saveState = true }` +
  `launchSingleTop = true` + `restoreState = true` — the standard
  drawer/bottom-bar pattern, avoiding backstack accumulation when repeatedly
  switching between the same sections.
- Every screen receives only an `onMenuClick: () -> Unit` lambda (opens the
  drawer), never the drawer state itself — consistent with UDF (events
  flow up, state flows down) already followed across the rest of the app.
- **`LibraryScreen`**: top bar without the app name any more (now
  `stringResource(R.string.library_title)`, "Recensioni"/"Reviews"),
  a hamburger icon instead of the back arrow, the
  Backlog/Statistics/Settings `IconButton`s removed from the toolbar (now
  reachable only from the drawer). `BacklogScreen`/`StatsScreen`: same
  treatment (`onBack` → `onMenuClick`, back arrow icon → hamburger).
  `SettingsScreen` **did not** receive the hamburger: it remains reachable
  only from the drawer, with a back arrow in its own top bar — it is "at
  the bottom" of the drawer, not one of the three main sections.

### TheGamesDB search always-failed fix

- **Certain and corrected cause**: `GameMetadataSearchCoordinator.search()`
  replaced any exception (network, HTTP, parsing) with the same
  fixed generic message, discarding the real detail. It now logs
  the exception (`Log.w`) and **appends** its message (when present) to the
  generic text shown in the dialog — a future failure will be
  diagnosable by the user themselves (e.g. "HTTP 401: ..." for an invalid
  key) instead of remaining a mysterious "failed".
- **Additional defensive fixes** (based on research, not on
  direct reproduction — this sandbox has no network access to
  `api.thegamesdb.net`, explicitly blocked by the proxy policy):
  missing `Accept: application/json` header + explicit connect/read
  timeouts on the connection; removed `"platform"` from the `fields`
  requested on `Games/ByGameName` (not a valid field for that endpoint); the
  platform filter syntax corrected to the Laravel indexed form
  (`filter[platform][0]=` instead of `filter[platform]=`).
  See `docs/implementation-decisions.md` for the full detail —
  including why the first fix (the message no longer generic) is the only one
  whose correctness is guaranteed, regardless of how well-targeted the other
  defensive fixes turn out to be.

**Build status**: as with Phases 5 and 6, this change was written and
reviewed statically (parenthesis balance on every touched `.kt` file,
package/directory path consistency after the mass rename, XML
validity across all resources, 1:1 parity of `strings.xml` IT/EN keys) but
**not verified on CI at the time of writing this note** — same
isolated sandbox with no network access to `dl.google.com` (build) nor to
`api.thegamesdb.net` (search fix) described under "Known
sandbox environment limitation". Check the status of the checks on the relevant PR
before considering it green, and manually verify on device/emulator that
the TheGamesDB search fix now shows a useful error message
when the search fails.

## Phase 8 — Markdown import, backlog export/import, HowLongToBeat, grid views

Five distinct requests in the same session, treated as a single
coordinated change: review import from Markdown, backlog export/import,
estimated HowLongToBeat times in the backlog, the same times in statistics, and
grid view for library and backlog.

### Review import from Markdown

- `domain/export/ReviewMarkdownParser.kt`: `parseReviewMarkdown(String):
  Result<ReviewDraft>`, a pure function (no Android import, unit-tested
  in plain JVM like the rest of `domain/export`) that is the exact reverse of
  `toRedditMarkdown()` — same fixed Italian labels (`Voto`, `Stato`,
  `Piattaforme`, `Generi`, `Tag`, `Iniziato il`, `Terminato il`, `Ore di
  gioco`), same bullet-list structure, same optional `## Pro`/
  `## Contro` sections. It is not a generic Markdown parser: it only recognizes
  the format the app itself produces.
- Strict on the fields the exporter always writes (title, rating, status, start
  date — a file missing one of these is not a review written by
  this app), lenient on everything else (platforms/genres/tags/hours/
  pros/cons/body), exactly mirroring what `toRedditMarkdown`
  omits when empty. Every parsing failure produces a failed
  `Result` with a specific message (e.g. "Voto mancante o non valido"), never
  a generic exception.
- Entry point: an upload icon in the library's top bar, opens a
  `.md` file via SAF (`ActivityResultContracts.OpenDocument`), reads the
  content with the new `data/export/ImportFileReader.kt` (the read-side
  counterpart of `ExportFileWriter`, also reused by the backlog import below),
  passes it to the parser and — if valid — always creates a **new** review
  (`ReviewRepository.save(id = null, ...)`), never an update of an
  existing one. Outcome shown with a snackbar (`import_completed`/
  `import_failed`), the same pattern as `exportMessage` in `LibraryViewModel`.

### Backlog export/import with its lists

- Format **deliberately separate** from `domain/backup` (Phase 4, the
  Drive backup of the entire review library with a full-overwrite
  restore): this is a file the user explicitly creates/opens via SAF
  to share or merge their backlog, not a safety
  restore. `domain/export/BacklogExportDto.kt` (pure payload, JSON via
  kotlinx.serialization, Italian labels like `ReviewExportDto`) +
  `data/export/BacklogExportArchive.kt` (zip `data.json` + `images/`,
  the same schema as `data/backup/BackupArchive.kt` but scoped only to the
  covers actually referenced by the backlog, not the entire
  `ImageStorage`) + `data/export/BacklogExporter.kt`/`BacklogImporter.kt`
  (I/O orchestration, injected via Hilt like `ReviewExporter`).
- **Always additive, never a replacement**: `BacklogRepository.importLists()`
  always creates new lists and new items with new ids — even importing the
  same file twice (not idempotent, a choice accepted to keep things
  simple: a merge by title/similarity would have introduced ambiguity —
  two games with the same name on different platforms? — which the request
  did not specify). `reviewId` is discarded on import (the linked
  review belongs to the library that exported the file and might
  not exist on this device); comments and history are re-inserted
  verbatim with their original timestamps, without adding a synthetic
  `CREATO` entry (the original one is already in the exported history). Covers
  are rewritten with a new file name (UUID), never reusing the
  original name — the `covers/` folder is shared with reviews, reusing
  a name would risk a collision with a file already present on the device.
- Entry point: upload/download icons in `BacklogScreen`'s top bar.
  Export always over the entire backlog (the same "always everything, never
  filtered" rule as JSON/CSV in Phase 2).

### Estimated HowLongToBeat times in the backlog

- **No public API exists**: verified online before implementing
  (the same rule already applied in Phase 6 for TheGamesDB's apikey
  policy) — unlike TheGamesDB, which at least requires an apikey
  but remains a documented endpoint, HowLongToBeat has never had a public
  API. Every existing unofficial integration (howlongtobeatpy,
  ckatzorke/howlongtobeat, etc.) works by re-deriving the current
  search endpoint from HowLongToBeat's frontend JavaScript bundle at
  runtime, because the path changes with every one of their deploys — there is no
  stable contract to implement against.
- `data/howlongtobeat/HowLongToBeatApiClient.kt` uses the same
  reverse-engineered technique: fetching the homepage, extracting the bundle
  `_app-*.js`, a regex on the POST endpoint, with a fallback to the historically
  stable path `/api/s/` if the extraction fails. **This is inherently
  more fragile than `TheGamesDbApiClient`/`DriveApiClient`**: those are
  reverse-engineered from documented or otherwise stable REST endpoints
  (Phase 4/6), this one is reverse-engineered from a frontend that can change
  with every deploy without notice. It was not possible to run it against
  the real `howlongtobeat.com` from this sandbox (no network access,
  same known limitation already noted for `dl.google.com`/`api.thegamesdb.net`) —
  **it must be considered unverified until tested on a real device**.
- **Always fails silently**: every error (changed bundle, blocked
  endpoint, no match, different response schema) becomes
  `null` in `GameMetadataSearchCoordinator.searchHowLongToBeat()` — never
  a propagated exception, never a message shown to the user (unlike
  `search()`/TheGamesDB, which shows a message on
  failure: here it is a silent enrichment on top of an already-successful
  TheGamesDB search, not a standalone action). The existing "search online"
  flow does not change in any way if HowLongToBeat does not respond.
- `hltbMainStoryHours`/`hltbMainExtraHours`/`hltbCompletionistHours`
  live **only on `BacklogItemEntity`/`BacklogItem`**, the same precedent
  already justified for `releaseYear`/`developer` in Phase 6: they are cataloging
  metadata, not part of the core of a review. The online search
  in the review form remains unchanged; only `BacklogItemFormViewModel`
  calls `searchHowLongToBeat()`, after the user has chosen a
  TheGamesDB result (using the exact title of the chosen result, not the
  typed text, for maximum matching precision).
- `MIGRATION_2_3` (`data/local/Migrations.kt`) adds the three nullable
  `REAL` columns to `backlog_items`, additive like `MIGRATION_1_2`.
  `@Database` goes from `version = 2` to `version = 3`.
- Visible in the backlog detail card (`BacklogItemDetailScreen`,
  a dedicated card below the metadata, shown only if at least one field is
  set) — not editable by hand, same principle as
  year/developer.

### Statistics: estimated backlog time

- `domain/stats/BacklogStatisticsCalculator.kt`:
  `computeBacklogTimeEstimateStatistics()` sums the estimated hours
  (main story/main+extra story/completionist) across all backlog
  items that have **at least one HowLongToBeat field set**,
  regardless of status — reading as "how much total time these
  games require", not just the ones not yet started. It also exposes
  `itemsWithEstimate` to show "X items with an estimate" in the UI.
  Integrated into `BacklogStatistics` (used by `BacklogScreen`'s lightweight
  header) and into a new `StatsUiState.backlogTimeEstimate`
  (`StatsViewModel` now combines `ReviewRepository.observeAll()` with
  `BacklogRepository.observeAllItems()`).
- New section in `StatsScreen` ("Tempo stimato backlog (HowLongToBeat)"),
  shown only if at least one item has an estimate — independent of the number of
  reviews, so it is visible even with an empty library if the backlog has
  HowLongToBeat data.

### List/grid views for reviews and backlog

- `domain/model/ViewMode.kt` (`LIST`/`GRID`) + `data/settings/
  ViewModePreferences.kt` — just two persisted flags (library view, backlog
  view), a simple `SharedPreferences` like `BackupPreferences`/
  `TheGamesDbPreferences`, not `DataStore` (that remains reserved for `ThemeMode`,
  where the explicit request in Phase 5 was DataStore).
- `ui/common/GameGridTile.kt` (full-width cover, 2:3 aspect ratio
  enforced via `Modifier.aspectRatio` + `ContentScale.Crop`, not the
  fixed square thumbnail from `CoverThumbnail` used in list view) and
  `ui/common/ViewModeToggle.kt` (a toggling icon, shared between
  `LibraryScreen` and `BacklogListDetailScreen` to avoid duplicating the same
  UI twice).
- **The backlog grid does not support manual drag-to-reorder**
  (Phase 6, Stage 1, available only in list-view
  `BacklogListDetailScreen`): extending the existing vertical drag gesture to a
  2D grid would have required a substantially different positioning
  logic for a purely cosmetic benefit. The user goes back to the
  list view to reorder.
- `BacklogScreen` (the "list of lists"/cross-list search view) **did not**
  receive the toggle: the grid applies where *games* are browsed
  (library, detail of a backlog list), not where *lists* themselves
  are browsed.

**Build status**: as with the previous phases, this change was
written and reviewed statically line by line (parenthesis balance,
imports, field-name consistency between entity/DTO/mapper/draft,
1:1 parity of `strings.xml` IT/EN keys) but **not verified on CI at the
time of writing this note**. In this session I also personally verified
whether the environment had broader network access than the usual
isolated sandbox: some Google hosts do respond (`maven.google.com`
returns 200), but actually downloading Android Gradle Plugin artifacts
remains blocked by the outbound proxy (redirect to a host
not in the allowlist, CONNECT tunnel rejected with 403) — the same known
limitation, confirmed with a direct test instead of merely assumed. See
`docs/implementation-decisions.md` for the full reasoning behind
every choice in this phase. Check the status of the checks on the PR before
considering it green, and manually verify on device/emulator both
the Markdown import and — especially — the HowLongToBeat integration, which
remains the highest-risk-of-fragility part of this change.

### Fixes after real-device verification

Manual on-device verification (after the initial PR was merged) found
four real problems, not visible from static review alone:

- **"Abbandonato" `FilterChip` splitting vertically character by
  character** in the backlog detail's status selector: a `Row` without
  wrapping compressed the last chip beyond the text's minimum width. Fix:
  `FlowRow` (`@OptIn(ExperimentalLayoutApi::class)`, the same pattern already in
  use in `FilterSheet.kt`/`BacklogFilterSheet.kt`/`TagInputField.kt`) so
  the chips wrap onto a new row instead of getting squeezed.
- **Top bar titles (`Recensioni`, `Backlog`, etc.) breaking onto two
  lines**, overlapping the hamburger icon: too many action icons next
  to the title (up to 5 in the library after Phase 8) left too little
  room. Fix: `maxLines = 1` + `overflow = TextOverflow.Ellipsis` on
  **all** `TopAppBar` titles across the app (not just library/backlog,
  for consistency and to prevent the same bug elsewhere — e.g. a long title
  of a backlog list or of a review). If the title truncates too much on
  narrow screens, the next step is to reduce the number of icons by
  consolidating them into an overflow menu, not yet done.
- **TheGamesDB search failing with an unreadable JSON error**
  (`Expected JsonArray, but had JsonNull ... element: $.developers`),
  regardless of platform/title: TheGamesDB returns `null`
  (not simply omitting the key) for `genres`/`developers` on games
  without that data cataloged — a default value in
  `kotlinx.serialization` only covers the *missing* key, not an explicit
  `null`, so every game with `developers: null` in the response caused
  the entire search to fail. Fix: `genres`/`developers` made `List<Long>?`
  in `GameDto` (`TheGamesDbApiClient.kt`) instead of only having a default,
  plus `coerceInputValues = true` on the `Json` instance as an additional
  safety net for other fields that might behave the same way in the
  future.
- **HowLongToBeat missing everywhere** (neither in the backlog card nor in the
  statistics): the first version of the client only implemented the "bare"
  search POST, without the `x-auth-token`/`x-hp-key`/`x-hp-val`
  headers that the currently maintained unofficial libraries (e.g.
  ScrappyCocco/HowLongToBeat-PythonAPI) document as necessary — they need to be
  obtained with a `GET <path>init` before the actual search.
  `HowLongToBeatApiClient` now implements the entire flow (homepage → bundle
  `_app-*.js` → endpoint → `init` → search with the headers), uses a
  realistic desktop User-Agent instead of one that identifies as an app
  (many sites with anti-scraping protections discard non-browser UAs
  outright), and logs a warning at every step that fails (tag
  `HowLongToBeatClient`, checkable with `adb logcat -s
  HowLongToBeatClient`) — the previous phase failed in absolute
  silence, impossible to diagnose remotely. **This remains the highest-
  risk part of this phase**: if the site is behind anti-bot protections more
  sophisticated than a header/User-Agent check (e.g. a Cloudflare
  challenge requiring JavaScript execution), no `HttpURLConnection`
  client can get past it — in that case the only viable path
  would be a hidden `WebView` that loads the real page and
  intercepts network calls, a much more invasive change not yet
  made. If the estimates remain always absent after this fix,
  check the logs with that tag before assuming other causes.

### Second device verification: HowLongToBeat diagnostics, backlog→review flow, dynamic grid

Three further requests after retrying the first round of fixes above —
HowLongToBeat was still completely absent and without `adb`
available there was no way to know why, the "complete item →
write review" flow felt clunky, and covers in the grid with
different aspect ratios (square vs. vertical) wasted space.

- **HowLongToBeat diagnostics moved into the app, no longer just `Log.w`**:
  with no way for the user to read `adb logcat`, a silent failure
  remained a black box. `GameMetadataSearchCoordinator.searchHowLongToBeat()`
  now returns an `HltbOutcome` (`Found`/`NotFound`/`Error(message)`)
  instead of a bare `HowLongToBeatEstimate?` — `BacklogItemFormViewModel`
  turns it into `BacklogItemFormUiState.hltbMessage`, a line of text
  shown in the form right after picking a "Search online" result
  (e.g. "HowLongToBeat: search failed — HTTP 403: ..."). The same
  principle already applied to the TheGamesDB generic-message fix in
  Phase 7: a real message, even if technical, beats a silent failure —
  now any future failure is readable directly on screen
  and reportable without debugging tools.
- **"Complete → write review" flow made explicit instead of
  immediate**: previously, tapping the "Completed" chip immediately applied the
  status *and* brought up the "want to write a review?" dialog on
  every single tap, even just while exploring the options. `StatusEditor`
  (`BacklogItemDetailScreen.kt`) now keeps the selection (status +
  abandonment note) as uncommitted local state; a "Save" button appears
  only when the selection differs from the saved one, and only on pressing
  that button does `BacklogItemDetailViewModel.onSaveStatus()`
  write the status and — only if the status actually changed to COMPLETATO —
  trigger the prompt. It replaces the previous `onStatusChange`/
  `onAbandonNoteChange` (which wrote on every tap/keystroke).
- **The prefilled review form now opens already set to "Completed"**:
  `ReviewFormViewModel` did not set the `status` in the draft prefilled from
  a backlog item (it stayed at the default `IN_CORSO`), even though the only
  way to reach it is precisely the post-completion prompt — it now explicitly
  sets `ReviewStatus.COMPLETATO` (and uses `LocalDate.now()` as a
  fallback `dataFine` if the backlog item did not have one yet).
- **The back button from the prefilled form no longer goes back into the backlog**:
  previously it simply did a `popBackStack()`, returning to the
  backlog card and discarding any typed data. `ReviewFormViewModel.onBackPressed()`
  now saves the review as a "draft" (if there is at least a title, without the
  validations of an explicit Save — a back is not a deliberate
  confirmation) and links it to the backlog item regardless;
  `ThePatientGamerHelperNavGraph` distinguishes the "form opened from the backlog"
  case (`Destination.Form.backlogItemId != null`)
  and in that case navigates to `Destination.Library` (the same
  `popUpTo(Home){saveState=true}` pattern already used by the drawer) instead of doing a
  plain pop — a cancel from a form opened normally from the library
  remains an unchanged pop.
- **Dynamic grid instead of uniform-height rows**: `GameGridTile`
  no longer forces a fixed `aspectRatio` on the cover when an
  image exists (`ContentScale.FillWidth` with no height constraint, the height
  follows the file's real proportions); `LibraryScreen`/`BacklogListDetailScreen`
  switch from `LazyVerticalGrid` (uniform rows, each row as tall as the
  tallest tile) to `LazyVerticalStaggeredGrid` (`StaggeredGridCells.Adaptive`,
  requires `@OptIn(ExperimentalFoundationApi::class)` on this BOM) so
  square and vertical covers sit side by side without wasted space
  above/below the shorter ones — only a small constant offset
  (`verticalItemSpacing`/`horizontalArrangement`, 12dp) remains between the tiles. The
  "no cover" placeholder stays at a fixed 2:3 ratio, the only
  case with no intrinsic dimension to derive the shape from.

**Build status**: same discussion as the previous notes — written and
reviewed statically, not runnable in this sandbox (`dl.google.com` blocked,
reconfirmed in this session too). The highest-risk part remains
the same: if HowLongToBeat keeps returning nothing, the message
now visible in the form (`hltb_status_error` with the technical detail) is the
first place to check — report it verbatim instead of guessing.

### Third device verification: diagnostics paid off, HTTP 308 redirect fix

The diagnostics added in the previous round worked exactly as
intended: instead of remaining a black box, the user was able to report the
exact message shown in the form — **"search failed — HTTP 308"**,
identical for any title searched. The real cause, no longer a guess:
`HttpURLConnection` with default `followRedirects` **does not reliably
follow redirects on POST requests**, and has known gaps specifically
around code 308 (Permanent Redirect, which unlike 301/302 requires
preserving the method and body — introduced by RFC 7538, more recent than the rest
of the class's historical redirect handling). The search POST (or one
of the homepage→bundle→init flow's GETs) was therefore being redirected by the
server, and the library returned the bare 308 instead of following it.

Fix: `HowLongToBeatApiClient.request()` disables `instanceFollowRedirects`
and follows redirects **manually** (up to `MAX_REDIRECTS = 5`), re-issuing
the request with the same method, headers, and body toward the URL resolved from
`Location` — the correct behavior for 307/308 (which require it) and the
safest choice for 301/302/303 too in this context (a JSON response is
expected regardless). All four of the client's calls (the three
GETs of the authentication flow plus the search POST) now go through
this single point instead of an `openConnection()` that relied on the
default behavior. Every redirect followed is logged (tag
`HowLongToBeatClient`) to remain diagnosable if the new behavior
reveals a further downstream issue.

### Fourth device verification: duplicate reviews from the backlog flow, HowLongToBeat still 308

Two further reports after the HTTP 308 redirect fix: reviews
created from the "complete item → write review" flow were being duplicated on every
new attempt, and HowLongToBeat kept returning the same
"HTTP 308" error despite the manual redirect fix.

- **Duplicate reviews — real cause**: once a review was linked
  to a backlog item (`BacklogItem.reviewId` set), the only way to
  "get back to it" was again `onWriteReview` →
  `Destination.Form(backlogItemId = itemId)`, which **always** creates a
  new empty review (`reviewId = null` in the route), ignoring that a
  review was already linked. The only visible trace of the link
  was an inert label ("Recensione collegata"), not clickable — no
  way to reopen *that* review, so every time the user
  went back through the flow (e.g. to check on/continue the review) they ended up
  generating yet another draft. The guard `current.reviewId == null` in
  `BacklogItemDetailViewModel.onSaveStatus()` already correctly prevents a
  second "want to write a review?" prompt for the same item — the
  problem wasn't there, but in the total absence of a path to
  reach an already-existing review again.
- **Fix**: "Recensione collegata" (`BacklogItemDetailScreen.kt`) is now a
  clickable text (underlined, same primary color as before) that directly opens
  `Destination.Detail(reviewId)` — the normal review detail
  screen, with its existing, safe edit/delete paths
  (no risk of duplication: editing an existing review always goes through
  `editingId != null`, never through the backlog-prefill branch). Also added
  `onOpenReview: (String) -> Unit` as a new parameter of
  `BacklogItemDetailScreen`, wired in `ThePatientGamerHelperNavGraph.kt`. As an
  additional defensive protection against a double tap on the dialog's "Yes"
  button (which could queue two identical navigations before the
  dialog closes), `onWriteReview`'s navigation now also passes
  `launchSingleTop = true`.
- **Poorly visible status "Save" button**: `StatusEditor` used a
  plain `TextButton` — hard to distinguish from the rest of the text when
  it appears. Changed to `Button` (filled, primary color) to make it
  immediately recognizable as an action to take.
- **Draft reviews already duplicated on the user's device**: this fix
  prevents new duplication, but **does not touch data already present** — the
  double/triple drafts created before the fix remain in the local database and
  must be deleted by hand by the user (trash icon in the detail view of each
  extra review). No automatic deduplication migration step was written: there is no
  reliable way to distinguish "review duplicated by this bug" from
  "two identical reviews by title but intended by the user" without risking
  deleting real data.
- **HowLongToBeat still "HTTP 308" after the manual redirect fix**: the
  previous session's fix (following redirects by hand, see above) was
  a correction motivated by a real error reported by the user, but the
  new test reports the exact same error, not a different one — so it
  **has not been resolved**, or at least it is not yet possible to say so with
  certainty. Without network access to `howlongtobeat.com` from this sandbox
  (the same known limitation, unchanged), it is not possible to reproduce and
  verify further beyond what the user can report from a real device.
  Instead of attempting another "blind" fix on the same code already
  corrected once without success, `ensureSuccessful()` and the too-many-redirects
  message in `HowLongToBeatApiClient.request()` now also include
  the URL that actually failed (`HTTP $responseCode @ $url`, and for
  too-many-redirects both the starting URL and the last one reached) — previously the
  message was just "HTTP 308" without saying *which* of the flow's four
  calls (homepage, JS bundle, init, search) had produced it, nor whether
  it was the path derived from the bundle or the `/api/s/` fallback. A next
  report with the URL included will allow a targeted diagnosis instead of a
  further speculative attempt. **This remains the least reliable part of
  this phase**, as already noted — do not assume it resolved until the user
  confirms the estimates actually appear.

### Fifth device verification: missing BackHandler in the review form, HowLongToBeat finally reaches the real site (but 404)

Two reports: the review duplication from the backlog flow
kept occurring *every time* for the same game despite the previous
round's fix, and HowLongToBeat now returns a real 404 page
from howlongtobeat.com instead of a network error.

- **Real cause of the duplication, not resolved by the previous fix**: the
  previous round's fix (clickable "Recensione collegata" link +
  `editingId != null` as a guard) assumed the only way out of
  the review form was the top-left arrow, whose
  `onClick` calls `viewModel.onBackPressed { onCancel() }` (implicit
  draft save + linking to the backlog item + offer to move
  list). **The system back gesture (swipe/hardware button)
  does not go through there**: Compose Navigation registers its own
  `OnBackPressedCallback` that by default does a bare `popBackStack()`,
  completely bypassing the screen's custom logic unless it is
  explicitly intercepted with a `BackHandler`. Observed result:
  the user exits with the system gesture instead of tapping the arrow, nothing
  is saved or linked, the item returns to the backlog detail
  screen with `reviewId` still `null` — which, being the item's "honest"
  state, correctly brings back (not a bug in itself) the "write
  a review" link/prompt, and every subsequent attempt generates another
  independent review. Fix: `BackHandler` added in
  `ReviewFormScreen.kt` that invokes the same `onBackPressed()` — now
  the system gesture and the top arrow behave identically.
  No other screen in the app has custom back logic that
  diverges from the plain default pop, so it is the only spot that
  needed this fix.
- **HowLongToBeat: the (third-round) 308 redirect fix really did work** —
  concrete proof, no longer just theory: the error now reported
  is a **HTTP 404 with a real HTML body** ("HowLongToBeat - 404",
  coming from `https://howlongtobeat.com/api/s...`), no longer a bare 308
  or a connection error. This confirms that the client correctly follows
  redirects and talks to the real site. The current problem is therefore
  different and more specific: the search path used
  (derived from the JS bundle or from the historical `/api/s/` fallback) no longer exists
  on howlongtobeat.com. No new "guessed" fix on the
  path value was attempted (guessing another string with no way to
  verify it would have the same success rate as the previous attempt)
  — instead, `HltbAuth` now carries a `source` field that indicates whether the
  path used comes from the bundle extraction (with the exact value
  extracted) or from the static fallback (with the reason: bundle not found,
  regex with no match, or the entire extraction failed), included in the
  error message shown in-app. A future report will say with certainty whether the
  problem is "the historical fallback is now dead" (needs a fresh search
  for the current path, impossible from this sandbox without network
  access) or "the bundle regex is grabbing the wrong `fetch()`"
  (fixable by tightening the regex, but only with proof that this is really
  the case).

### Fix of the bundle regex: porting from an actively maintained library instead of another blind attempt

On the user's explicit suggestion ("can't you use this repo... or
this in python?"), instead of continuing to guess, I fetched via
`WebFetch` the real source of two third-party unofficial
HowLongToBeat integrations:
`ScrappyCocco/HowLongToBeat-PythonAPI` (Python, version 1.0.22, updated
in mid-2026 — so verifiably active and recent) and
`ckatzorke/howlongtobeat` (actually TypeScript, not Java as initially
assumed by the user — no problem, the code matters more than the
language).

- **Concrete cause of the previous round's 404, confirmed by comparison**:
  this client's regex that extracts the search path from the bundle
  `_app-*.js` (`fetchAuth()` in `HowLongToBeatApiClient.kt`) did not
  require that the matched `fetch(...)` specifically be a
  `POST` call — it could therefore latch onto the first `fetch("/api/...")`
  found anywhere in the bundle (e.g. an unrelated analytics/telemetry
  GET call), producing a plausible but wrong path, hence the
  404. The Python library (`HTMLRequests.py`) instead uses
  `r'fetch\s*\(\s*["\']/api/([a-zA-Z0-9_/]+)[^"\']*["\']\s*,\s*{[^}]*method:\s*["\']POST["\'][^}]*}'`
  — explicitly requiring `method: "POST"` in the same options block
  of the `fetch()`. Ported 1:1 into Kotlin as
  `SEARCH_ENDPOINT_REGEX`, not a freehand rewrite: the Python library
  is the most recent/active source found (other known integrations such as
  the TypeScript one are less recently maintained and use a
  different static endpoint, `/api/search`, with no authentication
  header — a structurally different approach from the auth-token flow
  already implemented here, not integrated so as not to mix two
  strategies without proof they are compatible).
- **Still not runnable from this sandbox** (no network access to
  `howlongtobeat.com`, unchanged limitation) — the fix is motivated
  by a verifiable and recent external source, not by a new guess,
  but still needs to be confirmed on a real device. If the 404 persists
  even with this tighter regex, the `source` field in the error
  message (see above) will tell whether the static fallback is the one now
  dead — at that point `FALLBACK_SEARCH_PATH` would also need updating,
  but only with a value verified the same way
  (a real external source), not guessed.

### TheGamesDB: 403 "Invalid API key" with a regenerated key and remaining quota

A report separate from HowLongToBeat: the online search from backlog/review
form was failing with `HTTP 403: {"code":403,"status":"Invalid API key
was provided.","remaining_monthly_allowance":0,"allowance_refresh_timer":0}`
even with a key **just regenerated** from the TheGamesDB panel, which
showed a real remaining quota (923/1000) — the two zeros in the error body
are therefore placeholder values for an unrecognized key, not a
reflection of the real quota.

- **Verified: not a regression from this session**. `git log` on the
  files in `data/thegamesdb/` shows the last touch during the "UI and
  robustness fixes after real-device verification" round (Phase 8), well before all
  the HowLongToBeat/navigation rounds of this session — no
  recent change to how the `apikey` is sent.
- **Request format confirmed correct by external comparison**: via
  `WebFetch` on `raw.githubusercontent.com` (the same approach used
  for the HowLongToBeat fix), fetched `src/thegamesdb.cpp` from
  `muldjord/skyscraper` — an actively maintained C++ scraper, one of
  the known TheGamesDB API consumers also cited in Phase 6 of this
  same file. It uses the same base URL (`https://api.thegamesdb.net/v1`),
  the same endpoint (`/Games/ByGameName`) and the same
  `&apikey=...` query-string parameter — a request structure identical to
  ours, so that is not the problem.
- **`TheGamesDbPreferences.apiKey` already does `trim()`** on the value before
  saving it (`set(value) = ... putString(KEY_API_KEY, value.trim())`),
  so a leftover space/newline from a copy-paste should not be
  the cause.
- **Diagnostics added, same principle as HowLongToBeat**:
  `TheGamesDbApiClient.ensureSuccessful()` now includes the URL of the
  failed request in the error message — `search()` calls four
  endpoints per search (`Platforms`/`Genres`/`Developers` as lookups, then
  `Games/ByGameName`), and without the URL it was not possible to know which of the
  four was failing.
- **Main suspicion, to be confirmed by the user**: given that the request
  format is verified correct and the key shows as valid in the
  TheGamesDB panel, the most likely problem is server-side
  (the regenerated key not yet propagated, or a bug in their
  validation system) — not fixable client-side. Suggested to the user
  a direct, conclusive test: opening from a mobile browser
  `https://api.thegamesdb.net/v1/Games/ByGameName?apikey=<KEY>&name=mario`
  (the same trick already used to check the quota panel) — if even
  there the same key shows as "invalid", it is unambiguously a
  TheGamesDB-side problem, not the app's (their own error page invites
  contacting support@thegamesdb.net or Discord in that case).
- **Confirmed by the user, cause isolated**: the exact same URL (with
  the same "invalid" key) tested by hand from a mobile browser **works**
  — the key is therefore valid and the endpoint is correct, the difference is
  entirely in the request headers. Main suspicion now:
  `USER_AGENT` was a string that explicitly identifies the app
  (`"ThePatientGamerHelper/1.0 (Android; ...)"`, plus the old
  repository name `GameReviewer`) instead of a browser User-Agent —
  TheGamesDB tightened its anti-bot measures in the same 02/17/2026
  policy change that made the apikey mandatory, and a misleading "invalid key"
  instead of an explicit block is a common pattern for that
  kind of filter. Fix: the same desktop Chrome `USER_AGENT` already used for
  `HowLongToBeatApiClient` (same cause, same fix, same source of
  reasoning — not a new isolated guess). To be confirmed on a real
  device: if the 403 persists even with the browser User-Agent, the
  next suspect is the `Accept: application/json` header (absent from a
  browser navigating the URL directly).

### Reviews/backlog import-export spec v2 (Tappa 1 + Tappa 2)

A dedicated spec document, `docs/reviews-backlog-import-export-spec-v2.md`
(with two fixtures under `docs/examples/`), was supplied as authoritative
— explicitly superseding prior assumptions in the code where the two
disagreed. Three real conflicts surfaced against already-shipped,
documented decisions before any code was written; all three were resolved
by asking the user rather than guessing — see
`docs/implementation-decisions.md`, "Reviews/backlog import-export spec
v2", for the full reasoning behind each. Summary of what changed:

- **Single-review Markdown export/import switched to a YAML front-matter
  format** (id/title/platforms\[\]/genres\[\]/tags/score/status/dates/
  hoursPlayed/coverImage/developer/publisher/releaseYear/metadataSource/
  externalId/linkedBacklogItemId/createdAt/updatedAt, then the same
  Pros/Cons/free-text body as before), replacing the earlier bare
  "Reddit-style" bullet-list format entirely (`ReviewMarkdownFormatter.kt`/
  `ReviewMarkdownParser.kt` deleted, replaced by
  `domain/export/ReviewBackupMarkdown.kt`). `platforms`/`genres` are
  arrays, not the singular strings the fixture happens to show for a
  review with exactly one of each — the app's data model is many-to-many
  for both, an array is the only lossless shape (confirmed with the user).
  The six new fields live on `Review`/`ReviewEntity` (`MIGRATION_4_5`, DB
  version 4→5) purely for round-trip fidelity: the create/edit form never
  edits them, `save()` always preserves whatever a review already had.
- **Single-review import moved from a library-level "always create new
  review" action to a form-level "replace form content" action**
  (`ReviewFormViewModel.importMarkdown()`, upload icon in the form's top
  bar) — this is a real behavior change the old implementation got wrong
  relative to the spec, not just a format change. The file's `id` is
  parsed (structurally required) but never applied; the review being
  edited keeps its own identity.
- **Multi-review ZIP export/import is new** (`data/export/
  ReviewZipArchive.kt`/`ReviewZipExporter.kt`/`ReviewZipImporter.kt`,
  upload/download in the library's top bar — the upload icon that used to
  trigger the old single-file import now triggers this instead).
  Content-atomic validation (any malformed `.md` blocks the whole batch,
  with every failing file name + reason reported), images always
  best-effort, upsert-by-id-from-front-matter on success.
  `ReviewRepository.upsertImported()` is the new repository method behind
  it — additive, preserves id/createdAt/updatedAt, distinct from both
  `save()` and the Drive-restore-only `replaceAll()`.
- **Backlog export/import (already shipped, Phase 8) kept its existing
  Italian-labeled, array-based schema** rather than being rewritten to
  match the fixture's English/singular-field shape (confirmed with the
  user — the fixture's JSON *structure*, lists→items→comments/history, is
  what the existing schema already follows; rewriting field names/shape
  would be a breaking change for no real gain). The one genuine addition:
  a best-effort `reviewId` round-trip (`recensioneCollegataId` in the
  export DTO, default `null` so older exported files still decode) —
  relinked on import only if a review with that id already exists on the
  importing device, `BacklogRepositoryImpl` now takes a `ReviewDao`
  dependency to check.
- **`PdfTemplateProvider` seam added** to `PdfReviewRenderer` (new
  `PdfModule` Hilt `@Binds`, interface + `NoOpPdfTemplateProvider`) — no
  real template exists yet, `currentTemplate()` always returns `null`
  today, every render still falls back to the existing plain layout.
- Also extended `domain/backup/BackupPayload.kt` (`BackupReviewDto`) with
  the same six new Review fields (default `null`, old backups still
  decode) — otherwise every Drive backup/restore cycle would have
  silently dropped them, a real regression even though Drive backup
  wasn't itself in scope for this session.
- **Not touched**: JSON/CSV whole-library export — v2 doesn't mention it,
  and there was never a JSON/CSV import path to begin with.

**Build status**: same discussion as every phase above — written and
reviewed statically, `dl.google.com` unreachable from this sandbox so no
local build was possible. Check the checks on the relevant PR before
considering it green.

## DOCX export — why it was not implemented

Explicitly removed from the roadmap (not "postponed" or "optional"): the
spec, section 5, already noted that no lightweight, mature DOCX writer
exists for Android — Apache POI depends on `java.awt` (not available on
Android) and bloats the APK, and the Kotlin wrappers out there (e.g. DocxKtm) are
still built on top of docx4j with the same kind of heavy dependencies.
The alternative of hand-writing OOXML XML via a ZIP remains a future
option, but with Markdown (readable sharing) and JSON/CSV (portable raw data)
already covered, there is no use case that makes it a priority. Do not
reconsider without an explicit request and a concrete reason.

## Build/test commands

```bash
./gradlew assembleDebug       # debug APK build
./gradlew testDebugUnitTest   # JVM unit tests (domain + repository logic)
./gradlew connectedDebugAndroidTest  # instrumented tests (requires device/emulator)
./gradlew lint                # Android Lint
```

### ⚠️ Known sandbox environment limitation

The environment this project was scaffolded in **has no network access to
`dl.google.com`** (blocked by the outbound proxy policy), so
**a full Gradle build cannot be run from this sandbox**
(the Android Gradle Plugin and the AndroidX/Compose/Room/Hilt libraries are
hosted on Google's Maven repository). Real build verification
happens via the GitHub Actions workflow in
`.github/workflows/android-ci.yml`, which runs on a runner with full
network access. **If you're working again in an isolated sandbox, check
first with `curl` whether `dl.google.com` is reachable before assuming
`./gradlew` will work.**

**Build status: green on CI** (`lintDebug`, `testDebugUnitTest`,
`assembleDebug` all pass on GitHub Actions — see PR #1 for Phase 1,
PR #2 for Phase 2, PR #3 for Phase 3, PR #4 for Phase 4). Phase 5
(this change) was written with a line-by-line static review but
**not yet verified on CI at the time of writing this note** —
check the status of the checks on the relevant PR before considering it green;
if compilation errors turn up, the new dependencies
(`androidx.datastore:datastore-preferences`, `androidx.appcompat`) and the
new manifest entries (`android:localeConfig`, the `AppLocalesMetadataHolderService`
service) are the first place to look — also watch out for `lint` on the two
`strings.xml` files: if the IT/EN keys diverge, `MissingTranslation` flags it.
The repository also has a second workflow, `build-apk.yml`, added
manually outside of these sessions: do not touch it unless needed, but
keep it in mind when checking a PR's CI status (usually several
`build-and-test` checks show up alongside a `build` check).

(Note: this paragraph describes the state as of Phase 5; Phases 6-8
were written with the same approach — static review, no
local build possible — each with its own "Build status" note in the
respective section below. Always check the checks on the most recent
PR, don't rely solely on this paragraph for the current state.)

Real bugs found only thanks to CI (none of these were visible with
a static review):
- `FlowRow` (Compose Foundation) requires an explicit
  `@OptIn(ExperimentalLayoutApi::class)` on this BOM version — the module treats missing
  opt-ins as **errors**, not warnings. If you add other experimental Compose
  APIs, keep this in mind.
- `Json.encodeToString(value)` **without** `import kotlinx.serialization.encodeToString`
  resolves to the two-argument overload (`serializer`, `value`) instead of
  the reified one-argument extension, and fails with a misleading
  type error ("No value passed for parameter 'value'"). Always import
  `kotlinx.serialization.encodeToString` explicitly when using
  `Json.encodeToString(x)` in its short form.
- `PdfDocument` under Robolectric throws `IllegalStateException` in its
  page lifecycle (see the Phase 2 section above) — not a bug in the
  application code, it is a limitation of the Robolectric shadow.
- Lint (`RemoveWorkManagerInitializer`) blocks the build if
  `Application` implements `androidx.work.Configuration.Provider` (Phase 4)
  without explicitly removing `androidx.startup.InitializationProvider`
  from the manifest — unlike what part of the WorkManager
  documentation suggests, which implies it is automatic. An explicit
  `<provider ... tools:node="remove">` is needed in
  `AndroidManifest.xml` (requires `xmlns:tools`).

What has been verified:
- Line-by-line static review of all Kotlin files (imports, package/directory
  consistency, Room @Relation/@Junction signatures, TypeConverter
  coverage, Hilt wiring) via a dedicated review sub-agent (Phase 1).
- Pure JVM unit tests (`domain/filter`, `domain/model`, `domain/export`) plus
  Room DAO tests via **Robolectric** (`data/local/ReviewDaoTest.kt`, runs as a
  JVM unit test with no need for an emulator) — run successfully in CI.
- `assembleDebug`, `lintDebug`, and `testDebugUnitTest` builds completed
  successfully in CI for both phases, one export format at a time
  (JSON/CSV → Markdown → PDF), each verified before moving on to the
  next.

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
  implementing them. Applied in Phase 3 (no charting library added)
  and in Phase 4 (hand-written Drive client instead of Google's official
  Java client, see dedicated section above) — the only dependencies added in
  Phase 4 are the explicitly requested ones (Credential Manager,
  AuthorizationClient, WorkManager) plus `googleid` and `androidx.hilt:hilt-work`,
  needed as a consequence and documented there. In Phase 5 likewise: only
  `androidx.datastore:datastore-preferences` (theme) and `androidx.appcompat`
  (per-app language), both explicitly requested. In Phase 6 **no
  dependency added**: TheGamesDB client hand-written like Drive (no
  Retrofit/Ktor despite being cited as an example in the request),
  backlog drag-to-reorder implemented with pure Compose Foundation
  (no reorder library). In Phase 8 likewise: **no dependency
  added** — HowLongToBeat client hand-written like TheGamesDB/Drive,
  grid view with `LazyVerticalGrid`/`GridCells` (already part of Compose
  Foundation, the same artifact as `LazyColumn`) and icons from
  `material-icons-extended` (already an existing dependency since Phase 1).
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
