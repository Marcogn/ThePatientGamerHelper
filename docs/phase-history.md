# Phase-by-phase development history

This file is the detailed, chronological build log behind `CLAUDE.md`:
device reports, root-cause investigations, false starts, and the reasoning
that led to fixes — organized under the same phase headings used there.
`CLAUDE.md` keeps only the condensed facts still relevant to writing new
code; this file is where to look when a similar bug resurfaces, or when
the full reasoning behind a past decision needs to be re-examined instead
of re-derived from scratch.

Sections here are grouped by the phase/section they expand on in
`CLAUDE.md` — matching headings make it easy to jump between the short
version and the full story.

## Phase 4 — Google Drive cloud backup

### Device report: "Sign in with Google" button does nothing (no error, no bottom sheet)

Reported after the user filled in a real `google_oauth_web_client_id` and
rebuilt: tapping "Accedi con Google" produced no visible effect at all —
no account picker, no snackbar, no crash. Reviewed the whole flow
(`DriveAuthManager.signIn()`/`authorize()`, `SettingsViewModel.onLoginClick()`,
`SettingsScreen`'s `GoogleLoginCard`) line by line against the current
official Credential Manager "Sign in with Google" implementation guide: the
code matched the documented pattern (`GetGoogleIdOption` +
`setFilterByAuthorizedAccounts(false)` +
`CredentialManager.getCredential(activityContext, request)`, an Activity
`LocalContext.current` from inside `setContent {}`), and `onLoginClick`'s
`try/catch` in `runBusy` did surface `e.message` (or a fallback string) via
a snackbar on any thrown exception — so a literal "nothing, not even an
error" wasn't explained by a bug in the code path checked so far.

**Leading hypothesis, external to the code**: per the same official guide,
"missing or incorrect SHA-1 [fingerprint]" registered as a companion
**Android** OAuth client (as opposed to the "Web application" client
configured via `local.properties`) is documented as a common cause of
exactly this kind of *silent* failure — distinct from the Drive
`AuthorizationClient` scope consent, which already has its own
configured-vs-not branch (`DriveNotConfiguredCard`).

Added diagnostics so the next report would be conclusive either way:
`DriveAuthManager.signIn()`/`authorize()` log (`Log.w`/`Log.i`, tag
`DriveAuthManager`) and wrap any `GetCredentialException`/`ApiException`
into a message that includes the exception's `type`/`statusCode`. Also
gave the login `Button` a visible `CircularProgressIndicator` while
`isBusy`.

**Confirmed by the diagnostics, and not the SHA-1 hypothesis above**: the
exact message was **"No credentials available"** — `NoCredentialException`,
which per the same official guide is the documented trigger for falling
back to the button-style flow (`GetSignInWithGoogleOption`) when there are
no Google accounts on the device that the bottom-sheet flow can offer.
`signIn()` now tries the bottom sheet first and, only on
`NoCredentialException`, retries with `GetSignInWithGoogleOption` (same
`serverClientId`).

**Follow-up, a newly confirmed root cause**: the button-flow fallback
worked — the account picker appeared and the user could select an
account — but choosing one then failed with
`GetCredentialCancellationException`, message **"[16] Account reauth
failed"**. Researched externally: this exact message is the documented
symptom of a SHA-1 fingerprint mismatch on the companion Android OAuth
client — the original leading hypothesis, now corroborated by a second,
more specific piece of evidence. Fix (external, not app code): register
the *exact* keystore's SHA-1 on the Android OAuth client — either
`~/.android/debug.keystore` for a local build, or (at the time) the CI
runner's own ephemeral debug keystore, which is not a fixed value and
regenerates on every clean runner. **Confirmed**: registering the correct
SHA-1 resolved "Account reauth failed" end to end.

### Client ID kept out of version control

The repository is public, and the OAuth web client ID had been committed
verbatim in `res/values/drive_config.xml`. The user regenerated that
client ID in Google Cloud Console to invalidate the one already exposed in
git history — a git history rewrite (BFG/`git filter-repo` + force-push)
was **explicitly declined** as overkill for a value Google itself doesn't
require kept confidential (it ends up baked into the APK and visible in
network requests either way); regenerating it was judged sufficient.

Going forward, the client ID is no longer a committed resource:
`app/build.gradle.kts` reads `DRIVE_OAUTH_WEB_CLIENT_ID` from the
repo-root `local.properties` (gitignored) and injects it via
`resValue("string", "google_oauth_web_client_id", ...)` — falling back to
the placeholder `[TO_COMPLETE]` when absent from every source.
`res/values/drive_config.xml` was deleted.

CI needs the same value: `driveOAuthWebClientId()` checks a
`DRIVE_OAUTH_WEB_CLIENT_ID` **environment variable** first, before falling
back to `local.properties`; both `build-apk.yml`/the release workflow and
`android-ci.yml` pass it from a `DRIVE_OAUTH_WEB_CLIENT_ID` GitHub Actions
repository secret.

### Persistent release signing

Once login was confirmed working end-to-end on device, `build-apk.yml`
was still building with `assembleDebug`, whose keystore
(`~/.android/debug.keystore`) is generated fresh by AGP on every run when
missing — which it always is on a clean GitHub Actions runner. That meant
the SHA-1 registered on the Android OAuth client would go stale on the
very next CI build, breaking Sign in with Google every time a new APK was
downloaded from Actions.

Fix: a real `signingConfigs { create("release") { ... } }` block in
`app/build.gradle.kts`, reading `RELEASE_KEYSTORE_PATH`/
`RELEASE_KEYSTORE_PASSWORD`/`RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD` from
the environment (left unsigned, not a build failure, when absent, so a
local `./gradlew assembleRelease` with no secrets still works). Generated
a dedicated keystore (RSA 2048, 10000-day validity, alias
`thepatientgamerhelper`) and delivered it directly to the user — not
committed, not printed anywhere retrievable afterward. This is a
credential to keep safe indefinitely: losing it means losing the ability
to ever again produce a build with the same signature.

CI workflows decode a base64-encoded copy of that keystore from a
`RELEASE_KEYSTORE_BASE64` GitHub Actions secret into `$RUNNER_TEMP` (never
into the git workspace) and run `assembleRelease`. `android-ci.yml` stays
on `assembleDebug` — it only verifies the build compiles on every
push/PR, it doesn't need a stable signature. Four repository secrets are
needed: `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. The resulting SHA-1 needs
registering once on the Android OAuth client in Google Cloud Console —
after that, since the keystore doesn't change, it never needs
re-registering again.

### Confirmed working end-to-end

The user confirmed the full flow works on a real device — account picker
→ account chosen → authorization granted → Drive REST calls succeeding,
backup/restore usable. Two things worth remembering as the deliberate
current state rather than open problems:

- **The Google Cloud OAuth consent screen stays in "Testing" publish
  status** — a decision, not an oversight. For a single-user personal app
  this costs nothing functionally, it just means: (1) the signing-in
  account must be added under OAuth consent screen > Audience/Test users
  first, and (2) Google shows an "app isn't verified" interstitial on
  every fresh consent, which the test user can click through.
- **Known consequence of staying in "Testing", not yet addressed**:
  Google expires the underlying authorization grant after **7 days** for
  any app in Testing publish status. The interactive login flow is
  unaffected (it just re-prompts), but the daily automatic `BackupWorker`
  relies on `authorize()` resolving silently; past 7 days without an
  interactive re-login it starts failing silently (`Result.failure()`, by
  design), until the user opens Settings and backs up manually again.
  `drive.appdata` is a non-sensitive scope, so moving the consent screen
  to "In production" should not require Google's full manual security
  review — but this has **not been done**, is entirely optional, and is a
  console action for the user to take, not a code change.

## Phase 5 — Internationalization, theme, and documentation

**`MainActivity` extends `AppCompatActivity`, not `ComponentActivity`**:
this was discovered as a real bug during manual on-device verification,
not from documentation alone. Per the official docs, *"If you're using
Compose with setApplicationLocales, you must extend your activity from
AppCompatActivity. Otherwise, setting the app locale won't work."* With
`ComponentActivity` the language change produced no error at all — it was
just silently ignored, which cost two wrong fix attempts before this one
was identified. As a consequence, `res/values/themes.xml` must descend
from `Theme.AppCompat` (here `Theme.AppCompat.DayNight.NoActionBar`) —
`AppCompatActivity` throws a runtime exception on an incompatible theme.

**Note on this file's own translation history**: `CLAUDE.md` used to stay
Italian-only, on the reasoning that it's operational for whoever develops
the app, not documentation aimed at repository readers — a separate
`docs/en/` translation tree existed for a while alongside the Italian-first
docs. That reasoning was reversed by explicit user request: this file and
the rest of the documentation are English-only, and the separate
translation tree was removed. The app's own UI is unaffected and keeps its
dual Italian (default)/English string resources.

## Phase 7 — Rebranding, drawer navigation, TheGamesDB search fix

### Device report: Google Drive login lost on every visit to Settings, not just app restart

Reported after the Drive login saga above was finally confirmed working:
sign-in and restore worked, but navigating away from Settings to **any**
other screen and back required signing in again every single time — not
just after an app restart, which was the only case the original design
("in-memory only in the ViewModel... an app restart requires a new
login") was meant to cover.

**Root cause**: `composable<Destination.Settings> { SettingsScreen(onBack
= { navController.popBackStack() }) }` used a bare `popBackStack()`,
which destroys the entry (and its `ViewModelStore`, and therefore
`SettingsViewModel`'s in-memory `signedInEmail`) outright — unlike every
drawer entry, which navigates with `popUpTo(Destination.Home) { saveState
= true }` + `restoreState = true` specifically to *preserve* a
destination's `ViewModelStore` across round-trips. Since Settings is
reachable only from the drawer and leaveable only via its own back arrow,
this bare pop fired on every single visit, not an edge case.

Fix: `Destination.Settings`'s `onBack` now navigates to `Destination.Home`
with the same `popUpTo`/`launchSingleTop`/`restoreState` triple already
used by `navigateFromDrawer`, instead of a plain pop. Also added a
`BackHandler(onBack = onBack)` in `SettingsScreen.kt`: **the system back
gesture/button bypasses a screen's custom `onBack` lambda entirely by
default** (Compose Navigation registers its own `OnBackPressedCallback`
that does a bare `popBackStack()` unless explicitly intercepted). This
turned out to be a recurring class of bug — see the identical root cause
in `ReviewFormScreen` under Phase 8's "Fifth device verification" below.
Any screen with custom back-button logic needs an explicit `BackHandler`,
or the system gesture will silently skip it.

### TheGamesDB search always-failed fix

**Additional defensive fixes** applied alongside the generic-message fix
mentioned in `CLAUDE.md` (based on research, not direct reproduction —
this project's sandbox has no network access to `api.thegamesdb.net`):
missing `Accept: application/json` header + explicit connect/read
timeouts on the connection; removed `"platform"` from the `fields`
requested on `Games/ByGameName` (not a valid field for that endpoint); the
platform filter syntax corrected to the Laravel indexed form
(`filter[platform][0]=` instead of `filter[platform]=`). Only the
generic-message fix's correctness was guaranteed at the time; the rest
were well-targeted but unverified until a real device confirmed them.

## Phase 8 — Markdown import, backlog export/import, HowLongToBeat, grid views

The single-review Markdown format described in the original Phase 8 work
below was later superseded by the front-matter format introduced in
"Reviews/backlog import-export spec v2" (see `CLAUDE.md`) — kept here for
the historical record of how the import feature was first built and then
debugged, since the debugging lessons (redirect handling, header/User-
Agent requirements, back-gesture handling) remain fully relevant.

### Fixes after real-device verification (first round)

Manual on-device verification (after the initial PR was merged) found
four real problems, not visible from static review alone:

- **"Abbandonato" `FilterChip` splitting vertically character by
  character** in the backlog detail's status selector: a `Row` without
  wrapping compressed the last chip beyond the text's minimum width. Fix:
  `FlowRow` (`@OptIn(ExperimentalLayoutApi::class)`) so the chips wrap
  onto a new row instead of getting squeezed.
- **Top bar titles breaking onto two lines**, overlapping the hamburger
  icon: too many action icons next to the title left too little room.
  Fix: `maxLines = 1` + `overflow = TextOverflow.Ellipsis` on **all**
  `TopAppBar` titles across the app, for consistency and to prevent the
  same bug elsewhere.
- **TheGamesDB search failing with an unreadable JSON error** (`Expected
  JsonArray, but had JsonNull ... element: $.developers`), regardless of
  platform/title: TheGamesDB returns `null` (not simply omitting the key)
  for `genres`/`developers` on games without that data cataloged — a
  default value in `kotlinx.serialization` only covers the *missing* key,
  not an explicit `null`. Fix: `genres`/`developers` made `List<Long>?` in
  `GameDto` instead of only having a default, plus
  `coerceInputValues = true` on the `Json` instance as a safety net for
  other fields that might behave the same way.
- **HowLongToBeat missing everywhere**: the first version of the client
  only implemented the "bare" search POST, without the
  `x-auth-token`/`x-hp-key`/`x-hp-val` headers that maintained unofficial
  libraries document as necessary — obtained with a `GET <path>init`
  before the actual search. `HowLongToBeatApiClient` now implements the
  entire flow (homepage → bundle `_app-*.js` → endpoint → `init` → search
  with the headers), uses a realistic desktop User-Agent instead of one
  that identifies as an app, and logs a warning at every step that fails
  (tag `HowLongToBeatClient`). If the site is ever behind anti-bot
  protection more sophisticated than a header/User-Agent check (e.g. a
  JS-executing challenge), no `HttpURLConnection` client can get past it —
  the only viable path at that point would be a hidden `WebView`
  intercepting network calls, a much more invasive change not made here.

### Second device verification: HowLongToBeat diagnostics, backlog→review flow, dynamic grid

- **HowLongToBeat diagnostics moved into the app**: with no way for the
  user to read `adb logcat`, a silent failure remained a black box.
  `GameMetadataSearchCoordinator.searchHowLongToBeat()` now returns an
  `HltbOutcome` (`Found`/`NotFound`/`Error(message)`) instead of a bare
  `HowLongToBeatEstimate?`, surfaced as `BacklogItemFormUiState.hltbMessage`
  right after picking a "Search online" result.
- **"Complete → write review" flow made explicit instead of immediate**:
  previously, tapping the "Completed" chip immediately applied the status
  *and* brought up the "want to write a review?" dialog on every tap,
  even while just exploring options. `StatusEditor` now keeps the
  selection as uncommitted local state; a "Save" button appears only when
  it differs from the saved value, and only then does
  `onSaveStatus()` write the status and — only if it actually changed to
  COMPLETATO — trigger the prompt.
- **The prefilled review form now opens already set to "Completed"**:
  previously it stayed at the default `IN_CORSO` even though the only way
  to reach it is the post-completion prompt.
- **The back button from the prefilled form no longer discards data**:
  previously a bare `popBackStack()` returned to the backlog card and
  discarded anything typed. `onBackPressed()` now saves the review as a
  draft (if there's at least a title) and links it to the backlog item
  regardless; the nav graph routes back to the library instead of a plain
  pop when the form was opened from the backlog.
- **Dynamic grid instead of uniform-height rows**: `GameGridTile` no
  longer forces a fixed `aspectRatio` on the cover when an image exists
  (`ContentScale.FillWidth`, height follows the file's real proportions);
  switched from `LazyVerticalGrid` to `LazyVerticalStaggeredGrid`
  (`StaggeredGridCells.Adaptive`, `@OptIn(ExperimentalFoundationApi::class)`)
  so square and vertical covers sit side by side without wasted space.

### Third device verification: diagnostics paid off, HTTP 308 redirect fix

The diagnostics from the previous round worked exactly as intended: the
user reported the exact message shown in the form — **"search failed —
HTTP 308"**, identical for any title. Root cause: `HttpURLConnection` with
default `followRedirects` **does not reliably follow redirects on POST
requests**, with known gaps specifically around code 308 (Permanent
Redirect, which unlike 301/302 requires preserving the method and body —
introduced by RFC 7538, more recent than the rest of the class's
historical redirect handling).

Fix: `HowLongToBeatApiClient.request()` disables `instanceFollowRedirects`
and follows redirects **manually** (up to `MAX_REDIRECTS = 5`), re-issuing
the request with the same method, headers and body toward the URL
resolved from `Location`. All four of the client's calls now go through
this single point. Every redirect followed is logged for diagnosability.

### Fourth device verification: duplicate reviews from the backlog flow, HowLongToBeat still 308

- **Duplicate reviews — real cause**: once a review was linked to a
  backlog item, the only way to "get back to it" was still
  `Destination.Form(backlogItemId = itemId)`, which **always** creates a
  new empty review regardless of whether one was already linked. The only
  visible trace of the link was an inert, non-clickable label — so every
  time the user went back through the flow they generated another draft.
- **Fix**: "Recensione collegata" is now a clickable link that opens
  `Destination.Detail(reviewId)` directly — the normal review detail
  screen, with its existing safe edit/delete paths. Also added
  `launchSingleTop = true` on the write-review navigation as a defensive
  guard against a double-tap on the confirmation dialog queuing two
  navigations before it closes.
- **Draft reviews already duplicated on the user's device were not
  cleaned up automatically**: no reliable way exists to distinguish "review
  duplicated by this bug" from "two identical reviews by title but
  intended by the user" — the user had to delete the extras by hand.
- **HowLongToBeat still "HTTP 308"**: the manual-redirect fix from the
  previous round was real and correct, but the new report was the *same*
  error, not a different one — not resolved, or at least not yet provable.
  `ensureSuccessful()` and the too-many-redirects message now include the
  URL that actually failed, so the next report could say *which* of the
  flow's four calls (homepage, JS bundle, init, search) produced it.

### Fifth device verification: missing BackHandler in the review form, HowLongToBeat finally reaches the real site (but 404)

- **Real cause of the duplication, not resolved by the previous fix**: the
  previous fix assumed the only way out of the review form was the
  top-left arrow (`onBackPressed()`). **The system back gesture bypasses
  that entirely** — Compose Navigation's default `OnBackPressedCallback`
  does a bare `popBackStack()` unless intercepted with a `BackHandler`
  (the identical bug class already found and fixed in `SettingsScreen`
  under Phase 7 above). Exiting via the system gesture skipped the
  draft-save-and-link logic, so `reviewId` stayed `null` and every
  subsequent attempt generated another independent review. Fix:
  `BackHandler` added in `ReviewFormScreen.kt` invoking the same
  `onBackPressed()`.
- **HowLongToBeat: the 308 redirect fix really did work** — the error now
  reported was a **HTTP 404 with a real HTML body** from
  `https://howlongtobeat.com/api/s...`, no longer a bare 308 or connection
  error, confirming redirects were being followed correctly to the real
  site. The remaining problem was that the search path itself (derived
  from the JS bundle or the historical fallback) no longer existed.
  `HltbAuth` gained a `source` field indicating whether the path came from
  bundle extraction (with the exact extracted value) or the static
  fallback (with the reason), surfaced in the in-app error message, so the
  next report would say with certainty which of the two needed fixing.

### Fix of the bundle regex: porting from an actively maintained library

On the user's suggestion, fetched the real source of two third-party
unofficial HowLongToBeat integrations —
`ScrappyCocco/HowLongToBeat-PythonAPI` (Python, actively maintained) and
`ckatzorke/howlongtobeat` (TypeScript) — instead of guessing again.

**Concrete cause of the previous round's 404, confirmed by comparison**:
this client's regex for extracting the search path from the bundle
`_app-*.js` did not require the matched `fetch(...)` to specifically be a
`POST` call — it could latch onto the first `fetch("/api/...")` found
anywhere in the bundle (e.g. an unrelated analytics/telemetry GET call),
producing a plausible but wrong path. The Python library's regex
explicitly requires `method: "POST"` in the same options block of the
`fetch()`. Ported 1:1 into Kotlin as `SEARCH_ENDPOINT_REGEX` rather than a
freehand rewrite, since the Python library was the most recently active
source found. Still not runnable from the development sandbox (no network
access to `howlongtobeat.com`) — motivated by a verifiable external
source rather than a new guess, but needing device confirmation.

### TheGamesDB: 403 "Invalid API key" with a regenerated key and remaining quota

A report separate from HowLongToBeat: online search was failing with
`HTTP 403: {"code":403,"status":"Invalid API key was provided.", ...}`
even with a key just regenerated from the TheGamesDB panel, which showed
real remaining quota — the zeros in the error body were placeholder
values for an unrecognized key, not the real quota.

- Verified not a regression: `git log` on `data/thegamesdb/` showed no
  recent change to how the `apikey` is sent.
- Verified the request format via external comparison: fetched
  `muldjord/skyscraper`'s `thegamesdb.cpp` (an actively maintained C++
  scraper) — same base URL, same endpoint, same `&apikey=...` parameter.
- `TheGamesDbPreferences.apiKey` already trims the value before saving, so
  a copy-paste artifact wasn't the cause either.
- Added the same URL-in-error-message diagnostics as HowLongToBeat.
- **Confirmed by the user**: the exact same URL (with the same key) tested
  by hand from a mobile browser **worked** — the key was valid, the
  endpoint was correct, the difference was entirely in the request
  headers. Root cause: `USER_AGENT` explicitly identified the app
  (`"ThePatientGamerHelper/1.0 (Android; ...)"`) instead of using a
  browser User-Agent — TheGamesDB tightened its anti-bot measures in the
  same policy change that made the apikey mandatory, and a misleading
  "invalid key" instead of an explicit block is a common pattern for that
  kind of filter. Fix: the same desktop Chrome `USER_AGENT` already used
  for `HowLongToBeatApiClient` — same cause, same fix, same source of
  reasoning, not a new isolated guess.

### HowLongToBeat: port from GameNative's `HltbService` (2026-08-20)

After all of the above, HowLongToBeat estimates kept breaking again in the
field. The user pointed at
[GameNative's `HltbService`](https://github.com/utkarshdalal/GameNative/blob/master/app/src/main/java/app/gamenative/utils/HltbService.kt)
as a confirmed-working reference and asked for it to be ported, "with the
appropriate differences" (a different app, OkHttp vs. `HttpURLConnection`,
a narrower domain model).

**What GameNative does differently, and why it's more robust**: every
prior round of debugging in this file was really debugging the endpoint-
discovery step — re-deriving the current `/api/...` search path at
runtime by scanning HowLongToBeat's homepage `<script>` bundles for a
regex match (the Turbopack rename to opaque chunk hashes, the regex that
once matched the wrong `fetch()` call, the two 404 rounds). GameNative
sidesteps that whole failure surface: it hits a **fixed, hardcoded
endpoint**, `/api/bleed` (+ `/api/bleed/init` for auth), with no
bundle-scraping at all. Still unofficial and still capable of rotating
without notice like before, but one less moving, breakable part.

**Ported, with the differences CLAUDE.md's Phase 8 section now documents**:
- Fixed `/api/bleed`/`/api/bleed/init` endpoint pair, replacing the
  homepage → bundle → regex → endpoint discovery entirely.
- The confirmed-working request body shape (`modifier: "hide_dlc"`,
  `sortCategory: "name"`, the `rangeTime`/`rangeYear`/`gameplay` objects)
  — kept exactly as GameNative sends it rather than guessed at, same
  reasoning as the earlier "port from an actively maintained library"
  fix above.
- Levenshtein-distance best-match selection with an acceptable-match
  heuristic (prefix/whole-word/distance-threshold), replacing the
  previous "exact title match, else just take the first result" logic —
  extracted into a new pure, unit-tested `domain/howlongtobeat/HltbMatcher.kt`
  rather than kept as private functions on the HTTP client, matching this
  codebase's existing `domain/filter`/`domain/stats` split (GameNative
  itself has no such split, single-app convenience code).
- Auth-rejected retry: a 401/403 from the search call now drops the
  cached token and retries once with a fresh `/init`, same as GameNative.
- Kept from this client's own history rather than copied from GameNative:
  manual 3xx-redirect following (`HttpURLConnection`'s POST-redirect gap,
  see above) and `HttpURLConnection` itself (no OkHttp dependency added).
- Not ported: GameNative's 12h-TTL DataStore-backed result cache
  (`HltbCache`) and its "all playstyles" hours field — neither was asked
  for, and the domain model / persistence layer would both need to grow
  to accommodate them for no currently-requested benefit. See
  `CLAUDE.md`'s Phase 8 section for the up-to-date behavior.

Verified: `./gradlew compileDebugKotlin` and `./gradlew testDebugUnitTest`
both succeeded in this environment (an Android SDK was bootstrapped
locally via `sdkmanager` specifically to check this change, since network
access to `dl.google.com` happened to be available this session — not
guaranteed in general, see the "Build/test commands" section). The actual
HTTP round trip against `howlongtobeat.com` — same limitation as every
previous round in this file — still needs manual on-device confirmation,
since no sandboxed environment used so far has had reliable, IP-stable
network access to the real site.
