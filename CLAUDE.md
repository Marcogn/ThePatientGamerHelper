# CLAUDE.md

Guida per agenti Claude che lavorano su questo repository. Leggi anche
`spec-app-recensioni-videogiochi.md` per la specifica funzionale completa.

## Cos'è questo progetto

App Android nativa, single-user, offline-first per gestire recensioni di
videogiochi (flusso personale per r/patientgamer). Kotlin + Jetpack Compose +
Material 3, Room, Hilt, ViewModel/StateFlow con unidirectional data flow.

## Stato di avanzamento per fasi

- **Fase 1 — MVP locale**: ✅ completata (CRUD, libreria con
  ricerca/filtri/ordinamento, dettaglio, form crea/modifica, copertina immagine).
- **Fase 2 — Export**: ✅ completata (JSON/CSV per l'intera libreria, Markdown
  compatibile Reddit per singola recensione, PDF nativo per singola
  recensione e libreria in batch). Vedi sezione dedicata sotto.
- **Fase 3 — Statistiche libreria**: ✅ completata (nuova schermata Statistiche
  raggiungibile dalla libreria: totali/medie, distribuzione piattaforma/genere,
  ripartizione per stato). Vedi sezione dedicata sotto.
- **Fase 4 — Backup cloud Google Drive**: ✅ completata (backup manuale +
  automatico via WorkManager, ripristino da elenco backup). Vedi sezione
  dedicata sotto.
- **Export DOCX**: **deciso di non implementarlo**, non solo rimandato. Vedi
  "Export DOCX — perché non è stato implementato" sotto.

Non implementare funzionalità non ancora presenti in questo file o nella
spec a meno che l'utente non lo richieda esplicitamente in una nuova
sessione.

## Decisioni di prodotto già prese (non richiederle di nuovo)

- Scala voto: **0–10 con un decimale** (es. 7.3), non stelle.
- Stato recensione: enum `IN_CORSO` / `COMPLETATO` / `ABBANDONATO`.
- Una sola recensione per gioco (nessun replay/rigioco nell'MVP).
- Copertina immagine: **implementata** in Fase 1 (photo picker + copia in
  storage interno app, nessun permesso runtime richiesto grazie a
  `ActivityResultContracts.PickVisualMedia`).
- Piattaforma e Genere: **relazione many-to-many** con la recensione (un
  gioco può uscire su più piattaforme / avere più generi), modellate come
  tabelle di lookup con tabelle ponte, per garantire autocomplete coerente.
- Tag personalizzati: stesso pattern di Piattaforma/Genere (tabella lookup +
  tabella ponte), per coerenza di modello e autocomplete.
- Pro/Contro: tabella figlia relazionale (`review_pro_con`) con un campo
  `tipo` (PRO/CONTRO) e `posizione` per l'ordine, non stringhe concatenate.
- Export JSON/CSV: **sempre l'intera libreria**, ignora i filtri attivi in
  UI (un backup deve essere completo).
- Export PDF in batch: **un unico file PDF multi-pagina** con tutte le
  recensioni (non uno zip di PDF separati) — coerente con SAF
  `ACTION_CREATE_DOCUMENT`, che fa scegliere una singola destinazione.

## Package/architettura

```
com.marcogn.gamereviewer
├── data/
│   ├── local/
│   │   ├── entity/      # Entità Room (Review, Platform, Genre, Tag, cross-ref, ProCon)
│   │   ├── dao/          # DAO Room, esposti come Flow
│   │   └── Converters.kt # TypeConverter per LocalDate/Instant/enum
│   ├── repository/       # Implementazioni dei repository (upsert transazionale)
│   ├── export/            # I/O Android per l'export: ExportFileWriter (SAF),
│   │                      # PdfReviewRenderer (PdfDocument), ReviewExporter (classe
│   │                      # concreta iniettata via Hilt, come ImageStorage — non
│   │                      # un'astrazione interfaccia/impl come i repository)
│   ├── drive/             # Client REST Drive v3 (DriveApiClient, HttpURLConnection)
│   │                      # + auth (DriveAuthManager: Credential Manager + AuthorizationClient)
│   ├── backup/            # Orchestrazione backup/restore: BackupManager, archivio zip
│   │                      # (BackupArchiveBuilder/Reader), BackupWorker (WorkManager +
│   │                      # Hilt), BackupScheduler, BackupPreferences (SharedPreferences)
│   └── debug/            # DebugSeeder, attivo solo dietro BuildConfig.SEED_DEBUG_DATA
├── domain/
│   ├── model/            # Modelli di dominio puri (no dipendenze Android)
│   ├── filter/            # Logica di filtro/ordinamento libreria, pure function, unit-testata
│   ├── export/            # Formattazione export pura: JSON (kotlinx.serialization),
│   │                      # CSV (writer manuale), Markdown (template stringhe) —
│   │                      # nessun import Android, unit-testabile in JVM puro
│   └── backup/            # Formato di backup puro: BackupPayload/BackupReviewDto,
│                          # mapping Review<->DTO, naming file — stesso pattern di domain/export
├── di/                    # Moduli Hilt (Database, Repository)
└── ui/
    ├── theme/             # Tema Material 3 (Compose)
    ├── navigation/        # Navigation Compose, route type-safe (kotlinx.serialization)
    ├── library/           # Schermata libreria (lista, ricerca, filtri, ordinamento, export)
    ├── detail/            # Schermata dettaglio recensione (+ export singola recensione)
    ├── form/              # Form crea/modifica
    ├── settings/           # Schermata Impostazioni: backup manuale/automatico, ripristino
    └── common/            # Composable condivisi (chip input, rating, date picker, ecc.)
```

Regola guida: **Room è la single source of truth**, esposta via `Flow`. I
ViewModel combinano il flow di dati con lo stato UI locale (query di ricerca,
filtri selezionati) usando `combine()`, producendo un unico `StateFlow` di UI
state consumato dalla Compose UI (pattern UDF: eventi salgono via lambda,
stato scende via `StateFlow`).

La logica di filtro/ordinamento vive in `domain/filter` come funzioni Kotlin
pure (nessun import Android), per essere unit-testabile in JVM puro senza
bisogno dell'SDK Android o di Robolectric.

## Fase 2 — Export

- **JSON/CSV**: sempre sull'intera libreria, **non filtrata** (un backup deve
  essere completo indipendentemente dai filtri attivi in UI). Punto di
  ingresso: menu nella top bar della libreria.
- **Markdown**: singola recensione, sintassi compatibile Reddit — titolo come
  `#`, metadati come bullet list (non trailing-space hard break, che sparisce
  facilmente in clipboard/editor prima di arrivare su Reddit), sezioni
  Pro/Contro/corpo solo se non vuote. Punto di ingresso: menu nella top bar
  del dettaglio.
- **PDF**: sia singola recensione che libreria intera in un unico file
  multi-pagina (un file per la SAF `ACTION_CREATE_DOCUMENT`, non uno zip).
  `android.graphics.pdf.PdfDocument` nativo — **non PDFBox né iText** (iText7
  è AGPL, esplicitamente escluso dalla spec). Impaginazione tramite
  `StaticLayout` + `Canvas.translate`/`clipRect` per affettare un unico
  layout su più pagine; ogni recensione in batch inizia sempre su una pagina
  nuova.
- Salvataggio file: **sempre** Storage Access Framework
  (`ActivityResultContracts.CreateDocument`), mai scritture dirette su
  storage esterno — coerente con scoped storage.
- `domain/export` è puro Kotlin (formattazione), `data/export` è dove vive
  l'I/O Android (SAF, `PdfDocument`). `ReviewExporter` è una classe concreta
  con `@Inject constructor`, non un'interfaccia con binding Hilt come i
  repository — non è un'astrazione di dominio sostituibile, è un utility di
  I/O (stesso pattern di `ImageStorage`).
- **`PdfDocument` non è testabile in modo significativo via Robolectric**: la
  serializzazione PDF dipende da codice nativo che Robolectric non fornisce
  (a differenza di SQLite/Room, shadowato bene). `PdfReviewRendererTest`
  testa solo `buildReviewText()` (costruzione dello `SpannableStringBuilder`,
  nessun rendering reale) — se tocchi la logica di impaginazione/pagine,
  verificala a mano in Android Studio con un device/emulatore.

## Fase 3 — Statistiche libreria

- Metriche calcolate: numero totale recensioni, voto medio, ore totali
  tracciate (somma `oreGioco`, `null` trattato come 0), distribuzione per
  piattaforma, distribuzione per genere, ripartizione percentuale per
  `stato` (`IN_CORSO`/`COMPLETATO`/`ABBANDONATO`).
- `domain/stats/LibraryStatisticsCalculator.kt`: funzione pura
  `computeLibraryStatistics(List<Review>): LibraryStatistics`, nessun import
  Android, unit-testata in JVM puro (`domain/model/LibraryStatistics.kt` per
  i modelli) — stesso pattern di `domain/filter`.
- Le distribuzioni piattaforma/genere **non** portano una percentuale: sono
  campi many-to-many (una recensione può avere più piattaforme/generi), quindi
  le quote non sommerebbero a 100% e una percentuale sarebbe fuorviante. Solo
  la ripartizione per `stato` (campo singolo) espone una percentuale, come
  richiesto dalla spec. Vedi `docs/decisioni-implementazione.md`.
- UI: nuova schermata `ui/stats/StatsScreen.kt` (+ `StatsViewModel`,
  `StatsUiState`), raggiungibile da un'icona nella top bar della libreria
  (`ui/library/LibraryScreen.kt`). Le distribuzioni sono barre orizzontali
  costruite con Compose nativo (`Box` + `fillMaxWidth(fraction = ...)`), la
  ripartizione per stato è una barra impilata a segmenti + legenda — **nessuna
  nuova dipendenza di charting introdotta** (niente Vico): con al più una
  manciata di piattaforme/generi per una libreria single-user, la complessità
  di una libreria di grafici non è sembrata giustificata. Se in futuro le
  distribuzioni dovessero diventare più ricche (es. grafici a torta, trend nel
  tempo), rivalutare Vico prima di scrivere altro codice di rendering a mano.
- Route di navigazione: `Destination.Stats` in
  `ui/navigation/Destinations.kt`, wiring in `GameReviewerNavGraph.kt`.

## Fase 4 — Backup cloud Google Drive

- **Autenticazione/autorizzazione**: due passi distinti, come da spec
  sezione 6.
  1. **Credential Manager** (`androidx.credentials`) per l'accesso "Accedi
     con Google" (`GetGoogleIdOption`), per far scegliere/confermare
     all'utente l'account Google.
  2. **AuthorizationClient** (`com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient`)
     per richiedere lo scope `drive.appdata` su quell'account.
  Entrambi vivono in `data/drive/DriveAuthManager.kt`.
  - **Nota su `play-services-auth`**: la richiesta esplicita era "non
    `GoogleSignInClient`/`play-services-auth` perché deprecato". In pratica
    `AuthorizationClient` (pacchetto `com.google.android.gms.auth.api.identity`,
    **non** `com.google.android.gms.auth.api.signin`) è distribuito proprio
    nell'artefatto Maven `com.google.android.gms:play-services-auth` — non
    esiste un artefatto separato. La parte deprecata è la classe
    `GoogleSignInClient`/`GoogleSignInOptions` (pacchetto `...auth.api.signin`),
    non l'intero artefatto: qui non viene mai importata. La dipendenza
    Gradle è quindi necessaria, ma il codice non tocca l'API deprecata —
    scelta verificata contro la documentazione Android Identity Services
    citata nella spec, non un'interpretazione libera. Se preferisci evitare
    del tutto quell'artefatto Maven anche solo per principio, dimmelo: è
    l'unico modo noto per ottenere un access token Drive con
    l'`AuthorizationClient` moderno.
  - **Autorizzazione silenziosa in background**: `DriveAuthManager.authorize()`
    chiamato con il solo `applicationContext` (nessuna Activity) restituisce
    un token fresco senza UI se il consenso è già stato concesso in
    precedenza — è quello che usa `BackupWorker` per i backup automatici. Se
    il consenso non è (più) valido, il worker fallisce silenziosamente
    (`Result.failure()`, nessun crash, nessun prompt): la prossima volta che
    l'utente apre Impostazioni e fa un backup manuale, il flusso interattivo
    ristabilisce il consenso.
- **Drive REST API v3**: client scritto a mano in
  `data/drive/DriveApiClient.kt` con `java.net.HttpURLConnection` — **niente
  dipendenza da `google-api-client`/`google-api-services-drive`** (il client
  Java ufficiale di Google), che porta con sé Guava e un grafo di
  dipendenze pesante per tre soli endpoint (upload multipart, list,
  download). Coerente con l'indicazione esplicita di CLAUDE.md di non
  aggiungere dipendenze senza necessità reale.
- **Formato del backup**: un unico archivio ZIP (`java.util.zip`, nessuna
  dipendenza) con `data.json` (l'intera libreria, DTO in
  `domain/backup/BackupPayload.kt`) e le copertine sotto `images/<nome-file>`.
  `domain/backup` è deliberatamente **separato** da `domain/export`
  (Fase 2): l'export Fase 2 è un formato rivolto all'utente con etichette in
  italiano e un percorso assoluto per la copertina (non riusabile per un
  restore su un altro device/installazione); il formato di backup porta
  invece solo il nome file della copertina (`coverImageFileName`), risolto a
  un path assoluto nuovo al momento del restore.
- **Ripristino**: `BackupManager.restoreBackup()` scarica l'archivio, lo
  decomprime, cancella tutte le copertine locali
  (`ImageStorage.clearAll()`) e chiama `ReviewRepository.replaceAll()` — un
  nuovo metodo sul repository che, in un'unica transazione, cancella
  interamente `reviews`+lookup (`platforms`/`genres`/`tags`, con cascade su
  cross-ref e pro/con) e reinserisce ogni recensione preservando
  `id`/`createdAt`/`updatedAt` dal backup (a differenza di `save()`, pensato
  per il form e non per un restore). **Nessuna gestione di merge/conflitti**:
  è un'app single-user, un restore è una sovrascrittura completa, come da
  richiesta esplicita.
- **Backup automatico**: `BackupWorker` (`@HiltWorker`, WorkManager) con
  cadenza fissa giornaliera (`BackupScheduler`, 24h,
  `NetworkType.CONNECTED`) — nessuna UI per configurare l'intervallo, stesso
  principio "non over-engineerare" già applicato in Fase 3.
  `GameReviewerApplication` implementa `Configuration.Provider` per iniettare
  `HiltWorkerFactory`; il manifest rimuove esplicitamente
  `androidx.startup.InitializationProvider`
  (`tools:node="remove"`) — necessario perché Android Lint
  (`RemoveWorkManagerInitializer`) lo richiede quando `Application`
  implementa `Configuration.Provider`, altrimenti la build fallisce (vedi
  sezione "Bug reali trovati solo grazie alla CI" sotto).
- **Stato persistito**: `BackupPreferences` (semplice `SharedPreferences`,
  niente DataStore per tre soli flag) tiene il toggle "backup automatico" e
  l'esito dell'ultimo backup (timestamp/errore), scritti da
  `BackupManager.createBackup()` così sia il pulsante manuale che il worker
  periodico aggiornano lo stesso stato mostrato in Impostazioni.
- **UI**: nuova schermata `ui/settings/SettingsScreen.kt` (+
  `SettingsViewModel`, `SettingsUiState`), raggiungibile da un'icona
  ingranaggio nella top bar della libreria. Il flusso di consenso
  interattivo (`AuthorizationResult.hasResolution() == true`) usa lo stesso
  pattern già in uso per gli export (`rememberLauncherForActivityResult`),
  con un ponte `StateFlow<IntentSenderRequest?>` +
  `CompletableDeferred<ActivityResult>` nel ViewModel per sospendere la
  coroutine di autorizzazione finché l'utente non risponde al consenso.
  Route: `Destination.Settings` in `ui/navigation/Destinations.kt`.
- **Configurazione esterna richiesta** (fuori dallo scope di queste
  modifiche): `res/values/drive_config.xml` contiene
  `google_oauth_web_client_id` con placeholder `[DA_COMPLETARE]` — va
  sostituito con il client ID OAuth "Web application" creato in Google
  Cloud Console (progetto con schermata di consenso in modalità testing,
  client ID Android con lo SHA-1 del certificato di firma). Se lasciato al
  placeholder, `DriveAuthManager` lancia `DriveNotConfiguredException` con
  un messaggio esplicito invece di tentare il sign-in.
- **Non testabile in modo significativo via Robolectric**: le chiamate
  `HttpURLConnection` verso `googleapis.com`, Credential Manager e
  `AuthorizationClient` richiedono rete reale/Play Services — stesso discorso
  già fatto per `PdfDocument` in Fase 2. Sono invece unit-testati: il mapping
  DTO/JSON (`domain/backup/BackupPayloadTest.kt`), l'archivio zip
  (`data/backup/BackupArchiveTest.kt`, Robolectric con `ImageStorage` reale)
  e `ReviewRepositoryImpl.replaceAll()`
  (`data/repository/ReviewRepositoryImplTest.kt`, Robolectric). Il flusso di
  autenticazione/autorizzazione va verificato a mano su device/emulatore con
  Play Services, dopo aver configurato il client OAuth.

## Export DOCX — perché non è stato implementato

Rimosso in modo esplicito dalla roadmap (non "rimandato" o "opzionale"): la
spec, sezione 5, già segnalava che non esiste un writer DOCX leggero e
maturo per Android — Apache POI dipende da `java.awt` (non disponibile su
Android) e appesantisce l'APK, e i wrapper Kotlin in giro (es. DocxKtm) sono
comunque costruiti sopra docx4j con lo stesso tipo di dipendenze pesanti.
L'alternativa via ZIP di XML OOXML scritto a mano resta un'opzione futura,
ma con Markdown (condivisione leggibile) e JSON/CSV (dato grezzo portabile)
già coperti, non c'è un caso d'uso che lo renda prioritario. Non
riconsiderare senza una richiesta esplicita e un motivo concreto.

## Comandi di build/test

```bash
./gradlew assembleDebug       # build APK debug
./gradlew testDebugUnitTest   # unit test JVM (domain + repository logic)
./gradlew connectedDebugAndroidTest  # test strumentali (richiede device/emulatore)
./gradlew lint                # Android Lint
```

### ⚠️ Limitazione nota dell'ambiente sandbox

L'ambiente in cui questo progetto è stato scaffoldato **non ha accesso di
rete a `dl.google.com`** (bloccato dalla policy del proxy in uscita), quindi
**non è possibile eseguire una build Gradle completa da questo sandbox**
(l'Android Gradle Plugin e le librerie AndroidX/Compose/Room/Hilt sono
ospitate sul repository Maven di Google). La verifica reale della build
avviene tramite la GitHub Actions workflow in
`.github/workflows/android-ci.yml`, che gira su runner con accesso di rete
completo. **Se lavori di nuovo in un sandbox isolato, verifica prima con
`curl` se `dl.google.com` è raggiungibile prima di assumere che `./gradlew`
funzioni.**

**Stato build: verde su CI** (`lintDebug`, `testDebugUnitTest`,
`assembleDebug` passano tutti su GitHub Actions — vedi PR #1 per la Fase 1,
PR #2 per la Fase 2, PR #3 per la Fase 3). La Fase 4 (questa modifica) è
stata scritta con revisione statica riga per riga ma **non ancora
verificata su CI al momento di scrivere questa nota** — controlla lo stato
dei check sulla relativa PR prima di considerarla verde; se emergono errori
di compilazione (nuove dipendenze `androidx.credentials`/`play-services-auth`/
`androidx.work`/`androidx.hilt:hilt-work`, `@HiltWorker`, `Configuration.Provider`),
sono il primo posto dove guardare. Il repository ha anche un secondo workflow,
`build-apk.yml`, aggiunto manualmente fuori da queste sessioni: non
toccarlo a meno che non serva, ma tienilo a mente quando controlli lo stato
CI di una PR (di solito compaiono più check `build-and-test` insieme a un
check `build`).

Bug reali trovati solo grazie alla CI (nessuno di questi era visibile con
una revisione statica):
- `FlowRow` (Compose Foundation) richiede `@OptIn(ExperimentalLayoutApi::class)`
  esplicito su questa versione del BOM — il modulo tratta i mancati opt-in
  come **errori**, non warning. Se aggiungi altre API Compose sperimentali,
  ricordalo.
- `Json.encodeToString(value)` **senza** `import kotlinx.serialization.encodeToString`
  risolve all'overload a due argomenti (`serializer`, `value`) invece
  dell'estensione reified a un argomento, e fallisce con un errore di tipo
  fuorviante ("No value passed for parameter 'value'"). Importa sempre
  esplicitamente `kotlinx.serialization.encodeToString` quando usi
  `Json.encodeToString(x)` in forma breve.
- `PdfDocument` sotto Robolectric lancia `IllegalStateException` nel suo
  ciclo di vita delle pagine (vedi sezione Fase 2 sopra) — non è un bug del
  codice applicativo, è una limitazione dello shadow Robolectric.
- Lint (`RemoveWorkManagerInitializer`) blocca la build se
  `Application` implementa `androidx.work.Configuration.Provider` (Fase 4)
  senza rimuovere esplicitamente `androidx.startup.InitializationProvider`
  dal manifest — a differenza di quanto suggerisce parte della
  documentazione WorkManager, che la implica automatica. Serve un
  `<provider ... tools:node="remove">` esplicito in
  `AndroidManifest.xml` (richiede `xmlns:tools`).

Cosa è stato verificato:
- Revisione statica riga per riga di tutti i file Kotlin (import, coerenza
  package/directory, firme Room @Relation/@Junction, copertura dei
  TypeConverter, wiring Hilt) via un sub-agent di review dedicato (Fase 1).
- Unit test JVM puri (`domain/filter`, `domain/model`, `domain/export`) più
  test Room DAO via **Robolectric** (`data/local/ReviewDaoTest.kt`, gira come
  unit test JVM senza bisogno di emulatore) — eseguiti con successo in CI.
- Build `assembleDebug`, `lintDebug` e `testDebugUnitTest` completate con
  successo in CI per entrambe le fasi, un formato di export alla volta
  (JSON/CSV → Markdown → PDF), ognuno verificato prima di passare al
  successivo.

## Convenzioni di codice

- Nessun dato mock nella UI finale: tutti gli screen leggono da Room tramite
  repository. Il seed di dati demo esiste solo in `data/debug/DebugSeeder.kt`
  ed è attivo solo se `BuildConfig.SEED_DEBUG_DATA == true` (solo build
  `debug`).
- Date: `java.time.LocalDate` / `java.time.Instant` nativi (disponibili senza
  desugaring da API 26, che è già il nostro `minSdk`).
- ID recensioni/entità di lookup: `String` (UUID) per le recensioni; le
  tabelle di lookup (Platform/Genre/Tag) usano `Long` autogenerato con
  vincolo `UNIQUE` sul nome normalizzato (trim + lowercase per il confronto).
- Non introdurre nuove dipendenze senza che sia esplicitamente richiesto o
  che servano davvero: se emergono necessità relative, segnalale invece di
  implementarle. Applicato in Fase 3 (nessuna libreria di charting aggiunta)
  e in Fase 4 (client Drive scritto a mano invece del client Java ufficiale
  di Google, vedi sezione dedicata sopra) — le uniche dipendenze aggiunte in
  Fase 4 sono quelle esplicitamente richieste (Credential Manager,
  AuthorizationClient, WorkManager) più `googleid` e `androidx.hilt:hilt-work`,
  necessarie di conseguenza e documentate lì.
- Export PDF: solo `android.graphics.pdf.PdfDocument` nativo. Niente
  Apache PDFBox né iText7 (iText7 è AGPL, esplicitamente escluso).

## Cosa NON fare finché non richiesto esplicitamente

Export DOCX: **permanentemente fuori scope** (decisione presa, non solo
rimandata — vedi sezione dedicata sopra), non riconsiderare senza una
richiesta esplicita. Autenticazione utente/multi-account: fuori scope, la
Fase 4 usa OAuth solo per l'autorizzazione verso Drive, non introduce un
concetto di account applicativo.
