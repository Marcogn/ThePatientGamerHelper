> Translated from the Italian source (`docs/decisioni-implementazione.md`) —
> may lag behind updates. Italian is the source of truth for this project's
> documentation.

# Implementation decisions

This file documents technical choices made during implementation of the
various phases that weren't already spelled out in `docs/spec.md` or in
`CLAUDE.md`.

## Phase 7 (ThePatientGamerHelper rebrand, drawer navigation, TheGamesDB search fix)

See the "Fase 7 — Rebranding, navigazione a drawer, fix ricerca
TheGamesDB" section in `CLAUDE.md` for the full architectural summary.
Only the choices that weren't obvious from the original request are
covered here.

### Renaming `applicationId`/package too, not just the display name

The request was "rename the app everywhere", which on its own could have
meant just the `app_name` string shown in the UI. I explicitly asked
whether the rename should also extend to `applicationId`/the Kotlin
package (`com.marcogn.gamereviewer` → `com.marcogn.thepatientgamerhelper`),
explaining two concrete consequences first:
- Anyone who already installed the app loses it as a "different app":
  Android treats `applicationId` as the app's identity, so a different
  `applicationId` isn't an update but a fresh install — no automatic
  migration of local data (Room database, cover images).
- The Drive OAuth client configured in Google Cloud Console (Phase 4) is
  registered for the `applicationId`+signing-certificate-SHA1 pair: a new
  `applicationId` requires a new registration. The existing one (still at
  the `[DA_COMPLETARE]` placeholder at the time of this change) isn't
  affected at runtime by this rename, but will need re-registration for the
  new `applicationId` whenever it does get configured.

Answer received: rename `applicationId`/package too. Done as a mechanical
directory move (`git mv`) + text substitution (`sed`) of
`com.marcogn.gamereviewer`→`com.marcogn.thepatientgamerhelper` and
`GameReviewer`→`ThePatientGamerHelper` across every `.kt`/`.xml`/build
script/doc file, followed by multiple static checks (brace balance,
package-declaration-vs-directory-path match, XML validity, IT/EN string key
parity) — this session's sandbox has no access to `dl.google.com`, so an
actual build could not be run to confirm.

### What was deliberately left un-renamed

- **The GitHub repository name** (`Marcogn/GameReviewer`): not explicitly
  requested, and renaming a repository has a blast radius beyond the code
  (existing links, CI integrations, forks) — out of scope for a request
  that talked about the "app name", not the repository hosting it.
- **The `recensioni-videogiochi-` prefix in `domain/export/ExportFileNaming.kt`**
  (exported JSON/CSV/PDF file names): describes the *content* of the
  exported file ("video game reviews"), it isn't derived from the app
  name — consistent with the Phase 5 choice to keep `domain/export` labels
  fixed in Italian regardless of the app's display name.
- **The HTTP User-Agent string in `TheGamesDbApiClient`**: the blanket text
  substitution (`GameReviewer`→`ThePatientGamerHelper`) would also have
  corrupted the GitHub repository URL embedded there
  (`github.com/Marcogn/GameReviewer`, not renamed — see above), turning it
  into a URL that points nowhere. Caught before running the `sed` and
  manually restored to the correct value afterward.

### Room database name not catchable by `sed`

`DATABASE_NAME` in `ThePatientGamerHelperDatabase.kt` was
`"game_reviewer.db"` (snake_case), not matched by the `sed` patterns used
for `com.marcogn.gamereviewer`/`GameReviewer` (different casing). Renamed
by hand to `"the_patient_gamer_helper.db"`. **Note**: combined with the
`applicationId` change, this means an existing install (with the old
`applicationId`) is untouched by this rename either way — it's literally a
different app as far as Android is concerned, so there's no filesystem
migration path to handle here.

### Navigation: side drawer (hamburger) instead of top-bar icons

The request explicitly described the desired mechanism ("side drawer
opened via a top-left hamburger"), so this wasn't a choice between
alternatives but a direct implementation: `ModalNavigationDrawer`
(Material 3 Compose) wraps the entire `NavHost`, with drawer state
(`rememberDrawerState`) hoisted at the navigation graph level — each
screen only receives an `onMenuClick` lambda that opens the drawer, not
the drawer state itself. Drawer items (Reviews/Backlog/Statistics +
divider + Settings) navigate with `popUpTo(Destination.Home) { saveState = true }`
+ `launchSingleTop = true` + `restoreState = true`, the standard pattern
Google recommends for drawer/bottom-bar-style navigation (avoids piling up
a deep backstack when repeatedly switching between the same 3-4 main
destinations).

`Destination.Settings` stays reachable only from the drawer, with a
"back" arrow (not the hamburger) in its own top bar — it's a destination
"at the bottom", not one of the three main sections freely switched
between, consistent with how the user described it.

### New Home screen as a chooser, not an automatic redirect

The request asked for a "what do you want to do?" screen with 3 choices,
distinct from the library which used to be the initial screen. I added
`Destination.Home` as the navigation graph's new `startDestination` (the
library/`Destination.Library` is no longer the first screen shown on app
open) instead of, say, remembering the last-visited section and reopening
it directly: the user explicitly asked for a "what do you want to do?"
entry point, which would lose its purpose if the app jumped elsewhere
automatically. No persisted "last section used" state — consistent with
"don't introduce unrequested features/state" already followed in earlier
phases.

### TheGamesDB search fix: the visible cause was a generic message, not necessarily the only bug

The reported symptom ("search always fails, unclear why") has one certain,
statically diagnosable cause: `GameMetadataSearchCoordinator.search()`
caught any exception (network error, non-2xx HTTP, JSON parsing) and
always replaced it with the same generic text (`R.string.game_search_failed`),
discarding the exception's real message. Fixed by logging the full
exception (`Log.w`, visible in Logcat) and **appending** the exception's
message (when present) to the generic text shown in the dialog, instead of
replacing it — so a future failure shows both the reassuring generic text
and the technical detail useful for diagnosis (e.g. "HTTP 401: ..." for an
invalid key).

This sandbox has no network access to `api.thegamesdb.net` (explicitly
blocked by the outbound proxy policy, confirmed via `/__agentproxy/status`
before ruling out a transient issue), so it wasn't possible to reproduce
the original failure or conclusively confirm the specific underlying
cause. Plausible defensive fixes based on research (not direct
reproduction) were applied alongside fixing the swallowed exception:
- Missing `Accept: application/json` header and explicit connect/read
  timeouts on the connection — previously absent; some REST endpoints
  respond with an unexpected content type or hang indefinitely without an
  explicit timeout.
- The `"platform"` field in the `fields` list requested from
  `Games/ByGameName` doesn't appear to be a valid field for that endpoint
  per the TheGamesDB documentation consulted — removed from the list.
- The platform filter in the query (`filter[platform]`) uses the indexed
  array syntax typical of PHP/Laravel APIs (`filter[platform][0]=`), not
  the unindexed form used previously — TheGamesDB is built on Laravel.

**The fix that certainly resolves the reported symptom** is the first one
(the swallowed message): even if the defensive fixes above turn out not to
address the real cause, the next failure will now show a diagnosable
message instead of the same opaque text, making further diagnosis possible
without whoever writes the code needing direct API access.

## Phase 6 (Trackable backlog and TheGamesDB metadata fetch)

See the "Fase 6 — Backlog tracciabile e fetch metadati (TheGamesDB)"
section in `CLAUDE.md` for the full architectural summary. Only the
choices that weren't obvious from the original request are covered here.

### The TheGamesDB API key: verify before assuming

The original request contained an explicit assumption ("shouldn't be
necessary for gamedb, it isn't for ES-DE") with the instruction to verify
it before implementing a pointless placeholder. The verification (search on
the official forum and on the changelogs of open-source scrapers like
Skyscraper and sselph/scraper) gave the opposite result: **as of
2026-02-17 TheGamesDB requires an `apikey` on every endpoint**, public or
private — anonymous access no longer exists. ES-DE and Skyscraper don't ask
the end user for a *personal* key, but they embed a shared public key
(rate-limited per IP) in their own source code — the key is there anyway,
the scraper's end user just doesn't see it.

Having no reliable way to recover the literal value of that shared public
key from the search results available (the main site's pages weren't
reachable, 403), and not wanting to paste a string found online without
certainty it's the correct/still-valid one, I **explicitly asked the user**
how to proceed instead of guessing — consistent with the session's explicit
instruction ("if something is ambiguous, stop and ask me"). Answer
received: the key must be fillable **inside the app** at runtime, no
placeholder in the build. Hence `TheGamesDbPreferences` (see below) instead
of the `[DA_COMPLETARE]` pattern already used for Drive in Phase 4.

### Why not the same pattern as `drive_config.xml`

Drive's OAuth client ID (Phase 4) is a value the user swaps **in source
code before building** (`res/values/drive_config.xml`, placeholder
`[DA_COMPLETARE]`), because it's tied to the app's own registration in
Google Cloud Console — an application-level configuration value, not an
end-user one. The TheGamesDB API key is instead personal to the account the
user registers on the site: two different users of the same APK would have
different keys. A build-time placeholder would have required rebuilding the
app on every key (or account) change, while a Settings field lets it change
without touching code — the correct pattern for a per-user value, not a
per-install one.

### Retrofit/Ktor mentioned in the request, but not added

The request mentioned Retrofit/Ktor as an example HTTP library ("if not
already present"). The project had already solved the same problem in
Phase 4 with `DriveApiClient` (`HttpURLConnection` + `kotlinx.serialization`,
zero dependencies beyond the one already present for JSON). For internal
consistency, and because CLAUDE.md explicitly asks not to add dependencies
without a real need, `TheGamesDbApiClient` was written with the same
hand-rolled pattern instead of introducing Retrofit/Ktor — four GET
endpoints (search + three id→name lookups) aren't enough to justify a full
HTTP client with its dependency chain (OkHttp/JSON converter, interceptors,
etc.), which would also end up duplicating what `DriveApiClient` already
proves works well for this project.

### `releaseYear`/`developer` on the backlog only, not on the review

The request listed "platform, genre, year, developer" as metadata to save
from online search, generically for "Step 2" (which touches both the
backlog form and the review form). Platform and genre already existed on
both models; year and developer didn't. Extending `ReviewEntity`/`Review`
— a schema with five phases of functionality already built on top of it
(JSON/CSV/PDF/Markdown export, a dedicated backup DTO, statistics
computation) — for two bibliographic fields that were never part of a
review's core (rating/pros/cons/free text) would have had a disproportionate
blast radius relative to the benefit: a new migration, new fields in every
export formatter, a new field in the backup DTO, possible impact on
statistics. Both fields were added only to
`BacklogItemEntity`/`BacklogItem`, where they make more conceptual sense
(cataloging data for a game not yet played) and where the blast radius is
contained to an entity introduced in this same session. Online search in
the review form therefore stays limited to
title/platform/genre/cover — the same field choice already used for
pre-filling from a backlog item (Step 1).

### Additive migration instead of `fallbackToDestructiveMigration()`

The app this project builds is already in real use on its developer's own
device (see the opening of `CLAUDE.md`), not a throwaway prototype. A
`fallbackToDestructiveMigration()` from database version 1 to 2 would have
silently wiped every existing review on the first launch after the update
— unacceptable. `MIGRATION_1_2` (`data/local/Migrations.kt`) was written
with raw SQL that only creates the backlog's seven new tables/indices,
without touching `reviews` or the existing lookup tables.

### Hand-rolled drag-to-reorder, gesture kept separate from the row's click

Manual reordering of items within a list was explicitly requested
("drag-to-reorder, useful for prioritizing"). Without adding a third-party
library, the simplest option would have been applying
`Modifier.pointerInput`/`detectDragGestures` to the entire clickable row —
but that same row also needs to open the item detail on tap. Overlapping a
drag detector and a `clickable` on the same element in Compose leads to
gesture-handling conflicts that aren't trivial to resolve reliably. The
drag gesture was instead isolated on a small dedicated "handle" icon next
to the row (which stays clickable to open the detail), translating the
whole row vertically through hoisted shared state
(`graphicsLayer { translationY = ... }`) while `pointerInput` stays only on
the handle. The final order is written to the repository once, when the
gesture is released (`onDragEnd`), not on every offset change during the
drag.

### Lists reordered with arrows, not drag-and-drop

Neither the spec nor CLAUDE.md specify the reorder mechanism for the lists
themselves (only for items within a list). The number of lists in a
personal backlog is typically small (a handful: "to buy", "in progress",
etc.), unlike items, which can be numerous and for which the spec
explicitly asks for drag-to-reorder ("useful for prioritizing"). For lists,
up/down buttons were chosen instead — just as functional a reorder with
much less implementation/interaction complexity, avoiding writing the same
drag logic twice for a case where the benefit (dragging instead of
pressing an arrow a couple of times) is marginal.

## Phase 5 (Internationalization, theme, documentation)

See the "Fase 5 — Internazionalizzazione, tema e documentazione" section in
`CLAUDE.md` for the full architectural summary. Only the choices that
weren't obvious from the original request are covered here.

### `ReviewStatus.label()` left untouched, new `displayName()` for the UI

The request asked to extract the strings "from the screens", not from the
content of exported files. `ReviewStatus.label()` in `domain/model` serves
both purposes, though: it's called both from the screens (to show "In
progress"/"Completed"/"Abandoned") and from `ReviewMarkdownFormatter` and
`PdfReviewRenderer` during export, where labels stay fixed in Italian so as
not to break the "domain/export is pure, no Android dependencies" pattern
already established in Phase 2.

`label()` was left unchanged (it's now used only by export) and
`ReviewStatus.displayName()` was added, a `@Composable` in
`ui/common/ReviewStatusDisplay.kt` that resolves the localized string
resource. The screens (`FilterSheet`, `ReviewListItem`, `DetailScreen`,
`ReviewFormScreen`, `StatsScreen`) all use `displayName()`. The alternative
would have been making `label()` itself language-aware, but that would have
required passing it a `Context`/Android resources, leaking the Android
dependency into `domain/export` and defeating its pure-JVM testability.

### ViewModel messages: `@ApplicationContext Context` instead of moving construction into the UI

Several ViewModels (`LibraryViewModel`, `DetailViewModel`,
`ReviewFormViewModel`, `SettingsViewModel`) build text messages (export/backup
outcomes, validation errors) that end up in a Snackbar or an error field on
screen. `stringResource()` can't be used outside a `@Composable`, so the
options were: (a) inject `@ApplicationContext Context` into the ViewModel and
call `context.getString(...)`, or (b) have the UI receive a resource
id/outcome enum and resolve the text there. Option (a) was chosen: it's the
more direct pattern, requires minimal changes to existing ViewModels (one
extra parameter in the Hilt constructor), and doesn't introduce a new
"result" type just to carry a localized string — consistent with the "no
abstractions beyond what's needed" principle already followed elsewhere in
the project.

### Language switching broken two different ways before the real fix: needs `AppCompatActivity`

The language switch was manually verified on a real device after the Phase
5 merge, and took two wrong iterations before landing on the real cause —
worth documenting both, not just the final fix.

**Attempt 1**: `MainActivity` was a plain `ComponentActivity` (pure Compose,
no View system). Since the automatic recalculation of resources/running
Activities that AppCompat reliably offers is tied to the `AppCompatActivity`
lifecycle, `applyAppLanguage()` always called an explicit `recreate()` on
the current Activity right after `setApplicationLocales()` (the Activity
resolved from `Context` via `ContextWrapper`). Result on a real device (API
33+): selecting a language showed the "Drive not configured" error (see
note below) and, going back and reopening the app, the UI stayed stuck on a
solid-colored screen, no longer responsive to touch.

**Attempt 2**: (wrong) hypothesis that the freeze was caused by two
`recreate()` calls racing each other — the OS's own (which handles the
language change as a real configuration change and recreates foreground
activities on its own from Android 13 onward) plus the manual one. Fix
applied: gate the explicit `recreate()` on
`Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU`. Result on the real
device: the freeze went away, but the language switch stopped working
altogether — no error, no reaction, the UI stayed in Italian no matter
what. The symptom, by elimination, disproved the double-`recreate()`
hypothesis.

**Real cause**: the official Android documentation is explicit — *"If
you're using Compose with setApplicationLocales, you must extend your
activity from AppCompatActivity. Otherwise, setting the app locale won't
work."* `ComponentActivity` doesn't just have "less reliable" support for
language switching under Compose — **it doesn't work at all**, because it's
missing the hook that carries the new configuration (the locale) through to
Compose's recomposition mechanism. Manually calling `recreate()` on a
`ComponentActivity` in this state doesn't fix the root problem — it
recreates the Activity, but without the resolved configuration reflecting
the new language, leaving the app in an inconsistent state (hence the freeze
seen in Attempt 1).

**Final fix**: `MainActivity` now extends `AppCompatActivity` (not
`ComponentActivity`) — it's still pure Compose, `setContent {}` remains the
only UI entry point, no XML layout introduced. `AppCompatActivity` requires
an Android theme descending from `Theme.AppCompat` (otherwise it throws at
runtime): the theme in `res/values/themes.xml`, previously
`android:Theme.Material.Light.NoActionBar`, is now
`Theme.AppCompat.DayNight.NoActionBar`. With this base class,
`setApplicationLocales()` triggers the correct Activity recreation on its
own, both on API 33+ and below — `applyAppLanguage()` in
`ui/settings/AppLanguage.kt` just calls `setApplicationLocales()`, no manual
`recreate()`, no API-level branching.

Note (superseded by Phase 7): at the time of diagnosis, a language change
that triggered a real Activity recreation also restarted the automatic
backup loading on the Settings screen from scratch, bringing back the
"Drive not configured" message — not an effect of the language change
itself, just the same `LaunchedEffect(Unit)` already present since Phase 4
any time the screen got recomposed. Phase 7's rework of the backup UI
(explicit Google sign-in, no automatic loading before sign-in) removed this
side effect.

### `ThemeMode` with Italian names, like `ReviewStatus`

`domain/model/ThemeMode.kt` uses `SISTEMA`/`CHIARO`/`SCURO` instead of
`SYSTEM`/`LIGHT`/`DARK`. This wasn't a forced choice — it's a generic
technical concept, not domain vocabulary the way a review's status is — but
staying consistent with the precedent already set by `ReviewStatus` (enum
with Italian names, separate localized labels) was preferred over
introducing two different conventions in the same codebase.

### Two `ThemeViewModel` instances, no shared scope

`ThemeViewModel` is created via `hiltViewModel()` both at the app root
(`MainActivity.ThePatientGamerHelperApp`, to apply the theme) and in `SettingsScreen`
(to show/change the selection) — two distinct `ViewModel` instances, with
different scopes (Activity vs the Settings route's backstack entry). No
shared instance was introduced at the navigation graph level:
`ThemePreferences.themeMode` is a `Flow` read from `DataStore`, which remains
the real single source of truth, so the two instances converge on the same
state regardless, without needing a shared scope — the same principle
already used for Room/`Flow` elsewhere in the app.

## Phase 4 (Google Drive cloud backup)

See the "Fase 4 — Backup cloud Google Drive" section in `CLAUDE.md` for the
full architectural summary (authentication, backup format, periodic worker,
UI). Only the choices that weren't obvious from the original request are
covered here.

### `domain/backup` kept separate from `domain/export`

The request talked generically about "the full JSON of the data" for the
backup, and Phase 2 already had a JSON export format
(`domain/export/ReviewExportDto.kt`). It was **not reused**; a dedicated
backup DTO was created instead (`domain/backup/BackupReviewDto.kt`), for two
concrete reasons, not just separation for its own sake:
- The Phase 2 export DTO has `copertina` (cover) as an **absolute path on
  the device** (`context.filesDir/covers/<uuid>.jpg`) — correct for an
  export the user downloads and reads, but useless for a restore on a
  different install (different path, maybe a different device). The backup
  DTO instead only carries the cover's **file name**, resolved to a new path
  at restore time.
- The export DTO's labels are in Italian, meant to be readable by whoever
  opens the exported JSON; the backup format is internal and doesn't need
  that constraint, nor should it be coupled to the evolution of the export
  format (it already has its own `schemaVersion` specifically so it can
  change in the future without touching the user-facing export, and vice
  versa).

### Restore as a full overwrite, not a merge

Explicitly requested ("no merge/conflict handling needed... a full overwrite
on restore is fine"). Implemented with a new method
`ReviewRepository.replaceAll(reviews: List<Review>)`, distinct from
`save()`:
- `save()` is meant for the create/edit form: it generates a new id if
  missing, sets `createdAt` to "now" for new reviews and preserves it for
  edits by looking it up on the existing row.
- A restore instead needs to **preserve exactly** `id`/`createdAt`/`updatedAt`
  from the backup — reusing `save()` would have meant that, after deleting
  the existing reviews (necessary for the overwrite), looking up the
  previous `createdAt` on the (by then deleted) row would fail, losing the
  original creation date for every restored review. `replaceAll()` writes
  the Room entity directly with the backup's timestamps, in a single
  transaction that also clears the lookup tables (platform/genre/tag)
  before recreating them — otherwise repeated restores would accumulate
  orphaned lookup entries no longer referenced by any review, polluting
  autocomplete.
- The name→id lookup resolution logic and cross-ref/pro-con writing was
  duplicated between `save()` and the first draft of `replaceAll()`:
  extracted into a shared private method (`writeRelations`) on
  `ReviewRepositoryImpl`.

### Drive client written by hand, not Google's official Java client

The request specified "Drive REST API v3" (not "Google API Client Library
for Java"), and CLAUDE.md explicitly asks not to add dependencies without a
real need. `google-api-client-android` + `google-api-services-drive` are the
official libraries but heavy (they bring in Guava and a wide dependency
graph) for just three endpoints (multipart upload, list, download by id). A
minimal client was written with `java.net.HttpURLConnection` in
`data/drive/DriveApiClient.kt` — zero additional dependencies beyond
`kotlinx.serialization` (already present) for parsing JSON responses.

### Automatic backup cadence not configurable

WorkManager supports a minimum interval of 15 minutes for periodic work; a
fixed daily cadence was chosen (`BackupScheduler`, 24h,
`NetworkType.CONNECTED`) without exposing a way to change it in the UI. For
a personal review app, where data changes at most a few times a day, a daily
backup is more than enough, and a UI for configuring the interval would be
unrequested complexity — the same "don't over-engineer" principle applied to
the rest of Phase 4 (restore without merge, no UI to manage/delete old
backups).

## Phase 3 (Library statistics)

The decisions below are specific to Phase 3. See also the "Fase 3 —
Statistiche libreria" section in `CLAUDE.md` for the architectural summary.

### No charting dependency added

The spec/task left the choice open between native Compose bars and Vico (if
"the added complexity is justified"). Native Compose bars were chosen
(`Box` with `fillMaxWidth(fraction = count / maxCount)` for distributions, a
segmented stacked bar for status) and **no new dependency**.

Reasoning:
- CLAUDE.md explicitly says not to add dependencies for Phase 3/4 without an
  explicit request, and to flag the need instead.
- The use case is a single-user library: the number of distinct
  platforms/genres is typically small (few platforms owned, a moderate
  number of genres), so simple horizontal bars stay readable without
  needing the scroll/zoom/interactivity that would justify a charting
  library.
- If visualization needs grow in the future (pie charts, time trends,
  interactive drill-down), Vico remains the recommended choice to
  re-evaluate at that point, instead of continuing to extend manual
  `Canvas`/`Box` rendering.

### Percentages only on the status breakdown

The spec explicitly asks for a "completed/abandoned/in progress percentage"
but doesn't ask for percentages on the platform/genre distributions. This
isn't an omission: platform and genre are many-to-many relationships with
the review (a review can have multiple platforms and multiple genres),
while status is a single field (enum, one value per review).

Computing a percentage for platform/genre by dividing by the total review
count would mean the displayed percentages wouldn't add up to 100% (a
multi-platform review would be counted more than once), which would be
misleading for anyone reading the screen and expecting a 100% total the way
status does. Platform/genre distributions are therefore shown only as
absolute counts (with a bar proportional to the largest value in the set),
keeping the percentage only where the data is single-choice and the
percentage is mathematically coherent.

### Data structure for the aggregations

- `domain/model/LibraryStatistics.kt`: pure models (`LibraryStatistics`,
  `DistributionEntry`, `StatusShare`), no Android dependency.
- `domain/stats/LibraryStatisticsCalculator.kt`: pure function
  `computeLibraryStatistics(List<Review>): LibraryStatistics`, same pattern
  as `domain/filter/LibraryFiltering.kt` — unit-testable in pure JVM without
  the Android SDK or Robolectric (see
  `domain/stats/LibraryStatisticsCalculatorTest.kt`).
- Distributions are sorted by descending count (then by name for ties) —
  same "most frequent on top" heuristic for both platform and genre.
- "total tracked hours" sums `hoursPlayed`, treating `null` (optional field)
  as 0 rather than excluding the review from the total count.
- "average rating" is `Double?` (not `Double`) to explicitly distinguish "0
  reviews" (no average rating, shown as "—" in the UI) from a hypothetical
  average of 0.0.

### UI and navigation

- New `Destination.Stats` route (parameterless object, consistent with
  `Destination.Library`), reachable from an icon (`Icons.Filled.BarChart`)
  in the library's top bar, next to filters/sort/export.
- `ui/stats/StatsScreen.kt` + `StatsViewModel` + `StatsUiState`: same
  MVVM/UDF pattern as the other screens (`ui/library`, `ui/detail`), with
  `ReviewRepository.observeAll()` as the single data source (no mocking).
