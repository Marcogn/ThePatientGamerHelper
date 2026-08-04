# Decisioni di implementazione

Questo file documenta le scelte tecniche prese durante l'implementazione
delle varie fasi che non erano già esplicitate in
`spec-app-recensioni-videogiochi.md` o in `CLAUDE.md`.

## Fase 4 (Backup cloud Google Drive)

Vedi la sezione "Fase 4 — Backup cloud Google Drive" in `CLAUDE.md` per il
riepilogo architetturale completo (autenticazione, formato di backup,
worker periodico, UI). Qui solo le scelte che non erano ovvie dalla
richiesta originale.

### `domain/backup` separato da `domain/export`

La richiesta parlava genericamente di "JSON completo dei dati" per il
backup, e la Fase 2 aveva già un formato di export JSON
(`domain/export/ReviewExportDto.kt`). Ho scelto di **non riusarlo** e creare
un DTO di backup dedicato (`domain/backup/BackupReviewDto.kt`) per due
motivi concreti, non solo principio di separazione:
- Il DTO di export Fase 2 ha `copertina` come **percorso assoluto sul
  device** (`context.filesDir/covers/<uuid>.jpg`) — corretto per un export
  che l'utente scarica e guarda, ma inutilizzabile per un restore su
  un'installazione diversa (percorso diverso, magari device diverso). Il
  DTO di backup porta invece solo il **nome file** della copertina,
  risolto a un path nuovo al momento del restore.
- Le etichette del DTO di export sono in italiano, pensate per essere
  leggibili da chi apre il JSON esportato; il formato di backup è interno
  e non ha bisogno di quel vincolo, né deve essere accoppiato
  all'evoluzione del formato di export (già ha un `schemaVersion` proprio
  per poter cambiare in futuro senza toccare l'export utente, e
  viceversa).

### Restore come sovrascrittura completa, non merge

Richiesto esplicitamente ("nessuna gestione di merge/conflitti... in caso
di restore va bene una sovrascrittura completa"). Implementato con un nuovo
metodo `ReviewRepository.replaceAll(reviews: List<Review>)`, distinto da
`save()`:
- `save()` è pensato per il form crea/modifica: genera un nuovo id se
  assente, imposta `createdAt` a "ora" per le nuove recensioni e lo
  preserva per le modifiche cercandolo sulla riga esistente.
- Un restore deve invece **preservare esattamente** `id`/`createdAt`/`updatedAt`
  dal backup — se avessi riusato `save()`, dopo aver cancellato le
  recensioni esistenti (necessario per l'overwrite) la ricerca del
  `createdAt` precedente sulla riga (ormai cancellata) sarebbe fallita,
  facendo perdere la data di creazione originale a ogni recensione
  ripristinata. `replaceAll()` scrive l'entità Room direttamente con i
  timestamp del backup, in un'unica transazione che cancella anche le
  tabelle di lookup (platform/genre/tag) prima di ricrearle — altrimenti
  restore ripetuti accumulerebbero voci di lookup orfane mai più
  referenziate da nessuna recensione, sporcando l'autocomplete.
- La logica di risoluzione nome→id lookup e scrittura cross-ref/pro-con
  era duplicata fra `save()` e la prima bozza di `replaceAll()`: estratta
  in un metodo privato condiviso (`writeRelations`) su
  `ReviewRepositoryImpl`.

### Client Drive scritto a mano, non il client Java ufficiale di Google

La richiesta specificava "Drive REST API v3" (non "Google API Client
Library for Java"), e CLAUDE.md chiede esplicitamente di non aggiungere
dipendenze senza necessità reale. `google-api-client-android` +
`google-api-services-drive` sono le librerie ufficiali ma pesanti (portano
Guava e un grafo di dipendenze ampio) per tre soli endpoint (upload
multipart, list, download by id). Ho scritto un client minimale con
`java.net.HttpURLConnection` in `data/drive/DriveApiClient.kt` — zero
dipendenze aggiuntive oltre a `kotlinx.serialization` (già presente) per
il parsing delle risposte JSON.

### Cadenza del backup automatico non configurabile

WorkManager supporta un intervallo minimo di 15 minuti per il lavoro
periodico; ho scelto una cadenza fissa giornaliera
(`BackupScheduler`, 24h, `NetworkType.CONNECTED`) senza esporre in UI la
possibilità di cambiarla. Per un'app di recensioni personale, dove i dati
cambiano al più qualche volta al giorno, un backup giornaliero è più che
sufficiente e una UI di configurazione dell'intervallo sarebbe complessità
non richiesta — stesso principio "non over-engineerare" applicato al resto
della Fase 4 (restore senza merge, nessuna UI per gestire/cancellare
backup vecchi).

## Fase 3 (Statistiche libreria)

Le decisioni sotto restano riferite specificamente alla Fase 3. Vedi anche
la sezione "Fase 3 — Statistiche libreria" in `CLAUDE.md` per il riepilogo
architetturale.

### Nessuna dipendenza di charting aggiunta

La spec/task lasciava la scelta aperta tra barre Compose native e Vico (se
"la complessità in più è giustificata"). Ho optato per barre Compose native
(`Box` con `fillMaxWidth(fraction = count / maxCount)` per le distribuzioni,
una barra impilata a segmenti per lo stato) e **nessuna nuova dipendenza**.

Motivazione:
- CLAUDE.md indica esplicitamente di non aggiungere dipendenze per Fase 3/4
  senza richiesta esplicita, segnalando piuttosto la necessità.
- Il caso d'uso è una libreria single-user: il numero di piattaforme/generi
  distinti è tipicamente piccolo (poche piattaforme possedute, un numero
  moderato di generi), quindi barre orizzontali semplici restano leggibili
  senza bisogno di scroll/zoom/interattività che giustifichino una libreria
  di charting.
- Se in futuro le esigenze di visualizzazione crescono (grafici a torta,
  trend temporali, drill-down interattivo), Vico resta la scelta raccomandata
  da rivalutare a quel punto, invece di continuare ad estendere rendering
  manuale con `Canvas`/`Box`.

### Percentuali solo sulla ripartizione per stato

La spec chiede esplicitamente una "percentuale completato/abbandonato/in
corso" ma non chiede percentuali per le distribuzioni piattaforma/genere.
Questo non è un'omissione: piattaforma e genere sono relazioni many-to-many
con la recensione (una recensione può avere più piattaforme e più generi),
mentre lo stato è un campo singolo (enum, un solo valore per recensione).

Se si calcolasse una percentuale per piattaforma/genere dividendo per il
numero totale di recensioni, le percentuali visualizzate non sommerebbero al
100% (una recensione multi-piattaforma verrebbe conteggiata più volte),
risultando fuorviante per chi legge lo schermo aspettandosi un totale del
100% come per lo stato. Ho quindi scelto di mostrare le distribuzioni
piattaforma/genere solo come conteggi assoluti (con barra proporzionale al
valore massimo nel set), mantenendo la percentuale solo dove il dato è a
scelta singola e la percentuale è matematicamente coerente.

### Struttura dati per le aggregazioni

- `domain/model/LibraryStatistics.kt`: modelli puri (`LibraryStatistics`,
  `DistributionEntry`, `StatusShare`), nessuna dipendenza Android.
- `domain/stats/LibraryStatisticsCalculator.kt`: funzione pura
  `computeLibraryStatistics(List<Review>): LibraryStatistics`, stesso
  pattern di `domain/filter/LibraryFiltering.kt` — unit-testabile in JVM
  puro senza SDK Android o Robolectric (vedi
  `domain/stats/LibraryStatisticsCalculatorTest.kt`).
- Le distribuzioni sono ordinate per conteggio decrescente (poi per nome, a
  parità di conteggio) — stessa euristica "più frequente in cima" per
  piattaforma e genere.
- `ore totali tracciate` somma `hoursPlayed`, trattando `null` (campo
  opzionale) come 0 anziché escludere la recensione dal conteggio totale.
- `voto medio` è `Double?` (non `Double`) per distinguere esplicitamente "0
  recensioni" (nessun voto medio, mostrato come "—" in UI) da un'ipotetica
  media pari a 0.0.

### UI e navigazione

- Nuova route `Destination.Stats` (oggetto senza parametri, coerente con
  `Destination.Library`), raggiungibile da un'icona (`Icons.Filled.BarChart`)
  nella top bar della libreria, accanto a filtri/ordinamento/export.
- `ui/stats/StatsScreen.kt` + `StatsViewModel` + `StatsUiState`: stesso
  pattern MVVM/UDF delle altre schermate (`ui/library`, `ui/detail`), con
  `ReviewRepository.observeAll()` come unica fonte dati (nessun mock).
