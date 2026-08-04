> Translated from the Italian source (`docs/decisioni-implementazione.md`) —
> may lag behind updates. Italian is the source of truth for this project's
> documentation.

# Implementation decisions

This file documents technical choices made during implementation of the
various phases that weren't already spelled out in `docs/spec.md` or in
`CLAUDE.md`.

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

### Explicit `recreate()` after a language change

`AppCompatDelegate.setApplicationLocales()` on its own updates the persisted
language preference, but the automatic recalculation of resources/running
Activities that AppCompat offers is tied to the `AppCompatActivity`
lifecycle. This project uses `ComponentActivity` (pure Compose, no View
system), so an explicit `recreate()` on the current Activity was added right
after changing the language (`ui/settings/AppLanguage.kt`, the Activity
resolved from `Context` via `ContextWrapper`). Without this step the
language change would still be correctly persisted (thanks to
`autoStoreLocales`) but not visible until the user left and reopened the
app — a confusing behavior to run into, not acceptable for a switch living
in Settings.

### `ThemeMode` with Italian names, like `ReviewStatus`

`domain/model/ThemeMode.kt` uses `SISTEMA`/`CHIARO`/`SCURO` instead of
`SYSTEM`/`LIGHT`/`DARK`. This wasn't a forced choice — it's a generic
technical concept, not domain vocabulary the way a review's status is — but
staying consistent with the precedent already set by `ReviewStatus` (enum
with Italian names, separate localized labels) was preferred over
introducing two different conventions in the same codebase.

### Two `ThemeViewModel` instances, no shared scope

`ThemeViewModel` is created via `hiltViewModel()` both at the app root
(`MainActivity.GameReviewerApp`, to apply the theme) and in `SettingsScreen`
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
