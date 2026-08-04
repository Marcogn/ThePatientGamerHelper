> Translated from the Italian source (`docs/spec.md`) — may lag behind
> updates. Italian is the source of truth for this project's documentation.

# Functional and technical specification — Video game review app

**Version:** 1.0 (roadmap complete, Phase 5)
**Purpose of this document:** define scope, data model, features and architecture for a personal Android app that replaces/supports the review workflow for r/patientgamer, with multi-format export and cloud backup. Originally a design document, now kept up to date as a reference for roadmap status — implementation detail for each phase lives in `CLAUDE.md`.

---

## 1. Goal and guiding principles

**Single-user, offline-first** app: no account is required for basic use, no backend server is needed for the MVP. All data lives locally on the device; the cloud only comes into play as an optional backup (Phase 4).

---

## 2. Data model

Main entity: **Review** (1 review = 1 reviewed game).

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | primary key |
| `title` | string | game name |
| `platform` | string/tag | free text with autocomplete on already-used values |
| `genre` | string/tag | same |
| `customTags` | list of strings | free taxonomy |
| `rating` | numeric | 0–10 scale with one decimal, chosen over a 1–5 star scale |
| `startDate` | date | when you started the game |
| `endDate` | date | when you completed/abandoned it |
| `hoursPlayed` | numeric | manually entered completion time (no automatic integration in the MVP) |
| `status` | enum | `completed` / `abandoned` / `in progress` — useful for tracking the backlog |
| `pros` | list of strings | structured points |
| `cons` | list of strings | structured points |
| `reviewText` | long text (markdown) | free-form body of the review |
| `coverImage` | local URI (optional) | image saved in the app's internal storage |
| `createdAt` / `updatedAt` | timestamp | metadata |

Supporting entities: **Platform** and **Genre** as separate lookup tables, to guarantee consistent autocomplete without duplicating strings (avoids "PS5" vs "Playstation 5" as different tags).

---

## 3. Features

### 3.1 MVP (Phase 1)
- Full CRUD on reviews
- Library/list with full-text search and combinable filters (platform, genre, tag, rating, status, date range)
- Sorting by date, rating, title, hours played
- Review detail view

### 3.2 Library statistics (Phase 3) — complete
Given the target usage (high completion rate, attention to backlog and complete series), it makes sense to include:
- total review count, average rating, total tracked hours
- distribution by platform/genre
- completed vs abandoned percentage

Implemented as a new Statistics screen reachable from the library, with the metrics above plus the "in progress" share (percentage calculated only on the single-value `status` field — not on platform/genre, which are many-to-many and wouldn't add up to 100%). Implementation detail and technical choices in `CLAUDE.md` and `docs/decisioni-implementazione.md`.

### 3.3 Export (Phase 2)
- **Markdown**: formatting compatible with Reddit syntax, for direct copy-paste into posts
- **JSON/CSV**: raw data, for backup/portability and any external processing
- **PDF**: single review or entire library in batch
- **DOCX**: **not implemented, final decision** — see dedicated technical note below

### 3.4 Cloud backup on Google Drive (Phase 4) — complete
Manual and automatic (periodic via WorkManager) backup of a ZIP archive (full JSON + image folder) saved in Google Drive's appDataFolder. Restore: list of available backups, selection, download and reimport into Room with a full overwrite of local data (no merge). Implementation detail and technical choices in `CLAUDE.md`, section "Fase 4 — Backup cloud Google Drive".

---

## 4. Proposed technical architecture

- **Kotlin + Jetpack Compose** for the UI. This is the stack Google officially recommends in its current architecture documentation; the View system is now in maintenance mode and no longer receives investment for new features.
- **Pattern**: ViewModel with StateFlow + Unidirectional Data Flow (events go up, state comes down) — the pattern described in the official Compose architecture guide.
- **Local persistence**: Room as the single source of truth, with Flow for reactive UI observability.
- **DI**: Hilt (the de facto standard in the Compose/Room ecosystem).
- **Images**: app-internal storage, referenced via URI in Room (no need for a dedicated content provider in a single-user app).
- **Background work**: WorkManager for periodic sync/backup.

---

## 5. Export — technical detail

### PDF
Two concrete paths:
1. **`android.graphics.pdf.PdfDocument`** — native, free, but low-level: you draw every element manually on a Canvas. Maximum control, zero licensing risk, more code to write.
2. **Apache PDFBox (Android port)** — free under the Apache 2.0 license, higher-level API for text/paragraphs/tables.

**Honesty note**: iText7 was avoided for this project — it is distributed under the AGPL license (free to use but with an obligation to release the source code of the app that uses it, unless you buy a commercial license). Not a blocker in itself for a personal app, but a constraint worth knowing before adopting it, not after.

### Markdown
No library needed: it's template string generation, the simplest of the four formats.

### CSV/JSON
`kotlinx.serialization` for JSON (idiomatic in Kotlin); a manual writer for CSV, no particular pitfalls.

### DOCX — technical honesty note (and decision taken)
The uncomfortable truth: **there is no lightweight, mature DOCX writer built for Android**. Apache POI (the JVM standard for Office files) has known problems on Android — it depends on `java.awt` classes not available on the platform and adds significant weight to the APK. The Kotlin wrappers found online (e.g. DocxKtm) are still built on top of docx4j, carrying the same kind of heavy dependencies.

The only viable path without heavy dependencies would be generating the DOCX manually as a ZIP archive of XML (a `.docx` file is technically a ZIP containing `document.xml` plus OOXML structure files) — feasible for a simple document, but not a trivial initial investment.

**Decision taken** (no longer an open question): **do not implement DOCX export**. With Markdown (readable sharing, Reddit-compatible) and JSON/CSV (portable raw data) already covered by Phase 2, DOCX remains a nice-to-have without a concrete use case that justifies the implementation cost. It is no longer part of the project roadmap.

---

## 6. Cloud backup — technical detail (Phase 4)

A few points that changed relative to a lot of guides found online, verified directly against Google's documentation:

- **Don't use `GoogleSignInClient` / `play-services-auth`**: it is deprecated and being phased out of the Play Services Auth SDK. Many "WhatsApp-style Drive backup" guides online still use it — they should be considered outdated.
- Current recommended approach: **Credential Manager** for authentication + **AuthorizationClient API** for Drive-specific access authorization.
- Scope to request: `drive.appdata`, which grants access to the **appDataFolder** — a private per-app folder, not visible in Drive's UI and not shareable. Perfect for an automatic backup invisible to the user.
- API to use: **Drive REST API v3**. The old "Drive API for Android" (based on `DriveClient`/`DriveResourceClient`) was deprecated in 2019 and fully shut down in 2023 — not an available choice, regardless of preference.
- Backup format: single archive with the full data as JSON plus an image folder, versioned with a timestamp in the file name.

Practical note: the app still needs to be registered in Google Cloud Console with an OAuth consent screen configured. For personal use it's enough to stay in "testing" mode (up to a cap of test users), which avoids Google's public verification process.

---

## 7. Proposed development phases

1. **Phase 1 — Local MVP** — complete: CRUD, list, filters, review detail
2. **Phase 2 — Export** — complete: JSON/CSV → Markdown → PDF (in this order of increasing complexity)
3. **Phase 3 — Library statistics** — complete: see `CLAUDE.md` for implementation detail
4. **Phase 4 — Google Drive cloud backup** — complete: see `CLAUDE.md` for implementation detail
5. **Phase 5 — Internationalization, theme and documentation** — complete: app translated to
   IT/EN with an in-app language switcher, light/dark/system theme, and
   documentation reorganized under `docs/` (plus `docs/en/` for the
   English translation). See `CLAUDE.md` for implementation detail.

Phase 5 closes out this document's original roadmap: every phase listed above is complete. DOCX export remains **not implemented**, a final decision — see section 5.

---

## 8. Open points left to decide

These were product choices not assumed up front (now resolved — see `CLAUDE.md`, "Decisioni di prodotto già prese", for the actual decisions taken):
- Rating scale (0–10 with decimals vs 1–5 stars)
- Whether an "in progress" status is actually needed, or whether tracking only completed/abandoned games is preferable (reviewing "in hindsight" is typical of the patientgamer spirit)
- Whether to allow only a single review per game or the possibility of replaying and adding a second, linked entry (replay) for the same game

---

## Main sources consulted

- developer.android.com — Recommendations for Android architecture
- developer.android.com — Compose UI Architecture
- developer.android.com — About the migration from legacy Google Sign-In
- developer.android.com — Store application-specific data (Drive appDataFolder)
- developers.google.com — Drive Android API deprecation notice
- android-developers.googleblog.com — Streamlining Android authentication: Credential Manager replaces legacy APIs
- ironpdf.com / medium.com — iText7 (AGPL) license comparison vs alternatives
- dev.to — Kotlin PDF Libraries: Free & Paid (PDFBox overview)
- discuss.kotlinlang.org / github.com (DocxKtm) — state of DOCX generation tools on Android/Kotlin
