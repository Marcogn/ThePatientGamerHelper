# Decisioni di implementazione

Questo file documenta le scelte tecniche prese durante l'implementazione
delle varie fasi che non erano già esplicitate in `docs/spec.md` o in
`CLAUDE.md`.

## Fase 6 (Backlog tracciabile e fetch metadati TheGamesDB)

Vedi la sezione "Fase 6 — Backlog tracciabile e fetch metadati (TheGamesDB)"
in `CLAUDE.md` per il riepilogo architetturale completo. Qui solo le
scelte che non erano ovvie dalla richiesta originale.

### La API key TheGamesDB: verificare prima di assumere

La richiesta di partenza conteneva un'assunzione esplicita ("non dovrebbe
essere necessaria per gamedb, per esde non lo è") con l'istruzione di
verificarla prima di implementare un placeholder inutile. La verifica
(ricerca sul forum ufficiale e sui changelog di scraper open source come
Skyscraper e sselph/scraper) ha dato un risultato opposto: **dal 17/02/2026
TheGamesDB richiede una `apikey` su ogni endpoint**, pubblico o privato che
sia — l'accesso anonimo non esiste più. ES-DE e Skyscraper non chiedono una
chiave *personale* all'utente finale, ma incorporano nel proprio codice
sorgente una chiave pubblica condivisa (rate-limited per IP) — la chiave
c'è comunque, solo che non la vede l'utente finale dello scraper.

Non avendo un modo affidabile per recuperare il valore letterale di quella
chiave pubblica condivisa dai risultati di ricerca disponibili (pagine del
sito principale non raggiungibili, 403), e non volendo incollare una
stringa trovata online senza certezza che sia quella corretta/valida
tuttora, ho **chiesto esplicitamente all'utente** come procedere invece di
indovinare — coerente con l'istruzione esplicita della sessione ("se
qualcosa è ambiguo, fermati e chiedimi"). Risposta ricevuta: la chiave deve
essere compilabile **dentro l'app** a runtime, nessun placeholder nella
build. Da qui `TheGamesDbPreferences` (vedi sotto) invece del pattern
`[DA_COMPLETARE]` già usato per Drive in Fase 4.

### Perché non lo stesso pattern di `drive_config.xml`

Il client ID OAuth di Drive (Fase 4) è un valore che l'utente sostituisce
**nel codice sorgente prima della build** (`res/values/drive_config.xml`,
placeholder `[DA_COMPLETARE]`), perché è legato alla registrazione
dell'app su Google Cloud Console — un valore di configurazione
dell'applicazione, non dell'utente finale. La API key TheGamesDB è invece
personale all'account che l'utente registra sul sito: due utenti diversi
dello stesso APK avrebbero chiavi diverse. Un placeholder di build avrebbe
quindi richiesto di ricompilare l'app ad ogni cambio di chiave (o di
account), mentre un campo in Impostazioni permette di cambiarla senza
toccare il codice — pattern più corretto per un valore per-utente, non
per-installazione.

### Retrofit/Ktor citati nella richiesta, ma non aggiunti

La richiesta menzionava Retrofit/Ktor come esempio di libreria HTTP "se non
già presente". Il progetto aveva già risolto lo stesso problema in Fase 4
con `DriveApiClient` (`HttpURLConnection` + `kotlinx.serialization`, zero
dipendenze aggiuntive oltre a quella già presente per JSON). Per coerenza
interna e perché CLAUDE.md chiede esplicitamente di non aggiungere
dipendenze senza necessità reale, ho scritto `TheGamesDbApiClient` con lo
stesso pattern hand-rolled invece di introdurre Retrofit/Ktor — quattro
endpoint GET (ricerca + tre lookup id→nome) non sono abbastanza per
giustificare un intero client HTTP con la sua catena di dipendenze
(OkHttp/converter JSON, interceptor, ecc.), che peraltro finirebbe per
duplicare quello che `DriveApiClient` già dimostra funzionare bene per
questo progetto.

### `releaseYear`/`developer` solo sul backlog, non sulla recensione

La richiesta elencava "piattaforma, genere, anno, sviluppatore" come
metadati da salvare dalla ricerca online, genericamente per "Tappa 2" (che
tocca sia il form backlog sia il form recensione). Piattaforma e genere
esistevano già su entrambi i modelli; anno e sviluppatore no. Estendere
`ReviewEntity`/`Review` — uno schema con cinque fasi di funzionalità già
costruite sopra (export JSON/CSV/PDF/Markdown, DTO di backup dedicato,
calcolo statistiche) — per due campi bibliografici mai stati parte del
nucleo di una recensione (voto/pro/contro/testo libero) avrebbe avuto un
raggio d'azione sproporzionato rispetto al beneficio: nuova migration,
nuovi campi in ogni formatter di export, nuovo campo nel DTO di backup,
possibile impatto sul calcolo statistiche. Ho aggiunto i due campi solo a
`BacklogItemEntity`/`BacklogItem`, dove hanno più senso concettualmente
(dati di catalogazione per un gioco non ancora giocato) e dove il raggio
d'azione è contenuto a un'entità introdotta in questa stessa sessione. La
ricerca online nel form recensione resta quindi limitata a
titolo/piattaforma/genere/copertina — la stessa scelta di campi già usata
per la pre-popolazione da backlog item (Tappa 1).

### Migration additiva invece di `fallbackToDestructiveMigration()`

L'app introdotta da questo progetto è già in uso reale sul dispositivo di
chi lo sta sviluppando (vedi l'apertura di `CLAUDE.md`), non un prototipo
usa-e-getta. Un `fallbackToDestructiveMigration()` da versione 1 a 2 del
database Room avrebbe cancellato silenziosamente tutte le recensioni
esistenti al primo avvio dopo l'aggiornamento — inaccettabile. Ho scritto
`MIGRATION_1_2` (`data/local/Migrations.kt`) con SQL raw che crea solo le
sette nuove tabelle/indici del backlog, senza toccare `reviews` o le
tabelle di lookup esistenti.

### Drag-to-reorder scritto a mano, gesto separato dal click della riga

Il riordino manuale degli item dentro una lista era esplicitamente
richiesto ("drag-to-reorder, utile per prioritizzare"). Senza aggiungere
una libreria di terze parti, l'opzione più semplice sarebbe stata applicare
`Modifier.pointerInput`/`detectDragGestures` all'intera riga cliccabile —
ma la stessa riga deve anche aprire il dettaglio item al tap. Sovrapporre
un rilevatore di drag e un `clickable` sullo stesso elemento in Compose
porta a conflitti di gestione del gesto non banali da risolvere in modo
affidabile. Ho invece isolato il gesto di drag su una piccola icona
"maniglia" dedicata a fianco della riga (che resta cliccabile per aprire il
dettaglio), traducendo verticalmente l'intera riga tramite stato sollevato
condiviso (`graphicsLayer { translationY = ... }`) mentre il
`pointerInput` resta solo sulla maniglia. L'ordine finale viene scritto sul
repository una sola volta al rilascio del gesto (`onDragEnd`), non ad ogni
variazione di offset durante il trascinamento.

### Liste riordinate con frecce, non drag-and-drop

Spec e CLAUDE.md non specificano il meccanismo di riordino per le liste
stesse (solo per gli item dentro una lista). Il numero di liste in un
backlog personale è tipicamente piccolo (una manciata: "da comprare", "in
corso", ecc.), a differenza degli item che possono essere numerosi e per
cui la spec chiede esplicitamente drag-to-reorder ("utile per
prioritizzare"). Per le liste ho scelto pulsanti su/giù — riordino
altrettanto funzionale con una complessità di implementazione/interazione
molto minore, evitando di scrivere due volte la stessa logica di drag per
un caso d'uso dove il beneficio (poter trascinare invece di premere una
freccia un paio di volte) è marginale.

## Fase 5 (Internazionalizzazione, tema, documentazione)

Vedi la sezione "Fase 5 — Internazionalizzazione, tema e documentazione" in
`CLAUDE.md` per il riepilogo architetturale completo. Qui solo le scelte
che non erano ovvie dalla richiesta originale.

### `ReviewStatus.label()` non toccato, nuova `displayName()` per la UI

La richiesta chiedeva di estrarre le stringhe "delle schermate", non del
contenuto dei file esportati. `ReviewStatus.label()` in `domain/model` però
serve entrambi gli scopi: viene chiamato sia dalle schermate (per mostrare
"In corso"/"Completato"/"Abbandonato") sia da `ReviewMarkdownFormatter` e
`PdfReviewRenderer` in fase di export, dove le etichette restano fisse in
italiano per non rompere il pattern "domain/export puro, senza dipendenze
Android" già stabilito in Fase 2.

Ho lasciato `label()` invariato (continua a essere usato solo dall'export)
e aggiunto `ReviewStatus.displayName()`, un `@Composable` in
`ui/common/ReviewStatusDisplay.kt` che risolve la string resource
localizzata. Le schermate (`FilterSheet`, `ReviewListItem`, `DetailScreen`,
`ReviewFormScreen`, `StatsScreen`) usano tutte `displayName()`. L'alternativa
sarebbe stata rendere `label()` stesso consapevole della lingua, ma avrebbe
richiesto passargli un `Context`/risorse Android, propagando la dipendenza
Android dentro `domain/export` e vanificando la sua testabilità JVM pura.

### Messaggi dei ViewModel: `@ApplicationContext Context` invece di spostare la costruzione in UI

Diversi ViewModel (`LibraryViewModel`, `DetailViewModel`, `ReviewFormViewModel`,
`SettingsViewModel`) costruiscono messaggi testuali (esiti di export/backup,
errori di validazione) che finiscono in uno Snackbar o in un campo di errore
sullo schermo. `stringResource()` non è utilizzabile fuori da un
`@Composable`, quindi le alternative erano: (a) iniettare
`@ApplicationContext Context` nel ViewModel e chiamare `context.getString(...)`,
o (b) far risalire alla UI un id di risorsa/enum di esito e risolvere il
testo lì. Ho scelto (a): è il pattern più diretto, richiede la modifica
minima ai ViewModel esistenti (un parametro in più nel costruttore Hilt) e
non introduce un nuovo tipo "risultato" solo per veicolare una stringa
localizzata — coerente con "niente astrazioni oltre quello che serve" già
seguito nel resto del progetto.

### `recreate()` esplicito dopo il cambio lingua — solo sotto API 33

`AppCompatDelegate.setApplicationLocales()` da solo aggiorna la preferenza
di lingua persistita, ma il ricalcolo automatico delle risorse/Activity in
esecuzione che AppCompat offre in modo affidabile è legato al ciclo di vita
di `AppCompatActivity`. Questo progetto usa `ComponentActivity` (Compose
puro, niente View system), quindi la prima versione chiamava sempre una
`recreate()` esplicita sull'Activity corrente subito dopo aver cambiato la
lingua (`ui/settings/AppLanguage.kt`, risolta da `Context` tramite
`ContextWrapper`).

**Bug scoperto in verifica manuale su device reale (API 33+)**: da Android
13 in su, `setApplicationLocales()` è un cambio di configurazione gestito
dal sistema operativo, che ricrea da sé le activity in primo piano — vale
per qualunque Activity, non solo `AppCompatActivity`. Chiamare comunque la
`recreate()` esplicita anche lì produceva due ricreazioni della stessa
Activity in corsa tra loro (quella innescata dal sistema e quella manuale),
con il risultato di una UI bloccata su schermo a tinta unita, non più
reattiva al tocco, riproducibile in modo consistente. Fix: la `recreate()`
esplicita ora è condizionata a `Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU`
— sotto API 33 resta necessaria (il ricalcolo automatico non è garantito per
un'Activity che non estende `AppCompatActivity`), da API 33 in su il sistema
se ne occupa già e non va duplicata.

Nota separata, non un bug: dopo un cambio lingua che innesca una vera
ricreazione dell'Activity (dal sistema su API 33+, o dalla `recreate()`
manuale sotto API 33), la schermata Impostazioni riparte da zero e il suo
`LaunchedEffect(Unit)` di caricamento backup si ripete — se Google Drive non
è configurato (`google_oauth_web_client_id` ancora al placeholder) questo
fa ricomparire il messaggio "Drive non configurato" come se fosse legato al
cambio lingua. Non lo è: è lo stesso comportamento già presente dalla Fase 4
ogni volta che la schermata Impostazioni viene ricomposta da zero.

### `ThemeMode` con nomi italiani, come `ReviewStatus`

`domain/model/ThemeMode.kt` usa `SISTEMA`/`CHIARO`/`SCURO` invece di
`SYSTEM`/`LIGHT`/`DARK`. Non era una scelta obbligata — è un concetto
tecnico generico, non lessico di dominio come lo stato di una recensione —
ma ho preferito restare coerente con il precedente già stabilito da
`ReviewStatus` (enum con nomi italiani, etichette localizzate separate)
piuttosto che introdurre due convenzioni diverse nello stesso codebase.

### Due istanze di `ThemeViewModel`, nessuno scope condiviso

`ThemeViewModel` viene creato via `hiltViewModel()` sia nella root
dell'app (`MainActivity.GameReviewerApp`, per applicare il tema) sia in
`SettingsScreen` (per mostrare/cambiare la selezione) — due istanze
`ViewModel` distinte, scope diverso (Activity vs backstack entry della
route Settings). Non ho introdotto un'istanza condivisa a livello di grafo
di navigazione: `ThemePreferences.themeMode` è un `Flow` letto da
`DataStore`, che resta la vera single source of truth, quindi le due
istanze convergono comunque sullo stesso stato senza bisogno di scope
condiviso — stesso principio già in uso per Room/`Flow` nel resto dell'app.

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
