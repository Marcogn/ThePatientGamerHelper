# Test plan — human/application interaction

Manual test plan (black-box, from the point of view of the person using the
app) for **all** of ThePatientGamerHelper's features, covering both main
("happy path") cases and edge cases. Complementary to `docs/spec.md`
(functional specification) and to `CLAUDE.md` (progress status, technical
decisions, real bugs already found and fixed — see the "Fixes after manual
device verification" section and the ones that follow it near the end of
`CLAUDE.md`): those bugs have been added here as **regression** cases that
must never be skipped (section 10).

This file must be **kept up to date**: every new phase/feature added to the
app must get its own section here with happy-path and edge cases, and every
real bug discovered during manual verification must be added to section 10
as a permanent regression. This is not a "one-shot" document.

## How to use this document

- Each case is a checkbox `- [ ]`. Mark it `- [x]` once verified **in this
  release/test round**, with the date and outcome next to it (e.g.
  `- [x] 2026-08-07 OK` or `- [x] 2026-08-07 FAILED — see issue #NN`). Never
  leave a checked box without an indication of when/how it was verified —
  otherwise, at the next release, there's no way to know whether it's still
  valid.
- IDs (`LIB-01`, `FORM-12`, etc.) are stable: if a case is removed because
  the feature changes, do not reassign its ID to a new case — add a new one
  instead, so as not to confuse the history of previous test runs recorded
  elsewhere (issues, spreadsheets, etc.).
- Where relevant, the exact text the app must show is quoted, sourced from
  `values/strings.xml` (Italian, the app's default language) and
  `values-en/strings.xml` (English) — quotes in this document use the
  English wording. If the text actually observed on screen (in either
  language build) diverges from the corresponding string resource, that's a
  bug (even if it's just a string mismatch) to report.
- "Edge case" indicates unusual but reachable input/paths for a real user
  (does not require root/debug access or tampering with the APK).

## Test environment prerequisites

- Device or emulator with **Google Play Services** (required for Google
  Drive login, the `AuthorizationClient`, and — indirectly — Credential
  Manager). Minimum supported API level: 26 (`minSdk`); ideally test on at
  least two API levels, one near the minimum and one recent.
- Real data connectivity for: Drive backup/restore, TheGamesDB search,
  HowLongToBeat estimation. A "no network"/"slow network" profile (airplane
  mode, throttling) for the section 8.4 cases.
- A real Google account for Drive backup tests (section 7.3) — **do not run
  the restore tests against an account with real Drive data that isn't a
  test account**: restoring irreversibly overwrites all local reviews (by
  design, see `CLAUDE.md` Phase 4).
- A valid TheGamesDB API key (free registration at thegamesdb.net) for the
  tests in sections 3.5/5.6/7.4.
- An empty library/backlog for "empty state" tests, and a library/backlog
  with enough data (at least 15-20 reviews/items, with several different
  platforms/genres) for search/filter/sort/statistics/grid-view tests with
  real content.
- At least one `.md` file exported by the app itself (for import roundtrip
  tests) and at least one hand-written/malformed `.md` file (for failed
  import tests).

---

## 1. Home screen and drawer navigation

- [ ] **HOME-01** On first launch (no data yet) the app shows the Home
      screen with a hamburger icon, the subtitle "What do you want to do?",
      and three cards: Reviews, Backlog, Statistics.
- [ ] **HOME-02** Tapping each of the three cards navigates to the
      corresponding screen (Library / Backlog / Statistics).
- [ ] **HOME-03** Tapping the hamburger icon opens the side drawer with the
      entries Reviews, Backlog, Statistics, a divider, and Settings.
- [ ] **HOME-04** From any of the three sections, the drawer allows jumping
      directly to another section without having to go back to Home first.
- [ ] **HOME-05** Repeatedly navigating between the same 2-3 sections from
      the drawer does not accumulate a backstack: the system back button
      from a section reached via the drawer must not bounce through every
      screen visited previously.
- [ ] **HOME-06** System back button from Home closes the app (Home is the
      `startDestination`, there is no screen "underneath" it).
- [ ] **HOME-07 (edge)** Rapid repeated tapping (double/triple tap) on a
      Home card does not open the same screen twice on the backstack.
- [ ] **HOME-08 (edge)** Opening the drawer, then tapping outside the
      drawer area (scrim) closes it without navigating anywhere.
- [ ] **HOME-09** Settings is **not** reachable from the three Home cards,
      only from the drawer (at the bottom, after the divider) — verify it
      does not also appear as a fourth card.
- [ ] **HOME-10 (edge, known limitation)** No drawer entry is ever
      highlighted as the "current section" (`selected` is hardcoded to
      `false` on every entry in `ThePatientGamerHelperNavGraph.kt`) — this
      is not a crash or a functional blocker, but it's a real usability
      gap: opening the drawer while already, say, in Backlog, no entry
      appears selected. Confirm it's still the case and consider flagging
      it as an improvement (no immediate fix required, but it should be
      tracked so it isn't lost).

---

## 2. Reviews library

### 2.1 Empty state and basic navigation

- [ ] **LIB-01** Empty library: shows "No reviews yet" / "Tap + to add your
      first review", no empty list rows/cells, "+" FAB visible.
- [ ] **LIB-02** Library top bar: title "Reviews" (never the old app name),
      hamburger icon on the left (not a back arrow), action icons on the
      right (filters, sort, view, export, import) — verify the title never
      wraps to two lines even with several action icons active at once
      (known regression, see section 10).
- [ ] **LIB-03** Tapping a review in the list opens the detail screen for
      that review (title/cover consistent with the row that was tapped).

### 2.2 Search

- [ ] **LIB-04** Typing in the search field (placeholder "Search title,
      text, pros/cons…") filters the list live as you type.
- [ ] **LIB-05** Search is case-insensitive and matches on partial
      (substring) matches of the title.
- [ ] **LIB-06** Search also matches on the review body, on individual
      pros/cons, and on tags — **confirmed from the code**: the search
      engine compares title, body text, pros, cons, and tags. It does
      **not** search platform/genre (those are only filterable, never
      text-searchable) — explicitly verify that typing a platform/genre
      name in the search field does **not** produce a match based solely
      on the platform/genre name, if it doesn't also appear elsewhere in
      the review.
- [ ] **LIB-07** A search with no results shows "No results with the
      current filters" + a "Reset filters" button, not the library's
      generic empty state.
- [ ] **LIB-08 (edge)** Searching with **only spaces** doesn't collapse the
      list as if searching for the literal string " " — verify the actual
      behavior (no matches is the likely expected outcome, but confirm it,
      not a crash).
- [ ] **LIB-09 (edge)** Searching with special/regex-like characters
      (`.*`, `%`, `'`, `"`, `\`, an emoji 🎮) does not cause a crash or
      anomalous results.
- [ ] **LIB-10 (edge)** Clearing all search text after a search with active
      filters goes back to showing all results that still pass the
      filters (not the entire library if filters were active).
- [ ] **LIB-11 (edge)** Searching with a very long string (500+ pasted
      characters) does not freeze the UI or crash.

### 2.3 Filters

- [ ] **LIB-12** Opening the filter panel: sections for Status, Platform,
      Genre, Tags (multi-select chips), rating range (0-10 double slider),
      date range (From/To).
- [ ] **LIB-13** Selecting multiple chips within the same section (e.g. two
      platforms) applies an OR between them; selecting chips across
      different sections applies an AND between sections — verify the
      actual behavior against this expectation.
- [ ] **LIB-14** Filters apply **live** as chips are selected/sliders are
      moved (no need to tap "Apply" to see them reflected in the list
      below, if visible); "Apply" simply **closes** the panel. "Reset"
      clears all selections **except** the text search query (confirmed
      from the code: `onClearFilters` doesn't touch the search text).
- [ ] **LIB-15** The rating range shows "Rating: X.X – Y.Y" updated live as
      the slider is dragged.
- [ ] **LIB-16 (edge)** Setting the rating range to an interval that
      contains no existing review (e.g. 9.8–10.0 if no review has that
      rating) → "No results with the current filters".
- [ ] **LIB-17 (edge)** Date range with "From" later than "To" (filter
      start date after the filter end date): verify it doesn't crash and
      understand what happens (no results is an acceptable outcome, but it
      must be confirmed that it isn't a crash or a silently ignored
      filter).
- [ ] **LIB-18** Active filters persist when navigating to a review's
      detail screen and back (they don't reset on their own).
- [ ] **LIB-19 (edge)** Active filters combined with the library losing, in
      the meantime, the only review that satisfied them (e.g. deleted from
      elsewhere, or edited to remove the filtered platform): the list
      updates to "no results" without needing to reopen the screen (Room
      is the single source of truth via `Flow`).
- [ ] **LIB-20 (edge)** A platform/genre/tag filter that in the meantime
      becomes "orphaned" (no review references it anymore, e.g. after
      removing that one tag from the only review that used it): does the
      chip still remain in the filter list until the panel is reopened?
      Verify it doesn't crash.

### 2.4 Sorting

- [ ] **LIB-21** Sort menu: Date, Rating, Title, Hours played; tapping the
      same field repeatedly toggles ascending/descending (an up/down arrow
      icon reflects the current state).
- [ ] **LIB-22 (edge)** Sorting by "Hours played" with reviews that have
      the field empty (`null`): verify where they end up (top, bottom,
      treated as 0 — it must be consistent and not produce an
      unstable/random order on every refresh).
- [ ] **LIB-23 (edge)** Sorting by Title with titles starting with
      lowercase/uppercase/a number/an emoji/an accented character: verify
      the sorting is consistent (case-insensitive is expected) and stable.
- [ ] **LIB-24** The chosen sort order persists across sessions (or at
      least during the current session) and combines correctly with
      active search and filters at the same time.

### 2.5 List/grid view

- [ ] **LIB-25** The list/grid toggle (`ViewModeToggle`) immediately
      changes the library's layout.
- [ ] **LIB-26** The chosen view preference persists across app restarts
      (`ViewModePreferences`).
- [ ] **LIB-27** Grid view: `LazyVerticalStaggeredGrid` — covers with
      different aspect ratios (square, portrait, very wide if imported
      from an external source) sit side by side without forced uniform
      row heights and without odd empty gaps.
- [ ] **LIB-28 (edge)** Grid view with reviews that have **no cover**
      mixed with reviews that do: the placeholder stays at a fixed 2:3
      ratio and doesn't break the staggered layout.
- [ ] **LIB-29 (edge)** Changing orientation (portrait/landscape) in grid
      view correctly recalculates the number of columns
      (`Adaptive(minSize = 120.dp)`) without visual glitches.
- [ ] **LIB-30** Tapping a grid tile opens the correct detail screen (same
      behavior as list view).

### 2.6 Library export (JSON/CSV/PDF)

- [ ] **LIB-31** JSON export: always the **entire library**, even with
      filters/search active in the UI (never filtered) — verify by opening
      the exported file while filters that hide most reviews are active.
- [ ] **LIB-32** CSV export: same "always everything" behavior; open the
      CSV and verify correct encoding/separators with titles containing
      commas, quotes, and line breaks.
- [ ] **LIB-33** PDF export: a single multi-page file with all reviews,
      each review starting on a new page.
- [ ] **LIB-34** Every export uses the Storage Access Framework
      (`ActivityResultContracts.CreateDocument`): the user picks the
      folder/file name, never a silent direct write.
- [ ] **LIB-35** After a successful export, "Export complete" is shown
      (snackbar/message).
- [ ] **LIB-36 (edge)** Cancelling the SAF picker (back button or "Cancel"
      in the system file picker) during an export: no crash, no misleading
      error message, the app stays in its previous state.
- [ ] **LIB-37 (edge)** Exporting with an **empty** library: verify what
      happens (an empty/headers-only file for CSV, a JSON with an empty
      array, a PDF — does a zero-review PDF make sense? verify it doesn't
      crash).
- [ ] **LIB-38 (edge)** PDF/CSV/JSON export with reviews containing
      extended Unicode characters (an emoji in the title, CJK, RTL such as
      Arabic) — verify they don't corrupt the file or crash
      `PdfDocument`/`StaticLayout`.
- [ ] **LIB-39 (edge)** PDF export with a review whose text is extremely
      long (thousands of characters): verify correct pagination across
      multiple pages without truncating or overlapping text.
- [ ] **LIB-40 (edge)** Denying/revoking storage permissions (where
      applicable on that Android version) before an export: the error is
      handled, not a crash.
- [ ] **LIB-41 (edge)** Device storage full during an export: handled
      failure with a message such as "Export failed: ...", not a silent
      crash or a truncated file without warning.
- [ ] **LIB-41b** ZIP export (import-export spec v2): one front-matter
      `.md` file per review under `reviews/`, plus an `images/` folder —
      verify the folder is present when at least one exported review has a
      cover, and **absent entirely** when none do (open the zip with an
      archive tool, don't just trust the app).
- [ ] **LIB-41c (edge)** ZIP export with two reviews sharing the exact same
      title: verify both `.md` files are present (deduplicated file names,
      e.g. a `-2` suffix), neither silently overwrites the other.

### 2.7 Multi-review ZIP import

Single-review Markdown import moved into the review create/edit form (see
3.8) — this section now covers only the library-level **multi-review ZIP**
import (import-export spec v2 §2.4), which replaced it at this entry
point.

- [ ] **LIB-42** The upload icon in the library's top bar opens the SAF
      picker (`ActivityResultContracts.OpenDocument`) filtered for/suited
      to `.zip` files.
- [ ] **LIB-43** Importing a zip previously exported by this same feature
      (2.6): every review inside is imported/updated, "Imported N reviews"
      shown on success.
- [ ] **LIB-43b** Re-importing the **same** zip a second time: every review
      is **overwritten in place** (upsert by id from front matter), not
      duplicated — unlike the old single-file import, which always created
      a new review.
- [ ] **LIB-44** After a successful import, the library list reflects the
      change without needing to reload the screen.
- [ ] **LIB-45 (edge)** A zip where **one** `.md` file (out of several) is
      malformed (e.g. missing `score:`): **nothing** is imported — verify
      no review from the batch was written — and the warning lists the
      failing file's name and reason.
- [ ] **LIB-46 (edge)** A zip with no `.md` files at all (e.g. only an
      `images/` folder, or genuinely empty): fails with a readable message,
      no crash, nothing imported.
- [ ] **LIB-47 (edge)** A zip whose `images/` folder is **absent entirely**:
      every review imports successfully, just without a cover — no warning
      specific to the missing folder.
- [ ] **LIB-48 (edge)** A zip whose `images/` folder is present but is
      **missing one specific file** referenced by one review's
      `coverImage`: that one review imports without a cover, every other
      review in the batch (including ones with resolvable covers) is
      unaffected.
- [ ] **LIB-49 (edge)** Importing a zip that isn't one this feature
      produced (e.g. a random zip renamed to `.zip` with unrelated
      content, or a corrupted archive): fails with a readable error
      message, no crash.
- [ ] **LIB-50 (edge)** Full roundtrip: export the entire library (2.6) →
      delete/modify a couple of reviews → re-import the exported zip →
      confirm every review matches the original (title, platforms/genres/
      tags as full sets — not truncated to one — score, dates, hours,
      pros/cons, body, cover, and the id-based upsert did not create
      duplicates).
- [ ] **LIB-51 (edge)** Cancelling the SAF picker during export or import:
      no crash, no misleading error message.
- [ ] **LIB-52 (edge)** Importing a very large zip (many reviews, or one
      review with a very long body): does not freeze the UI indefinitely or
      crash from OOM on a lower-end device.

---

## 3. Review form (create/edit)

### 3.1 Main fields and validation

- [ ] **FORM-01** Opening the form to create a review (from the library's
      "+" FAB): all fields empty/default, screen title "New review".
- [ ] **FORM-02** Opening the form to edit (from detail → Edit): all fields
      pre-filled with the review's current values, screen title
      "Edit review".
- [ ] **FORM-03** Saving with an **empty title**: blocked, with the message
      "Title is required".
- [ ] **FORM-04 (edge)** Saving with a title made **only of spaces** (e.g.
      "   "): verify whether `isBlank()` catches it as empty (expected:
      yes, blocked with the same message).
- [ ] **FORM-05** Rating slider: range 0.0–10.0, step 0.1 (99 internal
      steps), it's not possible to set a rating out of range or with more
      than one decimal digit from the UI itself — the "Rating must be
      between 0 and 10" validation is therefore hard to trigger from the
      UI alone: note whether it's still reachable somehow (e.g. from an
      anomalous pre-filled state) or whether it's dead code on the UI side
      but useful as a safety net.
- [ ] **FORM-06** The label above the slider ("Rating: X.X") updates live
      as the finger is dragged, always showing exactly one decimal digit
      (e.g. "7.0", not "7").
- [ ] **FORM-07** Status selector: three chips (In progress/Completed/
      Abandoned) in a `FlowRow` — on a narrow screen or with scaled text
      (accessibility), the chips wrap to a new row, they don't overlap or
      split their text vertically (known regression, section 10).
- [ ] **FORM-08** Start/end dates: date picker; the end date is optional
      and **removable** (a "Remove" button in the picker) while the start
      date is not (`clearable = false`).
- [ ] **FORM-09** End date earlier than the start date → blocked on save,
      "End date cannot be before the start date".
- [ ] **FORM-10 (edge)** End date **equal** to the start date (same day):
      must be accepted (it's not "earlier").
- [ ] **FORM-11 (edge)** Setting the end date first and then a start date
      later than it (reversed order of entry): verify the validation still
      triggers on save, not only if the dates are touched in a specific
      order.
- [ ] **FORM-12** "Hours played" field: decimal numeric keyboard, accepts
      both `.` and `,` as the decimal separator (automatic comma-to-dot
      replacement).
- [ ] **FORM-13 (edge)** Hours field with pasted non-numeric text (e.g.
      "abc"): `toDoubleOrNull()` silently returns null, the field goes
      back to empty/doesn't update the draft — no error message shown.
      Verify the resulting experience still makes sense (not a crash, no
      phantom value saved).
- [ ] **FORM-14 (edge)** Hours field with a **negative value** (e.g.
      "-5"): **no validation blocks it** in the current code — verify it
      is indeed possible to save a review with negative hours played, and
      assess whether this is acceptable behavior or a bug to report
      (likely a real bug: no `>= 0` check).
- [ ] **FORM-15 (edge)** Hours field with a huge number (e.g.
      "999999999999"): must not cause visual overflow or a crash in
      export/statistics that sum it up.
- [ ] **FORM-16 (edge)** Hours field with many decimals typed (e.g.
      "12.3456789"): verify how it's displayed/rounded the next time the
      form loads (`formatHours`).
- [ ] **FORM-17** Review body (multi-line markdown field, min 6 rows):
      accepts free multi-line text, no visible length limit tested up to
      at least a few thousand characters.

### 3.2 Platforms / genres / tags (chip input with autocomplete)

- [ ] **FORM-18** Typing in one of the three fields shows autocomplete
      suggestions drawn from values already existing in the library.
- [ ] **FORM-19** Selecting a suggestion or pressing enter/comma adds a
      chip; the chip is removable via the "x"/dedicated icon.
- [ ] **FORM-20 (edge)** Adding the same value twice with different
      casing (e.g. "PC" then "pc"): the comparison is case-insensitive
      (`equals(ignoreCase = true)`), the second one doesn't duplicate the
      chip.
- [ ] **FORM-21 (edge)** Trying to add a chip made of **only spaces**:
      silently ignored (`trim().isEmpty()` → return).
- [ ] **FORM-22 (edge)** Adding a value with leading/trailing spaces (e.g.
      "  PS5  "): saved trimmed, doesn't produce a duplicate distinct from
      an already-existing "PS5".
- [ ] **FORM-23 (edge)** Adding a completely new platform/genre/tag name
      (never seen before in the library): created on the fly as a new
      lookup entry, available in autocomplete for the next review.
- [ ] **FORM-24 (edge)** Removing every chip in a section after having
      added some: the draft accepts an empty list (platforms/genres/tags
      are optional).
- [ ] **FORM-25 (edge)** A very long platform/genre/tag name (e.g. 200
      characters) or one with an emoji: accepted without being truncated
      in a way that breaks the chip's layout.
- [ ] **FORM-26** Autocomplete does not re-suggest a value that's already
      selected as a chip (avoiding duplicates in the suggestion list).

### 3.3 Pros / cons

- [ ] **FORM-27** Adding/removing free-text rows in Pros and in Cons
      independently; the entry order is preserved (`posizione`).
- [ ] **FORM-28 (edge)** Adding an empty pro/con row (no text) and saving:
      verify whether it's discarded or saved as an empty string (likely
      behavior to clarify/flag if it produces an empty bullet in the
      Markdown/PDF export).
- [ ] **FORM-29 (edge)** Many pro/con rows (20+): the list scrolls
      correctly in the form, no element cut off.

### 3.4 Cover image

- [ ] **FORM-30** Tapping the cover area opens the system photo picker
      (`ActivityResultContracts.PickVisualMedia`) — no runtime permission
      requested/asked for upfront.
- [ ] **FORM-31** Selecting an image shows it as a preview in the form; the
      image is copied into the app's internal storage (not merely
      referenced by an external URI).
- [ ] **FORM-32** The "Remove cover" button clears the preview and, on
      save, the review ends up with no cover.
- [ ] **FORM-33 (edge)** Cancelling the photo picker (back/cancel button):
      the previous cover (if present, in edit mode) stays unchanged, no
      "broken" cover.
- [ ] **FORM-33b (edge, regression check)** Edit an existing review that
      already has a cover; pick a *different* cover (photo picker or a
      "Search online" result — FORM-39), then leave the form via
      back/cancel **without saving**. Re-open the review: the **original**
      cover must still display correctly, not be missing/broken. (Before
      the cover-storage fix, the old file was deleted the instant a
      replacement was picked, before the change was ever saved — cancelling
      left the review pointing at a deleted file.)
- [ ] **FORM-34 (edge)** Replacing an existing cover with a new one several
      times in a row: no visible accumulation of orphaned files to the
      user (not verifiable from the UI, but verify the final preview is
      always the correct one and performance doesn't degrade).
- [ ] **FORM-34b (edge)** Pick a cover, then cancel out of a **brand new**
      (never-saved) review entirely. Restart the app (so
      `CoverImageReconciler` runs) — no functional check possible from the
      UI, but this is the scenario the startup sweep exists for; if disk
      usage is being tracked (SET-16c below), verify it doesn't keep
      growing across repeated cancels like this.
- [ ] **FORM-35 (edge)** Selecting a very large image (e.g. a 12MP+ photo
      straight from the camera): copied/displayed with no OOM and no
      noticeable excessive delay, and the resulting cover file on disk is
      well under the original photo's size (downsampled/compressed, not a
      byte-for-byte copy).
- [ ] **FORM-36 (edge)** Selecting an image with extreme proportions (a
      very wide panorama, or very tall and narrow): the preview in the
      form and in the library grid view don't break the layout.

### 3.5 "Search online" (TheGamesDB)

- [ ] **FORM-37** With the API key **not configured**: tapping
      "Search online" shows "Set your TheGamesDB API key in Settings to
      use online search" instead of attempting the network call.
- [ ] **FORM-38** With the API key configured: typing a title and
      searching opens a dialog with results (cover, platform, year) to
      choose from.
- [ ] **FORM-39** Selecting a result pre-fills the cover (downloaded and
      saved locally, not just linked) and the available platform/genre
      fields from the result — it must **not** overwrite title/rating/
      other fields already filled in by hand by the user (verify the
      actual behavior: if it overwrites already-filled fields, that's a
      case to flag).
- [ ] **FORM-39b** The result list's row thumbnails and the final saved
      cover both display correctly (no broken-image icon) — the list now
      requests TheGamesDB's smaller "thumb" crop instead of the full-size
      image, with a fallback to the full image if the API response doesn't
      include one; if thumbnails are ever missing/broken across several
      different searches, that fallback path is worth checking.
- [ ] **FORM-39c** The cover saved locally after picking a result is
      reasonably sized (well under a megabyte for a typical box art, not
      several MB) — it's downsampled/re-encoded on save, not stored as
      TheGamesDB's original download.
- [ ] **FORM-40 (edge)** A search with no results at all: "No results
      found", the form remains fillable by hand.
- [ ] **FORM-41 (edge)** A search that fails with a network/HTTP error
      (e.g. airplane mode, invalid key): "Search failed. You can still
      fill in the fields by hand" (or a message with a technical detail
      appended, per the Phase 7 fix) — never an unhandled crash or
      exception.
- [ ] **FORM-42 (edge)** A search with a title containing special
      characters (e.g. ":", "'", accents — e.g. "Pokémon", "Assassin's
      Creed"): the query is sent/URL-encoded correctly, no crash and no
      malformed request.
- [ ] **FORM-43 (edge)** A search with a **platform already set** in the
      form (a platform chip added before searching): results should be
      filtered/disambiguated by that platform (deduced from the first
      platform tag) — verify the filter is actually effective.
- [ ] **FORM-44 (edge)** Rapid double-tap on "Search online": must not
      trigger two parallel searches that overwrite the UI state
      inconsistently (a stuck spinner, duplicated results).
- [ ] **FORM-45 (edge)** Closing the results dialog without selecting
      anything: the form remains as it was, no field altered.

### 3.6 Save / cancel / back

- [ ] **FORM-46** Tapping the checkmark (explicit save) with valid data:
      the review is saved, navigates back to the detail screen (edit mode)
      or to the library (create mode), with a confirmation message if
      applicable.
- [ ] **FORM-47** Top-left back arrow (form opened **from the library**,
      not from the backlog): behaves as a plain pop, no implicit save.
- [ ] **FORM-48** The system back gesture (swipe/hardware button) behaves
      exactly like the top-left back arrow in **every** context the form
      can be opened from — known regression already fixed with a
      `BackHandler`, explicitly verify it still holds (section 10).
- [ ] **FORM-49 (edge)** Form opened **from the backlog** (pre-filled from
      an item), exiting via back **before pressing the explicit
      checkmark**: the draft is still saved (if at least the title is
      present) and linked to the backlog item — verify with both the back
      arrow and the system back gesture.
- [ ] **FORM-50 (edge)** Same scenario but with the **title still empty**
      at the moment of going back: no "empty" draft must be created (the
      implicit save requires at least a title).
- [ ] **FORM-51 (edge)** Repeating the cycle "open a completed backlog item
      with no review → open the form (via the 'Write a review' link) →
      exit via back without explicitly saving → reopen again" multiple
      times: must not create a new duplicate draft each time if the item
      already has a linked draft (known regression, section 10 — verify
      carefully, it was the cause of a real multi-round bug).

### 3.7 Pre-filling from the backlog and automatic list move

- [ ] **FORM-52** Form opened from the backlog's "Write a review?" prompt:
      title/platforms/genres/dates/cover pre-filled from the item, with
      **status preset to "Completed"** (not the default "In progress").
- [ ] **FORM-53 (edge)** Backlog item without a `completedDate` set: the
      pre-filled form's end date falls back to today's date.
- [ ] **FORM-54** On the **first** successful save (explicit or implicit
      via back) of a review created from this flow, a "Move"/"Don't move"
      dialog appears asking to move the item to the "Completed with
      review" system list — the confirmation/exit navigation stays
      **suspended** until the user responds to this dialog.
- [ ] **FORM-55 (edge)** Editing at a later time (days later) a review
      already linked to a backlog item (opened via the "Review linked"
      link): saving does **not** re-trigger the list-move dialog (only on
      the original creation, guarded by `editingId == null`).
- [ ] **FORM-56 (edge)** Answering "Don't move" in the dialog: the review
      is still saved and linked, the backlog item stays in the list it was
      already in.

### 3.8 Markdown import ("replace form content")

Import-export spec v2 §2.2: single-review Markdown import moved from a
library-level "always create new review" action to a form-level "replace
form content" action — the upload icon is now in the **form's** top bar,
not the library's (2.7 covers the library's own zip import instead).

- [ ] **FORM-57** Opening the create/edit form and tapping the upload icon:
      opens the SAF picker (`ActivityResultContracts.OpenDocument`)
      filtered for/suited to `.md`/text files.
- [ ] **FORM-58** Importing a `.md` file exported by this app (2.6/2.7)
      while **editing an existing review**: the form's fields (title,
      platforms, genres, tags, score, dates, hours played, pros, cons,
      body) are overwritten with the file's content — the review being
      edited keeps its **own id**, verify no new review is created and no
      other existing review is touched, even if the imported file's own
      front-matter `id` matches a *different* review.
- [ ] **FORM-59** Same import while creating a **new** review (no
      `editingId`): the blank form is filled from the file, saving still
      creates a brand-new review afterward (import alone doesn't save).
- [ ] **FORM-60** A cover already set on the form before the import: after
      importing, the cover is **cleared** (a standalone `.md` pick never
      carries image bytes, so a referenced `coverImage` can never resolve
      — best-effort degrades to no cover) — verify the previous cover
      file is not left orphaned in storage and the form shows no cover.
- [ ] **FORM-61 (edge)** Importing a `.md` file missing a required
      front-matter field (`id`, `title`, `score`, `status`, or
      `startDate`): the form is left **completely untouched** (no partial
      field-by-field overwrite) and a snackbar states which field failed.
- [ ] **FORM-62 (edge)** Importing a file with an unrecognized `status:`
      value (anything other than `in_progress`/`completed`/`abandoned`):
      fails the same way, form untouched.
- [ ] **FORM-63 (edge)** Importing a completely unrelated file (e.g. a
      README `.md` from the internet, or a binary file renamed to `.md`):
      fails with a readable message, no crash, form untouched.
- [ ] **FORM-64 (edge)** Full roundtrip: export a review with every
      optional field filled in (multiple platforms/genres/tags, hours,
      pros, cons, developer/publisher/releaseYear if present) → open a
      **different** review's edit form → import the exported file →
      confirm every form-visible field matches the original exactly (the
      six backup-only fields — developer/publisher/releaseYear/
      metadataSource/externalId/linkedBacklogItemId — are *not* expected to
      appear anywhere in the form, they're parsed but never applied here).
- [ ] **FORM-65 (edge)** Cancelling the SAF picker during import: no crash,
      form unchanged.

---

## 4. Review detail screen

- [ ] **DET-01** Opening the detail screen shows all saved fields: cover,
      title, rating, status, platforms, genres, tags, start/end dates,
      hours played, pros, cons, review body.
- [ ] **DET-02 (edge)** A review with all optional fields empty (created
      with only title/rating/status/start date): the detail screen doesn't
      show broken empty sections/labels with no content.
- [ ] **DET-03** Export icon in the top bar: a menu with Markdown and PDF
      (single review).
- [ ] **DET-04** Markdown export produces Reddit-compatible text (title as
      `#`, metadata as bullets, Pros/Cons/body sections only if not
      empty).
- [ ] **DET-05** Single-review PDF export: a readable file with all the
      data, saved via SAF.
- [ ] **DET-06** Edit: opens the form pre-filled with the current data (see
      section 3.1 for validation).
- [ ] **DET-07** Delete: confirmation dialog "Delete this review? This
      action cannot be undone" before proceeding.
- [ ] **DET-08** Confirming deletion: the review is removed, navigates back
      to the library, the review no longer appears in any list/search.
- [ ] **DET-09 (edge)** Cancelling the delete dialog: no change, the review
      is still present and intact.
- [ ] **DET-10 (edge)** Deleting a review **linked to a backlog item**
      (`reviewId` set on that item): verify what happens on the backlog
      side — the "Review linked" link must stop pointing to a non-existent
      review without crashing when the item is reopened (possible bug if
      unhandled: no known cleanup logic for `reviewId` on deletion, verify
      explicitly).
- [ ] **DET-11 (edge)** Navigating to a review's detail screen, then
      deleting it from elsewhere (not really possible in this single-user
      app without multi-window, but at least verify the "delete, then
      quickly press back" case for a UI race condition).
- [ ] **DET-12 (edge)** A very long or emoji-laden review title in the
      detail screen's top bar: `maxLines = 1` + ellipsis, doesn't wrap and
      doesn't push the action icons out of view (known regression,
      section 10).

---

## 5. Backlog

### 5.1 List overview

- [ ] **BKL-01** Empty backlog: "No lists yet" / "Tap + to create your
      first list".
- [ ] **BKL-02** A lightweight aggregate statistics header (counts by
      status/list) visible above the list overview, updated live.
- [ ] **BKL-03** Tapping a list opens `BacklogListDetailScreen` with its
      items.

### 5.2 Creating / renaming / deleting / reordering lists

- [ ] **BKL-04** "+" FAB opens a "New list" dialog with a name field;
      confirming creates the list at the end.
- [ ] **BKL-05 (edge)** Creating a list with an empty/spaces-only name:
      verify whether it's blocked or whether it creates a list with no
      visible name (likely an unvalidated edge case, verify).
- [ ] **BKL-06 (edge)** Creating two lists with the **same name**: no known
      uniqueness constraint on the list name — verify it's allowed and
      that the UI still distinguishes them correctly (by id).
- [ ] **BKL-07** Pencil icon → rename dialog with the current name
      pre-filled; confirming updates the name everywhere it appears
      (filters, "move to list" dropdown, header).
- [ ] **BKL-08** Trash icon → confirmation dialog "Delete this list? Every
      item in '...' will be deleted. This cannot be undone."
- [ ] **BKL-09 (edge)** Deleting a **non-empty** list: all its items (and
      their comments/history) are deleted in cascade, verify no orphaned
      references remain in search/filters.
- [ ] **BKL-10 (edge)** Trying to delete/rename one of the two **system
      lists** ("Completed with review" / "Completed awaiting review") once
      created: verify whether it's allowed (no known protection in the
      code) — if so, assess whether it's acceptable behavior or a bug (the
      user could break them without knowing, and a future move trigger
      would recreate them from scratch via `getOrCreateSystemList`).
- [ ] **BKL-11** Up/down arrows reorder the lists; the order persists after
      the app is closed and reopened.
- [ ] **BKL-12 (edge)** Up arrow on the first list / down arrow on the last
      one: disabled or a no-op, no crash/out-of-range index.

### 5.3 Unified search and filter

- [ ] **BKL-13** Typing in the search field or enabling a filter
      automatically switches from the "list overview" view to a flat
      cross-list result view, each row showing which list it belongs to.
- [ ] **BKL-14** Available filters: list, status (5 values: To start, In
      progress, Completed, Abandoned, Paused), platform, genre.
- [ ] **BKL-15 (edge)** Filtering by "Paused" status: verify there really
      is a way for an item to reach this state from the `StatusEditor`
      (5 status chips), and that the filter finds it correctly — this
      status gets less visibility elsewhere in `CLAUDE.md`, verify its
      entire lifecycle.
- [ ] **BKL-16** Clearing search and filters goes back to the normal list
      overview.
- [ ] **BKL-17 (edge)** A search/filter that produces no results: "No
      results with the current filters", not the backlog's generic empty
      state.
- [ ] **BKL-17b (edge)** Text search matches **only on title and tags**
      (confirmed from the code) — **not** on platform, genre, or comments.
      Typing a platform/genre/comment name that doesn't also appear in the
      title/tags of any item must give "no results", even if that item
      exists and is visible when filtering by platform/genre from the
      filter panel.

### 5.4 Backlog export/import (zip)

- [ ] **BKL-18** Download icon in the backlog top bar: always exports the
      **entire backlog** (all lists, unfiltered) into a `.zip` file via
      SAF.
- [ ] **BKL-19** Upload icon: opens the SAF picker, imports a previously
      exported backlog zip (from this device or another one).
- [ ] **BKL-20** Import is **always additive**: creates **new** lists and
      items with new ids, never a replace/merge.
- [ ] **BKL-21** After import: "Imported X lists, Y items" with correct
      counts.
- [ ] **BKL-22 (edge)** Importing **the same file twice in a row**:
      produces duplicate lists/items (expected and documented behavior,
      not a bug) — verify it's still usable without crashing with double
      the data.
- [ ] **BKL-23 (edge)** Importing a malformed/corrupted zip file, or one
      not generated by this feature (e.g. any zip renamed with a `.zip`
      extension, missing the `data.json` entry): fails with **"Import
      failed: File non valido: manca data.json nell'archivio"** (exact
      message confirmed from the code — the inner message is hardcoded
      Italian regardless of app language, see `CLAUDE.md`), not a crash.
- [ ] **BKL-24 (edge)** Importing a backlog export archive whose item
      references a `reviewId` that does **not** exist on the destination
      device (the common case — the review usually lives only on the
      exporting device's library): the link is dropped, no phantom/dangling
      reference, no crash.
- [ ] **BKL-24b (edge)** Importing a backlog export archive whose item's
      `reviewId` **does** exist on the destination device (e.g. re-import a
      backlog you exported from this same device, or export/import between
      two libraries that happen to share a review id): verify the item
      **is** relinked to that review ("Review linked" shows and opens it) —
      this is the new best-effort round-trip behavior (import-export spec
      v2), distinct from BKL-24.
- [ ] **BKL-25 (edge)** Importing a backlog with comments/history: original
      timestamps are preserved (not replaced with "now"), and **no**
      synthetic extra history entry such as "Added to backlog" is injected
      on top of the imported history.
- [ ] **BKL-26 (edge)** Importing an item with a cover: the cover is
      re-saved under a new file name (doesn't collide with covers already
      present on the device, even if by coincidence the same UUID were
      already in use — unlikely, but verify nothing gets overwritten).
- [ ] **BKL-27 (edge)** Exporting an **empty** backlog (no lists): still
      produces a valid zip (even with an empty `data.json`), not a crash.
- [ ] **BKL-28 (edge)** Cancelling the SAF picker during export or import:
      no crash, no misleading message.

### 5.5 List detail screen (items, drag-to-reorder, grid view)

- [ ] **BKL-29** Empty list: "No items in this list", "Add to backlog"
      FAB.
- [ ] **BKL-30** Drag-to-reorder in list view: dragging from the dedicated
      "handle" icon (not the whole row) reorders the items; the final
      order is written **only once**, at the end of the gesture.
- [ ] **BKL-31 (edge)** Starting a drag and releasing **outside** the
      list's area (dragging past the edges): the item must not disappear
      or end up in an undefined position, it must stay in the list at a
      valid position.
- [ ] **BKL-32 (edge)** A normal tap (not a drag) on a row: opens the item
      detail screen, is **not** interpreted as the start of a drag (the
      gesture must be disambiguated only on the handle icon).
- [ ] **BKL-33** List/grid view toggle (`ViewModeToggle`, the same
      component used in the library) on the list detail screen.
- [ ] **BKL-34** In **grid** view, drag-to-reorder is **not available** —
      verify there's no leftover handle or a gesture that seems to work
      but doesn't save the order.
- [ ] **BKL-35 (edge)** Switching between list and grid view **while**
      mid-scroll partway through a list: no crash, the scroll position may
      reasonably reset but rendering must not break.

### 5.6 Backlog item form

- [ ] **BKL-36** Fields: title (required — reuses the same "Title is
      required" message from the review form, confirmed from the code, no
      dedicated duplicate string), platforms/genres/tags (same chip input
      as section 3.2, same lookup pool shared with reviews), cover.
- [ ] **BKL-37 (edge)** Selecting, in this form, a platform/tag already
      created from the review form (or vice versa): shared autocomplete,
      no separate list for the backlog.
- [ ] **BKL-38** "Search online" (TheGamesDB) here also pre-fills **year**
      and **developer** (in addition to what the review form offers, which
      doesn't have those fields).
- [ ] **BKL-39** After picking a TheGamesDB result, a HowLongToBeat search
      also kicks off automatically (silent on failure, but with a
      diagnostic message visible in the form — see BKL-40).
- [ ] **BKL-40 (edge)** Verify the `hltb_status_*` message shown after the
      online search: "estimate found" / "no match found" / "search
      failed — <detail>" — with no network or a title with no
      HowLongToBeat match, the form must not freeze or lose the other
      data already pre-filled from TheGamesDB.
- [ ] **BKL-41 (edge)** Creating an item **without ever** using "Search
      online" (everything filled in by hand): savable normally, no
      HowLongToBeat/year/developer field filled in.
- [ ] **BKL-42** Saving an item: goes back to the list detail screen, the
      new item at the end (last position) or in the correct position if
      editing.
- [ ] **BKL-42b (edge)** Unlike the review form (section 3.6), the backlog
      item form has **no** implicit draft save on the back button/gesture:
      exiting without pressing the checkmark is a plain pop, **no** item
      is created with partial data — verify this is really the case (no
      phantom item shows up in the list after going back mid-entry).

### 5.7 Backlog item detail screen

- [ ] **BKL-43** Shows title, cover, platforms/genres/tags, year,
      developer, HowLongToBeat estimate (only if at least one field is
      filled in), current status, history, comments.
- [ ] **BKL-44** `StatusEditor`: status selection (5 chips in a `FlowRow`,
      verify they wrap correctly with "Abandoned" — known regression,
      section 10) works as a **local, uncommitted** selection; a "Save"
      button (now a filled `Button`, no longer a hard-to-notice
      `TextButton`) appears **only** when the selection differs from what
      is actually saved.
- [ ] **BKL-45 (edge)** Selecting a status chip and then reselecting the
      original one (undoing the change before saving): the "Save" button
      must go back to **not** being shown (no unnecessary write).
- [ ] **BKL-46** Switching to "In progress" for the first time: `startDate`
      is auto-filled to today (only if not already set previously).
- [ ] **BKL-47** Switching to "Completed" for the first time:
      `completedDate` is auto-filled; if the item doesn't have a
      `reviewId` yet, the "Write a review? Do you want to write a review
      for '...'" prompt appears.
- [ ] **BKL-48 (edge)** An item that goes back to "In progress" after
      having been "Completed", then "Completed" again: the original
      `completedDate` is **not** overwritten the second time (only the
      first transition sets it).
- [ ] **BKL-49 (edge)** An item with `reviewId` already set that transitions
      to "Completed" again (e.g. from "Abandoned" to "Completed"): must
      **not** re-trigger the "write a review?" prompt (guarded by
      `reviewId == null`).
- [ ] **BKL-50** Selecting "Abandoned" shows/enables the "Reason for
      abandoning" free-text field; saving it generates a history entry
      only if the status actually changed (not on every keystroke).
- [ ] **BKL-51 (edge)** Setting/editing only the abandon reason **without**
      changing the status (item already "Abandoned"): verify whether the
      "Save" button still appears for the text-only change, and whether it
      generates a history entry (likely yes for the text, but without a
      new "status changed" entry since the status itself doesn't change).
- [ ] **BKL-52** Answering "Yes" to the "write a review?" prompt: navigates
      to the pre-filled form (section 3.7); `launchSingleTop = true`
      protects against a double tap queueing up two navigations.
- [ ] **BKL-53** Answering "No": the **second** dialog appears, "Move to
      list? '...' will be moved to the 'Completed awaiting review'
      list." — buttons "Move"/"Don't move".
- [ ] **BKL-54 (edge)** Closing the first dialog by tapping **outside** it
      (scrim, not a button): equivalent to "decide later" — **no** move
      happens, no second dialog, the item stays in its current list.
- [ ] **BKL-55** Confirming "Move" in the second dialog: the item is really
      moved to the "Completed awaiting review" system list (created on
      the fly if it doesn't exist yet), with a "Moved to ..." history
      entry.
- [ ] **BKL-56 (edge)** Repeating the "Completed → No → Move" flow with the
      app language set to **English**: the system list created the first
      time must have its name in whichever language it was created in
      **that first time**, and must not be duplicated (a second list with
      the same `systemKind` must not be created) even if the language is
      changed again afterwards.
- [ ] **BKL-57** An item already linked to a review (`reviewId` set):
      "Review linked" is a **clickable** (underlined) text that opens that
      review's detail screen directly — it never creates a new review
      (known regression, section 10).
- [ ] **BKL-58** A "Completed" item **without** a linked review: a
      persistent clickable "Write a review" link (not only at the exact
      moment of the status change), works from whichever list the item is
      in.
- [ ] **BKL-59** "Move to list" icon (folder with an up arrow): a dropdown
      with the other existing lists (excluding the current one); a
      selection moves the item and generates a history entry.
- [ ] **BKL-60 (edge)** With **only one** list existing in the entire
      backlog (the current one): the "move to list" icon is **disabled**
      (not a silent tap on an empty menu — known regression, section 10).
- [ ] **BKL-61** Comments section: adding a comment shows it immediately at
      the top/bottom with a timestamp, generates a "Comment added" history
      entry.
- [ ] **BKL-62 (edge)** Adding a comment that's **only spaces** or
      **empty**: confirmed from the code as a silent no-op (a blank
      comment is not added) — verify this holds from the UI as well (no
      empty comment in the list, no error feedback needed since it's not
      an error from the user's perspective, just an ignored tap).
- [ ] **BKL-63** History section: a chronological list of every event
      (Added, Status changed, Moved, Comment, Review linked) with readable
      detail (the actual status/destination list name, not a raw id).
- [ ] **BKL-64** Deleting an item: confirmation dialog "Delete this item?"
      before proceeding; the item is removed along with its comments/
      history.
- [ ] **BKL-65 (edge)** Deleting an item that has a linked review: verify
      what happens to the review (it should remain, since deleting a
      backlog item has no reason to cascade to the review — confirm it
      isn't deleted too).

---

## 6. Statistics

- [ ] **STAT-01** Empty library: "No reviews yet: add one to see your
      statistics" — verify whether this message also hides the
      HowLongToBeat backlog section, or whether that stays visible
      independently (documented as independent in `CLAUDE.md`).
- [ ] **STAT-02** With at least one review: total review count, average
      rating, total tracked hours (sum, `null` treated as 0).
- [ ] **STAT-03** Platform and genre distributions: horizontal bars,
      **without** a percentage (many-to-many, by design).
- [ ] **STAT-04** Status breakdown: a stacked segment bar + legend,
      **with** a percentage ("%1$d (%2$.0f%%)").
- [ ] **STAT-05 (edge)** All reviews with the same status (e.g. all
      "Completed"): the segmented bar shows a single segment at 100%,
      doesn't break with zero-width segments elsewhere.
- [ ] **STAT-06 (edge)** Average rating with a single recurring rating
      value (e.g. every review at 10.0 or every review at 0.0): correct
      calculation and display, no visible division by zero.
- [ ] **STAT-07** "Estimated backlog time (HowLongToBeat)" section:
      visible **only** if at least one backlog item has at least one
      HowLongToBeat field filled in, regardless of how many items are
      "Completed"/other statuses.
- [ ] **STAT-08** Sum of main story / main+extra / completionist hours
      across all backlog items with at least one HowLongToBeat field
      filled in, plus the "X items with an estimate" count (correct
      plural forms for 0/1/N — verify in particular the singular "1 item"
      vs the plural "2 items").
- [ ] **STAT-09 (edge)** A backlog with items that have **only one of the
      three** HowLongToBeat fields filled in (e.g. only main story, not
      main+extra): the sum of the other two columns must not misleadingly
      include that review/item as if it were 0 — verify the presentation
      (it should stay distinguishable as "no data" vs "0 hours").
- [ ] **STAT-10** Adding/editing a review or a backlog item with
      HowLongToBeat data while the Statistics screen is open in the
      background, then navigating back to it: the numbers update
      (reactive `Flow`), no manual pull-to-refresh needed.

---

## 7. Settings

### 7.1 Theme

- [ ] **SET-01** Three options, System/Light/Dark; selecting one
      immediately changes the theme of the **entire** app (not just the
      Settings screen), without requiring a manual restart.
- [ ] **SET-02** With "System" theme selected, changing the device's
      system theme (from Android settings) while the app is open in the
      foreground/background consistently updates the app's appearance.
- [ ] **SET-03** The theme preference persists after fully closing and
      reopening the app (a full process kill, not just home/back).
- [ ] **SET-04 (edge)** Verify that **every** screen (not just the ones
      tested most often) correctly respects the dark theme: readable
      text/background contrast everywhere, no leftover "white on white"
      text from hardcoded colors.

### 7.2 Language

- [ ] **SET-05** Three options, System/Italian/English; selecting one
      immediately changes the language of **all** UI text (verify that
      `AppCompatActivity` + `setApplicationLocales` trigger the refresh
      without requiring a manual app restart).
- [ ] **SET-06** The language preference persists after a full process
      kill (`autoStoreLocales`).
- [ ] **SET-07 (edge)** Setting "System" with the device's system language
      set to an **unsupported** language (e.g. French): the app must fall
      back to Italian (the project's default language), not crash or show
      mixed text/raw resource keys.
- [ ] **SET-08 (edge)** Verify a 1:1 match between `values/strings.xml` and
      `values-en/strings.xml` on less-frequently-hit screens (confirmation
      dialogs, export/import error messages, plurals) — no string that
      stays in Italian when the app is in English or vice versa.
- [ ] **SET-09** Exported files (Markdown/CSV/JSON/PDF): the labels
      **always stay in Italian** regardless of the language chosen for
      the app — intentional behavior, not a bug (see `CLAUDE.md` Phase 5);
      verify this really is the case and not a forgotten leftover from
      before internationalization.
- [ ] **SET-10** Changing language **during** a session with loaded data
      (e.g. a library with active filters): the data stays consistent,
      only the labels change — no crash from the activity being recreated
      mid-operation.

### 7.3 Google Drive backup/restore

- [ ] **SET-11** With `google_oauth_web_client_id` not configured (still
      at the placeholder value): shows the `DriveNotConfiguredCard` with
      an explanation, **not** a generic error after pressing login.
- [ ] **SET-12** With a valid configuration: "Sign in with Google" opens
      the system account picker (Credential Manager), then the consent
      request for the `drive.appdata` scope (`AuthorizationClient`).
      **Confirmed working end-to-end** on a real device — see CLAUDE.md,
      "Phase 4" section, "Confirmed working end-to-end", for the full
      story of what it took to get here (SET-36 through SET-36c below).
- [ ] **SET-12b** The Google account signing in must be listed under
      Google Cloud Console > OAuth consent screen > Audience/Test users
      (the consent screen intentionally stays in "Testing" publish
      status — a deliberate decision, not a bug, see CLAUDE.md). An
      account **not** on that list gets a hard-blocking Google page
      ("Accesso bloccato: l'app ... non ha completato la procedura di
      verifica di Google ... Errore 403: access_denied") **before** the
      app ever receives a token — this is not something the app's own
      error handling can intercept or improve, it never reaches
      `DriveAuthManager`.
- [ ] **SET-12c** Even after a fully successful sign-in + authorization
      (valid session, "Esci"/logout showing), the **first** Drive REST
      call can still fail with HTTP 403
      `SERVICE_DISABLED`/`accessNotConfigured` ("Google Drive API has not
      been used in project ... or it is disabled") if the **Google Drive
      API** itself was never explicitly enabled for the project under
      APIs & Services > Library — confirmed by a real device report. This
      is unrelated to authentication and happens later in the flow
      (backup/list/restore), not at login time.
- [ ] **SET-13** After a successful login: the account's email is shown
      ("Connected to Google Drive"), the Backup/Restore sections become
      visible (previously hidden).
- [ ] **SET-14 (edge)** Cancelling the account picker during login: no
      crash, remains in the "not connected" state, no misleading success
      message.
- [ ] **SET-15 (edge)** Granting login but **denying/cancelling** the Drive
      scope consent: "Drive authorization cancelled" (or "not completed"),
      the app doesn't switch to the connected state regardless.
- [ ] **SET-16** "Back up now": creates a zip archive on Drive (the
      private `appDataFolder`), shows "Backup complete", updates "Last
      successful backup: ...".
- [ ] **SET-16b** Retention: after "Back up now" completes, "Refresh
      backup list" shows **only one** file — the one just created. If
      several backups already exist on Drive from before this feature
      (a pile of `the-patient-gamer-helper-backup-*.zip` in the private
      `appDataFolder`, not visible/manageable from the Drive UI since it
      requires the `drive.appdata` scope), the very next backup — manual
      or automatic — deletes all of them except the one it just
      uploaded, with no separate "clean up now" action needed. Also
      verify the automatic worker (SET-19) prunes the same way, not just
      a manual backup.
- [ ] **SET-16c** With a library of a couple dozen reviews that all have
      covers (a mix of photo-picker and "Search online" covers), the
      reported backup size (SET-21) is on the order of a few MB, not tens
      of MB — a backup used to also carry every backlog item's cover and
      any orphaned cover left over from a cancelled form, neither of
      which the backup ever restores. If it's still large, check whether
      the backlog also has many items with covers (expected to *not*
      inflate the backup any more) versus the review covers themselves
      being unexpectedly large (would point at the downsample/compress
      step regressing).
- [ ] **SET-17 (edge)** Backing up with an **empty** library: still
      succeeds (a valid zip with an empty `data.json` and no images), not
      an error.
- [ ] **SET-18 (edge)** Backup with the network dropping/unavailable
      mid-upload: "Operation failed" (or a message with more detail),
      "Last error: ..." populated, no partial/corrupted archive left on
      Drive that would break a future backup listing.
- [ ] **SET-19** "Automatic backup" toggle: enabling it schedules the
      periodic worker (24h, requires network); disabling it cancels it.
- [ ] **SET-20 (edge)** Automatic backup with Drive consent
      **expired/revoked** by the user from outside the app (e.g. from
      myaccount.google.com): the worker fails silently (`Result.failure()`,
      no crash, no intrusive notification) — only verifiable indirectly:
      open Settings after the scheduled time and check that "Last error"
      reflects the failure, then run a manual backup to re-establish
      consent via the interactive flow.
- [ ] **SET-21** "Refresh backup list": lists the backups present on Drive
      with date and size ("%1$d KB").
- [ ] **SET-22 (edge)** No backups present on Drive yet: "No backups found
      on Drive", not a silently empty list indistinguishable from a
      network error.
- [ ] **SET-23** Tapping "Restore this backup": confirmation dialog
      "Restore this backup? All current local data (reviews and cover
      images) will be replaced with the contents of '...'. This cannot be
      undone." — "Restore" button.
- [ ] **SET-24** Confirming the restore: **all** current local
      reviews/covers are deleted and replaced with the chosen backup's
      contents (full overwrite, no merge) — "Restore complete".
- [ ] **SET-25 (edge)** ⚠️ **Destructive by design**: explicitly verify,
      on a local test dataset (not real data), that data created after
      the last backup (reviews added, edited, deleted in the meantime)
      **disappears** after a restore — this is expected behavior, not a
      bug, but it must be confirmed there is no misleading message
      suggesting a merge.
- [ ] **SET-26 (edge)** A restore interrupted mid-way (e.g. the app is
      closed or the network drops during archive download/decompression):
      verify the library's state afterwards — in the worst case, data
      partially deleted without being replaced would be a real data loss,
      to report with high priority if reproduced.
- [ ] **SET-27 (edge)** Restoring a backup that references covers that
      then actually get downloaded: verify the images show up correctly
      in the library after the restore, not just the text data.
- [ ] **SET-28** "Sign out" (logout): goes back to the "not connected"
      state, backup/restore become hidden again; **no** local data is
      touched by a simple logout.
- [ ] **SET-29 (edge)** Logging out and then logging back in with a
      **different** Google account: the backup list must reflect the
      current account, not show/mix backups from the previous one.
- [ ] **SET-36 (edge)** If SET-12 fails with no visible picker and no
      error message at all: check `adb logcat -s DriveAuthManager` — it
      now logs at every `signIn()`/`authorize()` step, and wraps
      `GetCredentialException`/`ApiException` with their `type`/
      `statusCode`. Report the exact tag output; if it says something like
      "no matching credential" or a `DEVELOPER_ERROR` status, the likely
      cause is a missing/mismatched companion **Android** OAuth client
      (SHA-1 of the exact keystore used for the tested build) in the same
      Google Cloud project as the "Web application" client id configured
      via `local.properties` — see CLAUDE.md, "Phase 4" section, "Device
      report: 'Sign in with Google' button does nothing" for the full
      writeup.
- [ ] **SET-36b (edge)** A device/emulator with **no Google account added**
      (Settings > Accounts): the bottom-sheet flow should fail internally
      with `NoCredentialException` ("No credentials available") and
      `signIn()` should transparently retry with the button-style
      `GetSignInWithGoogleOption` flow instead — verify the picker still
      appears (rather than the snackbar showing the raw "No credentials
      available" message, which was the confirmed real report that led to
      this fallback). If it still fails even from the button flow, check
      whether the device/emulator has Google Play Store (not just "Google
      APIs") and a Google account actually added.
- [ ] **SET-36c (edge)** If the account picker appears and picking an
      account fails with "\[16\] Account reauth failed" (a
      `GetCredentialCancellationException`): this is a confirmed symptom
      (matched against external Google Sign-In bug reports, not guessed)
      of the companion **Android** OAuth client's SHA-1 not matching the
      keystore that signed the installed APK. Get the SHA-1 of the actual
      APK under test — `./gradlew signingReport` for a local build, or the
      "Print release keystore SHA-1" step's log in the `build-apk.yml` run
      for a CI-built one — and register/correct it on the Android-type
      OAuth client in the same Google Cloud Console project as the Web
      client configured via `local.properties`. See CLAUDE.md, "Phase 4"
      section, for the full writeup. **Confirmed fixed**: registering the
      correct SHA-1 resolved this on the user's real device.
- [ ] **SET-37** Fresh checkout with no `local.properties` (or one missing
      `DRIVE_OAUTH_WEB_CLIENT_ID`): `./gradlew assembleDebug` still
      succeeds (falls back to the `[TO_COMPLETE]` placeholder), and the app
      shows `DriveNotConfiguredCard` rather than crashing or silently
      attempting sign-in — same behavior SET-11 already covers, now also
      exercised via the Gradle-injected path instead of a committed
      resource.
- [ ] **SET-38** Running `build-apk.yml` manually (Actions > Build APK >
      Run workflow) with the four `RELEASE_KEYSTORE_*`/`RELEASE_KEY_*`
      secrets set: produces a signed `app-release.apk` artifact (not
      `app-debug.apk`), `BuildConfig.SEED_DEBUG_DATA` is `false` (no demo
      data seeded on first launch), and the "Print release keystore SHA-1"
      step's log always reports the **same** SHA-1 across separate runs —
      confirming the keystore is now persistent, not regenerated per run.
- [ ] **SET-38b (edge)** Installing that release APK over a previously
      side-loaded **debug** build of the same app (different signing
      keys): the install is rejected (signature mismatch) — uninstall the
      old debug build first. Not a bug, an expected Android platform
      behavior worth knowing about before testing.
- [ ] **SET-39 (edge, long-running — not yet reproduced)** The OAuth
      consent screen intentionally stays in "Testing" publish status
      (see CLAUDE.md, "Confirmed working end-to-end"), and Google expires
      the underlying authorization grant after **7 days** for any app in
      that status, regardless of scope. Leave the app installed with
      automatic backup enabled and **no** manual login for more than a
      week, then check Settings: "Last error" on the backup section
      should reflect the automatic worker's failure (`Result.failure()`,
      silent by design — see SET-20), not a crash or a stuck "in
      progress" state. A manual login should re-establish it. If this
      never actually happens in practice, downgrade/remove this item.
- [ ] **SET-40** After logging in (SET-13), navigate away from Settings —
      via the drawer to any other section, **and** separately via the
      back arrow/system back gesture — and back to Settings again: the
      account should still show as connected ("Esci" visible,
      Backup/Restore sections visible), **not** back to the
      "Accedi con Google" button. A real bug (fixed, not yet verified on
      device): the back arrow used to destroy `SettingsViewModel`'s
      in-memory login state on every single visit, not just on an app
      restart — see CLAUDE.md, "Phase 7" section, "Google Drive login
      lost on every visit to Settings". Test **both** the tap-target back
      arrow and the system back gesture/button separately — they were two
      different bugs with two different fixes.

### 7.4 TheGamesDB API key

- [ ] **SET-30** API key text field; the "Save" button is **disabled**
      while the field is empty (`apiKey.isNotBlank()`).
- [ ] **SET-31** Saving a key: "API key saved", persisted
      (`SharedPreferences`), immediately available for "Search online" in
      the forms.
- [ ] **SET-32 (edge)** Pasting a key with leading/trailing spaces/newlines
      (e.g. from copy-paste): saved trimmed
      (`TheGamesDbPreferences.apiKey` calls `trim()`), must not fail due
      to leftover whitespace.
- [ ] **SET-33 (edge)** Saving an obviously invalid key (e.g. random
      text): the local save still succeeds (no client-side validation
      against the real API); the failure only surfaces on the first use
      of "Search online", with a detailed HTTP message (401/403).
- [ ] **SET-34** Fully clearing the field and trying to save again: the
      button goes back to being disabled, it's impossible to "save" an
      empty key that would silently disable search without the user
      noticing from the button's state alone.
- [ ] **SET-35 (edge)** A valid key with the monthly quota exhausted:
      verify that the error message shown in the form (section 3.5)
      reflects the real problem (quota, not "invalid key") if TheGamesDB
      communicates it distinguishably in the HTTP response.

---

## 8. Cross-cutting scenarios

### 8.1 Screen rotation / configuration change

- [ ] **CFG-01** Rotating the device (portrait ↔ landscape) on each main
      screen (Home, Library, Form, Detail, Backlog, Statistics, Settings)
      does not lose entered data/the current navigation state.
- [ ] **CFG-02 (edge)** Rotating while a dialog is open (e.g. delete
      confirmation, "move list" dialog): the dialog stays open and
      functional after rotation, doesn't silently disappear and lose its
      context.
- [ ] **CFG-03 (edge)** Rotating mid-way through an ongoing asynchronous
      operation (export, online search, backup): the operation is neither
      duplicated nor lost, the outcome still reaches the user.
- [ ] **CFG-04** Changing the system font size (accessibility, "large"/
      "largest") doesn't break critical layouts (chips wrapping correctly,
      top bars using ellipsis instead of overflowing).

### 8.2 Process death / activity recreation

- [ ] **CFG-05** With "Don't keep activities" enabled in developer
      options, backgrounding the app and reopening it from another app:
      returns to the same point with no crash (recreated from
      `SavedStateHandle`/navigation).
- [ ] **CFG-06 (edge)** Process death while the review form is open with
      unsaved changes: verify what happens to the not-yet-saved data
      (silent loss is expected in many Android apps, but confirm there's
      no partial/corrupted save).

### 8.3 Permissions and Storage Access Framework

- [ ] **CFG-07** First opening of the photo picker: no runtime permission
      dialog requested (by design, `PickVisualMedia`).
- [ ] **CFG-08** Every SAF operation (export, Markdown import, backlog
      export/import) always asks the user explicitly for the
      destination/source, never a fixed silent path.

### 8.4 Absent/unstable network

- [ ] **CFG-09** With no network at all: the app remains fully usable for
      all **local** functions (review/backlog CRUD, statistics, local
      file export/import via SAF) — only Drive/TheGamesDB/HowLongToBeat
      must degrade with clear messages.
- [ ] **CFG-10 (edge)** Network that drops **mid-call** (not absent from
      the start): a timeout handled with a message, not an indefinite UI
      hang (verify the hand-written HTTP clients have explicit
      connect/read timeouts, as documented for HowLongToBeat).

### 8.5 Extreme data / cross-cutting anomalous input

- [ ] **CFG-11** Titles/text with emoji, CJK characters, RTL (Arabic/
      Hebrew) in any text field across the app (review, backlog, list
      names, comments): displayed correctly wherever they appear (list,
      detail, export, statistics if involved in groupings).
- [ ] **CFG-12** Text with HTML/script markup (e.g.
      `<script>alert(1)</script>`, `<b>test</b>`) in a free-text field
      (review body, comment, abandon reason): treated as literal text
      everywhere it's shown in Compose (no injection risk since
      everything is rendered via `Text`/`StaticLayout`, not `WebView`) —
      verify it appears literally and is not "executed" or silently
      stripped.
- [ ] **CFG-13** Pasting text with control characters/multiple consecutive
      newlines into single-line fields (title): verify it doesn't break
      the layout (single-line fields should truncate the newlines, but
      this needs confirming).

### 8.6 Database migration (schema upgrade between versions)

- [ ] **CFG-14** Installing an older build (schema `version = 1`, reviews
      tables only), populating it with real data, then upgrading
      **without uninstalling** to the current build: existing reviews
      survive intact (additive migrations `MIGRATION_1_2` →
      `MIGRATION_2_3` → `MIGRATION_3_4`, never
      `fallbackToDestructiveMigration`).
- [ ] **CFG-15 (edge)** Same test starting from an intermediate schema
      (e.g. `version = 2`, with backlog but without the HowLongToBeat
      columns): verify that only `MIGRATION_2_3`+`MIGRATION_3_4` run, not
      the entire chain from 1.
- [ ] **CFG-16** After the migration, the new features (backlog,
      HowLongToBeat, system lists) are immediately usable without
      requiring any extra manual step from the user.

### 8.7 Multi-tasking

- [ ] **CFG-17** Minimizing the app during an ongoing export/backup/online
      search and reopening it after a few minutes: the operation has
      completed (if short enough to survive the lifecycle) or failed in a
      handled way, never a "stuck forever" state visible to the user.
- [ ] **CFG-18** Opening another memory-heavy app while
      ThePatientGamerHelper is in the background, then switching back: no
      OOM crash on screens with many loaded images (grid view with a
      large library).

### 8.8 Localization — see also 7.2

- [ ] **CFG-19** With the Android system language set to something other
      than Italian or English (e.g. Spanish) and the app preference set
      to "System": falls back to Italian, consistent with
      `locales_config.xml` (only it/en declared).

### 8.9 Basic accessibility

- [ ] **CFG-20** With TalkBack (or another screen reader) active:
      icon-only action buttons have a sensible `contentDescription`
      (verify in particular the less obvious ones: drag handle, move to
      list, grid/list view) — many are already present in `strings.xml`
      (`cd_*`), verify complete coverage across all screens.
- [ ] **CFG-21** Keyboard/D-pad-only navigation (if the device supports
      it, e.g. Chromebook/TV-like) on forms and dialogs: visible focus and
      a sensible tab order.

---

## 9. End-to-end paths (complete user scenarios)

- [ ] **E2E-01** Full lifecycle of a game: create a backlog item →
      "Search online" (TheGamesDB + HowLongToBeat) → move to "In
      progress" → add a comment → complete it → decline the immediate
      review prompt ("No") → confirm the move to "Completed awaiting
      review" → later, use the "Write a review" link → fill in and save →
      confirm the move to "Completed with review" → open "Review linked"
      from the backlog → edit the review from the detail screen → export
      to Markdown → verify the generated text.
- [ ] **E2E-02** End-to-end backup and restore across two devices (or two
      user profiles on the same device): create data on device A → manual
      backup to Drive → sign in on device B with the same account →
      restore → confirm the library and covers match.
- [ ] **E2E-03** Backlog export/import between two devices: export a zip
      from device A, transfer it (e.g. via email/personal drive, not the
      app's own backup), import it on device B → verify lists/items/
      covers/history are consistent.
- [ ] **E2E-04** Full Markdown roundtrip (already covered in LIB-52)
      repeated with the app language set to **English**: verify the
      exported file still stays in Italian (fixed labels) and that
      importing works identically regardless of the active UI language.
- [ ] **E2E-05** A user who never configures Drive, TheGamesDB, or
      HowLongToBeat: the entire app (review/backlog CRUD, statistics,
      local export/import, theme, language) remains fully functional
      using only its offline features.

---

## 10. Known regressions (real bugs already found and fixed — re-verify on every release)

List taken from `CLAUDE.md` (the "Fixes after manual device verification"
section and the ones that follow it). Each of these was a **real** bug
found only through manual on-device verification, not through static code
review alone — all the more reason not to skip them in future test rounds.

- [ ] **REG-01** The "Abandoned" `FilterChip` in the backlog detail
      screen's status selector no longer splits vertically character by
      character (fix: `FlowRow`).
- [ ] **REG-02** Top bar titles ("Reviews", "Backlog", long review/list
      titles) no longer wrap to two lines and overlap the hamburger/back
      icon (fix: `maxLines = 1` + ellipsis everywhere).
- [ ] **REG-03** TheGamesDB search with games that have `null` (not just
      absent) `genres`/`developers` no longer makes the entire search fail
      with an unreadable JSON error.
- [ ] **REG-04** HowLongToBeat: the client correctly follows HTTP 307/308
      redirects on all four calls in the flow (homepage/bundle/init/
      search), no longer returns a bare 308.
- [ ] **REG-05** HowLongToBeat: the regex that extracts the search endpoint
      from the `_app-*.js` bundle now requires `method: "POST"` within the
      same `fetch()` block, and no longer latches onto the first `fetch()`
      call in the bundle regardless of what it is (the cause of the
      previous round's 404).
- [ ] **REG-06** Backlog→review flow: "Review linked" is clickable and
      opens the existing review — duplicate reviews are no longer created
      by reopening the flow on an already-linked item.
- [ ] **REG-07** Explicit `BackHandler` in the review form: the system back
      gesture (swipe/hardware button) now behaves like the top-left back
      arrow, instead of a bare pop that used to discard the implicit
      save/backlog link.
- [ ] **REG-08** The "move to list" icon is disabled (no longer a silent
      tap on an empty `DropdownMenu`) when there's no other list to move
      to.
- [ ] **REG-09** The backlog `StatusEditor`'s "Save" button is a clearly
      visible filled `Button`, no longer an easy-to-miss `TextButton`.
- [ ] **REG-10** `ReviewFormViewModel` explicitly sets `status = COMPLETATO`
      (not the default `IN_CORSO`) when pre-filling from a completed
      backlog item.
- [ ] **REG-11** TheGamesDB: a desktop-browser `USER_AGENT` instead of a
      string that self-identifies as an app — verify online search no
      longer fails with "Invalid API key" on valid keys (suspected cause,
      to confirm as definitively fixed or still open at test time).
- [ ] **REG-12 (open, to monitor)** HowLongToBeat remains the app's most
      fragile integration (a reverse-engineered endpoint, no public API)
      — even though REG-04/REG-05 fixed two concrete, already-diagnosed
      causes, a new failure is **expected as possible** on any release
      (the site can change its bundle/protections at any time without
      notice). Do not treat a HowLongToBeat failure as automatically
      "the same bug as before": read the in-app diagnostic message
      (`hltb_status_error`, which includes the URL and the `source`) and
      report it in full.
- [ ] **REG-13** Cancelling the "write a review" form opened from a
      backlog item (Backlog → list → item → "write a review" → cancel/
      system back) no longer poisons the drawer's "Backlog" entry: verify
      that opening that flow and cancelling out of it, then tapping
      "Backlog" in the drawer from Library/Stats/Settings, lands on the
      Backlog list every time (not on the leftover form/review screen,
      which previously only self-corrected on a *second* tap). Root
      cause: that `onCancel` branch popped up to `Home` with
      `saveState = true` while discarding the whole
      Backlog → BacklogListDetail → BacklogItemDetail → Form chain;
      `NavController` keys saved back-stack state by the id of the first
      entry above the `popUpTo` target with a write-once guard per key,
      so that discarded chain got saved under the "Backlog" key and was
      restored wholesale on the next drawer tap to Backlog. Fix: drop
      `saveState = true` from that specific `popUpTo(Destination.Home)`
      call, since the chain is meant to be discarded, not preserved.

---

## Update history for this plan

- 2026-08-07 — First draft, covering every feature through Phase 8
  inclusive (Markdown import, backlog export/import, HowLongToBeat, grid
  views, system lists, multi-round post-device fixes).
- 2026-08-11 — Revised for `docs/reviews-backlog-import-export-spec-v2.md`:
  §2.6 gained ZIP export coverage (LIB-41b/c), §2.7 rewritten from
  single-file Markdown import to multi-review ZIP import (LIB-42–52,
  content-atomic validation, images always best-effort), a new §3.8 covers
  the single-review Markdown import that moved into the create/edit form
  (FORM-57–65), and §5.4's BKL-24 was split into BKL-24/BKL-24b to cover
  the new best-effort `reviewId` relink on backlog import. Not yet
  manually verified on device (see each phase's own "Build status" note in
  `CLAUDE.md`) — no new "Known regressions" entries added, since none of
  this has been through real-device verification yet.
- 2026-08-17 — REG-13 added: a real navigation bug reported from device
  use (cancelling the backlog "write a review" form could make the
  drawer's "Backlog" item open a stale leftover screen instead of the
  Backlog list, self-correcting only on a second tap), root-caused to a
  `saveState = true` on a `popUpTo(Destination.Home)` call that should
  have discarded the popped back stack instead of saving it, and fixed in
  `ThePatientGamerHelperNavGraph.kt`.
- 2026-08-18 — Cover image storage/backup bloat fix (see
  `docs/implementation-decisions.md`): FORM-33b, FORM-34b, FORM-39b/c,
  SET-16c added. Not yet manually verified on device — no "Known
  regressions" entry added for the cover-deleted-on-cancel issue this fix
  addresses, since it was found by code review, not device testing;
  re-verify FORM-33b specifically on-device before considering it closed.
