# game-reviewer

App Android nativa, single-user e offline-first per gestire le mie
recensioni di videogiochi (supporto al flusso di scrittura per
r/patientgamer). Vedi `spec-app-recensioni-videogiochi.md` per la specifica
completa e `CLAUDE.md` per le note di architettura/sviluppo.

## Stato

- **Fase 1 (MVP locale)** completata: CRUD recensioni, libreria con
  ricerca/filtri/ordinamento, dettaglio, form crea/modifica con copertina
  immagine.
- **Fase 2 (Export)** completata: JSON/CSV per l'intera libreria, Markdown
  compatibile Reddit per singola recensione, PDF (singola recensione o
  libreria in batch). Salvataggio sempre tramite Storage Access Framework.
- **Fase 3 (Statistiche libreria)** completata: schermata Statistiche
  raggiungibile dalla libreria con totali/medie, distribuzione per
  piattaforma/genere e ripartizione per stato.
- **Fase 4 (Backup cloud Google Drive)** completata: backup manuale e
  automatico (WorkManager) su Google Drive (appDataFolder, via Credential
  Manager + AuthorizationClient), ripristino da elenco backup disponibili.

Export DOCX non è più in roadmap: vedi `CLAUDE.md` per il perché.

## Stack

- Kotlin, Jetpack Compose, Material 3
- Room (persistenza locale, single source of truth via `Flow`)
- Hilt (dependency injection)
- ViewModel + `StateFlow`, unidirectional data flow
- WorkManager (backup automatico periodico) + Credential Manager /
  AuthorizationClient (autenticazione/autorizzazione Google Drive)
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
├── data/       # Room (entity/dao), repository, export (SAF/PDF), backup/drive
│               # (Google Drive, WorkManager), seed dati di debug
├── domain/     # Modelli puri, logica di filtro/ordinamento, formattazione export/backup
├── di/         # Moduli Hilt
└── ui/         # Schermate Compose (libreria, dettaglio, form, impostazioni) + navigazione
```

## Dati demo (solo debug)

Le build `debug` seedano automaticamente qualche recensione di esempio
(`data/debug/DebugSeeder.kt`) per facilitare lo sviluppo/anteprima UI. Le
build `release` non includono mai dati mock.
