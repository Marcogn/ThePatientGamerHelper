# game-reviewer

App Android nativa, single-user e offline-first per gestire le mie
recensioni di videogiochi (supporto al flusso di scrittura per
r/patientgamer). Vedi `spec-app-recensioni-videogiochi.md` per la specifica
completa e `CLAUDE.md` per le note di architettura/sviluppo.

## Stato

**Fase 1 (MVP locale)** completata: CRUD recensioni, libreria con
ricerca/filtri/ordinamento, dettaglio, form crea/modifica con copertina
immagine. Export, statistiche e backup cloud sono fasi future, non ancora
implementate.

## Stack

- Kotlin, Jetpack Compose, Material 3
- Room (persistenza locale, single source of truth via `Flow`)
- Hilt (dependency injection)
- ViewModel + `StateFlow`, unidirectional data flow
- `minSdk 26`, `targetSdk 36`, `compileSdk 36`

## Build

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Richiede Android SDK (`compileSdk 36`) e accesso al repository Maven di
Google. Vedi `CLAUDE.md` per una nota sui limiti dell'ambiente di sviluppo
usato per lo scaffolding iniziale.

## Struttura

```
app/src/main/java/com/marcogn/gamereviewer/
├── data/       # Room (entity/dao), repository, seed dati di debug
├── domain/     # Modelli puri e logica di filtro/ordinamento
├── di/         # Moduli Hilt
└── ui/         # Schermate Compose (libreria, dettaglio, form) + navigazione
```

## Dati demo (solo debug)

Le build `debug` seedano automaticamente qualche recensione di esempio
(`data/debug/DebugSeeder.kt`) per facilitare lo sviluppo/anteprima UI. Le
build `release` non includono mai dati mock.
