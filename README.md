# ThePatientGamerHelper

[![Android CI](https://github.com/Marcogn/ThePatientGamerHelper/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Marcogn/ThePatientGamerHelper/actions/workflows/android-ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![minSdk 26](https://img.shields.io/badge/minSdk-26-brightgreen.svg)](app/build.gradle.kts)

Native Android app to keep track of the video game reviews I finish (or
drop). Born to replace a workflow I used to keep by hand between scattered
notes and posts for r/patientgamer: one card per game with a rating,
platform, genre, pros and cons, and free-form space for the actual review
text.

Single-user, offline-first: data lives on the device, no account is needed
to use it, the cloud only comes into play as an optional backup.

## What it does

- **Review library**: create, edit, delete. Full-text search and
  combinable filters by platform, genre, tag, rating, status and date
  range. Sort by date, rating, title or hours played. Cover pulled from
  the phone's gallery, no storage permissions required.
- **Statistics**: number of reviews, average rating, total hours played,
  distribution by platform and genre, breakdown between completed, in
  progress and dropped.
- **Export**: Markdown ready to paste on Reddit, JSON and CSV for data
  portability, PDF for a single review or for the entire library in one
  file.
- **Google Drive backup**: manual or automatic once a day, saved to the
  app's private folder (not visible or shareable from the Drive UI).
  Restore from a list of available backups.
- **Language and theme**: interface in Italian or English, selectable
  in-app independently of the system language; light, dark or
  system-following theme.
- **Backlog**: lists of games to play, with status (to start, in
  progress, completed, dropped, on hold), comments, automatic event
  history and manual reordering to prioritize. Completing an item offers
  to write the review right away, pre-filled with the data already known.
- **Online search (TheGamesDB)**: from the backlog or review form, search
  for a game by title and pick from the results to automatically download
  the cover and metadata, instead of entering them by hand. Requires a
  personal TheGamesDB API key (free, register on their site), configurable
  in Settings — without a key the rest of the app still works, only the
  search stays disabled.

None of these features require an account: Drive backup and online search
are the only exceptions, and both are optional.

## Tech stack

Kotlin and Jetpack Compose with Material 3, following Google's current
architecture guidelines rather than the old View-based system.

- **Room** as the single source of truth for data, exposed via `Flow`
- **Hilt** for dependency injection
- **ViewModel + StateFlow**, unidirectional data flow (events go up,
  state comes down)
- **WorkManager** for periodic background backup
- **Preferences DataStore** for the theme preference
- **Credential Manager** and **AuthorizationClient** for authentication
  and authorization towards Google Drive (not the old, now-deprecated
  `GoogleSignInClient`)
- `minSdk 26`, `targetSdk 36`, `compileSdk 36`

No heavy dependency where it isn't needed: no charting library for
statistics (native Compose bars are enough), no official Google Java
client for Drive (a hand-written REST client with `HttpURLConnection`
covers the three endpoints needed), no Apache POI or iText for PDF
(native `android.graphics.pdf.PdfDocument`, iText7 is AGPL and therefore
excluded outright), no Retrofit/Ktor for TheGamesDB (same hand-written
REST client used for Drive) nor a reorder library for backlog
drag-to-reorder (plain Compose Foundation).

## Build

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

Requires the Android SDK (`compileSdk 36`) and access to Google's Maven
repository. To use Drive backup, an OAuth client also needs to be
configured in Google Cloud Console — details are in `CLAUDE.md`.

## Project structure

```
app/src/main/java/com/marcogn/thepatientgamerhelper/
├── data/       # Room (entity/dao), repositories, export (SAF/PDF), backup/drive
│               # (Google Drive, WorkManager), thegamesdb (online search),
│               # preferences (theme), debug data seeding
├── domain/     # Pure models, filter/sort logic, export/backup formatting
├── di/         # Hilt modules
└── ui/         # Compose screens (library, detail, form, statistics,
                # backlog, settings) + theme + navigation
```

## Documentation

- `docs/spec.md` — functional and technical spec, with the development
  phase roadmap
- `docs/implementation-decisions.md` — non-obvious technical choices made
  during development, phase by phase
- `docs/test-plan.md` — manual (human/app interaction) test plan, covering
  every feature and edge case
- `CLAUDE.md` — reference guide for whoever (or whatever) works on this
  code: architecture, conventions, known limitations

The app itself keeps its Italian/English dual-language UI (see
`app/src/main/res/values` and `values-en`); everything else in this
repository — documentation, comments, commit history going forward — is
in English.

## Demo data

`debug` builds automatically seed a few sample reviews
(`data/debug/DebugSeeder.kt`) so development doesn't start from an empty
screen. `release` builds never include fake data.

## Contributing

Personal project, built for single-user use. There's no public roadmap
open to feature proposals, but bug reports and small fix PRs are welcome —
open an issue before working on something big to avoid wasted effort. To
report a security vulnerability, see [`SECURITY.md`](SECURITY.md) instead
of opening a public issue.

## License

MIT, see `LICENSE`.
