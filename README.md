# ThePatientGamerHelper

App Android nativa per tenere traccia delle recensioni dei videogiochi che
finisco (o abbandono). Nasce per sostituire un flusso che tenevo a mano tra
note sparse e post per r/patientgamer: una scheda per gioco con voto,
piattaforma, genere, pro e contro, e uno spazio libero per il testo della
recensione vera e propria.

Single-user, offline-first: i dati vivono sul dispositivo, non serve nessun
account per usarla, il cloud entra in gioco solo come backup opzionale.

## Cosa fa

- **Libreria recensioni**: crea, modifica, cancella. Ricerca full-text e
  filtri combinabili per piattaforma, genere, tag, voto, stato e intervallo
  di date. Ordinamento per data, voto, titolo o ore di gioco. Copertina
  presa dalla galleria del telefono, senza permessi di storage.
- **Statistiche**: numero di recensioni, voto medio, ore totali giocate,
  distribuzione per piattaforma e genere, ripartizione tra completati, in
  corso e abbandonati.
- **Export**: Markdown pronto per essere incollato su Reddit, JSON e CSV
  per portabilità dei dati, PDF per singola recensione o per l'intera
  libreria in un unico file.
- **Backup su Google Drive**: manuale o automatico una volta al giorno,
  salvato nella cartella privata dell'app (non visibile né condivisibile
  dall'interfaccia di Drive). Ripristino da un elenco dei backup disponibili.
- **Lingua e tema**: interfaccia in italiano o inglese, selezionabile
  dall'app indipendentemente dalla lingua di sistema; tema chiaro, scuro o
  a scarto automatico su quello di sistema.
- **Backlog**: liste di giochi da giocare, con stato (da iniziare, in
  corso, completato, abbandonato, in pausa), commenti, storico automatico
  degli eventi e riordino manuale per prioritizzare. Al completamento di un
  item propone di scrivere subito la recensione, precompilata con i dati
  già noti.
- **Ricerca online (TheGamesDB)**: dal form di backlog o di recensione,
  cerca un gioco per titolo e scegli tra i risultati per scaricare
  copertina e metadati in automatico, invece di inserirli a mano. Richiede
  una API key personale TheGamesDB (gratuita, da registrare sul sito),
  configurabile in Impostazioni — senza chiave il resto dell'app funziona
  comunque, solo la ricerca resta disattivata.

Nessuna di queste funzionalità richiede un account: il backup su Drive e la
ricerca online sono le uniche eccezioni, ed entrambe sono facoltative.

## Stack tecnico

Kotlin e Jetpack Compose con Material 3, seguendo le linee guida
architetturali correnti di Google piuttosto che il vecchio sistema a View.

- **Room** come unica fonte di verità per i dati, esposta via `Flow`
- **Hilt** per la dependency injection
- **ViewModel + StateFlow**, flusso di dati unidirezionale (gli eventi
  salgono, lo stato scende)
- **WorkManager** per il backup periodico in background
- **Preferences DataStore** per la preferenza di tema
- **Credential Manager** e **AuthorizationClient** per l'autenticazione e
  l'autorizzazione verso Google Drive (non la vecchia `GoogleSignInClient`,
  ormai deprecata)
- `minSdk 26`, `targetSdk 36`, `compileSdk 36`

Nessuna dipendenza pesante dove non serve: niente libreria di charting per
le statistiche (bastano barre Compose native), niente client Java ufficiale
di Google per Drive (un client REST scritto a mano con `HttpURLConnection`
copre i tre endpoint che servono), niente Apache POI o iText per il PDF
(`android.graphics.pdf.PdfDocument` nativo, iText7 è AGPL e quindi escluso a
priori), niente Retrofit/Ktor per TheGamesDB (stesso client REST scritto a
mano usato per Drive) né libreria di reorder per il drag-to-reorder del
backlog (Compose Foundation puro).

## Build

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

Richiede l'Android SDK (`compileSdk 36`) e accesso al repository Maven di
Google. Per usare il backup su Drive va anche configurato un client OAuth
in Google Cloud Console — i dettagli sono in `CLAUDE.md`.

## Struttura del progetto

```
app/src/main/java/com/marcogn/thepatientgamerhelper/
├── data/       # Room (entity/dao), repository, export (SAF/PDF), backup/drive
│               # (Google Drive, WorkManager), thegamesdb (ricerca online),
│               # preferenze (tema), seed dati di debug
├── domain/     # Modelli puri, logica di filtro/ordinamento, formattazione export/backup
├── di/         # Moduli Hilt
└── ui/         # Schermate Compose (libreria, dettaglio, form, statistiche,
                # backlog, impostazioni) + tema + navigazione
```

## Documentazione

- `docs/spec.md` — specifica funzionale e tecnica, con la roadmap delle fasi
  di sviluppo
- `docs/decisioni-implementazione.md` — scelte tecniche non ovvie prese
  durante lo sviluppo, fase per fase
- `CLAUDE.md` — guida di riferimento per chi (o cosa) lavora su questo
  codice: architettura, convenzioni, limiti noti
- `docs/en/` — traduzione inglese della documentazione sopra (l'italiano
  resta la fonte di verità)

## Dati demo

Le build `debug` seedano automaticamente qualche recensione di esempio
(`data/debug/DebugSeeder.kt`) per non partire da uno schermo vuoto durante
lo sviluppo. Le build `release` non includono mai dati finti.

## Licenza

MIT, vedi `LICENSE`.
