# Google Drive backup — design notes and external setup

Moved verbatim from `CLAUDE.md` ("Phase 4") on 2026-10-11 (issue #65); the rules that still bind are summarised in `CLAUDE.md`.


- **Authentication/authorization**: two distinct steps. (1) **Credential
  Manager** (`androidx.credentials`) for "Sign in with Google"
  (`GetGoogleIdOption`), with a fallback to `GetSignInWithGoogleOption`
  when `NoCredentialException` is thrown (bottom-sheet flow can't help
  when there are no accounts on the device). (2) **AuthorizationClient**
  (`com.google.android.gms.auth.api.identity`) to request the
  `drive.appdata` scope on that account. Both live in
  `data/drive/DriveAuthManager.kt`. Note: `AuthorizationClient` is
  distributed in the same Maven artifact as the deprecated
  `GoogleSignInClient` (`com.google.android.gms:play-services-auth`) —
  the dependency is unavoidable, but the deprecated class is never
  imported here.
- **Silent background authorization**: `DriveAuthManager.authorize()`
  called with only the `applicationContext` returns a fresh token with no
  UI if consent was already granted — used by `BackupWorker`. If consent
  is invalid, the worker fails silently (`Result.failure()`); the next
  manual backup re-establishes consent.
- **Drive REST API v3**: hand-written client in `data/drive/DriveApiClient.kt`
  using `java.net.HttpURLConnection` — **no dependency on
  `google-api-client`/`google-api-services-drive`** (heavy, brings Guava,
  for just three endpoints).
- **Backup format**: a single ZIP archive (`java.util.zip`) with
  `data.json` (`domain/backup/BackupPayload.kt`) and covers under
  `images/<file-name>`. `domain/backup` is deliberately **separate** from
  `domain/export`: the export format has Italian labels and an absolute
  cover path (not restorable elsewhere); the backup format carries only
  the cover's file name, resolved to a new path at restore time.
- **Backup/restore covers the whole library: reviews *and* the entire
  backlog** (every list — including which are system lists via
  `BacklogList.systemKind` — with its items, and each item's
  comments/history/cover/link back to its review). This was **not** true
  for a while after Phase 4 originally shipped: `BackupManager` only ever
  read/wrote reviews, so the backlog was silently unrecoverable from a
  Drive backup — found and fixed later, see
  `docs/implementation-decisions.md`, "Google Drive backup/restore
  silently excluded the entire backlog". `BackupPayload.backlogLists`
  defaults to an empty list so backups taken before the fix still decode.
- **Restore**: `BackupManager.restoreBackup()` decompresses, deletes all
  local covers, calls `ReviewRepository.replaceAll()` **then**
  `BacklogRepository.replaceAll()` (reviews first: backlog items'
  `reviewId` foreign key needs the review row to already exist) — each a
  single transaction that deletes and re-inserts everything, preserving
  every id and timestamp. **No merge/conflict handling**: single-user
  app, a restore is a full overwrite.
- **Retention: only the latest backup is kept on Drive.** Each backup is
  a full snapshot, so a single-user app has no use for a growing history
  of them, and the private `appDataFolder` isn't visible/manageable from
  the regular Drive UI (the app is the only way to clean it up).
  `BackupManager.createBackup()` uploads, then calls
  `pruneOldBackups()`, which lists the folder and deletes every file
  except the one just uploaded (best-effort per file via
  `DriveApiClient.deleteBackup()` — `DELETE /drive/v3/files/{id}`; a
  failed deletion doesn't fail the backup that already succeeded, the
  next run just tries again). This applies to both manual and automatic
  (`BackupWorker`) backups, and also cleans up any backlog of old backups
  left over from before this was added — no separate "clean up now"
  action needed, the next backup does it.
- **Automatic backup**: `BackupWorker` (`@HiltWorker`, WorkManager), fixed
  daily cadence (`BackupScheduler`, `NetworkType.CONNECTED`), no UI to
  configure the interval. `ThePatientGamerHelperApplication` implements
  `Configuration.Provider`; the manifest explicitly removes
  `androidx.startup.InitializationProvider` (`tools:node="remove"`) —
  Android Lint's `RemoveWorkManagerInitializer` requires this or the
  build fails.
- **Persisted state**: `BackupPreferences` (`SharedPreferences`) holds the
  automatic-backup toggle and the last backup's outcome.
- **Explicit login, not implicit**: `SettingsUiState.signedInEmail` tracks
  the session's "logged in" state (in-memory only, not persisted — app
  restart requires a new login). A single "Sign in with Google" button
  performs `signIn()` + `authorize()` together; subsequent actions only
  call `authorize()` (silent) via `ensureAccessToken()`.
  `DriveAuthManager.isConfigured()` detects the placeholder OAuth client
  ID and shows `DriveNotConfiguredCard` instead of a generic error.
- **External configuration required**: the OAuth web client ID goes in
  `local.properties` (`DRIVE_OAUTH_WEB_CLIENT_ID`, gitignored, not
  committed) or the `DRIVE_OAUTH_WEB_CLIENT_ID` GitHub Actions secret for
  CI. A companion **Android**-type OAuth client must also exist, with the
  SHA-1 of the *exact keystore* signing the tested build — a mismatch is
  a well-known, silent source of Sign-In failures (see
  `docs/phase-history.md` for the full multi-round debugging story). The
  Google account signing in must be added as a test user (OAuth consent
  screen stays in "Testing" mode), and the Google Drive API must be
  explicitly enabled on the Cloud project, or every Drive call fails with
  `SERVICE_DISABLED` even after a successful sign-in.
- **Release builds are signed with a persistent keystore**, not an
  ephemeral per-run debug one — otherwise the registered SHA-1 goes stale
  on every CI build. Secrets: `RELEASE_KEYSTORE_BASE64`,
  `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
- **Known limitation, not yet addressed**: with the OAuth consent screen
  in "Testing" mode, Google expires the authorization grant after 7 days.
  The interactive login just re-prompts, but the daily `BackupWorker` can
  start failing silently past that window until the user logs in again
  manually. Moving the consent screen to "In production" (optional,
  Google Cloud Console, a `drive.appdata` scope doesn't need full
  verification) would fix this but hasn't been done.
- **Cannot be meaningfully tested via Robolectric**: `HttpURLConnection`
  calls to `googleapis.com`, Credential Manager, and `AuthorizationClient`
  require real network/Play Services. What's unit-tested instead: DTO/JSON
  mapping (`domain/backup/BackupPayloadTest.kt`, including the backlog
  DTOs), the zip archive (`data/backup/BackupArchiveTest.kt`, Robolectric)
  and `ReviewRepositoryImpl.replaceAll()`/`BacklogRepositoryImpl.replaceAll()`
  (Robolectric). The auth flow must be verified by hand on a device/
  emulator with Play Services — so must the end-to-end backup→restore
  round trip for a real backlog, since none of the above tests exercise
  `BackupManager` itself.

Full incident history for this phase (a multi-round "Sign in with Google"
debugging saga, client-ID rotation, persistent-signing setup) is in
`docs/phase-history.md`.
