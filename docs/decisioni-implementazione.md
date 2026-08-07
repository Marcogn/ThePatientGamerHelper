# Decisioni di implementazione

Questo file documenta le scelte tecniche prese durante l'implementazione
delle varie fasi che non erano già esplicitate in `docs/spec.md` o in
`CLAUDE.md`.

## Fase 8 (Import Markdown, export/import backlog, HowLongToBeat, viste griglia)

Vedi la sezione "Fase 8 — Import Markdown, export/import backlog,
HowLongToBeat, viste griglia" in `CLAUDE.md` per il riepilogo
architetturale completo. Qui solo le scelte che non erano ovvie dalla
richiesta originale.

### Import Markdown come reverse esatto dell'export, non un formato nuovo

La richiesta era "importazione delle recensioni in formato markdown".
Invece di inventare un formato di import proprio, `parseReviewMarkdown()`
(`domain/export/ReviewMarkdownParser.kt`) è l'esatto reverse di
`toRedditMarkdown()` — stesse etichette italiane fisse, stessa struttura a
bullet list, stesse sezioni `## Pro`/`## Contro` opzionali. Motivo: è
l'unico formato Markdown che l'app stessa produce, quindi è l'unico per cui
un roundtrip export→import è garantito senza ambiguità. Un parser Markdown
"generico" (compatibile con qualunque markdown scritto a mano) avrebbe
richiesto euristiche molto più permissive e casi limite non specificati
dalla richiesta. Il parser è severo sui campi che l'exporter scrive sempre
(titolo/voto/stato/data inizio — un file senza questi non è una recensione
scritta da questa app) e permissivo su tutto il resto (piattaforme/generi/
tag/ore/pro/contro/corpo), esattamente rispecchiando cosa `toRedditMarkdown`
omette quando vuoto. Errori di parsing restituiscono un messaggio puntuale
("Voto mancante o non valido", ecc.) mostrato nello snackbar della
libreria, non un errore generico.

### Export/import backlog: formato separato da `domain/backup`, sempre additivo

`domain/backup` (Fase 4) è il formato di roundtrip per il backup Drive
dell'intera libreria recensioni, con restore a sovrascrittura completa
(single-user, "un backup è un backup dell'intero stato"). L'export/import
backlog è concettualmente diverso: è un file che l'utente crea/apre
esplicitamente via SAF per condividere o unire il proprio backlog (es. tra
due device, o per mandarlo a qualcuno), non un ripristino di sicurezza — ha
quindi senso che sia **sempre additivo**: ogni lista importata diventa una
lista nuova, ogni item un item nuovo con id nuovo, mai una sovrascrittura.
Importare lo stesso file due volte duplica i dati (non è idempotente) — è
un compromesso accettato per restare semplice, coerente con l'approccio
"single-user, non over-engineerare" già seguito altrove: implementare un
merge per titolo/somiglianza avrebbe introdotto ambiguità (due giochi con
lo stesso nome su piattaforme diverse?) senza che la richiesta lo chiedesse
esplicitamente. `reviewId` viene scartato in import: la recensione collegata
appartiene alla libreria che ha esportato il file e potrebbe non esistere
su questo device. Il formato è comunque uno zip (dati + copertine), stesso
schema di `data/backup/BackupArchive.kt`, ma con le proprie DTO
(`domain/export/BacklogExportDto.kt`) e senza toccare `domain/backup` —
due formati, due evoluzioni indipendenti, come già `domain/export`
(Fase 2) e `domain/backup` (Fase 4) sono tenuti separati.

### HowLongToBeat: nessuna API pubblica, tecnica reverse-engineered verificata solo da ricerca

Come richiesto esplicitamente da CLAUDE.md ("verifica prima di assumere",
già applicato in Fase 6 per TheGamesDB), ho controllato online prima di
implementare: **HowLongToBeat non ha mai avuto un'API pubblica**, a
differenza di TheGamesDB (che almeno richiede una apikey ma resta un
endpoint documentato). Ogni libreria non ufficiale esistente (howlongtobeatpy,
ckatzorke/howlongtobeat, ecc.) funziona ri-derivando l'endpoint di ricerca
corrente dal bundle JavaScript del frontend di HowLongToBeat ad ogni
sessione, perché il path cambia ad ogni loro deploy — non esiste un
contratto stabile da implementare contro. `HowLongToBeatApiClient`
(`data/howlongtobeat/`) usa la stessa tecnica documentata (fetch della
homepage, estrazione del bundle `_app-*.js`, regex sull'endpoint POST, con
fallback al path storicamente stabile `/api/s/` se l'estrazione fallisce).
**Questo sandbox non ha accesso di rete a `howlongtobeat.com`** (stessa
limitazione nota già documentata per `dl.google.com`/`api.thegamesdb.net`,
confermata di nuovo in questa sessione — vedi sotto), quindi il client non
è stato eseguito contro il sito reale: è scritto e rivisto staticamente,
ma **va considerato non verificato finché non testato su un device reale**.
Ogni fallimento (bundle cambiato, endpoint bloccato, schema di risposta
diverso) è intercettato e trasformato in `null` da
`GameMetadataSearchCoordinator.searchHowLongToBeat()` — mai un'eccezione
propagata, mai un blocco del flusso "cerca online" esistente, coerente con
`downloadCoverLocally()` che già fa lo stesso per la copertina.

### Campi HowLongToBeat solo su `BacklogItem`, stesso precedente di `releaseYear`/`developer`

Stessa scelta già motivata in Fase 6 per anno/sviluppatore: sono metadati
di catalogazione, non parte del cuore di una recensione (voto/pro/contro/
testo), e aggiungerli a `Review` avrebbe richiesto toccare export
JSON/CSV/PDF/Markdown e i DTO di backup per campi che la richiesta lega
esplicitamente al backlog ("quando inserisco un gioco nel backlog"). La
ricerca online nel form recensione resta quindi invariata: non chiama
`searchHowLongToBeat()`, solo `BacklogItemFormViewModel` lo fa dopo che
l'utente ha scelto un risultato TheGamesDB (la query usa il titolo esatto
del risultato scelto, non il testo digitato, per la massima precisione).

### Viste lista/griglia: `SharedPreferences`, non `DataStore`; niente drag-to-reorder in griglia

Due soli flag persistiti (vista libreria, vista backlog) — stesso
principio minimale già applicato a `BackupPreferences`/
`TheGamesDbPreferences` in Fase 4/6, non il pattern `DataStore` usato per
`ThemeMode` (lì la richiesta esplicita era DataStore). La vista a griglia
in `BacklogListDetailScreen` **non supporta il drag-to-reorder manuale**
(Fase 6, Tappa 1): estendere il gesto di trascinamento verticale a una
griglia 2D avrebbe richiesto una logica di posizionamento sostanzialmente
diversa per un beneficio cosmetico — l'utente può tornare alla vista a
lista per riordinare. Il toggle è condiviso tra libreria e backlog
(`ui/common/ViewModeToggle.kt`, `ui/common/GameGridTile.kt`) per evitare di
duplicare la stessa UI due volte.

**Stato build**: come per le fasi precedenti, questa modifica è stata
scritta e rivista staticamente riga per riga (bilanciamento parentesi,
import, coerenza dei nomi di campo tra entità/DTO/mapper, parità 1:1 delle
chiavi `strings.xml` IT/EN) ma **non verificata su CI al momento di
scrivere questa nota**. In questa sessione ho anche verificato di persona
se il sandbox avesse accesso di rete più ampio del solito (alcuni host
Google rispondevano): `maven.google.com` risponde, ma il download reale
degli artefatti dell'Android Gradle Plugin viene comunque bloccato dal
proxy in uscita (redirect verso un host non in allowlist) — stessa
limitazione già documentata in CLAUDE.md, solo confermata con un test
diretto invece che assunta. Controlla lo stato dei check sulla PR prima di
considerarla verde, e verifica manualmente su device/emulatore sia il fix
di importazione Markdown sia — soprattutto — l'integrazione HowLongToBeat,
che è la parte con il rischio di fragilità più alto di questa modifica.

### Fix dopo verifica su device reale (vedi CLAUDE.md, stessa sezione, per il dettaglio completo)

Quattro problemi reali trovati testando l'app su device dopo il merge:
`FilterChip` "Abbandonato" spezzato carattere per carattere (fix: `FlowRow`
invece di `Row`), titoli delle top bar spezzati su due righe (fix:
`maxLines = 1` + ellissi ovunque, non solo dove segnalato), ricerca
TheGamesDB che falliva sempre con un errore JSON illeggibile quando un
gioco aveva `developers`/`genres` esplicitamente `null` nella risposta
(fix: campi resi nullable nel DTO + `coerceInputValues = true`), e
HowLongToBeat completamente assente perché il client implementava solo la
POST di ricerca senza gli header di autenticazione (`x-auth-token`/
`x-hp-key`/`x-hp-val`) che le librerie non ufficiali attualmente
mantenute richiedono — riscritto per implementare l'intero flusso
homepage→bundle→endpoint→init→ricerca, con logging diagnostico ad ogni
passo (prima falliva in silenzio assoluto, senza alcun modo di capire
perché). Resta il rischio, esplicitamente non escluso, che il sito sia
dietro protezioni anti-bot che nessun client `HttpURLConnection` può
superare — vedi CLAUDE.md per il dettaglio.

### Seconda verifica su device (vedi CLAUDE.md, stessa sezione, per il dettaglio completo)

HowLongToBeat era ancora assente dopo il primo fix, senza un modo per
l'utente di leggerne la causa: `searchHowLongToBeat()` ora restituisce un
esito tipizzato (trovato/nessuna corrispondenza/errore con messaggio)
mostrato direttamente nel form invece di solo loggato — diagnosticabile
senza `adb`. Il flusso "completa item → scrivi recensione" applicava stato
e prompt ad ogni singolo tap sul chip; ora lo stato è una selezione locale
non committata con un pulsante "Salva" esplicito, e il form di recensione
precompilato si apre già a "Completato" invece del default "In corso". Il
tasto indietro da quel form salva la bozza (se ha almeno un titolo) e
naviga verso Recensioni invece che tornare nel backlog. La vista a griglia
usa `LazyVerticalStaggeredGrid` invece di `LazyVerticalGrid`, e le
copertine non hanno più un `aspectRatio` forzato: proporzioni reali,
niente spazio sprecato tra cover quadrate e verticali.

### Terza verifica su device (vedi CLAUDE.md, stessa sezione, per il dettaglio completo)

La diagnostica del giro precedente ha funzionato: l'utente ha riportato
l'errore esatto, "HTTP 308", identico per ogni titolo. Causa reale:
`HttpURLConnection` non segue in modo affidabile i redirect sulle richieste
POST, e ha lacune note sul codice 308 in particolare. Fix:
`HowLongToBeatApiClient` ora segue i redirect manualmente (fino a 5 hop),
rilanciando la stessa richiesta (metodo, header, body) verso l'URL
risolto — comportamento richiesto da 307/308, sicuro anche per gli altri
codici 3xx in questo contesto.

### Quarta verifica su device (vedi CLAUDE.md, stessa sezione, per il dettaglio completo)

Due segnalazioni: le recensioni create dal flusso backlog si duplicavano ad
ogni nuovo tentativo (perché non c'era modo di riaprire una recensione già
collegata a un item, solo di crearne un'altra vuota — fix: "Recensione
collegata" ora è un link cliccabile che apre la recensione esistente), e
HowLongToBeat continua a dare "HTTP 308" nonostante il fix del redirect
manuale del giro precedente — non risolto, non è stato tentato un secondo
fix "alla cieca": invece i messaggi di errore ora includono l'URL che ha
fallito, per una diagnosi mirata al prossimo report.

## Fase 7 (Rebranding ThePatientGamerHelper, navigazione a drawer, fix ricerca TheGamesDB)

Vedi la sezione "Fase 7 — Rebranding, navigazione a drawer, fix ricerca
TheGamesDB" in `CLAUDE.md` per il riepilogo architetturale completo. Qui
solo le scelte che non erano ovvie dalla richiesta originale.

### Rinominare anche `applicationId`/package, non solo il nome visualizzato

La richiesta era "cambia il nome dell'app ovunque", che di per sé avrebbe
potuto voler dire solo la stringa `app_name` mostrata in UI. Ho chiesto
esplicitamente all'utente se il cambio dovesse estendersi anche a
`applicationId`/package Kotlin (`com.marcogn.gamereviewer` →
`com.marcogn.thepatientgamerhelper`), spiegando le due conseguenze concrete
prima di procedere:
- Chi ha già installato l'app la perde come "app diversa": Android
  considera l'`applicationId` l'identità dell'app, un `applicationId`
  diverso non è un aggiornamento ma una nuova installazione — nessuna
  migrazione automatica dei dati locali (database Room, immagini
  copertina).
- Il client OAuth Drive configurato in Google Cloud Console (Fase 4) è
  registrato per la coppia `applicationId`+SHA1 del certificato di firma:
  un nuovo `applicationId` richiede una nuova registrazione, quella
  esistente (ancora al placeholder `[DA_COMPLETARE]` al momento di questa
  modifica) non è comunque interessata da questo cambio a runtime, ma un
  domani che venga configurata andrà rifatta per il nuovo `applicationId`.

Risposta ricevuta: rinominare anche `applicationId`/package. Eseguito come
spostamento meccanico di directory (`git mv`) + sostituzione testuale
(`sed`) di `com.marcogn.gamereviewer`→`com.marcogn.thepatientgamerhelper` e
`GameReviewer`→`ThePatientGamerHelper` su tutti i file `.kt`/`.xml`/build
script/documentazione, seguito da verifiche statiche multiple (bilanciamento
parentesi, corrispondenza package/percorso directory, validità XML, parità
chiavi stringhe IT/EN) — lo stesso sandbox di questa sessione non ha accesso
a `dl.google.com` quindi non è stato possibile compilare per verificare.

### Cosa è rimasto deliberatamente non rinominato

- **Il nome del repository GitHub** (`Marcogn/GameReviewer`): non richiesto
  esplicitamente, e rinominare un repository ha un raggio d'azione che va
  oltre il codice (link esistenti, integrazioni CI, fork) — fuori scope per
  una richiesta che parlava di "nome dell'app", non del repository che la
  ospita.
- **Il prefisso `recensioni-videogiochi-` in `domain/export/ExportFileNaming.kt`**
  (nome dei file esportati da JSON/CSV/PDF): descrive il *contenuto* del
  file esportato ("recensioni videogiochi"), non deriva dal nome
  dell'applicazione — resta coerente con la scelta di Fase 5 di lasciare le
  etichette di `domain/export` fisse in italiano indipendentemente
  dall'app.
- **Il nome utente HTTP nello `User-Agent` di `TheGamesDbApiClient`**: la
  sostituzione testuale in blocco (`GameReviewer`→`ThePatientGamerHelper`)
  avrebbe corrotto anche l'URL del repository GitHub incluso lì
  (`github.com/Marcogn/GameReviewer`, non rinominato — vedi punto sopra),
  trasformandolo in un URL che non esiste. Individuato prima di eseguire il
  `sed` e ripristinato manualmente al valore corretto dopo.

### Nome del database Room non rinominabile via `sed`

`DATABASE_NAME` in `ThePatientGamerHelperDatabase.kt` era `"game_reviewer.db"`
(snake_case), non intercettato dai pattern `sed` usati per
`com.marcogn.gamereviewer`/`GameReviewer` (case diverso). Rinominato a mano
in `"the_patient_gamer_helper.db"`. **Nota**: questo, combinato col cambio
di `applicationId`, significa che un'installazione esistente (con
`applicationId` vecchio) non viene comunque toccata da questo rename — è
letteralmente un'app diversa agli occhi di Android, quindi non esiste un
percorso di migrazione file system da gestire qui.

### Navigazione: cassetto laterale (hamburger) invece di icone in top bar

La richiesta descriveva esplicitamente il meccanismo voluto ("menù laterale
sx richiamabile da hamburger in alto a sx"), quindi non è stata una scelta
tra alternative ma un'implementazione diretta:
`ModalNavigationDrawer` (Material 3 Compose) attorno a tutto il `NavHost`,
con lo stato del drawer (`rememberDrawerState`) sollevato al livello del
grafo di navigazione — ogni schermata riceve solo una lambda `onMenuClick`
che apre il drawer, non lo stato del drawer stesso. Le voci del drawer
(Recensioni/Backlog/Statistiche + separatore + Impostazioni) navigano con
`popUpTo(Destination.Home) { saveState = true }` +
`launchSingleTop = true` + `restoreState = true`, il pattern standard
raccomandato da Google per navigazione stile drawer/bottom-bar (evita di
accumulare un backstack profondo quando si passa ripetutamente tra le
stesse 3-4 destinazioni principali).

`Destination.Settings` è rimasta raggiungibile solo dal drawer, con una
freccia "indietro" (non l'hamburger) nella sua stessa top bar — è una
destinazione "in fondo", non una delle tre sezioni principali tra cui si
salta liberamente, coerente con come l'utente l'ha descritta ("con in fondo
le impostazioni").

### Nuova schermata Home come selettore, non redirect automatico

La richiesta chiedeva una schermata "cosa vuoi fare?" con 3 scelte, distinta
dalla libreria che prima era la schermata iniziale. Ho aggiunto
`Destination.Home` come nuova `startDestination` del grafo di navigazione
(la libreria/`Destination.Library` non è più la prima schermata mostrata
all'apertura dell'app) invece di, per esempio, ricordare l'ultima sezione
visitata e riaprirla direttamente: l'utente ha chiesto esplicitamente un
punto di ingresso "cosa vuoi fare?", che perderebbe senso se l'app saltasse
automaticamente altrove. Nessuno stato persistito per "ultima sezione
usata" — coerente con "non introdurre funzionalità/stato non richiesti"
già seguito nelle fasi precedenti.

### Fix ricerca TheGamesDB: la causa visibile era un messaggio generico, non necessariamente l'unico bug

Il sintomo riportato ("la ricerca è sempre 'non riuscita', non si capisce
perché") ha una causa certa e diagnosticabile staticamente:
`GameMetadataSearchCoordinator.search()` catturava qualunque eccezione
(errore di rete, HTTP non-2xx, parsing JSON) e la sostituiva sempre con lo
stesso testo generico (`R.string.game_search_failed`), scartando il
messaggio reale dell'eccezione. Corretto loggando l'eccezione completa
(`Log.w`, visibile in Logcat) e **accodando** il messaggio dell'eccezione
(quando presente) al testo generico mostrato nel dialog, invece di
sostituirlo — così un futuro fallimento mostra sia il messaggio
rassicurante generico sia il dettaglio tecnico utile per diagnosticare
(es. "HTTP 401: ..." per una chiave non valida).

Questo sandbox non ha accesso di rete a `api.thegamesdb.net` (bloccato
esplicitamente dalla policy del proxy in uscita, verificato con
`/__agentproxy/status` prima di escludere un problema temporaneo), quindi
non è stato possibile riprodurre il fallimento originale né verificare in
modo definitivo quale fosse la causa sottostante specifica. Ho comunque
applicato correzioni difensive plausibili basate su ricerca (non su
riproduzione diretta) mentre sistemavo lo swallow dell'eccezione:
- Header `Accept: application/json` e timeout espliciti
  (connect/read) mancanti sulla connessione — assenti prima, alcuni
  endpoint REST rispondono con un content-type inatteso o restano appesi
  indefinitamente senza un timeout esplicito.
- Il campo `"platform"` nella lista `fields` richiesti a `Games/ByGameName`
  non risulta un campo valido per quell'endpoint secondo la documentazione
  TheGamesDB consultata — rimosso dalla lista.
- Il filtro per piattaforma nella query (`filter[platform]`) usa la sintassi
  di array indicizzato tipica di API PHP/Laravel (`filter[platform][0]=`),
  non la forma senza indice usata in precedenza — TheGamesDB è
  implementata in Laravel.

**Il fix che risolve con certezza il sintomo riportato** è il primo (lo
swallow del messaggio): anche se le correzioni difensive sopra si
rivelassero non centrare la causa reale, il prossimo fallimento mostrerà
ora un messaggio diagnosticabile invece dello stesso testo opaco, rendendo
possibile una diagnosi ulteriore senza bisogno di accesso diretto all'API
da parte di chi scrive il codice.

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

### Cambio lingua rotto in due modi diversi prima del fix vero: serve `AppCompatActivity`

Il cambio lingua è stato verificato manualmente su device reale dopo il
merge della Fase 5, e ha richiesto due iterazioni sbagliate prima di
arrivare alla causa reale — vale la pena documentarle entrambe, non solo la
soluzione finale.

**Tentativo 1**: `MainActivity` era una `ComponentActivity` pura (Compose,
niente View system). Poiché la ricreazione automatica delle Activity che
AppCompat offre in modo affidabile è legata al ciclo di vita di
`AppCompatActivity`, `applyAppLanguage()` chiamava sempre una `recreate()`
esplicita sull'Activity corrente subito dopo `setApplicationLocales()`
(risolta da `Context` tramite `ContextWrapper`). Risultato su device reale
(API 33+): selezionando una lingua compariva l'errore "Drive non
configurato" (vedi nota sotto) e, tornando indietro e riaprendo l'app, la UI
restava bloccata su schermo a tinta unita, non più reattiva al tocco.

**Tentativo 2**: ipotesi (sbagliata) che il blocco fosse dovuto a due
`recreate()` in corsa — quella del sistema operativo (che da Android 13 in
su gestisce il cambio lingua come un vero cambio di configurazione e ricrea
da sé le activity in primo piano) più quella manuale. Fix applicato:
condizionare la `recreate()` esplicita a `Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU`.
Risultato su device reale: il blocco spariva, ma il cambio lingua smetteva
di funzionare del tutto — nessun errore, nessuna reazione, la UI restava
sempre in italiano. Il sintomo per esclusione ha smentito l'ipotesi della
doppia `recreate()`.

**Causa reale**: la documentazione ufficiale Android è esplicita — *"If
you're using Compose with setApplicationLocales, you must extend your
activity from AppCompatActivity. Otherwise, setting the app locale won't
work."* `ComponentActivity` non ha semplicemente un supporto "meno
affidabile" per il cambio lingua sotto Compose: **non funziona per niente**,
perché manca l'aggancio che porta la nuova configurazione (la locale) fino
al meccanismo di ricomposizione di Compose. Chiamare `recreate()` a mano su
una `ComponentActivity` in questo stato non risolve il problema alla radice
— ricrea l'Activity, ma senza che la configurazione risolta rifletta la
nuova lingua, lasciando l'app in uno stato incoerente (da cui il blocco
visto nel Tentativo 1).

**Fix definitivo**: `MainActivity` estende ora `AppCompatActivity` (non
`ComponentActivity`) — resta comunque Compose puro, `setContent {}` è
l'unico entry point della UI, nessun layout XML introdotto.
`AppCompatActivity` richiede un tema Android che discenda da
`Theme.AppCompat` (altrimenti lancia un'eccezione a runtime): il tema in
`res/values/themes.xml`, prima `android:Theme.Material.Light.NoActionBar`,
è diventato `Theme.AppCompat.DayNight.NoActionBar`. Con questa base class,
`setApplicationLocales()` innesca da sola la ricreazione corretta
dell'Activity, sia su API 33+ che sotto — `applyAppLanguage()` in
`ui/settings/AppLanguage.kt` si limita a chiamare `setApplicationLocales()`,
nessuna `recreate()` manuale, nessuna condizione sull'API level.

Nota (superata dalla Fase 7): al momento della diagnosi, un cambio lingua
che innescava una vera ricreazione dell'Activity faceva ripartire da zero
anche il caricamento automatico dei backup nella schermata Impostazioni,
riportando in vista il messaggio "Drive non configurato" — non un effetto
del cambio lingua in sé, solo dello stesso `LaunchedEffect(Unit)` già
presente dalla Fase 4 ogni volta che la schermata veniva ricomposta.
Il rework della UI di backup nella Fase 7 (login Google esplicito, nessun
caricamento automatico prima del sign-in) ha eliminato questo effetto
collaterale.

### `ThemeMode` con nomi italiani, come `ReviewStatus`

`domain/model/ThemeMode.kt` usa `SISTEMA`/`CHIARO`/`SCURO` invece di
`SYSTEM`/`LIGHT`/`DARK`. Non era una scelta obbligata — è un concetto
tecnico generico, non lessico di dominio come lo stato di una recensione —
ma ho preferito restare coerente con il precedente già stabilito da
`ReviewStatus` (enum con nomi italiani, etichette localizzate separate)
piuttosto che introdurre due convenzioni diverse nello stesso codebase.

### Due istanze di `ThemeViewModel`, nessuno scope condiviso

`ThemeViewModel` viene creato via `hiltViewModel()` sia nella root
dell'app (`MainActivity.ThePatientGamerHelperApp`, per applicare il tema) sia in
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
