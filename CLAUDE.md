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
- **Fase 4 — Backup cloud Google Drive**: non iniziata, fuori scope.
- **Fase 5 — Export DOCX**: non iniziata, fuori scope, opzionale.

Non implementare funzionalità di fasi successive a meno che l'utente non lo
richieda esplicitamente in una nuova sessione.

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
│   └── debug/            # DebugSeeder, attivo solo dietro BuildConfig.SEED_DEBUG_DATA
├── domain/
│   ├── model/            # Modelli di dominio puri (no dipendenze Android)
│   ├── filter/            # Logica di filtro/ordinamento libreria, pure function, unit-testata
│   └── export/            # Formattazione export pura: JSON (kotlinx.serialization),
│                          # CSV (writer manuale), Markdown (template stringhe) —
│                          # nessun import Android, unit-testabile in JVM puro
├── di/                    # Moduli Hilt (Database, Repository)
└── ui/
    ├── theme/             # Tema Material 3 (Compose)
    ├── navigation/        # Navigation Compose, route type-safe (kotlinx.serialization)
    ├── library/           # Schermata libreria (lista, ricerca, filtri, ordinamento, export)
    ├── detail/            # Schermata dettaglio recensione (+ export singola recensione)
    ├── form/              # Form crea/modifica
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
PR #2 per la Fase 2). Il repository ha anche un secondo workflow,
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
- Non introdurre nuove dipendenze per la Fase 4 (backup) senza che sia
  esplicitamente richiesto: se emergono necessità relative, segnalale invece
  di implementarle. Stesso principio già applicato in Fase 3 (statistiche):
  nessuna libreria di charting aggiunta, vedi sezione dedicata sopra.
- Export PDF: solo `android.graphics.pdf.PdfDocument` nativo. Niente
  Apache PDFBox né iText7 (iText7 è AGPL, esplicitamente escluso).

## Cosa NON fare finché non richiesto esplicitamente

Export DOCX (Fase 5), backup cloud Google Drive (Fase 4), autenticazione:
fuori scope anche se menzionati nella spec.
