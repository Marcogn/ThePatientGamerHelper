# Functional and Technical Specification — Video Game Review App

**Version:** 1.3 (Phase 8 revisited against `reviews-backlog-import-export-spec-v2.md`)
**Purpose of this document:** to define the scope, data model, features, and architecture for a personal Android app that replaces/supports your current review workflow for r/patientgamer, with multi-format export, cloud backup, and a trackable backlog. Originally written as an initial design document, it is now kept up to date as a reference on the roadmap's status — implementation details for each phase live in `CLAUDE.md`.

**Authoritative source for review/backlog import-export-backup-restore behavior**:
`docs/reviews-backlog-import-export-spec-v2.md` — where sections 3.3/3.7/3.8
below summarize the current implementation, that document is the one that
wins on any future discrepancy. Fixture examples referenced there live under
`docs/examples/`.

---

## 1. Goal and guiding principles

A **single-user, offline-first** app: no account is required for basic use, no backend server is needed for the MVP. All data lives locally on the device; the cloud only comes into play as an optional backup (Phase 4).

The technical front-end choice is left up to you; this document proposes a reasoned default stack, but no choice here is binding — they are presented as recommendations with their alternatives.

---

## 2. Data model

Main entity: **Review** (1 review = 1 reviewed game).

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | primary key |
| `title` | string | name of the game |
| `platform` | string/tag | free-form but with autocomplete on already-used values |
| `genre` | string/tag | same |
| `customTags` | list of strings | free-form taxonomy, yours to define |
| `rating` | numeric | **open decision**: 0–10 scale (typical of patientgamer-style written reviews) or 1–5 stars. I'm not assuming which one you'll use — that's a product decision that's yours to make |
| `startDate` | date | when you started the game |
| `endDate` | date | when you completed/abandoned it |
| `hoursPlayed` | numeric | self-reported completion time, entered manually (no automatic integration planned for the MVP) |
| `status` | enum | `completato` / `abbandonato` / `in corso` — useful for tracking the backlog, consistent with your completionist profile |
| `pros` | list of strings | structured bullet points |
| `cons` | list of strings | structured bullet points |
| `reviewText` | long text (markdown) | free-form body of the review |
| `coverImage` | local URI (optional) | image saved in the app's internal storage |
| `createdAt` / `updatedAt` | timestamp | metadata |

Supporting entities: **Platform** and **Genre** as separate lookup tables, to guarantee consistent autocomplete without duplicating strings (avoids "PS5" vs "Playstation 5" ending up as different tags).

Six additional optional fields (`developer`, `publisher`, `releaseYear`,
`metadataSource`, `externalId`, `linkedBacklogItemId`) exist on the Review
entity purely for round-trip fidelity through the front-matter Markdown
export/import (3.3/3.7) and the Drive backup — the create/edit form never
shows or edits them, they only carry through whatever a review already had.

### 2.1 Backlog (Phase 6)

### 2.1 Backlog (Phase 6)

Additional entities for tracking games not yet reviewed:

| Entity | Main fields | Notes |
|---|---|---|
| **Backlog list** | `id`, `name`, `order`, `creationDate` | freely created/renamed/deleted/reordered by the user |
| **Backlog item** | `id`, `listId`, `title`, platform/genre/tag (many-to-many, same lookup tables as reviews), `coverImage`, `status` (`da_iniziare`/`in_corso`/`completato`/`abbandonato`/`in_pausa`), `addedDate`, `startDate`/`completedDate` (optional, set automatically on status change), `reviewId` (optional), `abandonNote`, `year`/`developer` (optional, set only via online search), `hltbMainStory`/`hltbMainPlusExtra`/`hltbCompletionist` (optional, estimated hours, Phase 8 — set only via online search, never by hand) | at most one review per item |
| **Comment** | `id`, `itemId`, `text`, `timestamp` | multiple per item, chronological order |
| **History entry** | `id`, `itemId`, `eventType`, `timestamp`, `detail` | generated automatically by the system, requires no manual input |

---

## 3. Features

### 3.1 MVP (Phase 1)
- Full CRUD on reviews
- Library/list with full-text search and combinable filters (platform, genre, tag, rating, status, date range)
- Sorting by date, rating, title, hours played
- Review detail view

### 3.2 Library statistics (Phase 3) ✅ completed
Given your profile (high completion rate, attention to backlog and complete series), it makes sense to include:
- total number of reviews, average rating, total hours tracked
- distribution by platform/genre
- completed vs. abandoned percentage

Implemented as a new Statistics screen reachable from the library, with the
metrics above plus the "in progress" share (a percentage computed only on
the single-choice status field — not on platform/genre, which are
many-to-many and wouldn't add up to 100%). Implementation details and
technical choices in `CLAUDE.md` and `docs/implementation-decisions.md`.

### 3.3 Export (Phase 2, single-review Markdown format revised per import-export spec v2)
- **Markdown (single review)**: a YAML front-matter block (id, title,
  platforms/genres as arrays, tags, score, status, dates, hours played,
  cover reference, developer/publisher/releaseYear/metadataSource/
  externalId/linkedBacklogItemId, createdAt/updatedAt) followed by the
  same Pros/Cons/free-text body as before. This **replaced** the earlier
  bare "Reddit-style" format (title as `#` heading + a bullet list of
  metadata, no front matter) — see 3.7 for why, and
  `docs/implementation-decisions.md` for the full reasoning. The exact
  shape matches `docs/examples/review-export-template-example.md`.
- **ZIP (multiple reviews)**: one front-matter `.md` per review plus an
  `images/` folder, included only if at least one exported review has a
  cover — round-trip importable (3.7), unlike the batch PDF below.
- **JSON/CSV**: raw data, for backup/portability and any external
  processing — unchanged, still the entire library with fixed Italian
  field names, not touched by the v2 spec.
- **PDF**: single review or entire library in batch. Now checks a
  `PdfTemplateProvider` seam before rendering — with no template
  configured (the only state today), it falls back to the existing plain
  layout unchanged.
- **DOCX**: **not implemented, final decision** — see dedicated technical note below

### 3.4 Google Drive cloud backup (Phase 4) ✅ completed
Manual and automatic (periodic, via WorkManager) backup of a ZIP archive
(full JSON + images folder) saved in Google Drive's appDataFolder.
Restore: list of available backups, selection, download, and reimport into
Room with a full overwrite of local data (no merge). Implementation
details and technical choices in `CLAUDE.md`, section "Phase 4 — Google
Drive cloud backup".

### 3.5 Trackable backlog (Phase 6, Stage 1) ✅ completed
List and item CRUD, status change via a dedicated selector, multiple
comments in chronological order, automatic history (event timeline),
manual item reordering within a list (drag-to-reorder), unified
search/filter (list, status, platform, genre) consistent with the review
library experience, a lightweight aggregate view (counts by status/list),
a free-text note on the reason for abandoning a game. When switching to
"completed," the user is prompted to write the review right away, with
the form pre-filled from data already known from the backlog.
Implementation details in `CLAUDE.md`, section "Phase 6 — Trackable
backlog and metadata fetch (TheGamesDB)".

### 3.6 Automatic cover and metadata fetch (Phase 6, Stage 2) ✅ completed
A "Search online" (TheGamesDB) button in the backlog add form and the
review form: queries by title (+ platform to disambiguate), shows all
results for manual selection (no auto-selection), downloads and saves the
cover and useful metadata locally upon selection. Manual cover upload
remains always available as an alternative. Requires a TheGamesDB API key
configurable in Settings (no key included in the build). Implementation
details in `CLAUDE.md`, same section above.

### 3.7 Markdown review import (Phase 8, revised per import-export spec v2) ✅ completed

**Single review**: no longer a library-level "always create a new review"
action — it is now a **"replace form content"** action inside the review
create/edit screen (an upload icon in the form's top bar). It parses the
selected `.md` file (front-matter format, 3.3) and overwrites the current
form's fields (title, platforms, genres, tags, score, dates, hours played,
pros, cons, body text, cover image reference) with what the file
contains; the file's `id` is ignored — the review being edited keeps its
own identity, only its content changes. Full pre-parse validation: any
failure leaves the form completely untouched and shows a system warning
with the specific reason. A referenced cover image can never be resolved
from a single standalone `.md` pick (no accompanying image bytes come
with it), so it always degrades to "no cover" — the previous cover file,
if any, is deleted so nothing is orphaned.

**Multiple reviews**: a new ZIP import (upload icon in the library's top
bar, the same one that previously triggered the single-file import
above). Validation is atomic on review *content*: if even one `.md` file
in the archive is malformed, nothing is imported and a warning lists
every failing file with its reason. Images are always best-effort — an
absent/empty `images/` folder or a missing individual file never blocks
the batch and never produces its own warning, the corresponding review is
just imported without a cover. Once the whole batch validates, it commits
with an upsert by `id` from front matter (known id → overwrite, unknown
id → insert).

Implementation details in `CLAUDE.md`, section "Phase 8", and
`docs/reviews-backlog-import-export-spec-v2.md` §2.

### 3.8 Backlog export/import with its lists (Phase 8, +reviewId round-trip per v2) ✅ completed

Same principle as the Markdown export/import but for the entire backlog: a
single ZIP archive (data + covers) downloadable/openable via SAF from the
Backlog screen. The import is **always additive** (new lists, new items),
never a replacement — unlike the Drive backup restore (3.4/6), which is a
full restore. The export now also carries the linked review's id (if any);
on import it's relinked **only if** a review with that id already exists
on the importing device — otherwise, same as before, the item is imported
without a link. Implementation details in `CLAUDE.md`, section "Phase 8",
and `docs/reviews-backlog-import-export-spec-v2.md` §3.

### 3.9 HowLongToBeat time estimates in the backlog (Phase 8) ✅ completed

When "Search online" in the backlog form leads to selecting a TheGamesDB
result, the app also attempts a HowLongToBeat search on the same title
and, if it finds a match, saves the estimates (main story, main + extra,
completionist, in hours) on the item — visible on the backlog detail
screen. **HowLongToBeat does not expose a public API**: the integration
uses the same reverse-engineered technique as every existing unofficial
library, so it is inherently more fragile than the TheGamesDB search and
can stop working if HowLongToBeat changes its frontend — it always fails
silently (no field gets set), never with a blocking error. Implementation
details in `CLAUDE.md`, section "Phase 8".

### 3.10 Statistics: estimated backlog time (Phase 8) ✅ completed

The Statistics screen shows, when available, the total estimated hours
(main story/main + extra/completionist) summed across all backlog items
that have a HowLongToBeat estimate, plus a count of how many items have
one. Implementation details in `CLAUDE.md`, section "Phase 8".

### 3.11 List/grid views for reviews and backlog (Phase 8) ✅ completed

A button in the library and in a backlog list's detail view toggles
between the existing list view and a grid view with full-width covers at
the correct aspect ratio (2:3, typical of box-art covers). The choice is
persisted per screen. The backlog grid does not support manual reordering
(drag-to-reorder), which is only available in list view. Implementation
details in `CLAUDE.md`, section "Phase 8".

### 3.12 Reviews/backlog import-export spec v2 ✅ completed

`docs/reviews-backlog-import-export-spec-v2.md` was introduced as the
authoritative behavior document for everything in 3.3/3.7/3.8, superseding
prior assumptions where they diverged. Concretely, against what Phase 8
had shipped:
- Single-review Markdown export/import switched to a YAML front-matter
  format (3.3) and single-review import moved from a library-level
  "always create new" action to a form-level "replace form content"
  action (3.7) — a real behavior change, not just a format change.
- Multi-review ZIP export/import is new (3.3/3.7): round-trip importable,
  content-atomic validation, images always best-effort.
- The backlog export/import (3.8) — already implemented — gained a
  best-effort `reviewId` round-trip and had its "images folder only if
  at least one cover exists" rule verified (already correct).
- A `PdfTemplateProvider` seam was added to the PDF renderer (3.3) for a
  future custom template, with no behavior change today.

Implementation details in `CLAUDE.md`, section "Phase 8" (the "Fixes after
real-device verification" subsections), and
`docs/implementation-decisions.md`.

---

## 4. Proposed technical architecture (not binding)

- **Kotlin + Jetpack Compose** for the UI. This is the stack Google officially recommends in its up-to-date architecture documentation; the View-based system is now in maintenance mode and no longer receives investment for new features.
- **Pattern**: ViewModel with StateFlow + Unidirectional Data Flow (events flow up, state flows down) — this is the pattern described in the official Compose architecture guide.
- **Local persistence**: Room as the single source of truth, with Flow for reactive UI observability.
- **DI**: Hilt (the de facto standard in the Compose/Room ecosystem).
- **Images**: the app's internal storage, referenced via a URI in Room (no need for a dedicated content provider in a single-user app).
- **Background work**: WorkManager for periodic sync/backup.

If you'd rather stay closer to your day-job stack (Spring/JHipster already
makes you comfortable with MVC/dependency-injection patterns), the
classic MVVM alternative with View + ViewModel remains viable, but it's
an investment in technology that Google is explicitly deprioritizing —
I'm flagging this for the sake of honesty, not to push you toward Compose
at all costs.

---

## 5. Technical detail for export

### PDF
Two concrete paths:
1. **`android.graphics.pdf.PdfDocument`** — native, free, but low-level: you draw every element manually on a Canvas. Maximum control, zero licensing risk, more code to write.
2. **Apache PDFBox (Android port)** — free under the Apache 2.0 license, higher-level API for text/paragraphs/tables.

**A note of honesty**: I would avoid iText7 for this project — it's
distributed under the AGPL license (free to use, but with an obligation
to release the source code of any app that uses it, unless you pay for a
commercial license). For a personal app this isn't a blocker in itself,
but it's a constraint worth knowing before adopting it, not after.

### Markdown
No library needed: it's template-based string generation, the simplest of the four formats.

### CSV/JSON
`kotlinx.serialization` for JSON (idiomatic in Kotlin); for CSV, a manual writer or OpenCSV, with no particular pitfalls.

### DOCX — a note of technical honesty (and the decision made)
Here's the uncomfortable truth: **there is no lightweight, mature DOCX
writer built for Android**. Apache POI (the JVM standard for Office) has
known problems on Android — it depends on `java.awt` classes that aren't
available on the platform and adds significant weight to the APK. The
Kotlin wrappers you can find out there (e.g., DocxKtm) are still built on
top of docx4j, which brings the same kind of heavy dependencies.

The only viable path without heavy dependencies would be to generate the
DOCX manually as a ZIP archive of XML (a .docx file is technically a ZIP
containing `document.xml` plus OOXML structure files) — feasible for a
simple document, but with a non-trivial upfront investment.

**Decision made** (no longer an open question): **DOCX export will not be
implemented**. With Markdown (readable, Reddit-compatible sharing) and
JSON/CSV (portable raw data) already covered by Phase 2, DOCX remains a
nice-to-have with no concrete use case that would justify the
implementation cost. It is no longer part of the project roadmap.

---

## 6. Cloud backup — technical detail (Phase 4)

A few points that, as of 2026, have changed relative to many guides found online, so they were verified directly against Google's documentation:

- **Don't use `GoogleSignInClient` / `play-services-auth`**: it's deprecated and being removed from the Play Services Auth SDK. Many "WhatsApp-style Drive backup" guides circulating online still use it — they should be considered outdated.
- Current recommended approach: **Credential Manager** for authentication + the **AuthorizationClient API** for the specific Drive access authorization.
- Scope to request: `drive.appdata`, which grants access to the **appDataFolder** — a private per-app folder, not visible in the Drive user interface and not shareable. Perfect for an automatic backup that's invisible to the user.
- API to use: **Drive REST API v3**. The old "Drive API for Android" (based on `DriveClient`/`DriveResourceClient`) has been deprecated since 2019 and completely shut down since 2023 — it's not an available option, regardless of preference.
- Backup format: a single archive with the full JSON data + an images folder, versioned with a timestamp in the file name.

Practical note: you'll still need to register the app on Google Cloud
Console and configure an OAuth consent screen. For a personal-use app you
can stay in "testing" mode (up to a cap on test users), which avoids
Google's public verification process — sufficient for a single-user use
case like yours.

---

## 7. Proposed development phases

1. **Phase 1 — Local MVP** ✅: CRUD, list, filters, review detail
2. **Phase 2 — Export** ✅: JSON/CSV → Markdown → PDF (in this order of increasing complexity)
3. **Phase 3 — Library statistics** ✅: see `CLAUDE.md` for implementation details
4. **Phase 4 — Google Drive cloud backup** ✅: see `CLAUDE.md` for implementation details
5. **Phase 5 — Internationalization, theming, and documentation** ✅: the
   app translated into IT/EN with an in-app language selector, a
   light/dark/system theme, and documentation reorganized under `docs/`.
   See `CLAUDE.md` for implementation details.
6. **Phase 6 — Trackable backlog and metadata fetch (TheGamesDB)** ✅: a
   new Backlog section (lists, items, comments, automatic history) and
   online TheGamesDB search for cover/metadata, in two stages. See
   `CLAUDE.md` for implementation details.
7. **Phase 7 — Rebranding, drawer navigation, TheGamesDB search fix** ✅:
   see `CLAUDE.md` for implementation details.
8. **Phase 8 — Markdown import, backlog export/import, HowLongToBeat, grid
   views** ✅: Markdown review import, export/import of the entire backlog
   (always additive), HowLongToBeat time estimates in the backlog and in
   statistics, grid view for library and backlog. Later revisited against
   `docs/reviews-backlog-import-export-spec-v2.md` (front-matter Markdown
   format, multi-review ZIP export/import, form-level single import,
   backlog `reviewId` round-trip, PDF template seam — see 3.12). See
   `CLAUDE.md` for implementation details and
   `docs/implementation-decisions.md` for the full reasoning, including
   the known fragility of the HowLongToBeat integration.

Phase 5 closed out this document's original roadmap; Phases 6-8 extend it
at explicit request in later sessions. DOCX export remains **not
implemented**, a final decision — see section 5.

---

## 8. Open points left for you to decide

These are product decisions I'm not presuming to make for you:
- Rating scale (0–10 with decimals vs. 1–5 stars)
- Whether you really need the "in progress" status, or whether you'd rather track only completed/abandoned games (reviewing "in hindsight" is typical of the patientgamer spirit)
- Whether you want a single review per game, or the ability to replay and add a second entry (replay) linked to the same game entry

---

## Main sources consulted

- developer.android.com — Recommendations for Android architecture (updated 2026-04-26)
- developer.android.com — Compose UI Architecture (updated 2026-06-16)
- developer.android.com — About the migration from legacy Google Sign-In (updated 2026-03-06)
- developer.android.com — Store application-specific data (Drive appDataFolder, updated 2026-04-20)
- developers.google.com — Drive Android API deprecation notice
- android-developers.googleblog.com — Streamlining Android authentication: Credential Manager replaces legacy APIs
- ironpdf.com / medium.com — iText7 (AGPL) license comparison vs. alternatives
- dev.to — Kotlin PDF Libraries: Free & Paid (PDFBox overview)
- discuss.kotlinlang.org / github.com (DocxKtm) — state of DOCX generation tools on Android/Kotlin
- forums.thegamesdb.net — API access policy (17/02/2026 change, apikey now required on every endpoint) and limits of the shared public key
- github.com (muldjord/skyscraper, sselph/scraper, picandocodigo/gamesdb) — the real structure of the TheGamesDB v1 endpoints (search, include=boxart, Platforms/Genres/Developers lookup) used to implement `TheGamesDbApiClient` without reachable official documentation
- github.com (ScrappyCocco/HowLongToBeat-PythonAPI, ckatzorke/howlongtobeat, other unofficial libraries) — confirmed that HowLongToBeat exposes no public API and that every existing integration re-derives the search endpoint from the frontend bundle at runtime; used to implement `HowLongToBeatApiClient` (Phase 8) with the explicit understanding that it's a reverse-engineered technique, not a stable contract
