# CLAUDE.md

Guida per agenti Claude che lavorano su questo repository. Leggi anche
`spec-app-recensioni-videogiochi.md` per la specifica funzionale completa.

## Cos'è questo progetto

App Android nativa, single-user, offline-first per gestire recensioni di
videogiochi (flusso personale per r/patientgamer). Kotlin + Jetpack Compose +
Material 3, Room, Hilt, ViewModel/StateFlow con unidirectional data flow.

## Stato di avanzamento per fasi

- **Fase 1 — MVP locale**: ✅ completata in questa sessione (CRUD, libreria con
  ricerca/filtri/ordinamento, dettaglio, form crea/modifica, copertina immagine).
- **Fase 2 — Export** (Markdown/JSON/CSV/PDF): non iniziata, fuori scope.
- **Fase 3 — Statistiche libreria**: non iniziata, fuori scope.
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

## Package/architettura

```
com.marcogn.gamereviewer
├── data/
│   ├── local/
│   │   ├── entity/      # Entità Room (Review, Platform, Genre, Tag, cross-ref, ProCon)
│   │   ├── dao/          # DAO Room, esposti come Flow
│   │   └── Converters.kt # TypeConverter per LocalDate/Instant/enum
│   ├── repository/       # Implementazioni dei repository (upsert transazionale)
│   └── debug/            # DebugSeeder, attivo solo dietro BuildConfig.SEED_DEBUG_DATA
├── domain/
│   ├── model/            # Modelli di dominio puri (no dipendenze Android)
│   └── filter/            # Logica di filtro/ordinamento libreria, pure function, unit-testata
├── di/                    # Moduli Hilt (Database, Repository)
└── ui/
    ├── theme/             # Tema Material 3 (Compose)
    ├── navigation/        # Navigation Compose, route type-safe (kotlinx.serialization)
    ├── library/           # Schermata libreria (lista, ricerca, filtri, ordinamento)
    ├── detail/            # Schermata dettaglio recensione
    ├── form/              # Form crea/modifica
    └── common/            # Composable condivisi (chip input, rating, ecc.)
```

Regola guida: **Room è la single source of truth**, esposta via `Flow`. I
ViewModel combinano il flow di dati con lo stato UI locale (query di ricerca,
filtri selezionati) usando `combine()`, producendo un unico `StateFlow` di UI
state consumato dalla Compose UI (pattern UDF: eventi salgono via lambda,
stato scende via `StateFlow`).

La logica di filtro/ordinamento vive in `domain/filter` come funzioni Kotlin
pure (nessun import Android), per essere unit-testabile in JVM puro senza
bisogno dell'SDK Android o di Robolectric.

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

**Stato build al termine della Fase 1: verde su CI** (`lintDebug`,
`testDebugUnitTest`, `assembleDebug` passano tutti su GitHub Actions, vedi
PR #1). Il primo push aveva fallito la compilazione per un `FlowRow` usato
senza `@OptIn(ExperimentalLayoutApi::class)` (`ui/common/TagInputField.kt`,
`ui/library/FilterSheet.kt`) — corretto e riverificato in CI. Se aggiungi
altre API Compose Foundation/Material3 sperimentali, ricorda l'opt-in
esplicito: il modulo tratta i mancati opt-in come **errori**, non warning.

Cosa è stato verificato in questa sessione:
- Revisione statica riga per riga di tutti i file Kotlin (import, coerenza
  package/directory, firme Room @Relation/@Junction, copertura dei
  TypeConverter, wiring Hilt) via un sub-agent di review dedicato.
- Unit test JVM puri (`domain/filter`, `domain/model`) più test Room DAO via
  **Robolectric** (`data/local/ReviewDaoTest.kt`, gira come unit test JVM
  senza bisogno di emulatore) — eseguiti con successo in CI.
- Build `assembleDebug` e `lintDebug` completate con successo in CI dopo il
  fix del `FlowRow`.

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
- Non introdurre nuove dipendenze per Fase 2/3/4 (export, statistiche,
  backup) in questa fase: se emergono necessità relative, segnalale invece
  di implementarle.

## Cosa NON fare in questa fase

Export (Markdown/JSON/CSV/PDF/DOCX), statistiche libreria, backup cloud
Google Drive, autenticazione: tutto esplicitamente fuori scope per la Fase 1,
anche se menzionato nella spec.
