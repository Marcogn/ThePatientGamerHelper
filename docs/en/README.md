> Translated from the Italian source (`README.md`) — may lag behind updates.
> Italian is the source of truth for this project's documentation.

# Game Reviewer

Native Android app for keeping track of the video games I finish (or drop).
It replaces a workflow I used to keep by hand across scattered notes and
posts for r/patientgamer: one entry per game with a rating, platform, genre,
pros and cons, and free-form space for the actual review text.

Single-user, offline-first: data lives on the device, no account is needed
to use it, the cloud only comes in as an optional backup.

## What it does

- **Review library**: create, edit, delete. Full-text search and
  combinable filters by platform, genre, tag, rating, status and date
  range. Sorting by date, rating, title or hours played. Cover image picked
  from the phone's gallery, no storage permissions needed.
- **Statistics**: review count, average rating, total hours played,
  distribution by platform and genre, breakdown of completed, in-progress
  and abandoned games.
- **Export**: Markdown ready to paste into Reddit, JSON and CSV for data
  portability, PDF for a single review or the entire library as one file.
- **Google Drive backup**: manual or automatic once a day, saved to the
  app's private folder (not visible or shareable from Drive's own
  interface). Restore from a list of available backups.
- **Language and theme**: interface in Italian or English, selectable from
  within the app independently of the system language; light, dark, or
  following the system theme automatically.

None of these features require an account: Drive backup is the only
exception, and it's optional either way.

## Tech stack

Kotlin and Jetpack Compose with Material 3, following Google's current
architecture guidelines rather than the older View-based system.

- **Room** as the single source of truth for data, exposed via `Flow`
- **Hilt** for dependency injection
- **ViewModel + StateFlow**, unidirectional data flow (events go up, state
  comes down)
- **WorkManager** for periodic background backups
- **Preferences DataStore** for the theme preference
- **Credential Manager** and **AuthorizationClient** for Google Drive
  authentication and authorization (not the older `GoogleSignInClient`,
  now deprecated)
- `minSdk 26`, `targetSdk 36`, `compileSdk 36`

No heavy dependencies where they aren't needed: no charting library for
statistics (native Compose bars are enough), no official Google Java client
for Drive (a hand-written REST client with `HttpURLConnection` covers the
three endpoints needed), no Apache POI or iText for PDF
(`android.graphics.pdf.PdfDocument` native, iText7 is AGPL and therefore
ruled out from the start).

## Build

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

Requires the Android SDK (`compileSdk 36`) and access to Google's Maven
repository. Using Drive backup also requires configuring an OAuth client in
Google Cloud Console — details are in `CLAUDE.md`.

## Project structure

```
app/src/main/java/com/marcogn/gamereviewer/
├── data/       # Room (entity/dao), repositories, export (SAF/PDF), backup/drive
│               # (Google Drive, WorkManager), preferences (theme), debug data seeding
├── domain/     # Pure models, filter/sort logic, export/backup formatting
├── di/         # Hilt modules
└── ui/         # Compose screens (library, detail, form, statistics,
                # settings) + theme + navigation
```

## Documentation

- `docs/spec.md` — functional and technical specification, with the
  development phase roadmap
- `docs/decisioni-implementazione.md` — non-obvious technical choices made
  during development, phase by phase
- `CLAUDE.md` — reference guide for whoever (or whatever) works on this
  code: architecture, conventions, known limitations
- `docs/en/` — English translation of the documentation above (Italian
  remains the source of truth)

## Demo data

Debug builds automatically seed a handful of sample reviews
(`data/debug/DebugSeeder.kt`) so development doesn't start from a blank
screen. Release builds never include fake data.

## License

MIT, see `LICENSE`.
