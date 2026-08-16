# Implementation decisions

This file documents the technical choices made during the implementation
of the various phases that were not already spelled out in `docs/spec.md`
or in `CLAUDE.md`.

## Phase 8 (Markdown import, backlog export/import, HowLongToBeat, grid views)

See the "Phase 8 — Markdown import, backlog export/import, HowLongToBeat,
grid views" section in `CLAUDE.md` for the full architectural summary.
Only the choices that weren't obvious from the original request are
covered here.

### Markdown import as the exact reverse of export, not a new format

The request was "import reviews in markdown format". Instead of inventing
a proprietary import format, `parseReviewMarkdown()`
(`domain/export/ReviewMarkdownParser.kt`) is the exact reverse of
`toRedditMarkdown()` — same fixed Italian labels, same bullet-list
structure, same optional `## Pro`/`## Contro` sections. Reason: it's the
only Markdown format the app itself produces, so it's the only one for
which an export→import roundtrip is guaranteed without ambiguity. A
"generic" Markdown parser (compatible with any hand-written markdown)
would have required much more permissive heuristics and edge cases not
specified by the request. The parser is strict on the fields the exporter
always writes (title/rating/status/start date — a file without these is
not a review written by this app) and permissive on everything else
(platforms/genres/tags/hours/pros/cons/body), exactly mirroring what
`toRedditMarkdown` omits when empty. Parsing errors return a specific
message ("Rating missing or invalid", etc.) shown in the library's
snackbar, not a generic error.

### Backlog export/import: format separate from `domain/backup`, always additive

`domain/backup` (Phase 4) is the roundtrip format for the Drive backup of
the entire review library, with restore as a full overwrite (single-user,
"a backup is a backup of the entire state"). Backlog export/import is
conceptually different: it's a file the user explicitly creates/opens via
SAF to share or merge their backlog (e.g. between two devices, or to send
to someone), not a safety restore — so it makes sense for it to be
**always additive**: every imported list becomes a new list, every item a
new item with a new id, never an overwrite. Importing the same file twice
duplicates the data (it's not idempotent) — an accepted trade-off to stay
simple, consistent with the "single-user, don't over-engineer" approach
already followed elsewhere: implementing a merge by title/similarity would
have introduced ambiguity (two games with the same name on different
platforms?) without the request explicitly asking for it. `reviewId` is
discarded on import: the linked review belongs to the library that
exported the file and might not exist on this device. The format is still
a zip (data + covers), same schema as `data/backup/BackupArchive.kt`, but
with its own DTOs (`domain/export/BacklogExportDto.kt`) and without
touching `domain/backup` — two formats, two independent evolutions, just
like `domain/export` (Phase 2) and `domain/backup` (Phase 4) are already
kept separate.

### HowLongToBeat: no public API, reverse-engineered technique verified only through research

As explicitly required by CLAUDE.md ("verify before assuming", already
applied in Phase 6 for TheGamesDB), I checked online before implementing:
**HowLongToBeat has never had a public API**, unlike TheGamesDB (which at
least requires an apikey but remains a documented endpoint). Every
existing unofficial library (howlongtobeatpy, ckatzorke/howlongtobeat,
etc.) works by re-deriving the current search endpoint from
HowLongToBeat's frontend JavaScript bundle on every session, because the
path changes on every one of their deploys — there's no stable contract to
implement against. `HowLongToBeatApiClient` (`data/howlongtobeat/`) uses
the same documented technique (fetch the homepage, extract the `_app-*.js`
bundle, regex on the POST endpoint, with a fallback to the historically
stable path `/api/s/` if extraction fails). **This sandbox has no network
access to `howlongtobeat.com`** (same known limitation already documented
for `dl.google.com`/`api.thegamesdb.net`, confirmed again in this session
— see below), so the client was not run against the real site: it's
written and reviewed statically, but **must be considered unverified until
tested on a real device**. Every failure (bundle changed, endpoint
blocked, different response schema) is caught and turned into `null` by
`GameMetadataSearchCoordinator.searchHowLongToBeat()` — never a propagated
exception, never a block on the existing "search online" flow, consistent
with `downloadCoverLocally()`, which already does the same for the cover
image.

### HowLongToBeat fields only on `BacklogItem`, same precedent as `releaseYear`/`developer`

Same choice already justified in Phase 6 for year/developer: they are
cataloguing metadata, not part of the core of a review (rating/pros/cons/
text), and adding them to `Review` would have required touching JSON/CSV/
PDF/Markdown export and the backup DTOs for fields the request explicitly
ties to the backlog ("when I add a game to the backlog"). Online search in
the review form is therefore left unchanged: it doesn't call
`searchHowLongToBeat()`, only `BacklogItemFormViewModel` does, after the
user has chosen a TheGamesDB result (the query uses the exact title of the
chosen result, not the typed text, for maximum precision).

### List/grid views: `SharedPreferences`, not `DataStore`; no drag-to-reorder in grid

Just two persisted flags (library view, backlog view) — same minimal
principle already applied to `BackupPreferences`/`TheGamesDbPreferences` in
Phase 4/6, not the `DataStore` pattern used for `ThemeMode` (there the
explicit request was DataStore). The grid view in
`BacklogListDetailScreen` **does not support manual drag-to-reorder**
(Phase 6, Stage 1): extending the vertical drag gesture to a 2D grid would
have required substantially different positioning logic for a purely
cosmetic benefit — the user can switch back to the list view to reorder.
The toggle is shared between library and backlog
(`ui/common/ViewModeToggle.kt`, `ui/common/GameGridTile.kt`) to avoid
duplicating the same UI twice.

**Build status**: as with previous phases, this change was written and
reviewed statically line by line (parenthesis balancing, imports, field
name consistency between entities/DTOs/mappers, 1:1 parity of `strings.xml`
IT/EN keys) but **not yet verified on CI at the time of writing this
note**. In this session I also personally checked whether the sandbox had
broader network access than usual (some Google hosts responded):
`maven.google.com` responds, but actually downloading Android Gradle
Plugin artifacts is still blocked by the outbound proxy (redirect to a
host not in the allowlist) — same limitation already documented in
CLAUDE.md, just confirmed with a direct test instead of assumed. Check the
status of the PR checks before considering it green, and manually verify
on device/emulator both the Markdown import fix and — above all — the
HowLongToBeat integration, which is the part with the highest fragility
risk in this change.

### Fixes after real-device verification (see CLAUDE.md, same section, for the full detail)

Four real issues found testing the app on a device after the merge: the
"Abandoned" `FilterChip` broken character by character (fix: `FlowRow`
instead of `Row`), top bar titles wrapping to two lines (fix:
`maxLines = 1` + ellipsis everywhere, not just where reported), TheGamesDB
search that always failed with an unreadable JSON error when a game had
explicitly `null` `developers`/`genres` in the response (fix: fields made
nullable in the DTO + `coerceInputValues = true`), and HowLongToBeat being
completely absent because the client only implemented the bare search POST
without the authentication headers (`x-auth-token`/`x-hp-key`/`x-hp-val`)
that currently maintained unofficial libraries require — rewritten to
implement the entire homepage→bundle→endpoint→init→search flow, with
diagnostic logging at every step (previously it failed in complete
silence, with no way to understand why). The risk remains, explicitly not
ruled out, that the site is behind anti-bot protections that no
`HttpURLConnection` client can overcome — see CLAUDE.md for detail.

### Second device verification (see CLAUDE.md, same section, for the full detail)

HowLongToBeat was still absent after the first fix, with no way for the
user to read the cause: `searchHowLongToBeat()` now returns a typed
outcome (found/no match/error with message) shown directly in the form
instead of only logged — diagnosable without `adb`. The "complete item →
write a review" flow applied status and prompt on every single tap on the
chip; now the status is an uncommitted local selection with an explicit
"Save" button, and the pre-filled review form now opens already set to
"Completed" instead of the default "In progress". The back button from
that form saves the draft (if it has at least a title) and navigates to
Reviews instead of returning to the backlog. The grid view uses
`LazyVerticalStaggeredGrid` instead of `LazyVerticalGrid`, and covers no
longer have a forced `aspectRatio`: real proportions, no wasted space
between square and vertical covers.

### Third device verification (see CLAUDE.md, same section, for the full detail)

The diagnostics from the previous round worked: the user reported the
exact error, "HTTP 308", identical for every title. Real cause:
`HttpURLConnection` does not reliably follow redirects on POST requests,
and has known gaps specifically around code 308. Fix:
`HowLongToBeatApiClient` now follows redirects manually (up to 5 hops),
re-issuing the same request (method, headers, body) toward the resolved
URL — behavior required by 307/308, and the safest choice for the other
3xx codes in this context too.

### Fourth device verification (see CLAUDE.md, same section, for the full detail)

Two reports: reviews created from the backlog flow were duplicating on
every new attempt (because there was no way to reopen a review already
linked to an item, only to create another empty one — fix: "Review
linked" is now a clickable link that opens the existing review), and
HowLongToBeat still returns "HTTP 308" despite the previous round's manual
redirect fix — not resolved, no second "blind" fix was attempted: instead,
the error messages now include the URL that failed, for a targeted
diagnosis on the next report.

### Automatic move into "Completed with/without review" lists (see CLAUDE.md, same section, for the full detail)

A completed item now automatically "disappears" (with a notice before the
move) into one of two app-managed lists — "Completed with review" if the
review is written, "Completed awaiting review" if the user answers "No" to
the prompt. Also used the opportunity to disable the "move to list" icon
when there's no other list to choose from (previously the tap silently
opened an empty menu, which looked like nothing was happening).

Two revisions after user feedback: (1) answering "No" left no way to write
the review afterward — added a persistent "Write a review" link whenever
the item is Completed without a linked review, from whichever list it's
in; (2) the names of the two lists were initially fixed Kotlin constants
in Italian to avoid duplicates on a language change — replaced with
`BacklogListEntity.systemKind` (a new nullable column, additive
migration) as a stable identifier for the match, while the displayed name
is resolved from the app's current language at creation time, without
fragmenting the list or always showing only Italian.

### Fifth device verification (see CLAUDE.md, same section, for the full detail)

Review duplication kept happening for the same game: real cause, the
system back gesture (swipe/hardware button) completely bypassed
`onBackPressed()` — only the top-left back arrow invoked it. Fix:
`BackHandler` in `ReviewFormScreen`. HowLongToBeat now returns a real HTTP
404 from howlongtobeat.com (proof the 308 redirect fix worked) instead of
a network error — the search path used is, however, wrong/stale. Added
diagnostics (`source` on `HltbAuth`) to distinguish whether the path comes
from the JS bundle or the static fallback, instead of another blind
attempt.

### HowLongToBeat bundle-regex fix: ported from an actively maintained external library (see CLAUDE.md, same section, for the full detail)

On the user's suggestion, I fetched via `WebFetch` the real source of
`ScrappyCocco/HowLongToBeat-PythonAPI` (actively maintained, recent
version) and `ckatzorke/howlongtobeat` (TypeScript). The Python regex that
extracts the search path from the bundle explicitly requires
`method: "POST"` in the same `fetch(...)` block — mine didn't require it,
so it could latch onto the wrong `fetch()` in the bundle, explaining the
404 from the previous round. Ported 1:1 into Kotlin, not a from-scratch
rewrite.

### TheGamesDB: 403 "Invalid API key" despite a regenerated key (see CLAUDE.md, same section, for the full detail)

Report independent of HowLongToBeat. Confirmed via `git log` that it's not
a regression from this session (no recent touch to `data/thegamesdb/`),
and confirmed via `WebFetch` on the source of `muldjord/skyscraper` that
the request format (base URL, endpoint, `apikey` parameter) is identical
to that of an actively maintained scraper — not a client-side bug. Added
the same URL diagnostics already used for HowLongToBeat. Main suspicion:
a server-side TheGamesDB issue, to be confirmed with a direct browser
test.

Confirmed by the user: the same URL with the same key works from a
browser, isolating the cause to the request headers. Fix: `USER_AGENT`
was a string that explicitly identifies the app instead of a
browser-style User-Agent — same fix already applied to
`HowLongToBeatApiClient` for the same reason (TheGamesDB tightened
anti-bot measures in the same policy change that made the apikey
mandatory).

## Phase 7 (ThePatientGamerHelper rebranding, drawer navigation, TheGamesDB search fix)

See the "Phase 7 — Rebranding, drawer navigation, TheGamesDB search fix"
section in `CLAUDE.md` for the full architectural summary. Only the
choices that weren't obvious from the original request are covered here.

### Also renaming `applicationId`/package, not just the display name

The request was "change the app name everywhere", which on its own could
have meant just the `app_name` string shown in the UI. I explicitly asked
the user whether the change should also extend to
`applicationId`/Kotlin package (`com.marcogn.gamereviewer` →
`com.marcogn.thepatientgamerhelper`), explaining the two concrete
consequences before proceeding:
- Anyone who already installed the app loses it as a "different app":
  Android treats `applicationId` as the app's identity, a different
  `applicationId` is not an update but a new install — no automatic
  migration of local data (Room database, cover images).
- The Drive OAuth client configured in Google Cloud Console (Phase 4) is
  registered for the `applicationId`+signing-certificate-SHA1 pair: a new
  `applicationId` requires a new registration; the existing one (still at
  the `[TO_COMPLETE]` placeholder at the time of this change) isn't
  affected by this change at runtime either way, but if it's configured
  down the line it will need to be redone for the new `applicationId`.

Answer received: also rename `applicationId`/package. Carried out as a
mechanical directory move (`git mv`) + text substitution (`sed`) of
`com.marcogn.gamereviewer`→`com.marcogn.thepatientgamerhelper` and
`GameReviewer`→`ThePatientGamerHelper` across all `.kt`/`.xml`/build
script/documentation files, followed by multiple static checks
(parenthesis balancing, package/directory path match, XML validity,
IT/EN string key parity) — this same session's sandbox has no access to
`dl.google.com`, so it was not possible to build to verify.

### What was deliberately left un-renamed

- **The GitHub repository name** (`Marcogn/GameReviewer`): not explicitly
  requested, and renaming a repository has a blast radius beyond the code
  (existing links, CI integrations, forks) — out of scope for a request
  that talked about the "app name", not the repository hosting it.
- **The `recensioni-videogiochi-` prefix in
  `domain/export/ExportFileNaming.kt`** (name of files exported from
  JSON/CSV/PDF): it describes the *content* of the exported file ("video
  game reviews"), it doesn't derive from the application name — stays
  consistent with the Phase 5 choice to leave `domain/export` labels fixed
  in Italian regardless of the app.
- **The HTTP user name in `TheGamesDbApiClient`'s `User-Agent`**: the
  bulk text substitution (`GameReviewer`→`ThePatientGamerHelper`) would
  have also corrupted the GitHub repository URL included there
  (`github.com/Marcogn/GameReviewer`, not renamed — see point above),
  turning it into a URL that doesn't exist. Spotted before running the
  `sed` and manually restored to the correct value afterward.

### Room database name not renameable via `sed`

`DATABASE_NAME` in `ThePatientGamerHelperDatabase.kt` was
`"game_reviewer.db"` (snake_case), not caught by the `sed` patterns used
for `com.marcogn.gamereviewer`/`GameReviewer` (different casing). Renamed
by hand to `"the_patient_gamer_helper.db"`. **Note**: this, combined with
the `applicationId` change, means an existing install (with the old
`applicationId`) is not touched by this rename either way — it's
literally a different app in Android's eyes, so there's no filesystem
migration path to handle here.

### Navigation: side drawer (hamburger) instead of top-bar icons

The request explicitly described the desired mechanism ("side menu on the
left, opened from a hamburger icon top-left"), so this wasn't a choice
among alternatives but a direct implementation: `ModalNavigationDrawer`
(Material 3 Compose) wrapping the entire `NavHost`, with the drawer state
(`rememberDrawerState`) lifted to the navigation graph level — each screen
only receives an `onMenuClick` lambda that opens the drawer, not the
drawer state itself. The drawer entries (Reviews/Backlog/Statistics +
separator + Settings) navigate with
`popUpTo(Destination.Home) { saveState = true }` + `launchSingleTop = true`
+ `restoreState = true`, the standard pattern Google recommends for
drawer/bottom-bar style navigation (avoids accumulating a deep backstack
when repeatedly switching between the same 3-4 main destinations).

`Destination.Settings` remains reachable only from the drawer, with a
"back" arrow (not the hamburger) in its own top bar — it's a "bottom of
the list" destination, not one of the three main sections you freely
jump between, consistent with how the user described it ("with settings
at the bottom").

### New Home screen as a chooser, not an automatic redirect

The request asked for a "what do you want to do?" screen with 3 choices,
distinct from the library that previously was the initial screen. I added
`Destination.Home` as the navigation graph's new `startDestination` (the
library/`Destination.Library` is no longer the first screen shown when
opening the app) instead of, for instance, remembering the last visited
section and reopening it directly: the user explicitly asked for a "what
do you want to do?" entry point, which would lose its meaning if the app
automatically jumped elsewhere. No persisted state for "last section
used" — consistent with "don't introduce unrequested features/state"
already followed in previous phases.

### TheGamesDB search fix: the visible cause was a generic message, not necessarily the only bug

The reported symptom ("search always 'failed', no way to tell why") has a
certain, statically diagnosable cause:
`GameMetadataSearchCoordinator.search()` caught any exception (network
error, non-2xx HTTP, JSON parsing) and always replaced it with the same
generic text (`R.string.game_search_failed`), discarding the exception's
real message. Fixed by logging the full exception (`Log.w`, visible in
Logcat) and **appending** the exception's message (when present) to the
generic text shown in the dialog, instead of replacing it — so a future
failure shows both the reassuring generic message and the technical
detail useful for diagnosis (e.g. "HTTP 401: ..." for an invalid key).

This sandbox has no network access to `api.thegamesdb.net` (explicitly
blocked by the outbound proxy policy, verified with
`/__agentproxy/status` before ruling out a temporary issue), so it was not
possible to reproduce the original failure nor definitively verify the
specific underlying cause. I still applied plausible defensive fixes based
on research (not on direct reproduction) while fixing the swallowed
exception:
- Missing `Accept: application/json` header and explicit timeouts
  (connect/read) on the connection — previously absent, some REST
  endpoints respond with an unexpected content-type or hang indefinitely
  without an explicit timeout.
- The `"platform"` field in the `fields` list requested to
  `Games/ByGameName` doesn't appear to be a valid field for that endpoint
  according to the TheGamesDB documentation consulted — removed from the
  list.
- The platform filter in the query (`filter[platform]`) uses the indexed
  array syntax typical of PHP/Laravel APIs (`filter[platform][0]=`), not
  the index-less form used previously — TheGamesDB is implemented in
  Laravel.

**The fix that certainly resolves the reported symptom** is the first one
(the swallowed message): even if the defensive fixes above turn out not
to hit the real cause, the next failure will now show a diagnosable
message instead of the same opaque text, making further diagnosis
possible without needing direct API access on the part of whoever is
writing the code.

## Phase 6 (Trackable backlog and TheGamesDB metadata fetch)

See the "Phase 6 — Trackable backlog and metadata fetch (TheGamesDB)"
section in `CLAUDE.md` for the full architectural summary. Only the
choices that weren't obvious from the original request are covered here.

### The TheGamesDB API key: verify before assuming

The initial request contained an explicit assumption ("shouldn't be
necessary for gamedb, it isn't for esde") with the instruction to verify
it before implementing a useless placeholder. Verification (research on
the official forum and on open-source scraper changelogs like Skyscraper
and sselph/scraper) gave the opposite result: **as of 02/17/2026 TheGamesDB
requires an `apikey` on every endpoint**, public or private — anonymous
access no longer exists. ES-DE and Skyscraper don't ask the end user for a
*personal* key, but embed a shared public key (rate-limited per IP) in
their own source code — the key still exists, the scraper's end user just
doesn't see it.

Having no reliable way to retrieve the literal value of that shared public
key from the available search results (main site pages unreachable, 403),
and not wanting to paste a string found online without certainty it's the
correct/still-valid one, I **explicitly asked the user** how to proceed
instead of guessing — consistent with the session's explicit instruction
("if something is ambiguous, stop and ask me"). Answer received: the key
must be fillable **inside the app** at runtime, no placeholder in the
build. Hence `TheGamesDbPreferences` (see below) instead of the
`[TO_COMPLETE]` pattern already used for Drive in Phase 4.

### Why not the same pattern as `drive_config.xml`

The Drive OAuth client id (Phase 4) is a value the user replaces **in
source code before the build** (`res/values/drive_config.xml`, placeholder
`[TO_COMPLETE]`), because it's tied to the app's registration on Google
Cloud Console — an application-level configuration value, not an
end-user one. The TheGamesDB API key is instead personal to the account
the user registers on the site: two different users of the same APK would
have different keys. A build placeholder would therefore have required
rebuilding the app on every key (or account) change, while a Settings
field allows changing it without touching the code — the more correct
pattern for a per-user, not per-install, value.

### Retrofit/Ktor mentioned in the request, but not added

The request mentioned Retrofit/Ktor as an example HTTP library "if not
already present". The project had already solved the same problem in
Phase 4 with `DriveApiClient` (`HttpURLConnection` + `kotlinx.serialization`,
zero additional dependencies beyond the one already present for JSON). For
internal consistency and because CLAUDE.md explicitly asks not to add
dependencies without real need, I wrote `TheGamesDbApiClient` with the
same hand-rolled pattern instead of introducing Retrofit/Ktor — four GET
endpoints (search + three id→name lookups) aren't enough to justify an
entire HTTP client with its dependency chain (OkHttp/JSON converter,
interceptors, etc.), which would moreover end up duplicating what
`DriveApiClient` already demonstrates works well for this project.

### `releaseYear`/`developer` only on the backlog, not on the review

The request listed "platform, genre, year, developer" as metadata to save
from online search, generically for "Stage 2" (which touches both the
backlog form and the review form). Platform and genre already existed on
both models; year and developer didn't. Extending `ReviewEntity`/`Review`
— a schema with five phases of functionality already built on top (JSON/
CSV/PDF/Markdown export, dedicated backup DTO, statistics computation) —
for two bibliographic fields that were never part of the core of a review
(rating/pros/cons/free text) would have had a blast radius disproportionate
to the benefit: new migration, new fields in every export formatter, new
field in the backup DTO, possible impact on statistics computation. I
added the two fields only to `BacklogItemEntity`/`BacklogItem`, where they
make more conceptual sense (cataloguing data for a game not yet played)
and where the blast radius is contained to an entity introduced in this
same session. Online search in the review form therefore remains limited
to title/platform/genre/cover — the same field selection already used for
pre-filling from a backlog item (Stage 1).

### Additive migration instead of `fallbackToDestructiveMigration()`

The app introduced by this project is already in real use on the device of
whoever is developing it (see the opening of `CLAUDE.md`), not a
throwaway prototype. A `fallbackToDestructiveMigration()` from Room
database version 1 to 2 would have silently deleted all existing reviews
on the first launch after the update — unacceptable. I wrote
`MIGRATION_1_2` (`data/local/Migrations.kt`) with raw SQL that only
creates the seven new backlog tables/indices, without touching `reviews`
or the existing lookup tables.

### Hand-written drag-to-reorder, gesture separated from the row click

Manual reordering of items within a list was explicitly requested
("drag-to-reorder, useful for prioritizing"). Without adding a third-party
library, the simplest option would have been applying
`Modifier.pointerInput`/`detectDragGestures` to the whole clickable row —
but the same row must also open the item detail on tap. Overlapping a drag
detector and a `clickable` on the same element in Compose leads to
non-trivial gesture-handling conflicts that are hard to resolve reliably.
I instead isolated the drag gesture on a small dedicated "handle" icon next
to the row (which remains clickable to open the detail), translating the
whole row vertically via shared lifted state
(`graphicsLayer { translationY = ... }`) while `pointerInput` stays only
on the handle. The final order is written to the repository only once, on
gesture release (`onDragEnd`), not on every offset change during the
drag.

### Lists reordered with arrows, not drag-and-drop

Neither spec nor CLAUDE.md specify the reordering mechanism for the lists
themselves (only for items within a list). The number of lists in a
personal backlog is typically small (a handful: "to buy", "in progress",
etc.), unlike items, which can be numerous and for which the spec
explicitly asks for drag-to-reorder ("useful for prioritizing"). For lists
I chose up/down buttons — reordering just as functional with much less
implementation/interaction complexity, avoiding writing the same drag
logic twice for a use case where the benefit (being able to drag instead
of pressing an arrow a couple of times) is marginal.

## Phase 5 (Internationalization, theme, documentation)

See the "Phase 5 — Internationalization, theme and documentation" section
in `CLAUDE.md` for the full architectural summary. Only the choices that
weren't obvious from the original request are covered here.

### `ReviewStatus.label()` untouched, new `displayName()` for the UI

The request asked to extract "the screens'" strings, not the content of
exported files. `ReviewStatus.label()` in `domain/model`, however, serves
both purposes: it's called both by the screens (to show "In progress"/
"Completed"/"Abandoned") and by `ReviewMarkdownFormatter` and
`PdfReviewRenderer` during export, where the labels stay fixed in Italian
so as not to break the "pure `domain/export`, no Android dependencies"
pattern already established in Phase 2.

I left `label()` unchanged (it continues to be used only by export) and
added `ReviewStatus.displayName()`, a `@Composable` in
`ui/common/ReviewStatusDisplay.kt` that resolves the localized string
resource. The screens (`FilterSheet`, `ReviewListItem`, `DetailScreen`,
`ReviewFormScreen`, `StatsScreen`) all use `displayName()`. The
alternative would have been to make `label()` itself language-aware, but
that would have required passing it a `Context`/Android resources,
propagating the Android dependency into `domain/export` and defeating its
pure-JVM testability.

### ViewModel messages: `@ApplicationContext Context` instead of moving construction into the UI

Several ViewModels (`LibraryViewModel`, `DetailViewModel`,
`ReviewFormViewModel`, `SettingsViewModel`) build text messages (export/
backup outcomes, validation errors) that end up in a Snackbar or an
on-screen error field. `stringResource()` can't be used outside a
`@Composable`, so the alternatives were: (a) inject
`@ApplicationContext Context` into the ViewModel and call
`context.getString(...)`, or (b) bubble up a resource id/outcome enum to
the UI and resolve the text there. I chose (a): it's the most direct
pattern, requires the minimal change to existing ViewModels (one extra
parameter in the Hilt constructor) and doesn't introduce a new "result"
type just to carry a localized string — consistent with "no abstractions
beyond what's needed" already followed throughout the project.

### Language change broken in two different ways before the real fix: needs `AppCompatActivity`

The language change was manually verified on a real device after the
Phase 5 merge, and required two wrong iterations before reaching the real
cause — worth documenting both, not just the final solution.

**Attempt 1**: `MainActivity` was a plain `ComponentActivity` (Compose,
no View system). Since the automatic Activity re-creation that AppCompat
reliably offers is tied to `AppCompatActivity`'s lifecycle,
`applyAppLanguage()` always called an explicit `recreate()` on the current
Activity right after `setApplicationLocales()` (resolved from `Context`
via `ContextWrapper`). Result on a real device (API 33+): selecting a
language showed the "Drive not configured" error (see note below) and,
going back and reopening the app, the UI stayed stuck on a solid-color
screen, no longer responsive to touch.

**Attempt 2**: (wrong) hypothesis that the freeze was due to two
`recreate()` calls racing — the OS's own (which from Android 13 onward
handles a language change as a real configuration change and re-creates
foreground activities itself) plus the manual one. Fix applied:
conditioning the explicit `recreate()` on
`Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU`. Result on a real
device: the freeze disappeared, but the language change stopped working
entirely — no error, no reaction, the UI stayed in Italian at all times.
The symptom, by elimination, disproved the double-`recreate()` hypothesis.

**Real cause**: the official Android documentation is explicit — *"If
you're using Compose with setApplicationLocales, you must extend your
activity from AppCompatActivity. Otherwise, setting the app locale won't
work."* `ComponentActivity` doesn't simply have "less reliable" support
for language change under Compose: **it doesn't work at all**, because it
lacks the hook that carries the new configuration (the locale) through to
Compose's recomposition mechanism. Calling `recreate()` manually on a
`ComponentActivity` in this state doesn't fix the root problem — it
re-creates the Activity, but without the resolved configuration
reflecting the new language, leaving the app in an inconsistent state
(hence the freeze seen in Attempt 1).

**Definitive fix**: `MainActivity` now extends `AppCompatActivity` (not
`ComponentActivity`) — it's still pure Compose, `setContent {}` is still
the only UI entry point, no XML layout introduced. `AppCompatActivity`
requires an Android theme descending from `Theme.AppCompat` (otherwise it
throws a runtime exception): the theme in `res/values/themes.xml`,
previously `android:Theme.Material.Light.NoActionBar`, became
`Theme.AppCompat.DayNight.NoActionBar`. With this base class,
`setApplicationLocales()` triggers the correct Activity re-creation on its
own, both on API 33+ and below — `applyAppLanguage()` in
`ui/settings/AppLanguage.kt` just calls `setApplicationLocales()`, no
manual `recreate()`, no API level condition.

Note (superseded by Phase 7): at the time of diagnosis, a language change
that triggered a real Activity re-creation would also restart the
automatic backup loading in the Settings screen from scratch, bringing the
"Drive not configured" message back into view — not an effect of the
language change itself, just of the same `LaunchedEffect(Unit)` already
present since Phase 4 every time the screen was recomposed. The Phase 7
rework of the backup UI (explicit Google login, no automatic loading
before sign-in) eliminated this side effect.

### `ThemeMode` with Italian names, like `ReviewStatus`

`domain/model/ThemeMode.kt` uses `SISTEMA`/`CHIARO`/`SCURO` instead of
`SYSTEM`/`LIGHT`/`DARK`. This wasn't a forced choice — it's a generic
technical concept, not domain vocabulary like a review's status — but I
preferred to stay consistent with the precedent already set by
`ReviewStatus` (enum with Italian names, separate localized labels) rather
than introduce two different conventions in the same codebase.

### Two `ThemeViewModel` instances, no shared scope

`ThemeViewModel` is created via `hiltViewModel()` both at the app root
(`MainActivity.ThePatientGamerHelperApp`, to apply the theme) and in
`SettingsScreen` (to show/change the selection) — two distinct `ViewModel`
instances, different scope (Activity vs. the Settings route's backstack
entry). I did not introduce a shared instance at the navigation graph
level: `ThemePreferences.themeMode` is a `Flow` read from `DataStore`,
which remains the true single source of truth, so the two instances
converge on the same state regardless without needing a shared scope —
same principle already in use for Room/`Flow` throughout the rest of the
app.

## Phase 4 (Google Drive cloud backup)

See the "Phase 4 — Google Drive cloud backup" section in `CLAUDE.md` for
the full architectural summary (authentication, backup format, periodic
worker, UI). Only the choices that weren't obvious from the original
request are covered here.

### `domain/backup` separate from `domain/export`

The request talked generically about "complete JSON of the data" for
backup, and Phase 2 already had a JSON export format
(`domain/export/ReviewExportDto.kt`). I chose **not to reuse it** and
created a dedicated backup DTO (`domain/backup/BackupReviewDto.kt`) for
two concrete reasons, not just separation on principle:
- The Phase 2 export DTO has `copertina` as an **absolute path on the
  device** (`context.filesDir/covers/<uuid>.jpg`) — correct for an export
  the user downloads and looks at, but unusable for a restore on a
  different install (different path, possibly different device). The
  backup DTO instead carries only the cover's **file name**, resolved to a
  new path at restore time.
- The export DTO's labels are in Italian, meant to be readable by whoever
  opens the exported JSON; the backup format is internal and doesn't need
  that constraint, nor should it be coupled to the evolution of the export
  format (it already has its own `schemaVersion` precisely so it can
  change in the future without touching user-facing export, and vice
  versa).

### Restore as a full overwrite, not a merge

Explicitly requested ("no merge/conflict handling... a full overwrite is
fine on restore"). Implemented with a new
`ReviewRepository.replaceAll(reviews: List<Review>)` method, distinct from
`save()`:
- `save()` is meant for the create/edit form: it generates a new id if
  absent, sets `createdAt` to "now" for new reviews and preserves it for
  edits by looking it up on the existing row.
- A restore must instead **preserve exactly** `id`/`createdAt`/`updatedAt`
  from the backup — if I had reused `save()`, after deleting existing
  reviews (necessary for the overwrite) looking up the previous
  `createdAt` on the (by-then deleted) row would have failed, losing the
  original creation date on every restored review. `replaceAll()` writes
  the Room entity directly with the backup's timestamps, in a single
  transaction that also clears the lookup tables (platform/genre/tag)
  before recreating them — otherwise repeated restores would accumulate
  orphaned lookup entries never referenced by any review again, polluting
  autocomplete.
- The name→lookup-id resolution and cross-ref/pro-con writing logic was
  duplicated between `save()` and the first draft of `replaceAll()`:
  extracted into a shared private method (`writeRelations`) on
  `ReviewRepositoryImpl`.

### Hand-written Drive client, not Google's official Java client

The request specified "Drive REST API v3" (not "Google API Client Library
for Java"), and CLAUDE.md explicitly asks not to add dependencies without
real need. `google-api-client-android` + `google-api-services-drive` are
the official libraries but heavy (they bring in Guava and a large
dependency graph) for just three endpoints (multipart upload, list,
download by id). I wrote a minimal client with `java.net.HttpURLConnection`
in `data/drive/DriveApiClient.kt` — zero additional dependencies beyond
`kotlinx.serialization` (already present) for parsing JSON responses.

### "Sign in with Google" reported as doing nothing (see CLAUDE.md, same section, for the full detail)

Audited the whole login flow against Google's current official Credential
Manager guide after a report that the button produces no visible effect at
all (no picker, no error). Found no bug in the reviewed code path itself;
the leading suspect is an external Google Cloud Console configuration gap
(missing/mismatched companion "Android" OAuth client + SHA-1, distinct from
the "Web application" client id already in `drive_config.xml`) — a
documented common cause of exactly this kind of silent failure. Added
logging and richer exception messages (`type`/`statusCode` included) in
`DriveAuthManager` instead of a speculative code fix, plus a visible
progress indicator on the login button, so the next real-device report is
conclusive. Not yet confirmed either way.

### Automatic backup cadence not configurable

WorkManager supports a minimum interval of 15 minutes for periodic work; I
chose a fixed daily cadence (`BackupScheduler`, 24h, `NetworkType.CONNECTED`)
without exposing a way to change it in the UI. For a personal review app,
where data changes at most a few times a day, a daily backup is more than
sufficient and a configuration UI for the interval would be unrequested
complexity — same "don't over-engineer" principle applied to the rest of
Phase 4 (restore without merge, no UI to manage/delete old backups).

## Phase 3 (Library statistics)

The decisions below refer specifically to Phase 3. See also the "Phase 3
— Library statistics" section in `CLAUDE.md` for the architectural
summary.

### No charting dependency added

The spec/task left the choice open between native Compose bars and Vico
(if "the added complexity is justified"). I opted for native Compose bars
(`Box` with `fillMaxWidth(fraction = count / maxCount)` for the
distributions, a segmented stacked bar for status) and **no new
dependency**.

Rationale:
- CLAUDE.md explicitly indicates not to add dependencies for Phase 3/4
  without an explicit request, and instead to flag the need.
- The use case is a single-user library: the number of distinct
  platforms/genres is typically small (a few owned platforms, a moderate
  number of genres), so simple horizontal bars remain readable without
  needing scroll/zoom/interactivity that would justify a charting
  library.
- If visualization needs grow in the future (pie charts, time trends,
  interactive drill-down), Vico remains the recommended choice to
  reevaluate at that point, instead of continuing to extend hand-rolled
  `Canvas`/`Box` rendering.

### Percentages only on the status breakdown

The spec explicitly asks for a "completed/abandoned/in-progress
percentage" but doesn't ask for percentages on the platform/genre
distributions. This isn't an omission: platform and genre are many-to-many
relationships with the review (a review can have multiple platforms and
multiple genres), while status is a single field (enum, one value per
review).

If a percentage for platform/genre were computed by dividing by the total
number of reviews, the displayed percentages wouldn't add up to 100% (a
multi-platform review would be counted more than once), which would be
misleading to someone reading the screen expecting a 100% total like for
status. I therefore chose to show the platform/genre distributions only
as absolute counts (with a bar proportional to the maximum value in the
set), keeping the percentage only where the data is single-choice and the
percentage is mathematically coherent.

### Data structure for the aggregations

- `domain/model/LibraryStatistics.kt`: pure models (`LibraryStatistics`,
  `DistributionEntry`, `StatusShare`), no Android dependency.
- `domain/stats/LibraryStatisticsCalculator.kt`: pure function
  `computeLibraryStatistics(List<Review>): LibraryStatistics`, same
  pattern as `domain/filter/LibraryFiltering.kt` — unit-testable in pure
  JVM without the Android SDK or Robolectric (see
  `domain/stats/LibraryStatisticsCalculatorTest.kt`).
- The distributions are sorted by descending count (then by name, for
  equal counts) — same "most frequent on top" heuristic for both platform
  and genre.
- `total tracked hours` sums `hoursPlayed`, treating `null` (optional
  field) as 0 rather than excluding the review from the total count.
- `average rating` is `Double?` (not `Double`) to explicitly distinguish
  "0 reviews" (no average rating, shown as "—" in the UI) from a
  hypothetical average equal to 0.0.

### UI and navigation

- New `Destination.Stats` route (parameterless object, consistent with
  `Destination.Library`), reachable from an icon (`Icons.Filled.BarChart`)
  in the library's top bar, next to filters/sort/export.
- `ui/stats/StatsScreen.kt` + `StatsViewModel` + `StatsUiState`: same
  MVVM/UDF pattern as the other screens (`ui/library`, `ui/detail`), with
  `ReviewRepository.observeAll()` as the single data source (no mocks).

## Reviews/backlog import-export spec v2 (Tappa 1 + Tappa 2)

`docs/reviews-backlog-import-export-spec-v2.md` was supplied as the
authoritative behavior document, explicitly superseding prior assumptions
in the code — see its own §5 changelog vs. v1. Two attached fixtures
(`docs/examples/review-export-template-example.md`,
`docs/examples/backlog-backup-template-example.json`) were the format
reference. Several real conflicts surfaced between the fixtures and
already-shipped, CLAUDE.md-documented decisions; all were resolved by
asking the user rather than guessing, before writing any code.

### Conflict 1: front-matter Markdown vs. the existing Reddit-style format

The already-implemented single-review export (`ReviewMarkdownFormatter`/
`ReviewMarkdownParser`, Phase 8) produced a bare `# Title` + bullet-list
format with no front matter and none of the fixture's extra fields
(`developer`/`publisher`/`releaseYear`/`metadataSource`/`externalId`/
`linkedBacklogItemId`). The fixture is a YAML front-matter file. Matching
it meant adding six new columns to the `Review` entity — directly
reversing the Phase 6 decision documented in CLAUDE.md ("releaseYear/
developer only on BacklogItem, not on Review"). **Resolved by asking**:
the user chose to switch to the fixture's front-matter format. The six
new fields (`ReviewEntity`/`Review`, `MIGRATION_4_5`, DB version 4→5) are
**never edited by the create/edit form** — `ReviewDraft` deliberately
doesn't carry them, and `ReviewRepositoryImpl.save()` always preserves
whatever the review already had for them instead of wiping them on every
save. In practice they stay `null` for any review created purely through
the UI and only get populated by importing a file that already had them
set (single-review form import parses but never applies them; multi-review
import writes them as part of a full record upsert) — a deliberate, narrow
scope: no "search online" flow was wired to populate them, since
CLAUDE.md's Phase 6 decision to keep the review form's online search
limited to title/platform/genre/cover was left untouched.

The Drive backup DTO (`domain/backup/BackupPayload.kt`, Phase 4) was also
extended with the same six fields (all with a `= null` default, so old
backups without them still decode) — otherwise every Drive backup/restore
cycle would have silently dropped this new data, which would have been a
real regression even though Drive backup wasn't itself in scope for this
session.

### Conflict 2: singular `platform:`/`genre:` vs. the many-to-many model

The review fixture's front matter shows singular `platform:`/`genre:`
string fields. The app's data model has always been many-to-many for both
(CLAUDE.md, "Product decisions already made" — modeled as lookup tables +
bridge tables specifically to support a review with multiple platforms or
genres). Implementing the fixture literally would silently drop every
platform/genre past the first one on export, and cap a review to one of
each on import — a real, permanent loss of an existing capability.
**Resolved by asking**: arrays (`platforms:`/`genres:`), same as `tags:`,
which the fixture already shows as an array. The single-review example
just happens to have one platform and one genre, which round-trips
identically through a length-1 array — nothing in the resolved format
loses data for that case, only gains correctness for the general one.

### Conflict 3: backlog backup JSON schema vs. the already-shipped one

The backlog backup fixture uses English field names, singular
`platform`/`genre`, no HLTB/`releaseYear`/`developer`/abandon-note fields,
and includes `linkedReviewId` — while the already-implemented Phase 8
backlog export/import (`BacklogExportDto.kt`) uses Italian field names,
arrays for platform/genre (same many-to-many reasoning as Conflict 2),
the extra fields the fixture omits, and deliberately drops `reviewId` on
import (documented reasoning: the linked review usually doesn't exist on
the importing device). **Resolved by asking**: keep the existing,
already-shipped schema as-is (it's not a new feature, and rewriting its
field names/shape would be a breaking change for zero real gain — the
JSON structural *shape* the fixture illustrates, lists→items→comments/
history, is what the existing schema already follows) and only add the
one genuinely new thing the fixture calls for: a best-effort `reviewId`
round-trip. Export now includes `recensioneCollegataId` (default `null`,
so files exported before this change still decode); import links it back
onto the item **only if** a review with that id already exists locally
(`BacklogRepositoryImpl.importLists` now takes a `ReviewDao` dependency to
check), leaving it `null` otherwise — never a validation gate, and no new
history entry is synthesized for the link (the source device's own
"Linked to review ..." entry, if any, is already being re-inserted
verbatim as part of the item's history).

### Multi-review ZIP export/import: genuinely new, not a fix

Unlike the backlog export/import above, ZIP export/import for *reviews*
didn't exist before this session — v2 §2.3/§2.4 asked for it from
scratch. Implemented with the same architectural pattern already
established for the backlog archive (`data/export/BacklogExportArchive.kt`
→ `data/export/ReviewZipArchive.kt`): a `reviews/` folder of front-matter
`.md` files plus an `images/` folder, written/read with the same
`ZipOutputStream`/`ZipInputStream` (no new dependency). The `images/`
prefix is only ever written when at least one review has a cover, so a
batch with none produces a zip with no image folder in it at all — this
was verified to already hold for the pre-existing `BacklogExportArchive`
too (same construction, no dedicated fix needed there).

Validation is atomic on content (every `.md` must parse before anything
is written; on any failure nothing is imported and every failing file
name + reason is reported) and always best-effort on images (a missing
folder, an empty one, or one specific missing file degrades silently to
"import without a cover," matching the same rule already established for
the backlog). `ReviewRepository` gained `upsertImported()` — an additive,
preserve-id/createdAt/updatedAt upsert, distinct from both `save()` (form
semantics: resolves the id, keeps existing hidden fields) and
`replaceAll()` (Drive restore semantics: wipes everything first).

### Single-review import moved from the library to the form

Before this session, the only single-review Markdown import was a
library-level action that always created a brand new review — a real
functional mismatch against v2 §2.2's "replace form content inside the
review create/edit screen, not a database upsert." The old
`LibraryViewModel.importMarkdown()` was removed outright (not deprecated
alongside a new path) and replaced with
`ReviewFormViewModel.importMarkdown()`: it parses the file the same way,
but only ever calls `updateDraft {}` on the in-memory form state — the
file's `id` is parsed (needed for validation, since it's a required
front-matter field structurally) but never applied, so editing review A
and importing a file exported from review B still updates review A, never
creates or touches B. The library's former import icon slot was
repurposed for the new multi-review ZIP import (previous section) instead
of being removed, since v2 still wants a library-level batch import
entry point.

### PDF template seam

v2 §2.6 asked for the seam to be built now even though no real template
exists yet ("don't hardcode a single layout that has to be torn out when
the template arrives"). `PdfTemplateProvider` (interface) +
`NoOpPdfTemplateProvider` (the only implementation, always returns
`null`) are wired into `PdfReviewRenderer` via a new `PdfModule` Hilt
`@Binds`, following the same interface/impl/`@Binds` pattern already used
for the repositories (`RepositoryModule.kt`) — deliberately not the
"concrete class, `@Inject constructor`" pattern used for I/O utilities
like `ReviewExporter`/`ImageStorage`, since this really is meant to be a
swappable abstraction, not a fixed one. `PdfReviewRenderer.render()` now
checks `currentTemplate()` once per batch and threads it down to
`renderReview()`, which has a `if (template != null) { ... }` branch that
is genuinely unreachable today (there is no `PdfTemplate` implementation
with any content) but exists as the literal point a future template
would be wired into, rather than only a comment saying so.

### Not done: JSON/CSV whole-library export/import

v2 doesn't mention the existing JSON/CSV whole-library export
(`domain/export/ReviewExportDto.kt`/`ReviewCsvFormatter.kt`) at all, and
there was never a JSON/CSV *import* path to begin with. Left untouched —
in scope for this session was only what v2 §2/§3 actually describes
(single/multi-review Markdown, backlog JSON+zip), not a general audit of
every export format.
