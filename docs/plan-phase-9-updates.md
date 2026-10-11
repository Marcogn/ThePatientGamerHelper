# Phase 9 — In-app updates from GitHub and an "Info" screen: plan

Status on 2026-10-11: **planned**, not started. Requested by the author on 2026-10-11 for all six of
their Android apps. ThePatientGamerHelper is the **pilot**: this file holds the shared design, and the
sibling apps (Hall-Of-Memories, CoverDex, PdfToolkit, KartLog) copy the `update` package from here in
one Sonnet phase each, after this phase is merged and checked on the device. HackDex-Tracker gets only
the Info part (private repository, see "Decisions").

Two stages, like Phase 6: **Stage 1** (Opus) is the core, where a wrong design is expensive in six apps;
**Stage 2** (Sonnet) is the UI and the Info screen. Stage 1 ends with a handoff (≤ 15 lines) in
`CLAUDE.md` for Stage 2.

| Stage | Model | Ships |
|---|---|---|
| 9.1 Update core | **Opus** | Pure update logic, GitHub client, download + verification, installer, preferences, startup check; handoff |
| 9.2 Update UI and Info | Sonnet | Update dialog, progress, frequency in Settings, Info screen (version, check now, disclaimers, "made with ❤ by marcogn") |

## What the author asked for

1. **Automatic updates**: at startup, at a configurable frequency, check GitHub for a newer release;
   if there is one, show its changelog, ask whether to download it, then ask the user to install it.
2. **The current version in the app**, for checking, together with the disclaimers the app needs and
   "fatto con il cuore da marcogn".

## Decisions (author's answers, 2026-10-11)

- **Pilot + port**, not a shared library: one design here, copied by the siblings (same habit as the
  existing "copy its patterns" rule). A library in the kit would add a JitPack dependency and one more
  repository to version.
- **Frequency**: Never / Every start / Daily / Weekly, **default Weekly**. "Check now" is always in Info,
  also with Never.
- **"Not now"** only closes the dialog; the next due check asks again. No "skip this version".
- **Changelog shown = the Release body** as published by `release.yml` (the CHANGELOG bold
  headlines); the trailing "See CHANGELOG.md…" line becomes a "Full changelog" button that opens it on
  GitHub. No second request, no Markdown parser.
- **Install = the system installer, with the user's confirmation.** No silent install.

## Facts checked before planning (2026-10-11)

- `GET https://api.github.com/repos/{owner}/{repo}/releases/latest` returns "the most recent
  non-prerelease, non-draft release"; published releases of a public repository need no token
  ([REST: Releases](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)).
- Unauthenticated limit: "60 requests per hour" per IP
  ([REST: rate limits](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api)):
  a weekly or daily check is far below it; a 403/429 is just "try later".
- Each release asset has a `digest` (`"sha256:…"`, may be `null`), documented on the same page and
  seen live on `KartLog-1.2.0.apk` (2026-10-11).
- This repository's releases: tag `v1.0.6` = `v` + `versionName`; APK asset
  `ThePatientGamerHelper-1.0.6.apk`, next to an R8 `…-mapping.txt`. The kit has renamed assets before
  (Hall-Of-Memories, HackDex), so the app picks **the one asset ending in `.apk`**, not a fixed name.
- Installing needs `REQUEST_INSTALL_PACKAGES` and, from API 26, the per-app "install unknown apps"
  permission (`PackageManager.canRequestPackageInstalls()`,
  `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`). The `PackageInstaller` session API needs no
  FileProvider. Android itself refuses an update signed with another key
  (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), so the release keystore must stay the same (it does:
  persistent `RELEASE_*` secrets).
- Not verified, deliberately not relied on: installing without the confirmation dialog
  (`setRequireUserAction`, API 31+) has conditions that change between Android versions; out of scope.

## 9.1 Update core (Opus)

**Goal:** everything that decides, downloads, verifies and installs an update, tested where it can be
on the JVM, with no UI beyond what is needed to try it.

**Prerequisites:** none (Phase 8 and the import-export spec v2 are merged). Open bug #63 does not touch
this area.

**Scope**
- `domain/update/` (pure Kotlin, no Android import, JVM tests):
  - `AppVersion`: parse `x.y.z` with an optional leading `v`, compare numerically (`1.0.10 > 1.0.9`);
    anything else (pre-release suffixes, garbage) is "not comparable" → no update offered.
  - `ReleaseInfo` + parser of the `releases/latest` JSON (kotlinx.serialization, `ignoreUnknownKeys`,
    `coerceInputValues`, nullable `digest` and `body`): tag, name, body, `html_url`, assets.
  - APK asset picker: the single asset whose name ends in `.apk`; none or several → no update.
  - Release body cleanup: bullet lines → list of headlines with the `**` removed; the
    "See [CHANGELOG.md](…)" line → the full-changelog URL.
  - `UpdateFrequency` (NEVER, EVERY_START, DAILY, WEEKLY) and
    `UpdateCheckPolicy.isDue(frequency, lastCheckAt, now)`.
- `data/update/`:
  - `GitHubReleaseClient`: `HttpURLConnection`, same style as `TheGamesDbApiClient`
    (`withContext(Dispatchers.IO)`, timeouts, **no `Accept-Encoding`**), headers `Accept:
    application/vnd.github+json`, `X-GitHub-Api-Version`, a `User-Agent`; owner/repo from
    `BuildConfig` fields set in `app/build.gradle.kts` (one place to change per app).
  - `ApkDownloader`: GET `browser_download_url` (GitHub redirects to its CDN, https → https is
    followed) into `cacheDir/updates/`, progress as a `Flow`, cancellable, SHA-256 checked against
    `digest` when present; a partial or mismatching file is deleted. Files left there are deleted at
    the next start.
  - `ApkVerifier`: `getPackageArchiveInfo` on the file: same `packageName`, higher `versionCode`, same
    signing certificate as the installed app (`GET_SIGNING_CERTIFICATES`, API 28+; `GET_SIGNATURES`
    below). Any mismatch → refuse with a reason.
  - `ApkInstaller`: if `!canRequestPackageInstalls()`, an intent to
    `ACTION_MANAGE_UNKNOWN_APP_SOURCES` for this package; then a `PackageInstaller` session, a status
    `PendingIntent`, and on `STATUS_PENDING_USER_ACTION` the system confirmation; other statuses
    become a result the UI can show.
  - `UpdatePreferences` (SharedPreferences, like `BackupPreferences`): frequency (default WEEKLY),
    `lastCheckAt`.
  - `UpdateManager`: `checkIfDue()` (startup, errors swallowed and logged) and `checkNow()` (errors
    returned), exposing an `UpdateState` `StateFlow` (Idle, Available(release), Downloading(progress),
    ReadyToInstall, Failed(reason)).
- Startup: `MainActivity` calls `checkIfDue()` once per process. **Debug builds never check
  automatically** (debug key ≠ release key, the install would fail); "Check now" stays available.
- Manifest: `REQUEST_INSTALL_PACKAGES`. No new dependency.
- A minimal debug entry (e.g. a temporary row in Settings) is allowed only to try the flow on the
  device and is replaced in 9.2.
- `docs/implementation-decisions.md`: the entries listed under "Decisions" plus anything decided in the
  stage. Handoff (≤ 15 lines) in `CLAUDE.md` for 9.2: the `UpdateState` API, what the UI must do with
  each state, the unknown-sources round trip.

**Done when**
- JVM tests: version compare (incl. `1.0.10`/`1.0.9`, leading `v`, non-numeric), JSON parsing on a
  fixture of a real `releases/latest` response (with and without `digest`/`body`), asset picker, body
  cleanup, `isDue` for every frequency and the clock edge cases.
- `./gradlew lint testDebugUnitTest assembleDebug` green; CI green.
- `docs/test-plan.md`: new "Phase 9" section; CHANGELOG `[Unreleased]` entry if anything is visible.

**Device checks** (release build, no emulator here): install an older signed release APK, start →
the check finds the newer one; download; the "install unknown apps" screen the first time; the system
installer; the app reopens on the new version and the downloaded file is gone at the next start;
airplane mode at startup (nothing shown, no crash).

**Out of scope:** silent install; pre-releases; background checks with WorkManager; a mirror for
private repositories; any UI beyond the debug entry.

## 9.2 Update UI and Info (Sonnet)

**Goal:** the user-facing part, in Italian and English.

**Prerequisites:** 9.1 merged and its device checks passed; the handoff in `CLAUDE.md`.

**Scope**
- Update dialog on `Available`: "Version X is available" (current version next to it), the
  headlines as a list, "Full changelog" (opens the URL), **Download** / **Not now**. Then a progress
  dialog with Cancel; at the end the installer (through `ApkInstaller`). Errors as a short message
  with the reason (no network, checksum mismatch, different signature…).
- Settings: a "Updates" row with the frequency (Never / Every start / Daily / Weekly).
- **Info screen** (`ui/about/AboutScreen`, `Destination.About`, an entry in the drawer under
  Settings; system back with the same `popUpTo`/`BackHandler` pattern as Settings):
  - app name and `BuildConfig.VERSION_NAME` (+ `VERSION_CODE`, small);
  - "Check for updates" (`checkNow()`, with its result: up to date / available / error);
  - "Fatto con ❤ da marcogn" / "Made with ❤ by marcogn";
  - link to the GitHub repository, MIT licence;
  - disclaimers (wording reviewed by the author in the PR): unofficial, not affiliated with or endorsed
    by TheGamesDB, HowLongToBeat (data from an undocumented endpoint, may stop working) or Reddit /
    r/patientgamers; Google Drive is a trademark of Google LLC; game names and covers belong to their
    owners.
- Strings in `values/` and `values-en/`, key lists aligned. README: "Updates" and "Info" paragraphs.

**Done when**
- Robolectric tests for the dialog/ViewModel state mapping (Available → dialog, Not now → Idle,
  Failed → message) and the frequency setting round trip.
- `./gradlew lint testDebugUnitTest assembleDebug` green; CI green; `docs/test-plan.md` cases;
  README, `CLAUDE.md` (phase ticked), CHANGELOG updated.

**Device checks:** the whole flow from 9.1 through the real dialog; "Not now" then a restart with
frequency "Every start" asks again; "Never" never asks at startup but "Check now" works; Daily/Weekly
do not ask twice in the period; Info shows the installed version; both languages; dark theme.

**Out of scope:** the same as 9.1; a licences screen for third-party libraries (no such screen today;
can be its own task).

## Open questions
- Exact disclaimer wording: proposed above, to confirm in the PR.
- Release number of the build that carries this (proposed: next minor, 1.1.0).

## Risks
- GitHub changes the API or the asset shape → the parser fails closed (no update offered), and the
  fixture test shows what changed.
- `digest` is `null` (old assets, or GitHub stops sending it) → rely on the size and on the signature
  check, which Android repeats anyway.
- OEM differences in the "install unknown apps" screen and in returning to the app afterwards:
  device check on the author's phone only.
- A user who never opens the app never gets the update (accepted: no background work).
