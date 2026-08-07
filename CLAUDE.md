# CLAUDE.md

Guida per agenti Claude che lavorano su questo repository. Leggi anche
`docs/spec.md` per la specifica funzionale completa.

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
- **Fase 5 — Internazionalizzazione, tema e documentazione**: ✅ completata
  (app tradotta IT/EN con selettore lingua in-app, tema chiaro/scuro/sistema
  persistito con DataStore, documentazione riorganizzata sotto `docs/` con
  traduzione inglese in `docs/en/`). Vedi sezione dedicata sotto.
- **Fase 6 — Backlog tracciabile e fetch metadati (TheGamesDB)**: ✅
  completata in due tappe — Tappa 1: nuova sezione Backlog (liste di
  gioco, item con stato/commenti/storico automatico/riordino manuale,
  trigger "scrivi una recensione" al completamento). Tappa 2: pulsante
  "Cerca online" (TheGamesDB) nei form di backlog e recensione per
  precompilare copertina/piattaforma/genere (+ anno/sviluppatore per il
  backlog). Vedi sezione dedicata sotto.
- **Fase 7 — Rebranding, navigazione a drawer, fix ricerca TheGamesDB**: ✅
  completata (app rinominata ThePatientGamerHelper ovunque, incluso
  `applicationId`/package Kotlin; nuova schermata Home "cosa vuoi fare?" +
  cassetto laterale hamburger con le 3 sezioni principali + impostazioni;
  fix del bug "ricerca TheGamesDB sempre fallita"). Vedi sezione dedicata
  sotto.
- **Fase 8 — Import Markdown, export/import backlog, HowLongToBeat, viste
  griglia**: ✅ completata (import recensioni da Markdown, reverse
  dell'export esistente; export/import dell'intero backlog con le sue
  liste, sempre additivo, formato ZIP separato dal backup Drive; tempi
  stimati HowLongToBeat nel form/dettaglio backlog e nelle statistiche,
  integrazione intrinsecamente fragile perché HowLongToBeat non ha
  un'API pubblica — vedi sotto; vista lista/griglia per libreria e
  backlog). Vedi sezione dedicata sotto e
  `docs/decisioni-implementazione.md` per il ragionamento completo.
- **Export DOCX**: **deciso di non implementarlo**, non solo rimandato. Vedi
  "Export DOCX — perché non è stato implementato" sotto.

Con le Fasi 6-8 la roadmap è ulteriormente estesa oltre quella originaria (vedi
`docs/spec.md`). Non implementare funzionalità non ancora presenti in questo
file o nella spec a meno che l'utente non lo richieda esplicitamente in una
nuova sessione.

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
com.marcogn.thepatientgamerhelper
├── data/
│   ├── local/
│   │   ├── entity/      # Entità Room (Review, Platform, Genre, Tag, cross-ref, ProCon,
│   │   │                # Backlog* dalla Fase 6)
│   │   ├── dao/          # DAO Room, esposti come Flow (ReviewDao, LookupDaos, BacklogDao)
│   │   ├── Converters.kt # TypeConverter per LocalDate/Instant/enum
│   │   └── Migrations.kt # MIGRATION_1_2 (Fase 6: tabelle backlog), MIGRATION_2_3 (Fase 8:
│   │                      # colonne stima HowLongToBeat su backlog_items) — entrambe additive
│   ├── repository/       # Implementazioni dei repository (upsert transazionale)
│   ├── export/            # I/O Android per l'export/import: ExportFileWriter (SAF, scrittura),
│   │                      # ImportFileReader (Fase 8, SAF, lettura — usato sia dall'import
│   │                      # Markdown recensioni sia dall'import backlog), PdfReviewRenderer
│   │                      # (PdfDocument), ReviewExporter, BacklogExporter/BacklogImporter +
│   │                      # BacklogExportArchiveBuilder/Reader (Fase 8, zip dati+copertine) —
│   │                      # tutte classi concrete iniettate via Hilt, come ImageStorage, non
│   │                      # un'astrazione interfaccia/impl come i repository
│   ├── drive/             # Client REST Drive v3 (DriveApiClient, HttpURLConnection)
│   │                      # + auth (DriveAuthManager: Credential Manager + AuthorizationClient)
│   ├── backup/            # Orchestrazione backup/restore: BackupManager, archivio zip
│   │                      # (BackupArchiveBuilder/Reader), BackupWorker (WorkManager +
│   │                      # Hilt), BackupScheduler, BackupPreferences (SharedPreferences)
│   ├── settings/          # ThemePreferences (Fase 5, Preferences DataStore), ViewModePreferences
│   │                      # (Fase 8, SharedPreferences, vista lista/griglia libreria e backlog)
│   ├── thegamesdb/        # Fase 6, Tappa 2: TheGamesDbApiClient (HttpURLConnection, stesso
│   │                      # pattern di DriveApiClient), TheGamesDbPreferences (SharedPreferences,
│   │                      # API key inserita a runtime dall'utente), GameMetadataSearchCoordinator
│   │                      # (logica condivisa "cerca online" tra form recensione e form backlog,
│   │                      # Fase 8: espone anche searchHowLongToBeat(), backlog-only)
│   ├── howlongtobeat/     # Fase 8: HowLongToBeatApiClient — client HttpURLConnection per un
│   │                      # endpoint non ufficiale/non documentato, tecnica reverse-engineered
│   │                      # (vedi sezione dedicata sotto), non lo stesso livello di affidabilità
│   │                      # di TheGamesDbApiClient/DriveApiClient
│   └── debug/            # DebugSeeder, attivo solo dietro BuildConfig.SEED_DEBUG_DATA
├── domain/
│   ├── model/            # Modelli di dominio puri (no dipendenze Android), incluso ThemeMode,
│   │                      # Backlog* e GameMetadataSearchResult (Fase 6), HowLongToBeatEstimate/
│   │                      # ViewMode/ImportedBacklog* (Fase 8)
│   ├── filter/            # Logica di filtro/ordinamento libreria e backlog, pure function, unit-testata
│   ├── stats/             # Aggregazioni pure: LibraryStatisticsCalculator (Fase 3),
│   │                      # BacklogStatisticsCalculator (Fase 6, conteggi per stato/lista;
│   │                      # Fase 8: anche computeBacklogTimeEstimateStatistics)
│   ├── export/            # Formattazione export/import pura: JSON (kotlinx.serialization),
│   │                      # CSV (writer manuale), Markdown (template stringhe, Fase 8: anche
│   │                      # ReviewMarkdownParser, il reverse) — nessun import Android,
│   │                      # unit-testabile in JVM puro. Le etichette restano in italiano fisso
│   │                      # (vedi Fase 5, non seguono la lingua app). Fase 8: anche
│   │                      # BacklogExportDto.kt (payload zip export/import backlog, formato
│   │                      # separato da domain/backup — vedi sezione dedicata sotto)
│   ├── backup/            # Formato di backup puro: BackupPayload/BackupReviewDto,
│   │                      # mapping Review<->DTO, naming file — stesso pattern di domain/export
│   └── repository/        # Interfacce repository (ReviewRepository, LookupRepository,
│                          # BacklogRepository dalla Fase 6, + importLists() dalla Fase 8)
├── di/                    # Moduli Hilt (Database, Repository)
└── ui/
    ├── theme/             # Tema Material 3 (Compose) + ThemeViewModel (Fase 5, legge ThemePreferences)
    ├── navigation/        # Navigation Compose, route type-safe (kotlinx.serialization).
    │                      # Fase 7: ModalNavigationDrawer attorno al NavHost (cassetto
    │                      # hamburger con le 3 sezioni + impostazioni), Destination.Home
    │                      # come startDestination
    ├── home/              # Fase 7: HomeScreen, schermata "cosa vuoi fare?" con le 3 scelte
    │                      # principali (recensioni/backlog/statistiche)
    ├── library/           # Schermata libreria (lista, ricerca, filtri, ordinamento, export).
    │                      # Fase 7: non più startDestination, top bar senza nome app/icone
    │                      # backlog/statistiche/impostazioni (ora nel drawer). Fase 8: import
    │                      # Markdown, toggle vista lista/griglia
    ├── detail/            # Schermata dettaglio recensione (+ export singola recensione)
    ├── form/              # Form crea/modifica recensione (+ "Cerca online" e pre-popolamento
    │                      # da backlog item, Fase 6)
    ├── backlog/            # Fase 6: BacklogScreen (liste + ricerca/filtro unificata + stats
    │                       # aggregate leggere), BacklogListDetailScreen (drag-to-reorder),
    │                       # BacklogItemFormScreen, BacklogItemDetailScreen (stato/commenti/
    │                       # storico/nota abbandono, Fase 8: anche stima HowLongToBeat).
    │                       # Fase 8: export/import backlog in BacklogScreen, toggle vista
    │                       # lista/griglia in BacklogListDetailScreen (griglia senza
    │                       # drag-to-reorder)
    ├── settings/           # Schermata Impostazioni: preferenze tema/lingua (Fase 5), backup
    │                       # manuale/automatico, ripristino, API key TheGamesDB (Fase 6)
    └── common/            # Composable condivisi (chip input, rating, date picker, cover
                            # thumbnail, ReviewStatus/BacklogItemStatus display, GameSearchDialog
                            # Fase 6, GameGridTile/ViewModeToggle Fase 8, ecc.)
```

Risorse (`app/src/main/res/`): `values/strings.xml` è l'italiano (lingua di
default), `values-en/strings.xml` la traduzione inglese, `xml/locales_config.xml`
elenca le lingue supportate per l'integrazione con le impostazioni di sistema
(API 33+). Vedi sezione "Fase 5" sotto per i dettagli.

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
  `ui/navigation/Destinations.kt`, wiring in `ThePatientGamerHelperNavGraph.kt`.

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
  `ThePatientGamerHelperApplication` implementa `Configuration.Provider` per iniettare
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
- **Login esplicito, non implicito**: la prima versione faceva scattare
  `signIn()` (bottom sheet di Credential Manager) a ogni singola azione
  (backup, elenco backup, ripristino) — funzionale ma confuso, l'utente
  vedeva il picker dell'account senza un punto di ingresso chiaro. Ora
  `SettingsUiState.signedInEmail` traccia lo stato "connesso" della
  sessione (solo in memoria nel ViewModel, non persistito su disco — niente
  refresh token salvato: un riavvio dell'app richiede un nuovo login, scelta
  deliberata per non introdurre storage di credenziali). Un unico tasto
  "Accedi con Google" (`onLoginClick`) esegue `signIn()` + `authorize()`
  insieme; finché `signedInEmail == null` la UI mostra solo quel tasto e
  nasconde backup/ripristino (le sezioni Preferenze e TheGamesDB restano
  visibili, non dipendono da Drive). Le azioni successive
  (`onBackupNow`/`onRefreshBackups`/`onRestore`) richiamano solo
  `authorize()` (silenzioso una volta concesso lo scope, niente altro
  picker) tramite `ensureAccessToken()`, non più `signIn()`.
  `DriveAuthManager.isConfigured()` espone se `google_oauth_web_client_id`
  è ancora al placeholder: se sì, la schermata mostra direttamente in-app
  (`DriveNotConfiguredCard`, stringhe localizzate IT/EN) una card che
  spiega cosa manca e dove va configurato — invece di un errore generico
  dopo aver premuto login. Il client ID OAuth resta comunque l'unica cosa
  che **deve** vivere in un file di risorse: è la registrazione one-time
  dell'app su Google Cloud Console (legata a SHA-1 + `applicationId`), non
  un dato per-utente — nessuna API Google permette di crearla da codice a
  runtime, quindi non è spostabile dietro un tasto di login.
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

## Fase 5 — Internazionalizzazione, tema e documentazione

### Internazionalizzazione IT/EN

- Tutte le stringhe delle schermate (fasi 1-4) sono state estratte in
  string resource: `res/values/strings.xml` è l'italiano (lingua di
  default del progetto), `res/values-en/strings.xml` la traduzione
  inglese. Le due liste di chiavi sono tenute allineate 1:1 — se aggiungi
  una stringa in una, aggiungila anche nell'altra.
- **`domain/export` non è toccato da questa fase**: le etichette usate
  nei file esportati (Markdown/CSV/JSON/PDF) restano fisse in italiano,
  scritte a mano nei formatter puri. La richiesta era "internazionalizza
  le schermate", non il contenuto dei file generati, e localizzarli
  avrebbe richiesto passare `Context`/risorse Android dentro `domain/export`,
  rompendo la sua natura di Kotlin puro testabile in JVM (vedi Fase 2). Il
  file che l'utente esporta e magari incolla su Reddit resta quindi in
  italiano indipendentemente dalla lingua scelta per l'app — comportamento
  intenzionale, non un'incoerenza dimenticata.
- **`ReviewStatus.label()` (in `domain/model`) non è stato toccato** per lo
  stesso motivo: lo usa anche `ReviewMarkdownFormatter`/`PdfReviewRenderer`
  in `domain`/`data/export`. Per la UI esiste invece
  `ReviewStatus.displayName()` in `ui/common/ReviewStatusDisplay.kt`, un
  `@Composable` che risolve la string resource giusta — le schermate usano
  sempre `displayName()`, mai `label()`.
- I messaggi costruiti nei ViewModel (esiti di export/backup, errori di
  validazione form) non possono usare `stringResource()` (non è
  `@Composable`): i ViewModel che li generano iniettano
  `@ApplicationContext Context` via Hilt e chiamano `context.getString(...)`
  — pattern già adottato in `LibraryViewModel`, `DetailViewModel`,
  `ReviewFormViewModel`, `SettingsViewModel`.
- **Selettore lingua in-app**: `AppCompatDelegate.setApplicationLocales()`
  (API AndroidX per-app language, backport funzionante da API 26, non solo
  da API 33+), tre opzioni in Impostazioni — Sistema/Italiano/English
  (`ui/settings/AppLanguage.kt`). La persistenza è automatica grazie ad
  `autoStoreLocales` (vedi sotto), nessuno storage custom.
- **`autoStoreLocales`**: attivato aggiungendo in `AndroidManifest.xml` il
  `<service android:name="androidx.appcompat.app.AppLocalesMetadataHolderService">`
  con `<meta-data android:name="autoStoreLocales" android:value="true" />`
  — è il meccanismo documentato da AndroidX per persistere la scelta senza
  scrivere `SharedPreferences`/DataStore a mano. `android:localeConfig="@xml/locales_config"`
  sull'`<application>` (con `res/xml/locales_config.xml` che elenca `it`/`en`)
  è il complemento lato piattaforma per l'integrazione con Impostazioni >
  Lingue dell'app di sistema su API 33+.
- **`MainActivity` estende `AppCompatActivity`, non `ComponentActivity`**:
  richiesto esplicitamente dalla documentazione ufficiale per usare
  `AppCompatDelegate.setApplicationLocales()` con Compose — *"If you're using
  Compose with setApplicationLocales, you must extend your activity from
  AppCompatActivity. Otherwise, setting the app locale won't work."* Con
  `ComponentActivity` il cambio lingua non genera nessun errore, viene solo
  ignorato silenziosamente (bug reale scoperto in verifica manuale su
  device, vedi `docs/decisioni-implementazione.md` per il resoconto
  completo, inclusi due tentativi di fix sbagliati prima di questo). Non
  introduce View/XML: `setContent {}` resta l'unico entry point della UI,
  `AppCompatActivity` serve solo da hook per il ciclo di vita di
  `AppCompatDelegate`. Di conseguenza il tema Android sotto
  `res/values/themes.xml` deve discendere da `Theme.AppCompat` (qui
  `Theme.AppCompat.DayNight.NoActionBar`) — `AppCompatActivity` lancia
  un'eccezione a runtime se il tema non è compatibile.
- Nessuna `recreate()` manuale: con `AppCompatActivity`,
  `setApplicationLocales()` la innesca già da sé end-to-end (sia su API 33+
  che sotto). `ui/settings/AppLanguage.kt` si limita a chiamare
  `setApplicationLocales()`.
- Nuova dipendenza: `androidx.appcompat:appcompat` — necessaria per
  `AppCompatDelegate`/`AppCompatActivity`/`AppLocalesMetadataHolderService`;
  non introduce layout XML né altre API della vecchia UI a View.

### Tema chiaro/scuro/sistema

- Preferenza a tre stati (`domain/model/ThemeMode.kt`: `SISTEMA` di default,
  `CHIARO`, `SCURO`) persistita con **Preferences DataStore**
  (`data/settings/ThemePreferences.kt`) — a differenza di
  `BackupPreferences` (Fase 4, `SharedPreferences`, motivato lì da "solo tre
  flag semplici"), qui la richiesta esplicita era DataStore.
- `ui/theme/ThemeViewModel.kt` espone `themeMode` come `StateFlow` letto da
  `ThemePreferences.themeMode` (`Flow`, single source of truth) via
  `stateIn`. Due punti di consumo indipendenti, entrambi tramite
  `hiltViewModel()`: la root `ThePatientGamerHelperApp` in `MainActivity.kt` (decide
  se applicare `darkTheme = true/false` a `ThePatientGamerHelperTheme`, rispettando
  `isSystemInDarkTheme()` quando il modo è `SISTEMA`) e `SettingsScreen`
  (per mostrare/cambiare la selezione). Sono due istanze `ViewModel`
  diverse ma leggono lo stesso `DataStore`, quindi restano sincronizzate
  senza bisogno di uno scope condiviso — stesso principio "Room/DataStore
  come single source of truth via Flow" già in uso per il resto dell'app.
- `ThePatientGamerHelperTheme` (`ui/theme/Theme.kt`) non è cambiato nella firma:
  accetta già `darkTheme: Boolean`, è solo il chiamante in `MainActivity`
  che ora lo calcola da `ThemeMode` invece di usare sempre il default
  `isSystemInDarkTheme()`.
- Nuova dipendenza: `androidx.datastore:datastore-preferences`.

### Riorganizzazione documentazione

- La specifica funzionale, prima `spec-app-recensioni-videogiochi.md` nella
  root, è stata spostata in `docs/spec.md` (roadmap aggiornata per
  riflettere il completamento della Fase 5).
- `docs/en/` è la traduzione inglese di `docs/spec.md`,
  `docs/decisioni-implementazione.md` e di `README.md` (come
  `docs/en/README.md`) — l'italiano resta la fonte di verità, ogni file
  tradotto porta una nota in cima che segnala il rischio di disallineamento
  nel tempo. `CLAUDE.md` resta solo in italiano: è operativo per l'agente,
  non documentazione rivolta a chi legge il repository.

## Fase 6 — Backlog tracciabile e fetch metadati (TheGamesDB)

Due tappe, sviluppate in sequenza (Tappa 1 verificata prima di iniziare la
Tappa 2, come da richiesta).

### Tappa 1 — Backlog tracciabile

- **Modello dati**: `BacklogListEntity` (liste create/rinominate/eliminate/
  riordinate dall'utente), `BacklogItemEntity` (titolo, stato, posizione,
  date, `reviewId` opzionale, `abandonNote`, `releaseYear`/`developer` —
  questi ultimi due popolati solo dalla Tappa 2, vedi sotto),
  `BacklogCommentEntity`, `BacklogHistoryEntryEntity`. Piattaforma/genere/tag
  dell'item sono many-to-many **sulle stesse tabelle di lookup** già usate
  dalle recensioni (`platforms`/`genres`/`tags`, nuove cross-ref
  `backlog_item_*_cross_ref`) — stesso pool di autocomplete tra backlog e
  recensioni, coerente col resto del modello dati.
- **Migration additiva, non distruttiva**: `ThePatientGamerHelperDatabase` passa da
  `version = 1` a `version = 2`. **Non** è stato usato
  `fallbackToDestructiveMigration()`: l'app è già in uso reale (vedi intro di
  questo file), un `fallbackToDestructiveMigration()` avrebbe cancellato le
  recensioni esistenti al primo avvio dopo l'aggiornamento. `MIGRATION_1_2`
  in `data/local/Migrations.kt` crea solo le nuove tabelle/indici via SQL
  raw, non tocca `reviews`/`platforms`/`genres`/`tags`.
- **Storico automatico**: generato interamente da `BacklogRepositoryImpl`,
  non da input manuale — `CREATO` alla creazione item, `CAMBIO_STATO` solo
  quando lo stato cambia davvero (non ad ogni scrittura di `abandonNote`),
  `CAMBIO_LISTA` quando l'item viene spostato (`moveItem`), `COMMENTO` ad
  ogni commento aggiunto, `RECENSIONE_COLLEGATA` quando l'item viene
  collegato a una recensione. Il campo `detail` porta un payload
  interpretabile dalla UI in base al tipo (es. il nome dello stato per
  `CAMBIO_STATO`, il nome della lista di destinazione per `CAMBIO_LISTA`) —
  vedi `ui/backlog/BacklogHistoryDisplay.kt`.
- **`dataInizio`/`dataCompletamento` auto-popolate, nessun editor manuale**:
  la spec elenca questi due campi come opzionali sull'item ma non chiede un
  controllo UI per impostarli a mano (a differenza della recensione, che ha
  `DatePickerField` espliciti). L'unico modo sensato per valorizzarli è
  quindi automaticamente alla transizione di stato:
  `BacklogRepository.updateStatus()` imposta `startDate` la prima volta che
  lo stato passa a `IN_CORSO` (se non già impostata) e `completedDate` la
  prima volta che passa a `COMPLETATO`, senza mai sovrascrivere un valore
  già presente (così un item che torna "in corso" dopo essere stato
  completato non perde la data di completamento originale).
- **`updateStatus()` distinto da `saveItem()`**: la spec elenca "cambio
  stato tramite selettore" come funzionalità separata dal CRUD item. Il form
  di creazione/modifica (`BacklogItemFormScreen`) non tocca mai lo stato;
  solo `BacklogItemDetailScreen` lo fa, tramite un selettore dedicato che
  chiama `updateStatus()` — un solo punto che genera storico e date
  automatiche, invece di duplicare quella logica anche nel form.
- **Riordino**: le liste (tipicamente poche) si riordinano con frecce
  su/giù su `BacklogScreen` — niente drag-and-drop per un elenco di
  quell'ordine di grandezza. Gli item dentro una lista (potenzialmente
  numerosi, "utile per prioritizzare" da spec) hanno invece drag-to-reorder
  vero, implementato a mano in `BacklogListDetailScreen.kt` con
  `Modifier.pointerInput` + `detectDragGestures` su un'icona "maniglia"
  dedicata (non sull'intera riga, per evitare conflitti tra il gesto di
  drag e il click che apre il dettaglio) — **nessuna libreria di reorder
  aggiunta**, coerente con la sezione "non introdurre dipendenze senza
  necessità" più sotto. L'ordine finale viene scritto una sola volta a fine
  gesto (`onDragEnd`), non ad ogni frame.
- **Ricerca/filtro unificata**: `BacklogScreen` mostra normalmente l'elenco
  delle liste (con conteggio item e vista aggregata leggera per
  stato/lista); non appena una ricerca testuale o un filtro (lista, stato,
  piattaforma, genere) è attivo, la stessa schermata passa a un elenco
  piatto di risultati cross-lista (ogni riga mostra a quale lista
  appartiene) — stessa struttura search+filtro della libreria recensioni
  (`domain/filter/BacklogFilters.kt`/`BacklogFiltering.kt`, pure function,
  stesso pattern di `LibraryFilters`/`LibraryFiltering`), ma senza
  introdurre una schermata separata solo per la ricerca.
- **Trigger "vuoi scrivere una recensione?"**: quando `updateStatus()`
  imposta `COMPLETATO` e l'item non ha ancora `reviewId`,
  `BacklogItemDetailViewModel` espone un evento one-shot che la UI
  intercetta per mostrare il dialog di conferma. Alla conferma, naviga a
  `Destination.Form(backlogItemId = itemId)` — `Destination.Form` ha ora un
  secondo parametro opzionale, usato solo in creazione (ignorato se
  `reviewId` è già impostato). `ReviewFormViewModel` precompila il draft da
  `BacklogItem` (titolo/piattaforme/generi/date/copertina) e, al salvataggio
  riuscito, chiama `BacklogRepository.linkReview()` per richiudere il
  cerchio (registra anche la voce di storico `RECENSIONE_COLLEGATA`). La
  copertina **non** viene condivisa per riferimento tra backlog item e
  recensione: `ImageStorage.duplicate()` (nuovo metodo) copia il file su un
  nome nuovo, così cancellare la recensione in seguito non fa sparire la
  copertina mostrata nel backlog (o viceversa) — due file indipendenti sullo
  stesso contenuto iniziale.

### Tappa 2 — Fetch automatico copertina e metadati (TheGamesDB)

- **La API key è sempre richiesta, non è stata una scelta**: prima di
  implementare, ho verificato online (come richiesto) i limiti/requisiti
  attuali dell'API pubblica di TheGamesDB. Risultato, diverso
  dall'assunzione di partenza ("non dovrebbe servire, per ES-DE non
  serve"): **dal 17/02/2026 TheGamesDB ha cambiato policy e richiede una
  `apikey` su ogni richiesta**, pubblica o privata che sia — non esiste più
  accesso anonimo. ES-DE/Skyscraper non chiedono una chiave *personale*
  perché ne incorporano una propria (pubblica, condivisa, rate-limited) nel
  loro codice sorgente, ma quella chiave esiste comunque. Non avendo un modo
  affidabile di recuperarne il valore letterale attuale, ho chiesto
  esplicitamente all'utente come procedere invece di indovinare o incollare
  una chiave trovata online senza certezza.
- **Configurazione runtime, non un placeholder di build**: a differenza del
  client ID OAuth di Drive (Fase 4, `res/values/drive_config.xml`,
  `[DA_COMPLETARE]` sostituito prima della build), la API key TheGamesDB è
  un campo che l'utente compila **dentro l'app** (nuova sezione in
  Impostazioni, `TheGamesDbPreferences`, `SharedPreferences` — stesso
  pattern minimale di `BackupPreferences`, non DataStore). Nessuna build
  contiene una chiave, reale o placeholder: finché il campo è vuoto, il
  pulsante "Cerca online" fa scattare `GameMetadataSearchCoordinator`, che
  restituisce un messaggio informativo invece di chiamare l'API — mai un
  crash, mai una build che smette di compilare per una chiave mancante.
- **Client REST scritto a mano, non Retrofit/Ktor**: la richiesta originale
  citava Retrofit/Ktor come esempio ("se non già presente"), ma il progetto
  ha già risolto lo stesso problema in Fase 4 (`DriveApiClient`) con un
  client minimale `HttpURLConnection` + `kotlinx.serialization`. Ho seguito
  lo stesso pattern per `TheGamesDbApiClient` invece di introdurre una nuova
  dipendenza HTTP: quattro soli endpoint GET (ricerca +
  Platforms/Genres/Developers) non giustificano un client HTTP completo,
  coerente con "non introdurre dipendenze senza necessità" già applicato tre
  volte prima (Drive, PDF, charting statistiche). **Nessuna nuova
  dipendenza aggiunta in Fase 6.**
- **Cache in-memory dei lookup, non persistita**: `TheGamesDbApiClient`
  tiene in memoria (per la durata del processo) le mappe id→nome di
  Platforms/Genres/Developers, popolate al primo utilizzo e riusate per le
  ricerche successive nella stessa sessione app. Con un rate limit
  pubblico nell'ordine delle migliaia di richieste al mese, evitare tre
  chiamate di lookup extra ad ogni singola ricerca è stata una scelta
  deliberata, non un'ottimizzazione prematura.
- **Risultati multipli, nessuna auto-selezione**: `Games/ByGameName`
  (opzionalmente filtrato per piattaforma, dedotta dal primo tag piattaforma
  già inserito nel form, per disambiguare remaster/edizioni regionali) può
  restituire più corrispondenze; `GameSearchDialog` (composable condiviso
  tra `ReviewFormScreen` e `BacklogItemFormScreen`, `ui/common/`) le elenca
  tutte con copertina/piattaforma/anno, l'utente sceglie. Alla selezione, la
  copertina viene scaricata e salvata **localmente** con
  `ImageStorage.writeBytes()` (stesso storage delle copertine caricate a
  mano) — mai solo l'URL remoto.
- **`releaseYear`/`developer` solo su `BacklogItem`, non su `Review`**: la
  spec chiedeva di salvare "piattaforma, genere, anno, sviluppatore" come
  metadati utili. Piattaforma e genere sono già campi esistenti su entrambi
  i modelli; anno e sviluppatore no. Ho scelto di aggiungerli **solo** a
  `BacklogItemEntity`/`BacklogItem` (utili come dati di catalogazione prima
  ancora di aver giocato) e di **non** estendere `ReviewEntity`/`Review`:
  farlo avrebbe richiesto toccare uno schema maturo con cinque fasi di
  funzionalità già costruite sopra (export JSON/CSV/PDF/Markdown, DTO di
  backup, statistiche), per due campi bibliografici che non sono mai stati
  parte del cuore di una recensione (voto/pro/contro/testo). La ricerca
  online nel form recensione resta quindi limitata a
  titolo/piattaforma/genere/copertina, come il resto del form.
- **Fallback silenzioso, mai un crash**: `GameMetadataSearchCoordinator`
  centralizza la logica condivisa tra i due form — chiave mancante, nessun
  risultato, errore di rete/HTTP diventano tutti un `Outcome.Message`
  testuale mostrato nel dialog, mai un'eccezione propagata. Il flusso
  manuale esistente (digitare i campi a mano) resta sempre disponibile
  sotto, invariato.
- **Non testabile in modo significativo via Robolectric**: stesso discorso
  già fatto per Drive in Fase 4 — chiamate `HttpURLConnection` reali verso
  `api.thegamesdb.net` richiedono rete vera. Sono invece unit-testate le
  parti pure aggiunte in Fase 6: `domain/filter/BacklogFilteringTest.kt` e
  `domain/stats/BacklogStatisticsCalculatorTest.kt`, stesso pattern di
  `LibraryFilteringTest`/`LibraryStatisticsCalculatorTest`.

**Stato build**: come per la Fase 5, questa modifica è stata scritta e
rivista staticamente riga per riga (bilanciamento parentesi, import,
corrispondenza 1:1 delle chiavi `strings.xml` IT/EN, coerenza delle
signature Room `@Relation`/`@Junction`/`@ForeignKey`) ma **non ancora
verificata su CI al momento di scrivere questa nota** — lo stesso sandbox
isolato senza accesso a `dl.google.com` descritto sotto "Limitazione nota
dell'ambiente sandbox" era in vigore anche per questa sessione. Controlla lo
stato dei check sulla relativa PR prima di considerarla verde.

## Fase 7 — Rebranding, navigazione a drawer, fix ricerca TheGamesDB

Tre richieste distinte nella stessa sessione, trattate come un'unica
modifica coordinata.

### Rebranding ThePatientGamerHelper

- App rinominata **ovunque**, incluso `applicationId`/package Kotlin (non
  solo il nome visualizzato): `com.marcogn.gamereviewer` →
  `com.marcogn.thepatientgamerhelper`. Scelta confermata esplicitamente
  dall'utente dopo aver segnalato le due conseguenze concrete (nessuna
  migrazione dati per installazioni esistenti — `applicationId` diverso è
  un'app diversa per Android; il client OAuth Drive andrà ri-registrato per
  il nuovo `applicationId`+SHA1 quando configurato). Vedi
  `docs/decisioni-implementazione.md`, sezione Fase 7, per il dettaglio
  completo del ragionamento e di cosa è rimasto intenzionalmente
  invariato (nome del repository GitHub, prefisso file di export in
  `domain/export/ExportFileNaming.kt`).
- File/classi rinominate: `GameReviewerNavGraph.kt` →
  `ThePatientGamerHelperNavGraph.kt`, `GameReviewerApplication.kt` →
  `ThePatientGamerHelperApplication.kt`,
  `data/local/GameReviewerDatabase.kt` →
  `data/local/ThePatientGamerHelperDatabase.kt` (con
  `DATABASE_NAME = "the_patient_gamer_helper.db"`, cambiato a mano perché
  snake_case non intercettato dal `sed` di rename).
- `app_name` (`values/strings.xml`/`values-en/strings.xml`): ora
  `"ThePatientGamerHelper"` in entrambe le lingue (prima "Recensioni
  Videogiochi"/"Game Reviews").

### Navigazione: Home chooser + drawer hamburger

- Nuova `Destination.Home` (`ui/navigation/Destinations.kt`), ora
  `startDestination` del grafo — sostituisce `Destination.Library` come
  prima schermata mostrata. `ui/home/HomeScreen.kt`: top bar con hamburger
  + testo "cosa vuoi fare?" + tre card (Recensioni/Backlog/Statistiche,
  ciascuna con icona/titolo/sottotitolo/chevron), nessun dato mock — solo
  navigazione, nessuna lettura da Room.
- `ThePatientGamerHelperNavGraph.kt` avvolge l'intero `NavHost` in un
  `ModalNavigationDrawer` (Material 3), con `drawerState` sollevato al
  livello del grafo. Le voci del drawer (Recensioni, Backlog, Statistiche,
  poi un separatore e Impostazioni in fondo) navigano con
  `popUpTo(Destination.Home) { saveState = true }` +
  `launchSingleTop = true` + `restoreState = true` — pattern standard
  drawer/bottom-bar, evita di accumulare backstack quando si passa
  ripetutamente tra le stesse sezioni.
- Ogni schermata riceve solo una lambda `onMenuClick: () -> Unit` (apre il
  drawer), mai lo stato del drawer stesso — coerente con UDF (eventi
  salgono, stato scende) già seguito nel resto dell'app.
- **`LibraryScreen`**: top bar senza più il nome dell'app (ora
  `stringResource(R.string.library_title)`, "Recensioni"/"Reviews"),
  icona hamburger al posto della freccia indietro, rimossi gli
  `IconButton` di Backlog/Statistiche/Impostazioni dalla toolbar (ora
  raggiungibili solo dal drawer). `BacklogScreen`/`StatsScreen`: stesso
  trattamento (`onBack` → `onMenuClick`, icona freccia → hamburger).
  `SettingsScreen` **non** ha ricevuto l'hamburger: resta raggiungibile
  solo dal drawer, con una freccia indietro nella propria top bar — è "in
  fondo" al drawer, non una delle tre sezioni principali.

### Fix ricerca TheGamesDB sempre fallita

- **Causa certa e corretta**: `GameMetadataSearchCoordinator.search()`
  sostituiva qualunque eccezione (rete, HTTP, parsing) con lo stesso
  messaggio generico fisso, scartando il dettaglio reale. Ora logga
  l'eccezione (`Log.w`) e **accoda** il suo messaggio (quando presente) al
  testo generico mostrato nel dialog — un futuro fallimento sarà
  diagnosticabile dall'utente stesso (es. "HTTP 401: ..." per una chiave
  non valida) invece di restare un misterioso "non riuscita".
- **Correzioni difensive aggiuntive** (basate su ricerca, non su
  riproduzione diretta — questo sandbox non ha accesso di rete a
  `api.thegamesdb.net`, bloccato esplicitamente dalla policy del proxy):
  header `Accept: application/json` + timeout connect/read espliciti
  mancanti sulla connessione; rimosso `"platform"` dai `fields` richiesti a
  `Games/ByGameName` (non un campo valido per quell'endpoint); sintassi del
  filtro piattaforma corretta alla forma indicizzata Laravel
  (`filter[platform][0]=` invece di `filter[platform]=`).
  Vedi `docs/decisioni-implementazione.md` per il dettaglio completo —
  incluso perché il primo fix (il messaggio non più generico) è l'unico di
  cui è garantita la correttezza, indipendentemente da quanto le altre
  correzioni difensive si rivelino centrate.

**Stato build**: come per le Fasi 5 e 6, questa modifica è stata scritta e
rivista staticamente (bilanciamento parentesi su ogni file `.kt` toccato,
corrispondenza package/percorso directory dopo il rename massivo, validità
XML su tutte le risorse, parità 1:1 delle chiavi `strings.xml` IT/EN) ma
**non verificata su CI al momento di scrivere questa nota** — stesso
sandbox isolato senza accesso a `dl.google.com` (build) né a
`api.thegamesdb.net` (fix ricerca) descritto sotto "Limitazione nota
dell'ambiente sandbox". Controlla lo stato dei check sulla relativa PR
prima di considerarla verde, e verifica manualmente su device/emulatore che
il fix della ricerca TheGamesDB mostri ora un messaggio d'errore
utile quando la ricerca fallisce.

## Fase 8 — Import Markdown, export/import backlog, HowLongToBeat, viste griglia

Cinque richieste distinte nella stessa sessione, trattate come un'unica
modifica coordinata: import recensioni da Markdown, export/import backlog,
tempi stimati HowLongToBeat nel backlog, gli stessi tempi in statistica, e
vista a griglia per libreria e backlog.

### Import recensioni da Markdown

- `domain/export/ReviewMarkdownParser.kt`: `parseReviewMarkdown(String):
  Result<ReviewDraft>`, funzione pura (nessun import Android, unit-testata
  in JVM puro come il resto di `domain/export`) che è l'esatto reverse di
  `toRedditMarkdown()` — stesse etichette italiane fisse (`Voto`, `Stato`,
  `Piattaforme`, `Generi`, `Tag`, `Iniziato il`, `Terminato il`, `Ore di
  gioco`), stessa struttura a bullet list, stesse sezioni `## Pro`/
  `## Contro` opzionali. Non è un parser Markdown generico: riconosce solo
  il formato che l'app stessa produce.
- Severo sui campi che l'exporter scrive sempre (titolo, voto, stato, data
  di inizio — un file senza uno di questi non è una recensione scritta da
  questa app), permissivo su tutto il resto (piattaforme/generi/tag/ore/
  pro/contro/corpo), rispecchiando esattamente cosa `toRedditMarkdown`
  omette quando vuoto. Ogni fallimento di parsing produce un `Result`
  fallito con un messaggio puntuale (es. "Voto mancante o non valido"), mai
  un'eccezione generica.
- Punto di ingresso: icona upload nella top bar della libreria, apre un
  file `.md` via SAF (`ActivityResultContracts.OpenDocument`), legge il
  contenuto con il nuovo `data/export/ImportFileReader.kt` (controparte in
  lettura di `ExportFileWriter`, riusato anche dall'import backlog sotto),
  lo passa al parser e — se valido — crea sempre una **nuova** recensione
  (`ReviewRepository.save(id = null, ...)`), mai un aggiornamento di una
  esistente. Esito mostrato con uno snackbar (`import_completed`/
  `import_failed`), stesso pattern di `exportMessage` in `LibraryViewModel`.

### Export/import backlog con le sue liste

- Formato **deliberatamente separato** da `domain/backup` (Fase 4, backup
  Drive dell'intera libreria recensioni con restore a sovrascrittura
  completa): questo è un file che l'utente crea/apre esplicitamente via SAF
  per condividere o unire il proprio backlog, non un ripristino di
  sicurezza. `domain/export/BacklogExportDto.kt` (payload puro, JSON via
  kotlinx.serialization, etichette italiane come `ReviewExportDto`) +
  `data/export/BacklogExportArchive.kt` (zip `data.json` + `images/`,
  stesso schema di `data/backup/BackupArchive.kt` ma scoped solo alle
  copertine effettivamente referenziate dal backlog, non l'intera
  `ImageStorage`) + `data/export/BacklogExporter.kt`/`BacklogImporter.kt`
  (orchestrazione I/O, iniettati via Hilt come `ReviewExporter`).
- **Sempre additivo, mai una sostituzione**: `BacklogRepository.importLists()`
  crea sempre liste nuove ed item nuovi con id nuovo — anche importando lo
  stesso file due volte (non è idempotente, scelta accettata per restare
  semplice: un merge per titolo/somiglianza avrebbe introdotto ambiguità —
  due giochi con lo stesso nome su piattaforme diverse? — che la richiesta
  non specificava). `reviewId` viene scartato in import (la recensione
  collegata appartiene alla libreria che ha esportato il file e potrebbe
  non esistere su questo device); commenti e storico sono reinseriti
  verbatim con i timestamp originali, senza aggiungere una voce `CREATO`
  sintetica (quella originale è già nello storico esportato). Le copertine
  vengono riscritte con un nome file nuovo (UUID), mai riusando il nome
  originale — la cartella `covers/` è condivisa con le recensioni, riusare
  un nome rischierebbe una collisione con un file già presente sul device.
- Punto di ingresso: icone upload/download nella top bar di
  `BacklogScreen`. Export sempre sull'intero backlog (stessa regola
  "sempre tutto, mai filtrato" di JSON/CSV in Fase 2).

### Tempi stimati HowLongToBeat nel backlog

- **Nessuna API pubblica esiste**: verificato online prima di implementare
  (stessa regola già applicata in Fase 6 per la policy apikey di
  TheGamesDB) — a differenza di TheGamesDB, che almeno richiede una apikey
  ma resta un endpoint documentato, HowLongToBeat non ha mai avuto un'API
  pubblica. Ogni integrazione non ufficiale esistente (howlongtobeatpy,
  ckatzorke/howlongtobeat, ecc.) funziona ri-derivando l'endpoint di
  ricerca corrente dal bundle JavaScript del frontend di HowLongToBeat a
  runtime, perché il path cambia ad ogni loro deploy — non esiste un
  contratto stabile da implementare contro.
- `data/howlongtobeat/HowLongToBeatApiClient.kt` usa la stessa tecnica
  reverse-engineered: fetch della homepage, estrazione del bundle
  `_app-*.js`, regex sull'endpoint POST, con fallback al path storicamente
  stabile `/api/s/` se l'estrazione fallisce. **Questo è intrinsecamente
  più fragile di `TheGamesDbApiClient`/`DriveApiClient`**: quelli sono
  reverse-engineered da endpoint REST documentati o comunque stabili
  (Fase 4/6), questo è reverse-engineered da un frontend che può cambiare
  ad ogni deploy senza preavviso. Non è stato possibile eseguirlo contro
  `howlongtobeat.com` reale da questo sandbox (nessun accesso di rete,
  stessa limitazione già nota per `dl.google.com`/`api.thegamesdb.net`) —
  **va considerato non verificato finché non testato su un device reale**.
- **Fallimento sempre silenzioso**: ogni errore (bundle cambiato, endpoint
  bloccato, nessuna corrispondenza, schema di risposta diverso) diventa
  `null` in `GameMetadataSearchCoordinator.searchHowLongToBeat()` — mai
  un'eccezione propagata, mai un messaggio mostrato all'utente (a
  differenza di `search()`/TheGamesDB, che mostra un messaggio su
  fallimento: qui è un arricchimento silenzioso sopra una ricerca
  TheGamesDB già riuscita, non un'azione a sé). Il flusso "cerca online"
  esistente non cambia in nessun modo se HowLongToBeat non risponde.
- `hltbMainStoryHours`/`hltbMainExtraHours`/`hltbCompletionistHours`
  vivono **solo su `BacklogItemEntity`/`BacklogItem`**, stesso precedente
  già motivato per `releaseYear`/`developer` in Fase 6: sono metadati di
  catalogazione, non parte del cuore di una recensione. La ricerca online
  nel form recensione resta invariata; solo `BacklogItemFormViewModel`
  chiama `searchHowLongToBeat()`, dopo che l'utente ha scelto un risultato
  TheGamesDB (usa il titolo esatto del risultato scelto, non il testo
  digitato, per la massima precisione di corrispondenza).
- `MIGRATION_2_3` (`data/local/Migrations.kt`) aggiunge le tre colonne
  `REAL` nullable a `backlog_items`, additiva come `MIGRATION_1_2`.
  `@Database` passa da `version = 2` a `version = 3`.
- Visibili nella scheda di dettaglio backlog (`BacklogItemDetailScreen`,
  card dedicata sotto i metadati, mostrata solo se almeno un campo è
  valorizzato) — non editabili a mano, stesso principio di
  anno/sviluppatore.

### Statistiche: tempo stimato backlog

- `domain/stats/BacklogStatisticsCalculator.kt`:
  `computeBacklogTimeEstimateStatistics()` somma le ore stimate
  (storia principale/storia+extra/completista) su tutti gli item del
  backlog che hanno **almeno un campo HowLongToBeat valorizzato**,
  indipendentemente dallo stato — si legge come "quanto tempo richiedono in
  totale questi giochi", non solo quelli non ancora iniziati. Espone anche
  `itemsWithEstimate` per mostrare "X elementi con una stima" nella UI.
  Integrato in `BacklogStatistics` (usato dall'header leggero di
  `BacklogScreen`) e in un nuovo `StatsUiState.backlogTimeEstimate`
  (`StatsViewModel` ora combina `ReviewRepository.observeAll()` con
  `BacklogRepository.observeAllItems()`).
- Nuova sezione in `StatsScreen` ("Tempo stimato backlog (HowLongToBeat)"),
  mostrata solo se almeno un item ha una stima — indipendente dal numero di
  recensioni, quindi visibile anche con libreria vuota se il backlog ha
  dati HowLongToBeat.

### Viste lista/griglia per recensioni e backlog

- `domain/model/ViewMode.kt` (`LIST`/`GRID`) + `data/settings/
  ViewModePreferences.kt` — due soli flag persistiti (vista libreria, vista
  backlog), `SharedPreferences` semplice come `BackupPreferences`/
  `TheGamesDbPreferences`, non `DataStore` (quello resta per `ThemeMode`,
  dove la richiesta esplicita in Fase 5 era DataStore).
- `ui/common/GameGridTile.kt` (cover a piena larghezza, proporzione 2:3
  corretta via `Modifier.aspectRatio` + `ContentScale.Crop`, non la
  thumbnail quadrata fissa di `CoverThumbnail` usata in vista a lista) e
  `ui/common/ViewModeToggle.kt` (icona che alterna, condivisi tra
  `LibraryScreen` e `BacklogListDetailScreen` per non duplicare la stessa
  UI due volte).
- **La griglia del backlog non supporta il drag-to-reorder manuale**
  (Fase 6, Tappa 1, disponibile solo in `BacklogListDetailScreen` vista a
  lista): estendere il gesto di trascinamento verticale esistente a una
  griglia 2D avrebbe richiesto una logica di posizionamento sostanzialmente
  diversa per un beneficio puramente cosmetico. L'utente torna alla vista a
  lista per riordinare.
- `BacklogScreen` (la vista "elenco liste"/ricerca cross-lista) **non** ha
  ricevuto il toggle: la griglia si applica dove si sfogliano i *giochi*
  (libreria, dettaglio di una lista backlog), non dove si sfogliano le
  *liste* stesse.

**Stato build**: come per le fasi precedenti, questa modifica è stata
scritta e rivista staticamente riga per riga (bilanciamento parentesi,
import, corrispondenza dei nomi di campo tra entità/DTO/mapper/draft,
parità 1:1 delle chiavi `strings.xml` IT/EN) ma **non verificata su CI al
momento di scrivere questa nota**. In questa sessione ho anche verificato
di persona se l'ambiente avesse un accesso di rete più ampio del solito
sandbox isolato: alcuni host Google rispondono (`maven.google.com`
restituisce 200), ma lo scaricamento reale degli artefatti dell'Android
Gradle Plugin resta bloccato dal proxy in uscita (redirect verso un host
non in allowlist, tunnel CONNECT rifiutato con 403) — stessa limitazione
già nota, confermata con un test diretto invece che solo assunta. Vedi
`docs/decisioni-implementazione.md` per il ragionamento completo dietro
ogni scelta di questa fase. Controlla lo stato dei check sulla PR prima di
considerarla verde, e verifica manualmente su device/emulatore sia
l'import Markdown sia — soprattutto — l'integrazione HowLongToBeat, che
resta la parte a rischio di fragilità più alto di questa modifica.

### Fix dopo verifica su device reale

La verifica manuale su device (dopo il merge della PR iniziale) ha trovato
quattro problemi reali, non visibili dalla sola revisione statica:

- **`FilterChip` "Abbandonato" spezzato verticalmente carattere per
  carattere** nel selettore di stato del dettaglio backlog: un `Row` senza
  wrap comprimeva l'ultimo chip oltre la larghezza minima del testo. Fix:
  `FlowRow` (`@OptIn(ExperimentalLayoutApi::class)`, stesso pattern già in
  uso in `FilterSheet.kt`/`BacklogFilterSheet.kt`/`TagInputField.kt`) così
  i chip vanno a capo su una nuova riga invece di schiacciarsi.
- **Titoli delle top bar (`Recensioni`, `Backlog`, ecc.) spezzati su due
  righe**, sovrapposti all'icona hamburger: troppe icone azione affiancate
  al titolo (fino a 5 nella libreria dopo la Fase 8) lasciavano troppo poco
  spazio. Fix: `maxLines = 1` + `overflow = TextOverflow.Ellipsis` su
  **tutti** i titoli di `TopAppBar` dell'app (non solo libreria/backlog,
  per coerenza e per prevenire lo stesso bug altrove — es. titolo lungo di
  una lista backlog o di una recensione). Se il titolo tronca troppo su
  schermi stretti, il prossimo passo è ridurre il numero di icone
  consolidandole in un menu overflow, non ancora fatto.
- **Ricerca TheGamesDB che falliva con un errore JSON illeggibile**
  (`Expected JsonArray, but had JsonNull ... element: $.developers`),
  indipendentemente da piattaforma/titolo: TheGamesDB restituisce `null`
  (non semplicemente omette la chiave) per `genres`/`developers` sui giochi
  senza quei dati catalogati — un valore di default in
  `kotlinx.serialization` copre solo la chiave *assente*, non un `null`
  esplicito, quindi ogni gioco con `developers: null` nella risposta faceva
  fallire l'intera ricerca. Fix: `genres`/`developers` resi `List<Long>?`
  in `GameDto` (`TheGamesDbApiClient.kt`) invece di avere solo un default,
  più `coerceInputValues = true` sul `Json` come rete di sicurezza
  aggiuntiva per altri campi che dovessero comportarsi allo stesso modo in
  futuro.
- **HowLongToBeat assente ovunque** (né nella scheda backlog né nelle
  statistiche): la prima versione del client implementava solo la POST di
  ricerca "nuda", senza gli header `x-auth-token`/`x-hp-key`/`x-hp-val`
  che le librerie non ufficiali attualmente mantenute (es.
  ScrappyCocco/HowLongToBeat-PythonAPI) documentano come necessari — vanno
  ottenuti con una `GET <path>init` prima della ricerca vera e propria.
  `HowLongToBeatApiClient` ora implementa l'intero flusso (homepage → bundle
  `_app-*.js` → endpoint → `init` → ricerca con gli header), usa uno
  User-Agent desktop realistico invece di uno che si identifica come app
  (molti siti con protezioni anti-scraping scartano UA non-browser a
  priori), e logga un warning ad ogni passo che fallisce (tag
  `HowLongToBeatClient`, controllabile con `adb logcat -s
  HowLongToBeatClient`) — la fase precedente falliva in silenzio assoluto,
  impossibile da diagnosticare da remoto. **Resta comunque la parte più a
  rischio di questa fase**: se il sito è dietro protezioni anti-bot più
  sofisticate di un controllo su header/User-Agent (es. una challenge
  Cloudflare che richiede l'esecuzione di JavaScript), nessun client
  `HttpURLConnection` può superarla — in quel caso l'unica strada
  praticabile sarebbe una `WebView` nascosta che carica la pagina reale e
  intercetta le chiamate di rete, un cambiamento molto più invasivo non
  ancora fatto. Se dopo questo fix le stime restano sempre assenti,
  controllare i log con quel tag prima di ipotizzare altre cause.

### Seconda verifica su device: diagnostica HowLongToBeat, flusso backlog→recensione, griglia dinamica

Tre ulteriori richieste dopo aver riprovato il primo giro di fix sopra —
HowLongToBeat era ancora completamente assente e senza `adb` a
disposizione non c'era modo di sapere perché, il flusso "completa item →
scrivi recensione" risultava macchinoso, e le copertine in griglia con
proporzioni diverse (quadrate vs verticali) sprecavano spazio.

- **Diagnostica HowLongToBeat spostata dentro l'app, non più solo `Log.w`**:
  senza un modo per l'utente di leggere `adb logcat`, un fallimento silenzioso
  restava un buco nero. `GameMetadataSearchCoordinator.searchHowLongToBeat()`
  ora restituisce un `HltbOutcome` (`Found`/`NotFound`/`Error(message)`)
  invece di un `HowLongToBeatEstimate?` nudo — `BacklogItemFormViewModel`
  lo trasforma in `BacklogItemFormUiState.hltbMessage`, una riga di testo
  mostrata nel form subito dopo aver scelto un risultato "Cerca online"
  (es. "HowLongToBeat: ricerca non riuscita — HTTP 403: ..."). Stesso
  principio già applicato al fix del messaggio generico di TheGamesDB in
  Fase 7: il messaggio reale, anche se tecnico, batte un fallimento muto —
  ora un eventuale nuovo fallimento è leggibile direttamente sullo schermo
  e riportabile senza strumenti di debug.
- **Flusso "completa → scrivi recensione" reso esplicito invece che
  immediato**: prima, toccare il chip "Completato" applicava subito lo
  stato *e* faceva comparire il dialog "vuoi scrivere una recensione?" ad
  ogni singolo tap, anche solo per esplorare le opzioni. `StatusEditor`
  (`BacklogItemDetailScreen.kt`) ora tiene la selezione (stato + nota
  abbandono) come stato locale non committato; un pulsante "Salva" compare
  solo quando la selezione differisce da quella salvata, e solo alla
  pressione di quel pulsante `BacklogItemDetailViewModel.onSaveStatus()`
  scrive lo stato e — solo se lo stato è davvero cambiato in COMPLETATO —
  fa scattare il prompt. Sostituisce i precedenti `onStatusChange`/
  `onAbandonNoteChange` (che scrivevano ad ogni tap/carattere).
- **Il form di recensione precompilato ora si apre già "Completato"**:
  `ReviewFormViewModel` non impostava lo `status` nel draft precompilato da
  un backlog item (restava sull'`IN_CORSO` di default), nonostante l'unico
  modo di arrivarci sia proprio il prompt post-completamento — ora imposta
  esplicitamente `ReviewStatus.COMPLETATO` (e usa `LocalDate.now()` come
  `dataFine` di fallback se il backlog item non ne aveva ancora una).
- **Il tasto indietro dal form precompilato non torna più nel backlog**:
  prima faceva semplicemente `popBackStack()`, tornando alla scheda
  backlog e scartando qualunque dato digitato. `ReviewFormViewModel.onBackPressed()`
  salva la recensione come "bozza" (se c'è almeno un titolo, senza le
  validazioni della Save esplicita — un back non è una conferma
  deliberata) e la collega comunque al backlog item; `ThePatientGamerHelperNavGraph`
  distingue il caso "form aperto dal backlog" (`Destination.Form.backlogItemId != null`)
  e in quel caso naviga verso `Destination.Library` (stesso pattern
  `popUpTo(Home){saveState=true}` già usato dal drawer) invece di fare un
  semplice pop — un cancel da un form aperto normalmente dalla libreria
  resta un pop invariato.
- **Griglia dinamica invece di righe a altezza uniforme**: `GameGridTile`
  non forza più un `aspectRatio` fisso sulla cover quando esiste
  un'immagine (`ContentScale.FillWidth` senza vincolo di altezza, l'altezza
  segue le proporzioni reali del file); `LibraryScreen`/`BacklogListDetailScreen`
  passano da `LazyVerticalGrid` (righe uniformi, ogni riga alta quanto la
  tile più alta) a `LazyVerticalStaggeredGrid` (`StaggeredGridCells.Adaptive`,
  richiede `@OptIn(ExperimentalFoundationApi::class)` su questo BOM) così
  copertine quadrate e verticali stanno affiancate senza spazio sprecato
  sopra/sotto le più corte — resta solo un piccolo offset costante
  (`verticalItemSpacing`/`horizontalArrangement`, 12dp) tra le tile. Il
  placeholder "nessuna copertina" resta a proporzione fissa 2:3, l'unico
  caso senza una dimensione intrinseca da cui derivare la forma.

**Stato build**: stesso discorso delle note precedenti — scritto e rivisto
staticamente, non eseguibile in questo sandbox (`dl.google.com` bloccato,
riconfermato anche in questa sessione). La parte a più alto rischio resta
la stessa: se HowLongToBeat continua a non restituire nulla, il messaggio
ora visibile nel form (`hltb_status_error` con il dettaglio tecnico) è il
primo posto da controllare — riportalo così com'è, invece di ipotizzare.

### Terza verifica su device: la diagnostica ha dato frutti, fix del redirect HTTP 308

La diagnostica aggiunta nel giro precedente ha funzionato esattamente come
previsto: invece di restare un buco nero, l'utente ha potuto riportare il
messaggio esatto mostrato nel form — **"ricerca non riuscita — HTTP 308"**,
identico per qualunque titolo cercato. Causa reale, non più ipotesi:
`HttpURLConnection` con `followRedirects` di default **non segue in modo
affidabile i redirect su richieste POST**, e ha lacune note specificamente
sul codice 308 (Permanent Redirect, che a differenza di 301/302 impone di
preservare metodo e body — introdotto da RFC 7538, più recente del resto
della gestione redirect storica della classe). La POST di ricerca (o una
delle GET del flusso homepage→bundle→init) veniva quindi rediretta dal
server e la libreria restituiva il 308 nudo invece di seguirlo.

Fix: `HowLongToBeatApiClient.request()` disabilita `instanceFollowRedirects`
e segue i redirect **manualmente** (fino a `MAX_REDIRECTS = 5`), rilanciando
la richiesta con lo stesso metodo, header e body verso l'URL risolto da
`Location` — comportamento corretto per 307/308 (che lo richiedono) e la
scelta più sicura anche per 301/302/303 in questo contesto (ci si aspetta
comunque una risposta JSON). Tutte e quattro le chiamate del client (le tre
GET del flusso di autenticazione più la POST di ricerca) passano ora da
questo unico punto invece di un `openConnection()` che si affidava al
comportamento di default. Ogni redirect seguito viene loggato (tag
`HowLongToBeatClient`) per restare diagnosticabile se il nuovo comportamento
rivelasse un ulteriore problema a valle.

### Quarta verifica su device: recensioni duplicate dal flusso backlog, HowLongToBeat ancora 308

Due ulteriori segnalazioni dopo il fix del redirect HTTP 308: le recensioni
create dal flusso "completa item → scrivi recensione" si duplicavano ad ogni
nuovo tentativo, e HowLongToBeat continuava a restituire lo stesso errore
"HTTP 308" nonostante il fix del redirect manuale.

- **Recensioni duplicate — causa reale**: una volta collegata una recensione
  a un backlog item (`BacklogItem.reviewId` valorizzato), l'unico modo per
  "rientrarci" era di nuovo `onWriteReview` →
  `Destination.Form(backlogItemId = itemId)`, che crea **sempre** una
  recensione vuota nuova (`reviewId = null` nella route), ignorando che una
  recensione era già collegata. L'unica traccia visibile del collegamento
  era una scritta inerte ("Recensione collegata"), non cliccabile — nessun
  modo di riaprire *quella* recensione, quindi ogni volta che l'utente
  ripassava dal flusso (es. per verificare/continuare la recensione) finiva
  per generarne un'altra bozza. Il guard `current.reviewId == null` in
  `BacklogItemDetailViewModel.onSaveStatus()` impedisce già correttamente un
  secondo prompt "vuoi scrivere una recensione?" per lo stesso item — il
  problema non era lì, ma nell'assenza totale di un percorso per
  raggiungere di nuovo una recensione già esistente.
- **Fix**: "Recensione collegata" (`BacklogItemDetailScreen.kt`) è ora un
  testo cliccabile (sottolineato, stesso colore primario di prima) che apre
  direttamente `Destination.Detail(reviewId)` — la normale schermata di
  dettaglio recensione, con i suoi percorsi di modifica/cancellazione già
  esistenti e sicuri (nessun rischio di duplicazione: modificare una
  recensione esistente passa sempre da `editingId != null`, mai dal ramo di
  precompilazione da backlog). Aggiunto anche `onOpenReview: (String) -> Unit`
  come nuovo parametro di `BacklogItemDetailScreen`, cablato in
  `ThePatientGamerHelperNavGraph.kt`. Come protezione difensiva aggiuntiva
  contro un doppio tap sul pulsante "Sì" del dialog (che potrebbe accodare
  due navigazioni identiche prima che il dialog si chiuda), la navigazione
  di `onWriteReview` ora passa anche `launchSingleTop = true`.
- **Pulsante "Salva" dello stato poco visibile**: `StatusEditor` usava un
  semplice `TextButton` — poco distinguibile dal resto del testo quando
  compare. Cambiato in `Button` (pieno, colore primario) per renderlo
  immediatamente riconoscibile come azione da compiere.
- **Recensioni bozza già duplicate sul device dell'utente**: questo fix
  previene nuove duplicazioni, ma **non tocca i dati già presenti** — le
  bozze doppie/triple create prima del fix restano nel database locale e
  vanno cancellate a mano dall'utente (icona cestino nel dettaglio di ogni
  recensione di troppo). Non è stato scritto un passo di migrazione
  automatica per deduplicare: non c'è un modo affidabile di distinguere
  "recensione duplicata da questo bug" da "due recensioni identiche per
  titolo ma volute dall'utente" senza rischiare di cancellare dati reali.
- **HowLongToBeat ancora "HTTP 308" dopo il fix del redirect manuale**: il
  fix della sessione precedente (seguire i redirect a mano, vedi sopra) era
  una correzione motivata da un errore reale riportato dall'utente, ma il
  nuovo test riporta lo stesso identico errore, non uno diverso — quindi
  **non è stato risolto**, o almeno non è ancora possibile dirlo con
  certezza. Senza accesso di rete a `howlongtobeat.com` da questo sandbox
  (stessa limitazione nota, invariata), non è possibile riprodurre e
  verificare oltre quello che l'utente può riportare da un device reale.
  Invece di tentare un altro fix "alla cieca" sullo stesso codice già
  corretto una volta senza successo, `ensureSuccessful()` e il messaggio di
  troppi-redirect in `HowLongToBeatApiClient.request()` ora includono anche
  l'URL che ha effettivamente fallito (`HTTP $responseCode @ $url`, e per i
  troppi-redirect sia l'URL di partenza che l'ultimo raggiunto) — prima il
  messaggio era solo "HTTP 308" senza dire *quale* chiamata delle quattro
  del flusso (homepage, bundle JS, init, ricerca) lo avesse prodotto, né se
  fosse il path derivato dal bundle o il fallback `/api/s/`. Un prossimo
  report con l'URL incluso permetterà una diagnosi mirata invece di un
  ulteriore tentativo speculativo. **Resta la parte meno affidabile di
  questa fase**, come già segnalato — non dare per risolto finché l'utente
  non conferma che le stime compaiono davvero.

### Spostamento automatico in liste "Completati con/senza recensione"

Richiesta esplicita: una volta completato un item del backlog, dovrebbe
"sparire" automaticamente in una di due liste dedicate a seconda che
l'utente abbia scritto la recensione o meno, con un avviso prima dello
spostamento (non uno spostamento silenzioso).

- **Due liste gestite dall'app, identificate da un tag stabile, non dal
  nome**: `BacklogListEntity.systemKind` (colonna nullable, `MIGRATION_3_4`
  additiva) porta `domain/model/BacklogListKind` (`COMPLETED_WITH_REVIEW`/
  `COMPLETED_AWAITING_REVIEW`) — vedi la sottosezione dedicata più sotto
  ("Nomi delle due liste...") per il ragionamento completo, incluso perché
  la primissima versione usava invece un nome fisso non localizzato e
  perché è stata rivista dopo il feedback dell'utente.
- **`BacklogRepository.getOrCreateSystemList(kind, displayName)`** (nuovo,
  in `BacklogRepositoryImpl`) risolve per `systemKind` esatto
  (`backlog_lists WHERE systemKind = :kind`) o crea la lista in coda con
  quel tag e `displayName` come nome iniziale se non esiste — poi riusa
  `moveItem()` già esistente (stessa history `CAMBIO_LISTA` del riordino
  manuale). Il chiamante (ViewModel) risolve `displayName` da
  `context.getString()` nella lingua corrente dell'app.
- **Trigger "No" (non scrivere recensione)**: `BacklogItemDetailViewModel`
  — il pulsante "No" del prompt "vuoi scrivere una recensione?" ora chiama
  `onReviewDeclined()` invece di limitarsi a chiudere il dialog, che espone
  un secondo one-shot `pendingMove: StateFlow<PendingListMove?>`
  consumato da un nuovo `AlertDialog` in `BacklogItemDetailScreen`
  ("Sposta"/"Non spostare") — è **questo** dialog il punto in cui l'utente
  viene avvisato prima dello spostamento vero, non il primo prompt.
  Chiudere il primo dialog toccando fuori (`onDismissRequest`, invariato,
  → `onReviewPromptConsumed()`) resta un vero e proprio "decido dopo": non
  offre lo spostamento, l'item resta dov'è.
- **Trigger "Sì" (scrivi recensione)**: lo spostamento non può avvenire al
  tap su "Sì" (l'utente potrebbe non arrivare mai a salvare), quindi vive
  in `ReviewFormViewModel`, agganciato al **primo** salvataggio riuscito di
  una recensione creata dal backlog — sia il tasto ✓ esplicito (`save()`)
  sia il salvataggio implicito di bozza premendo indietro
  (`onBackPressed()`, Fase 8 precedente). Entrambi condividono
  `offerMoveToCompletedWithReview()`: popola lo stesso `pendingMove`
  one-shot e **rimanda** la callback di navigazione (`onSaved`/`onDone`)
  finché l'utente non risponde al dialog — chi tocca "Sposta" o "Non
  spostare" fa proseguire la navigazione, mai prima. Guardia esplicita
  `editingId == null`: modificare in un secondo momento una recensione già
  collegata (aperta ora anche tramite il link "Recensione collegata", vedi
  sopra) **non** ripropone l'offerta ad ogni salvataggio — solo alla
  creazione originale.
- **Icona "sposta in lista" (freccia su cartella) che "non faceva
  niente"**: causa reale, non un bug di rendering — con una sola lista nel
  backlog il menu a tendina non aveva alcuna voce da mostrare (il filtro
  esclude la lista corrente), quindi il tap apriva un `DropdownMenu` vuoto,
  visivamente indistinguibile dal "niente è successo". Fix:
  `IconButton` disabilitato (`enabled = otherLists.isNotEmpty()`) quando
  non c'è nessun'altra lista verso cui spostare — un'icona visibilmente
  spenta invece di un tap silenzioso. Con l'auto-creazione delle due liste
  sopra, dopo il primo completamento l'icona torna comunque utile (c'è
  sempre almeno un'altra lista tra cui scegliere).
- **Nessun modo di scrivere una recensione più tardi, da "Completati in
  attesa di recensione"**: l'unico innesco del flusso "vuoi scrivere una
  recensione?" era il momento esatto del cambio di stato a Completato —
  rispondere "No" (e quindi finire nella lista "in attesa") non lasciava
  nessun altro punto d'ingresso, a parte il trucco di cambiare stato e
  rimetterlo su Completato per far riscattare il guard `reviewId == null`
  in `onSaveStatus()`. Fix: nello stesso punto dove compare "Recensione
  collegata" (quando l'item ha già una recensione), ora compare un link
  cliccabile "Scrivi una recensione" ogni volta che l'item è Completato
  *senza* una recensione collegata — persistente, funziona da qualunque
  lista si trovi l'item, e riusa `onWriteReview(item.id)` esistente.
- **Nomi delle due liste: da costanti fisse a un tag di identità stabile
  con etichetta localizzata**: la prima versione usava due stringhe fisse
  in italiano (`BacklogSystemLists`, non `stringResource`) per evitare che
  un cambio di lingua dell'app facesse ricreare una seconda lista parallela
  al prossimo trigger (il lookup era per nome esatto). L'utente ha fatto
  notare, correttamente, che così un utente che usa l'app in inglese
  vedrebbe comunque nomi di lista in italiano — un compromesso peggiore del
  necessario. Soluzione adottata: `BacklogListEntity.systemKind` (colonna
  nuova, nullable, `MIGRATION_3_4` additiva — `NULL` per ogni lista
  esistente/normale) porta un identificatore stabile e non localizzato
  (`domain/model/BacklogListKind`, enum `COMPLETED_WITH_REVIEW`/
  `COMPLETED_AWAITING_REVIEW`), usato per il lookup in
  `BacklogRepository.getOrCreateSystemList(kind, displayName)` **al posto**
  del nome. Il nome vero e proprio (`displayName`, risolto dal chiamante
  via `context.getString(R.string.backlog_list_completed_...)`, quindi
  nella lingua corrente dell'app) viene scritto solo al momento della
  *creazione* della lista — esattamente come un nome di lista digitato a
  mano da un utente, non segue retroattivamente un cambio lingua successivo
  (stesso comportamento di qualunque altro dato testuale salvato
  nell'app). Il vantaggio pratico: la lista non si duplica mai più al
  cambio lingua (il match è sempre per `systemKind`), e chi la crea per la
  prima volta la vede nella propria lingua — senza dover toccare ogni punto
  della UI che mostra un nome di lista (sarebbe stato necessario solo con
  un'alternativa "nome sempre risolto al volo dal kind ad ogni render", non
  scelta per non introdurre quella complessità aggiuntiva).

### Quinta verifica su device: BackHandler mancante nel form recensione, HowLongToBeat raggiunge finalmente il sito reale (ma 404)

Due segnalazioni: la duplicazione di recensioni dal flusso backlog
continuava a verificarsi *sempre* per lo stesso gioco nonostante il fix
del giro precedente, e HowLongToBeat ora restituisce una pagina 404 reale
di howlongtobeat.com invece di un errore di rete.

- **Causa reale della duplicazione, non risolta dal fix precedente**: il
  fix del giro scorso (link cliccabile "Recensione collegata" +
  `editingId != null` come guardia) presumeva che l'unico modo di uscire
  dal form recensione fosse la freccia in alto a sinistra, la cui
  `onClick` chiama `viewModel.onBackPressed { onCancel() }` (salvataggio
  implicito della bozza + collegamento al backlog item + offerta di
  spostamento lista). **Il gesto di back di sistema (swipe/tasto hardware)
  non passa da lì**: Compose Navigation registra un proprio
  `OnBackPressedCallback` che di default fa un `popBackStack()` nudo,
  bypassando completamente la logica custom della schermata a meno di non
  intercettarlo esplicitamente con un `BackHandler`. Risultato osservato:
  l'utente esce col gesto di sistema invece di toccare la freccia, nulla
  viene salvato né collegato, l'item torna alla schermata di dettaglio
  backlog con `reviewId` ancora `null` — che essendo lo stato "onesto"
  dell'item, ripropone correttamente (non è un bug in sé) il link "Scrivi
  una recensione"/il prompt, e ogni tentativo successivo genera un'altra
  recensione indipendente. Fix: `BackHandler` aggiunto in
  `ReviewFormScreen.kt` che richiama la stessa `onBackPressed()` — ora
  gesto di sistema e freccia in alto si comportano identicamente.
  Nessun'altra schermata dell'app ha logica di back personalizzata che
  diverge dal semplice pop di default, quindi è l'unico punto che
  necessitava di questo fix.
- **HowLongToBeat: il fix del redirect 308 (terzo giro) ha funzionato
  davvero** — prova concreta, non più solo teoria: l'errore ora riportato
  è un **HTTP 404 con corpo HTML reale** ("HowLongToBeat - 404",
  proveniente da `https://howlongtobeat.com/api/s...`), non più un 308
  nudo o un errore di connessione. Questo conferma che il client segue
  correttamente i redirect e dialoga con il sito vero. Il problema attuale
  è quindi diverso e più specifico: il percorso di ricerca usato
  (derivato dal bundle JS o dal fallback storico `/api/s/`) non esiste più
  su howlongtobeat.com. Non è stato tentato un nuovo fix "a naso" sul
  valore del percorso (indovinare un'altra stringa senza poterla
  verificare avrebbe lo stesso tasso di successo del tentativo precedente)
  — invece, `HltbAuth` porta ora un campo `source` che distingue se il
  percorso usato viene dall'estrazione dal bundle (con il valore esatto
  estratto) o dal fallback statico (con il motivo: bundle non trovato,
  regex senza match, o l'intera estrazione fallita), incluso nel messaggio
  d'errore mostrato in-app. Un prossimo report dirà con certezza se il
  problema è "il fallback storico è ormai morto" (serve una nuova ricerca
  sul percorso corrente, impossibile da questo sandbox senza accesso di
  rete) oppure "la regex sul bundle intercetta il `fetch()` sbagliato"
  (fixabile stringendo la regex, ma solo con la prova che sia davvero
  quello il caso).

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
PR #2 per la Fase 2, PR #3 per la Fase 3, PR #4 per la Fase 4). La Fase 5
(questa modifica) è stata scritta con revisione statica riga per riga ma
**non ancora verificata su CI al momento di scrivere questa nota** —
controlla lo stato dei check sulla relativa PR prima di considerarla verde;
se emergono errori di compilazione, le nuove dipendenze
(`androidx.datastore:datastore-preferences`, `androidx.appcompat`) e il
nuovo manifest (`android:localeConfig`, il service `AppLocalesMetadataHolderService`)
sono il primo posto dove guardare — attenzione anche a `lint` sulle due
`strings.xml`: se le chiavi IT/EN divergono, `MissingTranslation` la segnala.
Il repository ha anche un secondo workflow, `build-apk.yml`, aggiunto
manualmente fuori da queste sessioni: non toccarlo a meno che non serva, ma
tienilo a mente quando controlli lo stato CI di una PR (di solito compaiono
più check `build-and-test` insieme a un check `build`).

(Nota: questo paragrafo racconta lo stato al momento della Fase 5; Fasi 6-8
sono state scritte con lo stesso approccio — revisione statica, nessuna
build locale possibile — ognuna con la propria nota "Stato build" nella
rispettiva sezione qui sotto. Controlla sempre i check della PR più
recente, non fidarti solo di questo paragrafo per lo stato attuale.)

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
  necessarie di conseguenza e documentate lì. In Fase 5 idem: solo
  `androidx.datastore:datastore-preferences` (tema) e `androidx.appcompat`
  (lingua per-app), entrambe esplicitamente richieste. In Fase 6 **nessuna
  dipendenza aggiunta**: client TheGamesDB scritto a mano come Drive (niente
  Retrofit/Ktor nonostante fossero citati come esempio nella richiesta),
  drag-to-reorder del backlog implementato con Compose Foundation puro
  (niente libreria di reorder). In Fase 8 idem: **nessuna dipendenza
  aggiunta** — client HowLongToBeat scritto a mano come TheGamesDB/Drive,
  vista a griglia con `LazyVerticalGrid`/`GridCells` (già parte di Compose
  Foundation, stesso artefatto di `LazyColumn`) e icone da
  `material-icons-extended` (già dipendenza esistente dalla Fase 1).
- Export PDF: solo `android.graphics.pdf.PdfDocument` nativo. Niente
  Apache PDFBox né iText7 (iText7 è AGPL, esplicitamente escluso).
- Nessuna stringa hardcoded nelle schermate: ogni testo visibile in `ui/`
  passa da `stringResource()` (Compose) o `context.getString()` (ViewModel,
  via `@ApplicationContext Context` iniettato con Hilt), con voce
  corrispondente in `values/strings.xml` **e** `values-en/strings.xml`. Le
  due liste di chiavi vanno tenute allineate: se aggiungi una stringa in
  una lingua, aggiungila subito anche nell'altra invece di lasciare un
  fallback silenzioso sull'italiano.

## Cosa NON fare finché non richiesto esplicitamente

Export DOCX: **permanentemente fuori scope** (decisione presa, non solo
rimandata — vedi sezione dedicata sopra), non riconsiderare senza una
richiesta esplicita. Autenticazione utente/multi-account: fuori scope, la
Fase 4 usa OAuth solo per l'autorizzazione verso Drive, non introduce un
concetto di account applicativo.
