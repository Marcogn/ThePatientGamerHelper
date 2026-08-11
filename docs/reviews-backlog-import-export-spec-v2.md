# Reviews & Backlog — Import / Export / Backup / Restore Specification (v2)

**Status: authoritative.** Supersedes `reviews-backlog-import-export-spec.md`
(v1) in full. Where the two differ, this document wins — see §5 for what
changed and why.

**Purpose:** use this to verify the existing implementation and modify the
code wherever it diverges from what's defined here.

---

## 1. What this changes vs. `markdown-backup-format-spec.md`

- **Backlog backup/restore uses a single JSON file, not per-item Markdown
  files.** The backlog Markdown templates in that document (§6–§7,
  `_list.md` and per-item `.md`) are dropped. Reviews still use per-review
  Markdown exactly as defined there in §5.
- **Batch import of reviews is atomic on content, not on images**: any
  malformed review `.md` blocks the whole batch; a missing or absent
  image never does. See §2.4.
- **Batch import of the backlog is atomic on the JSON only**: a malformed
  JSON blocks the restore; images are never a validation gate. See §3.2.

---

## 2. Reviews

### 2.1 Export — single review
Available once a review has been written and saved. Two formats, user's
choice: Markdown (per `markdown-backup-format-spec.md` §5) or PDF.
PDF layout: use the configured template if one exists (§2.6); otherwise
fall back to a plain, unstyled PDF with the same content.

### 2.2 Import — single review
This is a **"replace form content" action inside the review create/edit
screen**, not a database upsert-by-id. It parses the provided `.md` file
and overwrites the current form's fields (title, platform, genre, tags,
score, dates, hours played, pros, cons, body text, cover image reference)
with what the file contains.

The `id` in the file's front matter is **ignored** for this operation —
the review being created/edited keeps its own identity; only its content
is replaced.

Validation: full pre-parse validation against the §5 schema in
`markdown-backup-format-spec.md` (YAML parses, required fields present,
enum values valid). Any failure → import nothing, leave the form exactly
as it was, show a system warning stating what failed. No partial,
field-by-field import.

### 2.3 Export — multiple reviews
Triggered from the review library with more than one review selected (or
"export all"). User picks one of two mutually exclusive outputs:

- **ZIP** — one `.md` file per review (§5 schema) plus an `imgs/` folder.
  The `imgs/` folder is included **only if at least one** of the exported
  reviews has a cover image; if none do, the zip contains no image folder
  at all.
- **PDF** — a single structured document covering all selected reviews:
  title, cover, review data laid out per review. Uses the configured
  template if present, plain layout otherwise (§2.6).

These serve different purposes: the ZIP is round-trip importable, the PDF
is not (§2.5).

### 2.4 Import — multiple reviews
Input: a ZIP with one `.md` per review, optionally an image folder.

Validation is atomic on review **content**; images are always
**best-effort**, never a validation gate:
- Every `.md` file must pass the same validation as §2.2 (YAML parses,
  required fields present, enums valid). If **any** file fails, import
  nothing at all; show a warning listing which file(s) failed and why.
- If the image folder is present, matching images are imported and linked
  via `coverImage`. If the folder is absent, empty, or a specific
  referenced image is missing, the corresponding review is simply
  imported without a cover — this never blocks the rest of the batch and
  never produces its own warning.
- Once all `.md` files validate, the import commits, upserting by `id`
  from front matter: known id → overwrite, unknown id → insert.

### 2.5 Backup / Restore — reviews
**Backup = the ZIP export path of §2.3, always.** The PDF option in §2.3
is a display/sharing artifact only — it is never used for backup, since it
isn't machine-parseable for restore.
**Restore = the ZIP import path of §2.4**, unchanged.

### 2.6 PDF template hook
The PDF generator checks for a configured template (e.g. a
`PdfTemplateProvider` abstraction or config value) before rendering. If
present, apply it. If absent — the expected state today, since the
template "will be added later" — render a plain PDF with the same
content, unstyled. Build this as a seam now; don't hardcode a single
layout that has to be torn out when the template arrives.

---

## 3. Backlog

Scope is deliberately narrower than reviews: **only full backup and full
restore exist.** No single-list export, no single-item export/import, no
partial backlog operations of any kind.

### 3.1 Backup
A ZIP containing exactly **one JSON file** with the complete backlog: all
lists, all items per list, and all their data — including comments and
history, not just the core fields. Plus an image folder for cover images,
using the same deduplication rule as reviews
(`markdown-backup-format-spec.md` §8 still applies to image handling).

### 3.2 Restore
Input: a ZIP containing the JSON file and, optionally, an image folder.
Validation, atomic, pre-write — but only the JSON is a validation gate:
- JSON must parse and match the expected schema. If malformed → import
  nothing, warn.
- Images are best-effort, not a validation gate. If the image folder is
  present, matching images are imported and linked to their items. If the
  folder is absent, empty, or a specific referenced image is missing, the
  corresponding item is simply imported without a cover — this never
  blocks the rest of the import, and never produces a warning on its own.

---

## 4. Image handling — unified rule (reviews and backlog)

To state it once, plainly, since it now applies identically in both
places: **images never gate an import.** The only thing that can reject
an import is malformed *content* — a broken review `.md` or a broken
backlog JSON. A missing, empty, or partially-missing image folder always
degrades gracefully to "import the data, skip the cover," never to
"reject everything."

---

## 5. Changelog vs. v1

- Reviews multi-import (§2.4): images are now best-effort, matching the
  backlog behavior. v1 required the image folder to be structurally valid
  and fully consistent with front-matter references, rejecting the whole
  batch otherwise — that gate is removed.
- Backlog restore (§3.2): carried over unchanged from the v1 resolution
  (images already best-effort there); restated here for consistency now
  that both entities follow the same rule (§4).
- v1's "open point" framing is gone — both decisions are settled, not
  pending.
