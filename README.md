# ThePatientGamerHelper

[![Latest release](https://img.shields.io/github/v/release/Marcogn/ThePatientGamerHelper?label=release)](https://github.com/Marcogn/ThePatientGamerHelper/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![minSdk 26](https://img.shields.io/badge/minSdk-26-brightgreen.svg)](app/build.gradle.kts)

A native Android app for tracking, reviewing and backlogging the games you
play — built around the way patient gamers actually play: slowly, one game
at a time, often years after release, with a backlog that never quite
empties.

Every review is a proper card: rating, platform, genre, pros and cons, and
free-form space to write the actual review — ready to export or paste
straight into a post. No feed, no social layer, no account required to use
it. Your library lives on your device; the cloud is only ever an optional
backup you control.

## Why

Review notes scattered across text files. A backlog that only lives in your
head. A rating system you have to reinvent every time you sit down to write
about a game. ThePatientGamerHelper replaces all of that with one place:
add a game to your backlog, track it while you play, and when you finish
it, write the review with everything you already logged pulled in for you.

## Features

### Review library
Full CRUD on your reviews, with a 0–10 rating scale (one decimal, no
five-star guessing games), platforms, genres and custom tags as proper
autocompleted fields instead of free text you have to keep consistent by
hand. Full-text search plus combinable filters on platform, genre, tag,
rating, status and date range; sort by date, rating, title or hours played.
Add a cover straight from your gallery — no storage permission prompt.

### Backlog you can actually work through
Organize what's next into as many lists as you want, track each game's
status (to start, in progress, completed, dropped, on hold), leave
comments, and reorder items by hand to keep priorities straight. Every
status change is logged automatically, so you get a timeline of a game's
journey through your backlog for free. Mark something completed and the
app offers to turn it straight into a review, pre-filled with everything
it already knows.

### Search instead of typing
Look a game up by title from the backlog or review form and pick the right
result from TheGamesDB — cover, platform, genre, release year and
developer land in the form automatically. Prefer typing it yourself? Every
field stays editable, online search is just a shortcut. Backlog entries
can also pull estimated completion times (main story, main + extra,
completionist) so you know what you're signing up for before you start.

### Know your own numbers
A dedicated statistics screen: total reviews, average rating, hours
played, how your library breaks down by platform and genre, and the split
between completed, in-progress and dropped games. If your backlog has
completion-time estimates, you'll also see how many hours of gaming are
sitting in it right now.

### Take your data with you
- **Markdown** — a single review formatted for pasting straight into a
  Reddit post, or a portable front-matter format that round-trips back
  into the app (single review or a full multi-review archive).
- **JSON / CSV** — the entire library, unfiltered, for backup or any
  processing you want to do outside the app.
- **PDF** — one review or your whole library as a single, properly
  paginated document.
- **Backlog archive** — export every list, item, comment and history entry
  as one file you can move to another device or hand to someone else;
  importing is always additive, so it never overwrites what's already
  there.

### Your backup, your Google Drive
One-tap manual backup or a daily automatic one, stored in your own
private Drive app folder — not visible in your Drive UI, not shareable,
not readable by the app developer. Restore from any previous backup
whenever you need to. No account required to use the rest of the app;
this is opt-in.

### Make it yours
Italian or English, switchable in-app independently of your system
language. Light, dark, or follow-system theme. List or grid view for both
the library and the backlog, whichever fits how you browse.

None of the above requires an account except Drive backup and online
search — and both stay fully optional.

## Download

Grab the latest signed APK from the
[**Releases**](https://github.com/Marcogn/ThePatientGamerHelper/releases/latest)
page and install it directly — there's no Play Store listing, this is a
personal project distributed as a straight sideload.

1. Download `ThePatientGamerHelper.apk` from the latest release.
2. Open it from your file manager or browser downloads and allow your
   device to install it (Android will prompt you to allow installs from
   that source the first time).
3. Android may show a Play Protect warning because the app isn't
   distributed through the Play Store — that's expected for a sideloaded
   APK signed outside of Google's own signing program, not a sign of
   anything wrong with the build.

## Building from source

```bash
./gradlew assembleDebug       # debug APK
./gradlew testDebugUnitTest   # unit tests
./gradlew lint                # Android Lint
```

Requires the Android SDK (`compileSdk 36`) and network access to Google's
Maven repository. Google Drive backup and the online metadata search are
optional at build time — the app builds and runs without them configured,
those two features simply stay unavailable until you supply your own
credentials:

- **Drive backup** needs an OAuth client registered in Google Cloud
  Console, supplied via `DRIVE_OAUTH_WEB_CLIENT_ID` in a local
  `local.properties` file. See `CLAUDE.md` for the full setup.
- **Online metadata search** needs a free TheGamesDB API key, entered at
  runtime from the app's own Settings screen — nothing to configure at
  build time.

## Privacy

Your reviews and backlog stay on your device. There's no analytics, no
tracking, no account system of any kind. The only network calls the app
ever makes are the ones you trigger yourself: a Drive backup/restore, or
an online metadata search — both entirely optional and both easy to avoid
if you just want a local-only app.

## Tech stack

Kotlin and Jetpack Compose with Material 3, following Google's current
recommended architecture rather than the legacy View-based system.

- **Room** as the single source of truth, exposed via `Flow`
- **Hilt** for dependency injection
- **ViewModel + StateFlow**, unidirectional data flow
- **WorkManager** for periodic background backup
- **Preferences DataStore** for the theme preference
- **Credential Manager** and **AuthorizationClient** for Google Drive
  authentication and authorization
- `minSdk 26`, `targetSdk 36`, `compileSdk 36`

Dependencies are kept deliberately light: no charting library for
statistics (native Compose bars), no official Google Java client for
Drive (a small hand-written REST client instead), no Apache POI or iText
for PDF (native `android.graphics.pdf.PdfDocument` — iText7 is AGPL and
therefore excluded outright), no Retrofit/Ktor for the metadata search
(same hand-written REST client pattern as Drive), no reorder library for
the backlog's drag-to-reorder (plain Compose Foundation).

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

- [`CHANGELOG.md`](CHANGELOG.md) — what shipped in each release
- [`docs/spec.md`](docs/spec.md) — functional and technical specification,
  with the development roadmap
- [`docs/test-plan.md`](docs/test-plan.md) — manual test plan covering
  every feature and edge case
- [`docs/implementation-decisions.md`](docs/implementation-decisions.md) —
  non-obvious technical choices made during development
- [`docs/phase-history.md`](docs/phase-history.md) — detailed, phase-by-
  phase build log and troubleshooting history
- [`CLAUDE.md`](CLAUDE.md) — contributor/architecture guide: conventions,
  package layout, build notes

The app's own UI keeps its Italian/English dual-language support (see
`app/src/main/res/values` and `values-en`); everything else in this
repository — documentation, code comments, commit history — is in
English.

## Demo data

Debug builds seed a handful of sample reviews so development doesn't
start from an empty screen (`data/debug/DebugSeeder.kt`). Release builds —
including the APK on the Releases page — never include fake data.

## Contributing

This is a personal project built primarily for single-user use, so
there's no public feature roadmap open to proposals. Bug reports and
small, focused fix PRs are welcome — open an issue before working on
anything substantial to avoid wasted effort. To report a security
vulnerability, see [`SECURITY.md`](SECURITY.md) instead of opening a
public issue.

## License

MIT, see [`LICENSE`](LICENSE).
