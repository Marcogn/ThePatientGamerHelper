# Piano di test — interazione uomo/applicazione

Piano di test manuale (black-box, dal punto di vista di chi usa l'app) per
**tutte** le funzionalità di ThePatientGamerHelper, casi principali ("happy
path") ed edge case. Complementare a `docs/spec.md` (specifica funzionale) e
a `CLAUDE.md` (stato di avanzamento, decisioni tecniche, bug reali già
trovati e corretti — sezione "Fix dopo verifica su device reale" e successive
in fondo a `CLAUDE.md`): quei bug sono stati aggiunti qui come casi di
**regressione** da non saltare mai (sezione 10).

Questo file va **tenuto aggiornato**: ogni nuova fase/funzionalità aggiunta
all'app deve ricevere qui la propria sezione con casi happy path + edge
case, ogni bug reale scoperto in verifica manuale va aggiunto alla sezione
10 come regressione permanente. Non è un documento "one-shot".

## Come usarlo

- Ogni caso è una checkbox `- [ ]`. Segna `- [x]` quando verificato **in
  questa release/round di test**, con la data e l'esito accanto (es.
  `- [x] 2026-08-07 OK` oppure `- [x] 2026-08-07 FALLITO — vedi issue #NN`).
  Non lasciare mai una checkbox spuntata senza indicazione di quando/come è
  stata verificata: altrimenti alla release successiva non si sa se è
  ancora valida.
- Gli ID (`LIB-01`, `FORM-12`, ecc.) sono stabili: se un caso viene rimosso
  perché la funzionalità cambia, non riassegnare il suo ID a un caso nuovo
  — aggiungine uno nuovo, per non confondere la cronologia di run precedenti
  salvata altrove (issue, fogli di calcolo, ecc.).
- Dove rilevante viene citato il testo esatto (in italiano) che l'app deve
  mostrare, preso da `values/strings.xml` — se il testo osservato diverge,
  è un bug (anche solo di string mismatch) da segnalare.
- "Edge case" indica input/percorsi anomali ma raggiungibili da un utente
  reale (non richiede root/debug/manomissione dell'APK).

## Prerequisiti ambiente di test

- Device o emulatore Android con **Google Play Services** (richiesto per il
  login Google Drive, l'`AuthorizationClient`, e — indirettamente — per il
  Credential Manager). API level minimo supportato: 26 (`minSdk`); testare
  idealmente su almeno due API level, uno vicino al minimo e uno recente.
- Connessione dati reale per: backup/ripristino Drive, ricerca TheGamesDB,
  stima HowLongToBeat. Un profilo di rete "assente"/"lenta" (airplane mode,
  throttling) per i casi di sezione 8.4.
- Un account Google reale per i test di backup Drive (sezione 7.3) — **non
  eseguire i test di ripristino su un account con dati Drive reali che non
  siano di test**: il ripristino sovrascrive tutte le recensioni locali in
  modo irreversibile (per design, vedi `CLAUDE.md` Fase 4).
- Una API key TheGamesDB valida (registrazione gratuita su thegamesdb.net)
  per i test di sezione 3.5/5.6/7.4.
- Libreria/backlog vuoti per i test di stato "vuoto", e una libreria/backlog
  con dati sufficienti (almeno 15-20 recensioni/item, più piattaforme/generi
  diversi) per i test di ricerca/filtro/ordinamento/statistiche/viste a
  griglia con contenuti reali.
- Almeno un file `.md` esportato dall'app stessa (per i test di roundtrip
  import) e almeno un file `.md` scritto a mano/malformato (per i test di
  import fallito).

---

## 1. Home e navigazione a drawer

- [ ] **HOME-01** All'avvio (prima apertura, nessun dato) l'app mostra la
      schermata Home con hamburger, sottotitolo "Cosa vuoi fare?" e tre
      card: Recensioni, Backlog, Statistiche.
- [ ] **HOME-02** Tap su ciascuna delle tre card naviga alla rispettiva
      schermata (Libreria / Backlog / Statistiche).
- [ ] **HOME-03** Tap sull'icona hamburger apre il drawer laterale con le
      voci Recensioni, Backlog, Statistiche, separatore, Impostazioni.
- [ ] **HOME-04** Da una qualunque delle tre sezioni, il drawer permette di
      passare direttamente a un'altra senza dover tornare a Home.
- [ ] **HOME-05** Navigare ripetutamente tra le stesse 2-3 sezioni dal
      drawer non accumula backstack: il tasto back di sistema da una
      sezione raggiunta dal drawer non deve rimbalzare tra tutte le
      schermate visitate in precedenza.
- [ ] **HOME-06** Tasto back di sistema da Home chiude l'app (Home è la
      `startDestination`, non c'è una schermata "sotto").
- [ ] **HOME-07 (edge)** Tap ripetuto e rapido (double/triple tap) su una
      card Home non apre la stessa schermata due volte in backstack.
- [ ] **HOME-08 (edge)** Apertura del drawer, poi tap fuori dall'area del
      drawer (scrim) lo richiude senza navigare.
- [ ] **HOME-09** Impostazioni **non** è raggiungibile dalle tre card Home,
      solo dal drawer (in fondo, dopo il separatore) — verificare che non
      compaia anche come quarta card.
- [ ] **HOME-10 (edge, limite noto)** Nessuna voce del drawer risulta mai
      evidenziata come "sezione corrente" (`selected` è hardcoded `false`
      su tutte le voci in `ThePatientGamerHelperNavGraph.kt`) — non è un
      crash né un blocco funzionale, ma è un gap di usabilità reale:
      aprendo il drawer mentre si è già, es., in Backlog, nessuna voce
      appare selezionata. Confermare che sia ancora così e valutare se
      segnalarlo come miglioramento (non richiede una fix immediata, ma
      va tracciato per non perderlo).

---

## 2. Libreria recensioni

### 2.1 Stato vuoto e navigazione base

- [ ] **LIB-01** Libreria vuota: mostra "Nessuna recensione ancora" /
      "Tocca + per aggiungere la prima recensione", nessuna lista/celle
      vuote, FAB "+" visibile.
- [ ] **LIB-02** Top bar libreria: titolo "Recensioni" (mai il vecchio nome
      app), hamburger a sinistra (non freccia indietro), icone azione a
      destra (filtri, ordina, vista, export, import) — verificare che il
      titolo non vada mai su due righe anche con più icone attive
      contemporaneamente (regressione nota, vedi sezione 10).
- [ ] **LIB-03** Tap su una recensione in elenco apre il dettaglio di
      quella recensione (titolo/copertina coerenti con la riga toccata).

### 2.2 Ricerca

- [ ] **LIB-04** Digitare nel campo ricerca (placeholder "Cerca titolo,
      testo, pro/contro…") filtra live la lista mentre si digita.
- [ ] **LIB-05** Ricerca case-insensitive e su match parziale (substring),
      su titolo.
- [ ] **LIB-06** Ricerca match anche nel corpo della recensione, nei
      singoli Pro/Contro e nei tag — **confermato dal codice**: il motore
      di ricerca confronta titolo, testo, pro, contro, tag. **Non**
      cerca invece su piattaforma/genere (quelli sono solo filtrabili,
      mai cercabili per testo) — verificare esplicitamente che digitare
      il nome di una piattaforma/genere nel campo ricerca **non** produca
      match sul solo nome piattaforma/genere se non compare anche altrove
      nella recensione.
- [ ] **LIB-07** Ricerca senza risultati mostra "Nessun risultato con i
      filtri attuali" + pulsante "Reimposta filtri", non lo stato vuoto
      generico della libreria.
- [ ] **LIB-08 (edge)** Ricerca con **solo spazi** non fa collassare la
      lista come se cercasse la stringa " " letterale — verificare il
      comportamento effettivo (probabile nessun match, comportamento
      atteso ma da confermare, non un crash).
- [ ] **LIB-09 (edge)** Ricerca con caratteri speciali/regex-like (`.*`,
      `%`, `'`, `"`, `\`, emoji 🎮) non causa crash né risultati anomali.
- [ ] **LIB-10 (edge)** Cancellare tutto il testo di ricerca dopo una
      ricerca con filtri attivi torna a mostrare tutti i risultati che
      passano ancora i filtri (non tutta la libreria se i filtri erano
      attivi).
- [ ] **LIB-11 (edge)** Ricerca con stringa molto lunga (500+ caratteri
      incollati) non blocca la UI né crasha.

### 2.3 Filtri

- [ ] **LIB-12** Apertura pannello filtri: sezioni Stato, Piattaforma,
      Genere, Tag (chip multi-selezione), range voto (slider doppio
      0-10), intervallo date (Da/A).
- [ ] **LIB-13** Selezionare più chip nella stessa sezione (es. due
      piattaforme) applica un OR tra loro; selezionare chip in sezioni
      diverse applica un AND tra sezioni — verificare il comportamento
      reale contro questa aspettativa.
- [ ] **LIB-14** I filtri si applicano **in tempo reale** man mano che si
      selezionano i chip/si muovono gli slider (non serve toccare
      "Applica" per vederli riflessi nella lista sotto, se visibile);
      "Applica" si limita a **chiudere** il pannello. "Reimposta" azzera
      tutte le selezioni **tranne** la query di ricerca testuale
      (confermato da codice: `onClearFilters` non tocca la ricerca).
- [ ] **LIB-15** Il range voto mostra "Voto: X.X – Y.Y" aggiornato in
      tempo reale muovendo lo slider.
- [ ] **LIB-16 (edge)** Range voto impostato a un intervallo che non
      contiene nessuna recensione esistente (es. 9.8–10.0 se nessuna
      recensione ha quel voto) → "Nessun risultato con i filtri attuali".
- [ ] **LIB-17 (edge)** Intervallo date con "Da" successivo ad "A" (data
      di inizio filtro dopo la data di fine filtro): verificare che non
      crashi e capire cosa succede (nessun risultato è un esito
      accettabile, ma va verificato che non sia un crash o un filtro
      ignorato silenziosamente).
- [ ] **LIB-18** Filtri attivi persistono se si naviga al dettaglio di una
      recensione e si torna indietro (non si resettano da soli).
- [ ] **LIB-19 (edge)** Filtri attivi + libreria che nel frattempo perde
      l'unica recensione che li soddisfaceva (es. cancellata da un altro
      punto, o modificata togliendole la piattaforma filtrata): la lista
      si aggiorna a "nessun risultato" senza dover riaprire la schermata
      (Room è single source of truth via `Flow`).
- [ ] **LIB-20 (edge)** Filtro per piattaforma/genere/tag che nel
      frattempo diventa "orfano" (nessuna recensione lo referenzia più,
      es. dopo aver rimosso quell'unico tag dall'unica recensione che lo
      usava): il chip resta ancora nella lista filtri finché non si
      riapre il pannello? Verificare che non crashi.

### 2.4 Ordinamento

- [ ] **LIB-21** Menu ordina: Data, Voto, Titolo, Ore di gioco; tap ripetuto
      sullo stesso campo alterna crescente/decrescente (icona freccia
      su/giù che riflette lo stato corrente).
- [ ] **LIB-22 (edge)** Ordinamento per "Ore di gioco" con recensioni che
      hanno il campo vuoto (`null`): verificare dove finiscono (in cima,
      in fondo, trattate come 0 — deve essere coerente e non causare un
      ordine instabile/casuale a ogni refresh).
- [ ] **LIB-23 (edge)** Ordinamento per Titolo con titoli che iniziano per
      minuscolo/maiuscolo/numero/emoji/carattere accentato: verificare che
      l'ordinamento sia coerente (case-insensitive atteso) e stabile.
- [ ] **LIB-24** L'ordinamento scelto persiste tra sessioni (o almeno
      durante la sessione corrente) e si combina correttamente con
      ricerca e filtri attivi contemporaneamente.

### 2.5 Vista lista/griglia

- [ ] **LIB-25** Toggle vista lista/griglia (`ViewModeToggle`) cambia
      immediatamente il layout della libreria.
- [ ] **LIB-26** La preferenza vista scelta persiste tra riavvii dell'app
      (`ViewModePreferences`).
- [ ] **LIB-27** Vista a griglia: `LazyVerticalStaggeredGrid` — copertine
      con proporzioni diverse (quadrata, verticale, molto larga se
      importata da fonte esterna) stanno affiancate senza righe forzate a
      altezza uniforme e senza spazi vuoti anomali.
- [ ] **LIB-28 (edge)** Vista a griglia con recensioni **senza copertina**
      mescolate a recensioni con copertina: il placeholder resta a
      proporzione fissa 2:3 e non rompe il layout staggered.
- [ ] **LIB-29 (edge)** Cambio orientamento (verticale/orizzontale) in
      vista griglia ricalcola correttamente il numero di colonne
      (`Adaptive(minSize = 120.dp)`) senza artefatti grafici.
- [ ] **LIB-30** Tap su una tile in griglia apre il dettaglio corretto
      (stesso comportamento della vista a lista).

### 2.6 Export libreria (JSON/CSV/PDF)

- [ ] **LIB-31** Export JSON: sempre l'**intera libreria**, anche con
      filtri/ricerca attivi in UI (non filtrato) — verificare aprendo il
      file esportato con filtri attivi che nascondono la maggior parte
      delle recensioni.
- [ ] **LIB-32** Export CSV: stesso comportamento "sempre tutto"; aprire il
      CSV e verificare encoding/separatori corretti con titoli contenenti
      virgole, virgolette, a-capo.
- [ ] **LIB-33** Export PDF: singolo file multi-pagina con tutte le
      recensioni, ogni recensione inizia su una nuova pagina.
- [ ] **LIB-34** Ogni export usa Storage Access Framework
      (`ActivityResultContracts.CreateDocument`): l'utente sceglie
      cartella/nome file, mai una scrittura diretta silenziosa.
- [ ] **LIB-35** Dopo export riuscito compare "Esportazione completata"
      (snackbar/messaggio).
- [ ] **LIB-36 (edge)** Annullare il picker SAF (tasto back o "Annulla"
      nel file picker di sistema) durante un export: nessun crash, nessun
      messaggio di errore fuorviante, l'app resta nello stato precedente.
- [ ] **LIB-37 (edge)** Export con libreria **vuota**: verificare cosa
      succede (file vuoto/con solo intestazioni per CSV, JSON con array
      vuoto, PDF — un PDF con zero recensioni ha senso? verificare che non
      crashi).
- [ ] **LIB-38 (edge)** Export PDF/CSV/JSON con recensioni che contengono
      caratteri Unicode estesi (emoji nel titolo, CJK, RTL come arabo) —
      verificare che non corrompano il file o mandino in crash
      `PdfDocument`/`StaticLayout`.
- [ ] **LIB-39 (edge)** Export PDF con una recensione dal testo
      estremamente lungo (migliaia di caratteri): verifica corretta
      impaginazione su più pagine senza troncare o sovrapporre testo.
- [ ] **LIB-40 (edge)** Negare/revocare i permessi di storage (se
      applicabile su quella versione Android) prima di un export: errore
      gestito, non crash.
- [ ] **LIB-41 (edge)** Spazio di archiviazione del device pieno durante un
      export: fallimento gestito con messaggio "Esportazione non
      riuscita: ...", non un crash silenzioso o un file troncato senza
      avviso.

### 2.7 Import Markdown

- [ ] **LIB-42** Icona import Markdown apre il picker SAF
      (`ActivityResultContracts.OpenDocument`) filtrato/adatto a file
      `.md`/testo.
- [ ] **LIB-43** Import di un file `.md` esportato dall'app stessa (Fase 2)
      crea una **nuova** recensione (mai un update di una esistente),
      anche se il titolo coincide esattamente con una già presente
      (duplicati attesi, non un merge).
- [ ] **LIB-44** Dopo import riuscito: snackbar "Importazione completata",
      la nuova recensione compare in lista senza dover ricaricare la
      schermata.
- [ ] **LIB-45 (edge)** Import di un file `.md` **mancante di titolo**
      (nessuna riga `# Titolo`): fallisce con **"Manca il titolo (riga
      \"# Titolo\")"**; con riga `#` presente ma vuota: **"Il titolo non
      può essere vuoto"**.
- [ ] **LIB-46 (edge)** Import di un file `.md` con voto mancante o non
      numerico: fallisce con **"Voto mancante o non valido (atteso \"-
      **Voto:** X.X/10\")"**.
- [ ] **LIB-47 (edge)** Import di un file `.md` con stato mancante/non
      riconosciuto: **"Stato mancante o non riconosciuto (atteso \"-
      **Stato:** In corso/Completato/Abbandonato\")"**. Con data di
      inizio mancante: **"Data di inizio mancante (atteso \"- **Iniziato
      il:** gg/mm/aaaa\")"**; con data presente ma in formato non valido:
      **"Data non valida: \"{valore}\" (atteso gg/mm/aaaa)"**.
- [ ] **LIB-48 (edge)** Import di un file completamente estraneo (es. un
      `.md` di README scaricato da internet, non prodotto dall'app):
      fallisce con messaggio d'errore leggibile, nessun crash, nessuna
      recensione "spazzatura" creata parzialmente.
- [ ] **LIB-49 (edge)** Import di un file non-Markdown (es. rinominare un
      `.jpg` in `.md`, o un file binario): gestito come fallimento, non
      crash. Se il file/stream non è nemmeno leggibile (es. permesso
      negato dal content provider), il messaggio atteso è "Impossibile
      leggere il file selezionato" (`ImportFileReader`).
- [ ] **LIB-50 (edge)** Import con Pro/Contro/piattaforme/generi/tag
      omessi (sezioni facoltative assenti nel file): importazione
      riuscita comunque, campi opzionali vuoti nella recensione creata.
- [ ] **LIB-51 (edge)** Import di un file `.md` con sezioni Pro/Contro
      presenti ma vuote (solo l'intestazione `## Pro` senza bullet sotto):
      verificare che non produca un elemento pro/contro fantasma vuoto.
- [ ] **LIB-52 (edge)** Roundtrip completo: esporta una recensione con
      *tutti* i campi opzionali valorizzati (piattaforme multiple, generi
      multipli, tag multipli, ore, pro, contro, corpo con markdown
      annidato tipo elenco puntato dentro al corpo) → importa il file
      appena esportato → confrontare che i dati coincidano 1:1 con
      l'originale (a parte l'id, nuovo per design).
- [ ] **LIB-53 (edge)** Annullare il picker SAF durante l'import: nessun
      crash, nessun messaggio d'errore fuorviante.
- [ ] **LIB-54 (edge)** Import di un file `.md` molto grande (es. decine
      di migliaia di righe nel corpo): non blocca la UI a tempo
      indefinito né crasha per OOM su device di fascia bassa.

---

## 3. Form recensione (creazione/modifica)

### 3.1 Campi principali e validazione

- [ ] **FORM-01** Apertura form in creazione (FAB "+" da libreria): tutti i
      campi vuoti/default, titolo schermata "Nuova recensione".
- [ ] **FORM-02** Apertura form in modifica (da dettaglio → Modifica):
      tutti i campi precompilati con i valori correnti della recensione,
      titolo "Modifica recensione".
- [ ] **FORM-03** Salvataggio con **titolo vuoto**: bloccato, messaggio
      "Il titolo è obbligatorio".
- [ ] **FORM-04 (edge)** Salvataggio con titolo **solo spazi** (es. "   "):
      verificare se `isBlank()` lo intercetta come vuoto (atteso: sì,
      bloccato con lo stesso messaggio).
- [ ] **FORM-05** Slider voto: range 0.0–10.0, step 0.1 (99 step interni),
      non è possibile impostare un voto fuori range o con più di una
      cifra decimale dall'interfaccia stessa — la validazione
      "Il voto deve essere tra 0 e 10" è quindi difficile da raggiungere
      dalla sola UI: annotare se risulta comunque raggiungibile (es. da
      uno stato precompilato anomalo) o se è codice morto lato UI ma utile
      a difesa.
- [ ] **FORM-06** L'etichetta sopra lo slider ("Voto: X.X") si aggiorna in
      tempo reale muovendo il dito, con un solo decimale sempre mostrato
      (es. "7.0", non "7").
- [ ] **FORM-07** Selettore Stato: tre chip (In corso/Completato/
      Abbandonato) in `FlowRow` — con schermo stretto o testo scalato
      (accessibilità) i chip vanno a capo su una nuova riga, non si
      accavallano né spezzano il testo verticalmente (regressione nota,
      sezione 10).
- [ ] **FORM-08** Date inizio/fine: date picker; data fine è opzionale e
      **removibile** (pulsante "Rimuovi" nel picker) mentre data inizio no
      (`clearable = false`).
- [ ] **FORM-09** Data fine antecedente alla data inizio → bloccato al
      salvataggio, "La data di fine non può precedere quella di inizio".
- [ ] **FORM-10 (edge)** Data fine **uguale** alla data inizio (stesso
      giorno): deve essere accettata (non è "precedente").
- [ ] **FORM-11 (edge)** Impostare prima la data fine e poi una data
      inizio successiva ad essa (ordine di inserimento invertito):
      verificare che la validazione scatti comunque al salvataggio, non
      solo se le date vengono toccate in un ordine specifico.
- [ ] **FORM-12** Campo "Ore di gioco": tastiera numerica decimale, accetta
      sia `.` che `,` come separatore decimale (sostituzione automatica
      virgola→punto).
- [ ] **FORM-13 (edge)** Campo ore con testo non numerico (incollato, es.
      "abc"): `toDoubleOrNull()` restituisce null silenziosamente, il
      campo torna vuoto/non aggiorna il draft — nessun messaggio
      d'errore mostrato. Verificare che l'esperienza sia comunque
      sensata (non un crash, non un valore fantasma salvato).
- [ ] **FORM-14 (edge)** Campo ore con **valore negativo** (es. "-5"):
      **nessuna validazione lo blocca** nel codice attuale — verificare
      che sia effettivamente possibile salvare una recensione con ore di
      gioco negative e valutare se è un comportamento accettabile o un
      bug da segnalare (probabile bug reale: nessun controllo `>= 0`).
- [ ] **FORM-15 (edge)** Campo ore con un numero enorme (es.
      "999999999999"): non deve causare overflow visivo o crash
      nell'export/statistiche che lo sommano.
- [ ] **FORM-16 (edge)** Campo ore con molti decimali digitati (es.
      "12.3456789"): verificare come viene visualizzato/arrotondato al
      successivo caricamento del form (`formatHours`).
- [ ] **FORM-17** Corpo recensione (campo markdown multilinea, min 6
      righe): accetta testo libero multilinea, nessun limite di
      lunghezza visibile testato fino ad almeno alcune migliaia di
      caratteri.

### 3.2 Piattaforme / Generi / Tag (chip input con autocomplete)

- [ ] **FORM-18** Digitare in uno dei tre campi mostra suggerimenti
      autocomplete dai valori già esistenti in libreria.
- [ ] **FORM-19** Selezionare un suggerimento o premere invio/virgola
      aggiunge un chip; il chip è rimovibile con la "x"/icona dedicata.
- [ ] **FORM-20 (edge)** Aggiungere lo stesso valore due volte con
      maiuscole/minuscole diverse (es. "PC" poi "pc"): il confronto è
      case-insensitive (`equals(ignoreCase = true)`), il secondo non
      duplica il chip.
- [ ] **FORM-21 (edge)** Tentare di aggiungere un chip con **solo spazi**:
      ignorato silenziosamente (`trim().isEmpty()` → return).
- [ ] **FORM-22 (edge)** Aggiungere un valore con spazi iniziali/finali
      (es. "  PS5  "): salvato trimmato, non genera un duplicato distinto
      da "PS5" già esistente.
- [ ] **FORM-23 (edge)** Aggiungere un nome piattaforma/genere/tag
      completamente nuovo (mai visto prima in libreria): creato al volo
      come nuova voce di lookup, disponibile in autocomplete per la
      prossima recensione.
- [ ] **FORM-24 (edge)** Rimuovere tutti i chip di una sezione dopo averne
      aggiunti: il draft accetta lista vuota (piattaforme/generi/tag sono
      opzionali).
- [ ] **FORM-25 (edge)** Nome piattaforma/genere/tag molto lungo (es. 200
      caratteri) o con emoji: accettato senza troncare in modo che spezzi
      il layout del chip.
- [ ] **FORM-26** Autocomplete non propone di nuovo un valore già
      selezionato come chip (evitare doppioni nella lista suggerimenti).

### 3.3 Pro / Contro

- [ ] **FORM-27** Aggiungere/rimuovere righe libere in Pro e in Contro
      indipendentemente; l'ordine di inserimento è preservato
      (`posizione`).
- [ ] **FORM-28 (edge)** Aggiungere una riga Pro/Contro vuota (senza
      testo) e salvare: verificare se viene scartata o salvata come
      stringa vuota (probabile comportamento da chiarire/segnalare se
      produce un bullet vuoto nell'export Markdown/PDF).
- [ ] **FORM-29 (edge)** Molte righe Pro/Contro (20+): la lista scrolla
      correttamente nel form, nessun elemento tagliato.

### 3.4 Copertina immagine

- [ ] **FORM-30** Tap su area copertina apre il photo picker di sistema
      (`ActivityResultContracts.PickVisualMedia`) — nessun permesso
      runtime richiesto/richiesto in anticipo.
- [ ] **FORM-31** Selezionare un'immagine la mostra come anteprima nel
      form; l'immagine viene copiata in storage interno app (non solo
      referenziata per URI esterna).
- [ ] **FORM-32** Pulsante "Rimuovi copertina" toglie l'anteprima e, al
      salvataggio, la recensione risulta senza copertina.
- [ ] **FORM-33 (edge)** Annullare il photo picker (tasto back/annulla):
      la copertina precedente (se presente in modifica) resta invariata,
      nessuna copertina "rotta".
- [ ] **FORM-34 (edge)** Sostituire una copertina esistente con una nuova
      più volte di seguito: nessun accumulo di file orfani visibile
      all'utente (non verificabile da UI, ma verificare che l'anteprima
      finale sia sempre quella corretta e che le performance non
      degradino).
- [ ] **FORM-35 (edge)** Selezionare un'immagine molto grande (es. foto
      12MP+ da fotocamera): copiata/visualizzata senza OOM né attese
      eccessive percepibili.
- [ ] **FORM-36 (edge)** Selezionare un'immagine con proporzioni estreme
      (panoramica molto larga, o molto stretta e alta): l'anteprima nel
      form e in vista griglia libreria non rompono il layout.

### 3.5 "Cerca online" (TheGamesDB)

- [ ] **FORM-37** Con API key **non configurata**: tap su "Cerca online"
      mostra "Configura la API key di TheGamesDB nelle Impostazioni per
      usare la ricerca online" invece di tentare la chiamata di rete.
- [ ] **FORM-38** Con API key configurata: digitare un titolo e cercare
      apre un dialog con i risultati (copertina, piattaforma, anno) tra
      cui scegliere.
- [ ] **FORM-39** Selezionare un risultato precompila copertina (scaricata
      e salvata localmente, non solo linkata) e i campi
      piattaforma/genere disponibili dal risultato — **non** sovrascrive
      titolo/voto/altri campi già compilati a mano dall'utente (verificare
      il comportamento reale: se sovrascrive campi già compilati è un
      caso da segnalare).
- [ ] **FORM-40 (edge)** Ricerca senza nessun risultato: "Nessun risultato
      trovato", il form resta compilabile a mano.
- [ ] **FORM-41 (edge)** Ricerca con errore di rete/HTTP (es. airplane
      mode, chiave non valida): "Ricerca non riuscita. Puoi comunque
      compilare i campi a mano" (o messaggio con dettaglio tecnico
      accodato secondo il fix di Fase 7) — mai un crash o un'eccezione
      non gestita.
- [ ] **FORM-42 (edge)** Ricerca con un titolo contenente caratteri
      speciali (es. ":", "'", accenti — es. "Pokémon", "Assassin's
      Creed"): la query viene inviata/URL-encoded correttamente, nessun
      crash né richiesta malformata.
- [ ] **FORM-43 (edge)** Ricerca con **piattaforma già impostata** nel
      form (chip piattaforma aggiunto prima di cercare): i risultati
      dovrebbero essere filtrati/disambiguati per quella piattaforma
      (dedotta dal primo tag piattaforma) — verificare che il filtro
      sia effettivo.
- [ ] **FORM-44 (edge)** Doppio tap rapido su "Cerca online": non deve
      avviare due ricerche parallele che sovrascrivono lo stato UI in
      modo incoerente (spinner bloccato, risultati duplicati).
- [ ] **FORM-45 (edge)** Chiudere il dialog risultati senza selezionare
      nulla: il form resta come prima, nessun campo alterato.

### 3.6 Salvataggio / annullamento / back

- [ ] **FORM-46** Tap sul segno di spunta (salva esplicito) con dati
      validi: recensione salvata, torna al dettaglio (modifica) o alla
      libreria (creazione), messaggio di conferma se previsto.
- [ ] **FORM-47** Freccia indietro in alto (form aperto **dalla
      libreria**, non dal backlog): comportamento di semplice pop,
      nessun salvataggio implicito.
- [ ] **FORM-48** Gesto di back di sistema (swipe/tasto hardware) equivale
      esattamente alla freccia in alto in **ogni** contesto di apertura
      del form — regressione nota già corretta con `BackHandler`, verifica
      esplicita che sia ancora vero (sezione 10).
- [ ] **FORM-49 (edge)** Form aperto **dal backlog** (precompilato da un
      item), uscita con back **prima di premere il segno di spunta
      esplicito**: la bozza viene comunque salvata (se almeno il titolo è
      presente) e collegata al backlog item — verificare sia col tasto
      freccia sia col gesto di sistema.
- [ ] **FORM-50 (edge)** Stesso scenario ma con **titolo ancora vuoto** al
      momento del back: nessuna bozza "vuota" deve essere creata (il
      salvataggio implicito richiede almeno un titolo).
- [ ] **FORM-51 (edge)** Ripetere più volte il ciclo "apri item completato
      dal backlog senza recensione → apri form (via link 'Scrivi una
      recensione') → esci con back senza salvare esplicitamente → riapri
      di nuovo": non deve creare una nuova bozza duplicata ogni volta se
      l'item ha già una bozza collegata (regressione nota, sezione 10 —
      verificare con attenzione, è stata la causa di un bug reale
      multi-round).

### 3.7 Precompilazione da backlog e spostamento lista automatico

- [ ] **FORM-52** Form aperto dal prompt "vuoi scrivere una recensione?"
      del backlog: titolo/piattaforme/generi/date/copertina precompilati
      dall'item, **stato preimpostato su "Completato"** (non "In corso"
      di default).
- [ ] **FORM-53 (edge)** Backlog item senza `completedDate` impostata: la
      data fine del form precompilato usa la data odierna come fallback.
- [ ] **FORM-54** Al **primo** salvataggio riuscito (esplicito o implicito
      da back) di una recensione creata da questo flusso, compare il
      dialog "Sposta"/"Non spostare" verso la lista di sistema
      "Completati con recensione" — la navigazione di conferma/uscita
      resta **sospesa** finché l'utente non risponde a questo dialog.
- [ ] **FORM-55 (edge)** Modificare in un secondo momento (giorni dopo)
      una recensione già collegata a un backlog item (aperta dal link
      "Recensione collegata"): il salvataggio **non** ripropone il
      dialog di spostamento lista (solo alla creazione originale,
      `editingId == null` come guardia).
- [ ] **FORM-56 (edge)** Rispondere "Non spostare" al dialog: la
      recensione resta comunque salvata e collegata, l'item backlog resta
      nella lista in cui si trovava.

---

## 4. Dettaglio recensione

- [ ] **DET-01** Apertura dettaglio mostra tutti i campi salvati:
      copertina, titolo, voto, stato, piattaforme, generi, tag, date
      inizio/fine, ore di gioco, pro, contro, corpo recensione.
- [ ] **DET-02 (edge)** Recensione con tutti i campi opzionali vuoti
      (creata con solo titolo/voto/stato/data inizio): il dettaglio non
      mostra sezioni vuote rotte/etichette senza contenuto.
- [ ] **DET-03** Icona export in top bar: menu con Markdown e PDF (singola
      recensione).
- [ ] **DET-04** Export Markdown genera testo compatibile Reddit (titolo
      `#`, bullet metadati, sezioni Pro/Contro/corpo solo se non vuote).
- [ ] **DET-05** Export PDF singola recensione: file leggibile con tutti i
      dati, salvato via SAF.
- [ ] **DET-06** Modifica: apre il form precompilato con i dati correnti
      (vedi sezione 3.1 per la validazione).
- [ ] **DET-07** Eliminazione: dialog di conferma "Eliminare la
      recensione? L'azione non può essere annullata" prima di procedere.
- [ ] **DET-08** Conferma eliminazione: recensione rimossa, torna alla
      libreria, la recensione non compare più in nessun elenco/ricerca.
- [ ] **DET-09 (edge)** Annullare il dialog di eliminazione: nessuna
      modifica, recensione ancora presente e integra.
- [ ] **DET-10 (edge)** Eliminare una recensione **collegata a un backlog
      item** (`reviewId` impostato su quell'item): verificare cosa
      succede lato backlog — il link "Recensione collegata" deve
      smettere di puntare a una recensione inesistente senza crashare
      quando si riapre l'item (possibile bug se non gestito: nessuna
      logica nota di pulizia `reviewId` all'eliminazione, da verificare
      esplicitamente).
- [ ] **DET-11 (edge)** Navigare al dettaglio di una recensione, poi
      eliminarla da un altro punto (in teoria non possibile in questa
      single-user app senza multi-finestra, ma verificare almeno il
      caso "elimina, poi premi back rapidamente" per race condition sulla
      UI).
- [ ] **DET-12 (edge)** Titolo recensione lunghissimo o con emoji nella
      top bar del dettaglio: `maxLines = 1` + ellissi, non va a capo né
      spinge fuori le icone azione (regressione nota, sezione 10).

---

## 5. Backlog

### 5.1 Elenco liste

- [ ] **BKL-01** Backlog vuoto: "Nessuna lista ancora" / "Tocca + per
      creare la prima lista".
- [ ] **BKL-02** Header statistiche aggregate leggere (conteggio per
      stato/lista) visibile sopra l'elenco liste, aggiornato in tempo
      reale.
- [ ] **BKL-03** Tap su una lista apre `BacklogListDetailScreen` con i
      suoi item.

### 5.2 Creazione / rinomina / eliminazione / riordino liste

- [ ] **BKL-04** FAB "+" apre dialog "Nuova lista" con campo nome; conferma
      crea la lista in coda.
- [ ] **BKL-05 (edge)** Creare una lista con nome vuoto/solo spazi:
      verificare se bloccato o se crea una lista senza nome visibile
      (probabile edge case non validato, da verificare).
- [ ] **BKL-06 (edge)** Creare due liste con lo **stesso nome**: nessun
      vincolo di unicità noto sul nome lista — verificare che sia
      permesso e che la UI le distingua comunque correttamente (per id).
- [ ] **BKL-07** Icona matita → dialog rinomina con nome corrente
      precompilato; conferma aggiorna il nome ovunque compaia (filtri,
      dropdown "sposta in lista", header).
- [ ] **BKL-08** Icona cestino → dialog conferma "Eliminare questa lista?
      Tutti gli elementi di '...' verranno eliminati. L'azione non può
      essere annullata."
- [ ] **BKL-09 (edge)** Eliminare una lista **non vuota**: tutti i suoi
      item (e relativi commenti/storico) vengono eliminati in cascata,
      verificare che non restino riferimenti orfani in ricerca/filtri.
- [ ] **BKL-10 (edge)** Tentare di eliminare/rinominare una delle due
      **liste di sistema** ("Completati con recensione" / "Completati in
      attesa di recensione") una volta create: verificare se è permesso
      (nessuna protezione nota nel codice) — se sì, valutare se è un
      comportamento accettabile o un bug (l'utente potrebbe romperle
      senza saperlo, e un futuro trigger di spostamento le ricreerebbe
      da capo con `getOrCreateSystemList`).
- [ ] **BKL-11** Frecce su/giù riordinano le liste; l'ordine persiste dopo
      chiusura/riapertura app.
- [ ] **BKL-12 (edge)** Freccia su sulla prima lista / freccia giù
      sull'ultima: disabilitate o no-op, nessun crash/indice fuori
      range.

### 5.3 Ricerca e filtro unificato

- [ ] **BKL-13** Digitare nella ricerca o attivare un filtro passa
      automaticamente dalla vista "elenco liste" a un elenco piatto
      cross-lista dei risultati, ogni riga con il nome della lista di
      appartenenza.
- [ ] **BKL-14** Filtri disponibili: lista, stato (5 valori: Da iniziare,
      In corso, Completato, Abbandonato, In pausa), piattaforma, genere.
- [ ] **BKL-15 (edge)** Filtro per stato "In pausa": verificare che esista
      davvero un modo per un item di raggiungere questo stato dallo
      `StatusEditor` (5 chip status) e che il filtro lo trovi
      correttamente — stato meno visibile nelle altre sezioni di
      CLAUDE.md, verificarne l'intero ciclo di vita.
- [ ] **BKL-16** Cancellare ricerca e filtri torna alla vista elenco
      liste normale.
- [ ] **BKL-17 (edge)** Ricerca/filtro che non produce risultati:
      "Nessun risultato con i filtri attuali", non lo stato vuoto
      generico del backlog.
- [ ] **BKL-17b (edge)** La ricerca testuale cerca **solo su titolo e
      tag** (confermato da codice) — **non** su piattaforma, genere o
      commenti. Digitare il nome di una piattaforma/genere/commento che
      non compare anche nel titolo/tag di nessun item deve dare "nessun
      risultato", anche se quell'item esiste ed è visibile filtrando per
      piattaforma/genere dal pannello filtri.

### 5.4 Export/import backlog (zip)

- [ ] **BKL-18** Icona download in top bar backlog: esporta **sempre
      l'intero backlog** (tutte le liste, non filtrato) in un file
      `.zip` via SAF.
- [ ] **BKL-19** Icona upload: apre picker SAF, importa un file zip
      backlog precedentemente esportato (da questo o da un altro device).
- [ ] **BKL-20** Import **sempre additivo**: crea liste ed item **nuovi**
      con id nuovi, mai una sostituzione/merge.
- [ ] **BKL-21** Dopo import: "Importate X liste, Y elementi" con conteggi
      corretti.
- [ ] **BKL-22 (edge)** Importare **due volte di seguito lo stesso file**:
      produce liste/item duplicati (comportamento atteso e documentato,
      non un bug) — verificare che sia comunque utilizzabile senza
      crash con il doppio dei dati.
- [ ] **BKL-23 (edge)** Import di un file zip malformato/corrotto o non
      generato da questa funzione (es. uno zip qualsiasi rinominato, privo
      dell'entry `data.json`): fallisce con **"Importazione non riuscita:
      File non valido: manca data.json nell'archivio"** (messaggio esatto
      confermato da codice), non crash.
- [ ] **BKL-24 (edge)** Import di un archivio d'export backlog che
      referenzia una `reviewId`: verificare che venga scartata (nessuna
      recensione fantasma collegata a un id inesistente sul device di
      destinazione).
- [ ] **BKL-25 (edge)** Import di un backlog con commenti/storico:
      timestamp originali preservati (non sostituiti con "adesso"), e
      **nessuna** voce di storico sintetica aggiuntiva tipo "CREATO"
      iniettata sopra lo storico importato.
- [ ] **BKL-26 (edge)** Import di un item con copertina: la copertina
      viene ri-salvata con nome file nuovo (non collide con copertine già
      presenti sul device, anche se per coincidenza uno stesso UUID fosse
      già in uso — improbabile ma verificare che non sovrascriva nulla).
- [ ] **BKL-27 (edge)** Export di un backlog **vuoto** (nessuna lista):
      genera comunque uno zip valido (anche se con `data.json` vuoto),
      non un crash.
- [ ] **BKL-28 (edge)** Annullare il picker SAF durante export o import:
      nessun crash, nessun messaggio fuorviante.

### 5.5 Dettaglio lista (item, drag-to-reorder, vista griglia)

- [ ] **BKL-29** Lista vuota: "Nessun elemento in questa lista", FAB
      "Aggiungi al backlog".
- [ ] **BKL-30** Drag-to-reorder in vista a lista: trascinare dall'icona
      "maniglia" dedicata (non l'intera riga) riordina gli item; l'ordine
      finale è scritto **una sola volta** a fine gesto.
- [ ] **BKL-31 (edge)** Iniziare il drag e rilasciare **fuori** dall'area
      della lista (drag oltre i bordi): l'item non deve sparire o
      finire in una posizione indefinita, deve restare nella lista in
      una posizione valida.
- [ ] **BKL-32 (edge)** Tap normale (non drag) su una riga: apre il
      dettaglio item, **non** viene interpretato come inizio di un drag
      (il gesto deve essere disambiguato solo sull'icona maniglia).
- [ ] **BKL-33** Toggle vista lista/griglia (`ViewModeToggle`, stesso
      componente della libreria) sulla schermata dettaglio lista.
- [ ] **BKL-34** In vista **griglia**, il drag-to-reorder **non è
      disponibile** — verificare che non ci sia una maniglia residua o
      un gesto che sembra funzionare ma non salva l'ordine.
- [ ] **BKL-35 (edge)** Passare da lista a griglia e viceversa **durante**
      uno scroll a metà lista: nessun crash, la posizione di scroll può
      ragionevolmente resettarsi ma non deve rompere il rendering.

### 5.6 Form item backlog

- [ ] **BKL-36** Campi: titolo (obbligatorio — riusa lo stesso messaggio
      "Il titolo è obbligatorio" del form recensione, confermato da
      codice, nessuna stringa duplicata dedicata), piattaforme/generi/tag
      (stesso chip input di sezione 3.2, stesso pool di lookup condiviso
      con le recensioni), copertina.
- [ ] **BKL-37 (edge)** Selezionare in questo form una piattaforma/tag già
      creato dal form recensioni (o viceversa): autocomplete condiviso,
      nessuna lista separata per il backlog.
- [ ] **BKL-38** "Cerca online" (TheGamesDB) qui precompila anche **anno**
      e **sviluppatore** (in più rispetto al form recensione, che non li
      ha).
- [ ] **BKL-39** Dopo aver scelto un risultato TheGamesDB, parte in
      automatico anche la ricerca HowLongToBeat (silenziosa se fallisce,
      ma con messaggio diagnostico visibile nel form — vedi BKL-40).
- [ ] **BKL-40 (edge)** Verificare il messaggio `hltb_status_*` mostrato
      dopo la ricerca online: "stima trovata" / "nessuna corrispondenza
      trovata" / "ricerca non riuscita — <dettaglio>" — con rete assente
      o titolo senza corrispondenza HowLongToBeat, il form non deve
      bloccarsi né perdere gli altri dati già precompilati da TheGamesDB.
- [ ] **BKL-41 (edge)** Creare un item **senza mai** usare "Cerca online"
      (tutto compilato a mano): salvabile normalmente, nessun campo
      HowLongToBeat/anno/sviluppatore valorizzato.
- [ ] **BKL-42** Salvataggio item: torna al dettaglio lista, nuovo item in
      coda (posizione = ultima) o nella posizione corretta se in
      modifica.
- [ ] **BKL-42b (edge)** A differenza del form recensione (sezione 3.6),
      il form item backlog **non** ha alcun salvataggio implicito di
      bozza sul tasto/gesto indietro: uscire senza premere il segno di
      spunta è un semplice pop, **nessun** item viene creato con dati
      parziali — verificare che sia davvero così (nessuna bozza fantasma
      compare nella lista dopo un back a metà compilazione).

### 5.7 Dettaglio item backlog

- [ ] **BKL-43** Mostra titolo, copertina, piattaforme/generi/tag, anno,
      sviluppatore, stima HowLongToBeat (solo se almeno un campo
      valorizzato), stato corrente, cronologia, commenti.
- [ ] **BKL-44** `StatusEditor`: selezione stato (5 chip in `FlowRow`,
      verificare che vadano a capo correttamente con "Abbandonato" —
      regressione nota, sezione 10) come stato **locale non
      committato**; pulsante "Salva" (ora un `Button` pieno, non più un
      `TextButton` poco visibile) compare **solo** se la selezione
      differisce da quella effettivamente salvata.
- [ ] **BKL-45 (edge)** Selezionare un chip stato e poi riselezionare
      quello originale (annullando la modifica prima di salvare): il
      pulsante "Salva" deve tornare a **non** essere mostrato (nessuna
      scrittura inutile).
- [ ] **BKL-46** Passaggio a "In corso" per la prima volta: `startDate`
      auto-popolata a oggi (solo se non già impostata in precedenza).
- [ ] **BKL-47** Passaggio a "Completato" per la prima volta:
      `completedDate` auto-popolata; se l'item non ha ancora
      `reviewId`, scatta il prompt "Scrivere una recensione? Vuoi
      scrivere una recensione per '...'".
- [ ] **BKL-48 (edge)** Item che torna "In corso" dopo essere stato
      "Completato" e poi di nuovo "Completato": `completedDate`
      originale **non** viene sovrascritta la seconda volta (solo la
      prima transizione la imposta).
- [ ] **BKL-49 (edge)** Item con `reviewId` già impostato, che passa di
      nuovo a "Completato" (es. da "Abbandonato" a "Completato"): **non**
      deve riproporre il prompt "vuoi scrivere una recensione?" (guardia
      `reviewId == null`).
- [ ] **BKL-50** Selezionare "Abbandonato" mostra/abilita il campo "Motivo
      dell'abbandono" (testo libero); salvarlo genera la voce di storico
      solo se lo stato è effettivamente cambiato (non ad ogni carattere
      digitato).
- [ ] **BKL-51 (edge)** Impostare/modificare solo il motivo abbandono
      **senza** cambiare stato (item già "Abbandonato"): verificare se il
      pulsante "Salva" compare comunque per il solo cambio testo e se
      genera una voce di storico (probabilmente sì per il testo, ma
      senza una nuova voce "CAMBIO_STATO" visto che lo stato non cambia).
- [ ] **BKL-52** Rispondere "Sì" al prompt "vuoi scrivere una recensione?":
      naviga al form precompilato (sezione 3.7); `launchSingleTop = true`
      protegge da doppio tap che accoda due navigazioni.
- [ ] **BKL-53** Rispondere "No": scatta il **secondo** dialog "Spostare
      nella lista? '...' verrà spostato nella lista 'Completati in attesa
      di recensione'." — pulsanti "Sposta"/"Non spostare".
- [ ] **BKL-54 (edge)** Chiudere il primo dialog toccando **fuori**
      (scrim, non un pulsante): equivale a "decido dopo" — **nessuno**
      spostamento, nessun secondo dialog, l'item resta nella lista
      corrente.
- [ ] **BKL-55** Conferma "Sposta" nel secondo dialog: l'item si sposta
      davvero nella lista di sistema "Completati in attesa di
      recensione" (creata al volo se non esiste ancora), con voce di
      storico "Spostato in ...".
- [ ] **BKL-56 (edge)** Ripetere il flusso "Completato → No → Sposta" con
      la lingua dell'app impostata su **inglese**: la lista di sistema
      creata la prima volta deve avere il nome nella lingua in cui
      viene creata **quella prima volta**, e non deve duplicarsi (una
      seconda lista con lo stesso `systemKind` non va creata) anche se
      nel frattempo si cambia di nuovo lingua.
- [ ] **BKL-57** Item già collegato a una recensione (`reviewId`
      impostato): "Recensione collegata" è un testo **cliccabile**
      (sottolineato) che apre direttamente il dettaglio di quella
      recensione — non ricrea mai una recensione nuova (regressione
      nota, sezione 10).
- [ ] **BKL-58** Item "Completato" **senza** recensione collegata: link
      cliccabile persistente "Scrivi una recensione" (non solo al momento
      esatto del cambio stato), funziona da qualunque lista si trovi
      l'item.
- [ ] **BKL-59** Icona "sposta in lista" (freccia su cartella): dropdown
      con le altre liste esistenti (esclusa quella corrente); selezione
      sposta l'item e genera voce di storico.
- [ ] **BKL-60 (edge)** Con **una sola lista** esistente in tutto il
      backlog (quella corrente): l'icona "sposta in lista" è
      **disabilitata** (non un tap silenzioso su un menu vuoto —
      regressione nota, sezione 10).
- [ ] **BKL-61** Sezione Commenti: aggiungere un commento lo mostra subito
      in cima/fondo con timestamp, genera voce di storico "Commento
      aggiunto".
- [ ] **BKL-62 (edge)** Aggiungere un commento con **solo spazi** o
      **vuoto**: confermato da codice come no-op silenzioso (il commento
      blank non viene aggiunto) — verificare che sia davvero così anche
      dalla UI (nessun commento vuoto in lista, nessun feedback d'errore
      necessario dato che non è un errore per l'utente, solo un tap
      ignorato).
- [ ] **BKL-63** Sezione Storico: elenco cronologico di tutti gli eventi
      (CREATO, CAMBIO_STATO, CAMBIO_LISTA, COMMENTO,
      RECENSIONE_COLLEGATA) con dettaglio leggibile (nome stato/lista di
      destinazione, non un id grezzo).
- [ ] **BKL-64** Eliminazione item: dialog di conferma "Eliminare questo
      elemento?" prima di procedere; item rimosso con relativi
      commenti/storico.
- [ ] **BKL-65 (edge)** Eliminare un item che ha una recensione collegata:
      verificare cosa succede alla recensione (dovrebbe restare, dato che
      l'eliminazione dell'item backlog non ha motivo di cascare sulla
      recensione — confermare che non venga eliminata anche lei).

---

## 6. Statistiche

- [ ] **STAT-01** Libreria vuota: "Nessuna recensione ancora: aggiungine
      una per vedere le statistiche" — verificare se questo messaggio
      nasconde anche l'eventuale sezione HowLongToBeat backlog o se
      quella resta visibile indipendentemente (documentata come
      indipendente in `CLAUDE.md`).
- [ ] **STAT-02** Con almeno una recensione: totale recensioni, voto
      medio, ore totali tracciate (somma, `null` trattato come 0).
- [ ] **STAT-03** Distribuzione per piattaforma e per genere: barre
      orizzontali, **senza** percentuale (many-to-many, come da
      design).
- [ ] **STAT-04** Ripartizione per stato: barra impilata a segmenti +
      legenda, **con** percentuale ("%1$d (%2$.0f%%)").
- [ ] **STAT-05 (edge)** Tutte le recensioni con lo stesso stato (es.
      tutte "Completato"): la barra a segmenti mostra un solo segmento al
      100%, non si rompe con zero larghezza sugli altri.
- [ ] **STAT-06 (edge)** Voto medio con un solo tipo di voto ricorrente
      (es. tutte le recensioni a 10.0 o tutte a 0.0): calcolo e
      visualizzazione corretti, nessuna divisione per zero visibile.
- [ ] **STAT-07** Sezione "Tempo stimato backlog (HowLongToBeat)":
      visibile **solo** se almeno un item backlog ha almeno un campo
      HowLongToBeat valorizzato, indipendentemente da quanti item hanno
      stato "Completato"/altro.
- [ ] **STAT-08** Somma ore storia principale / storia+extra / completista
      su tutti gli item backlog con almeno un campo HowLongToBeat
      valorizzato, più il conteggio "X elementi con una stima" (plurali
      corretti per 0/1/N — verificare in particolare il caso singolare
      "1 elemento" vs plurale "2 elementi").
- [ ] **STAT-09 (edge)** Backlog con item che hanno **solo uno dei tre**
      campi HowLongToBeat valorizzato (es. solo storia principale, non
      storia+extra): la somma delle altre due colonne non include quella
      recensione/item come se fosse 0 fuorviante — verificare la
      presentazione (dovrebbe restare distinguibile "dato assente" da
      "0 ore").
- [ ] **STAT-10** Aggiungere/modificare una recensione o un item backlog
      con HowLongToBeat mentre la schermata Statistiche è aperta in
      background e si torna indietro: i numeri si aggiornano (Flow
      reattivo), non serve un pull-to-refresh manuale.

---

## 7. Impostazioni

### 7.1 Tema

- [ ] **SET-01** Tre opzioni Sistema/Chiaro/Scuro; selezionarne una cambia
      immediatamente il tema di **tutta** l'app (non solo la schermata
      Impostazioni), senza richiedere un riavvio manuale.
- [ ] **SET-02** Con tema "Sistema" selezionato, cambiare il tema di
      sistema del device (da Impostazioni Android) mentre l'app è aperta
      in foreground/background aggiorna coerentemente l'aspetto dell'app.
- [ ] **SET-03** La preferenza tema persiste dopo aver chiuso e riaperto
      l'app (kill completo del processo, non solo home/back).
- [ ] **SET-04 (edge)** Verificare che **tutte** le schermate (non solo
      quelle testate più di frequente) rispettino correttamente il tema
      scuro: contrasto testo/sfondo leggibile ovunque, nessun testo
      "bianco su bianco" residuo da colori hardcoded.

### 7.2 Lingua

- [ ] **SET-05** Tre opzioni Sistema/Italiano/English; selezionarne una
      cambia la lingua di **tutte** le stringhe UI immediatamente (verifica
      che `AppCompatActivity` + `setApplicationLocales` inneschino il
      refresh senza richiedere un riavvio manuale dell'app).
- [ ] **SET-06** La preferenza lingua persiste dopo un kill completo del
      processo (`autoStoreLocales`).
- [ ] **SET-07 (edge)** Impostare "Sistema" con la lingua di sistema del
      device su una lingua **non supportata** (es. francese): l'app deve
      ricadere sull'italiano (lingua di default del progetto), non
      crashare né mostrare stringhe miste/chiavi grezze.
- [ ] **SET-08 (edge)** Verificare parità 1:1 tra `values/strings.xml` e
      `values-en/strings.xml` su schermate meno battute (dialog di
      conferma, messaggi di errore export/import, plurali) — nessuna
      stringa che resta in italiano quando l'app è in inglese o
      viceversa.
- [ ] **SET-09** File esportati (Markdown/CSV/JSON/PDF): le etichette
      **restano sempre in italiano** indipendentemente dalla lingua
      scelta per l'app — comportamento intenzionale, non un bug (vedi
      `CLAUDE.md` Fase 5); verificare che sia davvero così e non un
      residuo dimenticato di prima della i18n.
- [ ] **SET-10** Cambiare lingua **durante** una sessione con dati caricati
      (es. libreria con filtri attivi): i dati restano coerenti, solo le
      etichette cambiano — nessun crash da ricreazione activity a metà
      operazione.

### 7.3 Backup/ripristino Google Drive

- [ ] **SET-11** Con `google_oauth_web_client_id` non configurato (ancora
      al placeholder): mostra `DriveNotConfiguredCard` con spiegazione,
      **non** un errore generico dopo aver premuto login.
- [ ] **SET-12** Con configurazione valida: "Accedi con Google" apre il
      picker account di sistema (Credential Manager), poi la richiesta di
      consenso per lo scope `drive.appdata` (`AuthorizationClient`).
- [ ] **SET-13** Dopo login riuscito: email dell'account mostrata
      ("Connesso a Google Drive"), sezioni Backup/Ripristino diventano
      visibili (prima nascoste).
- [ ] **SET-14 (edge)** Annullare il picker account durante il login:
      nessun crash, resta nello stato "non connesso", nessun messaggio
      fuorviante di successo.
- [ ] **SET-15 (edge)** Concedere il login ma **negare/annullare** il
      consenso allo scope Drive: "Autorizzazione Drive annullata" (o
      "non completata"), non si passa comunque allo stato connesso.
- [ ] **SET-16** "Esegui backup ora": crea un archivio zip su Drive
      (cartella privata `appDataFolder`), messaggio "Backup completato",
      aggiorna "Ultimo backup riuscito: ...".
- [ ] **SET-17 (edge)** Backup con libreria **vuota**: comunque riuscito
      (zip valido con `data.json` vuoto e nessuna immagine), non un
      errore.
- [ ] **SET-18 (edge)** Backup con rete assente/interrotta a metà upload:
      "Operazione non riuscita" (o messaggio con dettaglio), "Ultimo
      errore: ..." popolato, nessun archivio parziale/corrotto lasciato
      su Drive che rompa un futuro elenco backup.
- [ ] **SET-19** Toggle "Backup automatico": attivarlo pianifica il
      worker periodico (24h, richiede rete); disattivarlo lo cancella.
- [ ] **SET-20 (edge)** Backup automatico con consenso Drive **scaduto/
      revocato** dall'utente da fuori app (es. da myaccount.google.com):
      il worker fallisce silenziosamente (`Result.failure()`, nessun
      crash, nessuna notifica invadente) — verificabile solo
      indirettamente: aprire Impostazioni dopo l'orario previsto e
      controllare che "Ultimo errore" rifletta il fallimento, poi
      rifare un backup manuale per far ristabilire il consenso via
      flusso interattivo.
- [ ] **SET-21** "Aggiorna elenco backup": elenca i backup presenti su
      Drive con data e dimensione ("%1$d KB").
- [ ] **SET-22 (edge)** Nessun backup ancora presente su Drive: "Nessun
      backup trovato su Drive", non una lista vuota silenziosa
      indistinguibile da un errore di rete.
- [ ] **SET-23** Tap su "Ripristina questo backup": dialog di conferma
      "Ripristinare questo backup? Tutti i dati locali attuali (recensioni
      e copertine) verranno sostituiti dal contenuto di '...'.
      L'operazione non è reversibile." — pulsante "Ripristina".
- [ ] **SET-24** Conferma ripristino: **tutte** le recensioni/copertine
      locali attuali vengono cancellate e sostituite da quelle del
      backup scelto (sovrascrittura completa, nessun merge) — "Ripristino
      completato".
- [ ] **SET-25 (edge)** ⚠️ **Distruttivo per design**: verificare
      **esplicitamente** su un dataset locale di test (non reale) che
      dati creati dopo l'ultimo backup (recensioni aggiunte,
      modificate, cancellate nel frattempo) **spariscono** dopo un
      ripristino — comportamento atteso, non un bug, ma va confermato
      che non ci sia alcun avviso ingannevole che suggerisca un merge.
- [ ] **SET-26 (edge)** Ripristino interrotto a metà (es. si chiude l'app
      o cade la rete durante il download/decompressione dell'archivio):
      verificare lo stato della libreria dopo — nel peggiore dei casi
      dati parzialmente cancellati senza essere sostituiti sarebbe una
      perdita dati reale, da segnalare con priorità alta se riprodotto.
- [ ] **SET-27 (edge)** Ripristinare un backup che referenzia copertine
      poi effettivamente scaricate: verificare che le immagini
      compaiano correttamente in libreria dopo il ripristino, non solo i
      dati testuali.
- [ ] **SET-28** "Esci" (logout): torna allo stato "non connesso",
      backup/ripristino tornano nascosti; **nessun** dato locale viene
      toccato dal semplice logout.
- [ ] **SET-29 (edge)** Fare logout e poi login di nuovo con un account
      Google **diverso**: elenco backup deve riflettere l'account
      corrente, non mostrare/mischiare backup del precedente.

### 7.4 API key TheGamesDB

- [ ] **SET-30** Campo testo API key; pulsante "Salva" **disabilitato**
      finché il campo è vuoto (`apiKey.isNotBlank()`).
- [ ] **SET-31** Salvare una chiave: "API key salvata", persistita
      (`SharedPreferences`), disponibile subito per "Cerca online" nei
      form.
- [ ] **SET-32 (edge)** Incollare una chiave con spazi/a-capo iniziali o
      finali (es. da copia-incolla): salvata trimmata
      (`TheGamesDbPreferences.apiKey` fa `trim()`), non deve fallire per
      whitespace residuo.
- [ ] **SET-33 (edge)** Salvare una chiave palesemente non valida (es.
      testo a caso): salvataggio locale comunque riuscito (nessuna
      validazione client-side contro l'API reale); il fallimento emerge
      solo al primo uso di "Cerca online" con un messaggio HTTP
      dettagliato (401/403).
- [ ] **SET-34** Svuotare completamente il campo e tentare di risalvare:
      pulsante torna disabilitato, impossibile "salvare" una chiave
      vuota che disattiverebbe silenziosamente la ricerca senza che
      l'utente se ne accorga dal solo stato del pulsante.
- [ ] **SET-35 (edge)** Chiave valida ma con quota mensile esaurita:
      verificare che il messaggio d'errore mostrato nel form (sezione
      3.5) rifletta il problema reale (quota, non "invalid key") se
      TheGamesDB lo comunica in modo distinguibile nella risposta HTTP.

---

## 8. Scenari cross-cutting

### 8.1 Rotazione schermo / cambio configurazione

- [ ] **CFG-01** Ruotare il device (verticale ↔ orizzontale) su ciascuna
      schermata principale (Home, Libreria, Form, Dettaglio, Backlog,
      Statistiche, Impostazioni) non perde i dati inseriti/lo stato di
      navigazione corrente.
- [ ] **CFG-02 (edge)** Ruotare mentre un dialog è aperto (es. conferma
      eliminazione, dialog "sposta lista"): il dialog resta aperto e
      funzionante dopo la rotazione, non sparisce silenziosamente perdendo
      il contesto.
- [ ] **CFG-03 (edge)** Ruotare a metà di un'operazione asincrona in corso
      (export, ricerca online, backup): l'operazione non viene duplicata
      né persa, l'esito arriva comunque all'utente.
- [ ] **CFG-04** Cambiare la dimensione del testo di sistema (accessibilità,
      "grande"/"molto grande") non rompe layout critici (chip che vanno a
      capo correttamente, top bar con ellissi anziché overflow).

### 8.2 Process death / ricreazione activity

- [ ] **CFG-05** Con "Non mantenere attività" attivo nelle opzioni
      sviluppatore, mettere l'app in background e riaprirla da un'altra
      app: torna nello stesso punto senza crash (ricreazione da
      `SavedStateHandle`/navigazione).
- [ ] **CFG-06 (edge)** Process death mentre il form recensione è aperto
      con modifiche non salvate: verificare cosa succede ai dati non
      ancora salvati (perdita silenziosa attesa in molte app Android, ma
      va confermato che non ci sia un salvataggio parziale/corrotto).

### 8.3 Permessi e Storage Access Framework

- [ ] **CFG-07** Prima apertura del photo picker: nessun dialog di
      permesso runtime richiesto (per design, `PickVisualMedia`).
- [ ] **CFG-08** Ogni operazione SAF (export, import Markdown, export/
      import backlog) chiede sempre la destinazione/sorgente
      esplicitamente all'utente, mai un percorso fisso silenzioso.

### 8.4 Rete assente/instabile

- [ ] **CFG-09** Con rete completamente assente: l'app resta pienamente
      utilizzabile per tutte le funzioni **locali** (CRUD recensioni,
      backlog, statistiche, export/import file locali via SAF) — solo
      Drive/TheGamesDB/HowLongToBeat devono degradare con messaggi
      chiari.
- [ ] **CFG-10 (edge)** Rete che cade **a metà** di una chiamata (non
      assente dall'inizio): timeout gestito con messaggio, non un hang
      indefinito della UI (verificare che i client HTTP scritti a mano
      abbiano timeout connect/read espliciti, come documentato per
      HowLongToBeat).

### 8.5 Dati estremi / input anomali trasversali

- [ ] **CFG-11** Titoli/testi con emoji, caratteri CJK, RTL (arabo/ebraico)
      in qualunque campo testo dell'app (recensione, backlog, nomi
      lista, commenti): visualizzati correttamente ovunque compaiano
      (lista, dettaglio, export, statistiche se coinvolti in
      raggruppamenti).
- [ ] **CFG-12** Testo con markup HTML/script (es. `<script>alert(1)</script>`,
      `<b>test</b>`) in un campo libero (corpo recensione, commento,
      motivo abbandono): trattato come testo letterale ovunque venga
      mostrato in Compose (nessun rischio di injection essendo tutto
      reso via `Text`/`StaticLayout`, non `WebView`) — verificare che
      compaia letteralmente e non venga "eseguito" o tolto silenziosamente.
- [ ] **CFG-13** Incollare testo con caratteri di controllo/newline
      multipli consecutivi in campi single-line (titolo): verificare
      che non spezzi il layout (i single-line dovrebbero troncare i
      newline, ma va confermato).

### 8.6 Migrazione database (upgrade tra versioni schema)

- [ ] **CFG-14** Installare una build precedente (schema `version = 1`,
      solo tabelle recensioni), popolarla con dati reali, poi
      aggiornare **senza disinstallare** alla build corrente: le
      recensioni esistenti sopravvivono intatte (migrazioni additive
      `MIGRATION_1_2` → `MIGRATION_2_3` → `MIGRATION_3_4`, mai
      `fallbackToDestructiveMigration`).
- [ ] **CFG-15 (edge)** Stesso test partendo da uno schema intermedio
      (es. `version = 2`, con backlog ma senza colonne HowLongToBeat):
      verificare che solo `MIGRATION_2_3`+`MIGRATION_3_4` girino, non
      l'intera catena da 1.
- [ ] **CFG-16** Dopo la migrazione, le nuove funzionalità (backlog,
      HowLongToBeat, liste di sistema) sono immediatamente utilizzabili
      senza richiedere un passo manuale aggiuntivo dell'utente.

### 8.7 Multi-tasking

- [ ] **CFG-17** Minimizzare l'app durante un export/backup/ricerca
      online in corso e riaprirla dopo qualche minuto: l'operazione è
      completata (se abbastanza breve da sopravvivere al lifecycle) o
      fallita in modo gestito, mai in uno stato "bloccato per sempre"
      visibile all'utente.
- [ ] **CFG-18** Aprire un'altra app che consuma molta memoria mentre
      ThePatientGamerHelper è in background, poi tornare indietro: nessun
      crash da OOM su schermate con molte immagini caricate (vista
      griglia con libreria numerosa).

### 8.8 Localizzazione — vedi anche 7.2

- [ ] **CFG-19** Con lingua di sistema Android diversa sia da italiano
      sia da inglese (es. spagnolo) e preferenza app su "Sistema":
      ricade su italiano, coerente con `locales_config.xml` (solo
      it/en dichiarate).

### 8.9 Accessibilità di base

- [ ] **CFG-20** TalkBack (o altro screen reader) attivo: le icone-solo
      azione hanno `contentDescription` sensata (verificare in
      particolare quelle meno ovvie: maniglia drag, sposta lista,
      vista griglia/lista) — molte già presenti in `strings.xml`
      (`cd_*`), verificarne la copertura completa su tutte le
      schermate.
- [ ] **CFG-21** Navigazione a solo tastiera/D-pad (se il device lo
      supporta, es. Chromebook/TV-like) su form e dialog: focus visibile
      e ordine di tab sensato.

---

## 9. Percorsi end-to-end (scenari utente completi)

- [ ] **E2E-01** Ciclo di vita completo di un gioco: crea item backlog →
      "Cerca online" (TheGamesDB + HowLongToBeat) → sposta a "In corso" →
      aggiungi un commento → completa → rifiuta la recensione immediata
      ("No") → conferma spostamento a "Completati in attesa di
      recensione" → più tardi, usa il link "Scrivi una recensione" →
      compila e salva → conferma spostamento a "Completati con
      recensione" → apri "Recensione collegata" dal backlog → modifica la
      recensione dal dettaglio → esporta in Markdown → verifica il testo
      generato.
- [ ] **E2E-02** Backup e ripristino end-to-end su due device (o due
      profili utente sullo stesso device): crea dati sul device A →
      backup manuale su Drive → login su device B con lo stesso account →
      ripristina → confronta che libreria e copertine coincidano.
- [ ] **E2E-03** Export/import backlog tra due device: esporta zip da
      device A, trasferiscilo (es. via email/drive personale, non il
      backup dell'app), importalo su device B → verifica liste/item/
      copertine/storico coerenti.
- [ ] **E2E-04** Roundtrip Markdown completo (già in LIB-52) ripetuto con
      lingua app **inglese**: verificare che il file esportato resti
      comunque in italiano (etichette fisse) e che l'import funzioni
      identicamente indipendentemente dalla lingua UI attiva.
- [ ] **E2E-05** Utente che non configura mai né Drive né TheGamesDB né
      HowLongToBeat: l'intera app (CRUD recensioni/backlog, statistiche,
      export/import locali, tema, lingua) resta pienamente funzionale
      solo con le funzionalità offline.

---

## 10. Regressioni note (bug reali già trovati e corretti — riverificare ad ogni release)

Elenco preso da `CLAUDE.md` (sezioni "Fix dopo verifica su device reale" e
successive). Ognuno di questi era un bug **reale** trovato solo con
verifica manuale su device, non dalla sola revisione statica del codice —
motivo in più per non saltarli in futuri round di test.

- [ ] **REG-01** `FilterChip` "Abbandonato" nel selettore stato del
      dettaglio backlog non si spezza più verticalmente carattere per
      carattere (fix: `FlowRow`).
- [ ] **REG-02** Titoli delle top bar (`Recensioni`, `Backlog`, titolo
      recensione/lista lunghi) non vanno più su due righe sovrapponendosi
      all'icona hamburger/back (fix: `maxLines = 1` + ellissi ovunque).
- [ ] **REG-03** Ricerca TheGamesDB con giochi che hanno `genres`/
      `developers` `null` (non solo assenti) non fa più fallire l'intera
      ricerca con un errore JSON illeggibile.
- [ ] **REG-04** HowLongToBeat: il client segue correttamente redirect
      HTTP 307/308 su tutte e quattro le chiamate del flusso
      (homepage/bundle/init/ricerca), non restituisce più un 308 nudo.
- [ ] **REG-05** HowLongToBeat: la regex che estrae l'endpoint di ricerca
      dal bundle `_app-*.js` richiede `method: "POST"` nello stesso
      blocco `fetch()`, non si aggancia più al primo `fetch()` qualunque
      del bundle (causa del 404 del giro precedente).
- [ ] **REG-06** Flusso backlog→recensione: "Recensione collegata" è
      cliccabile e apre la recensione esistente — non si creano più
      recensioni duplicate riaprendo il flusso su un item già collegato.
- [ ] **REG-07** `BackHandler` esplicito nel form recensione: il gesto di
      back di sistema (swipe/tasto hardware) si comporta come la freccia
      in alto, non più come un pop nudo che scartava salvataggio
      implicito/collegamento backlog.
- [ ] **REG-08** Icona "sposta in lista" disabilitata (non più un tap
      silenzioso su un `DropdownMenu` vuoto) quando non esiste
      nessun'altra lista verso cui spostare.
- [ ] **REG-09** Pulsante "Salva" dello `StatusEditor` backlog è un
      `Button` pieno ben visibile, non più un `TextButton` facile da non
      notare.
- [ ] **REG-10** `ReviewFormViewModel` imposta esplicitamente `status =
      COMPLETATO` (non il default `IN_CORSO`) quando precompila da un
      backlog item completato.
- [ ] **REG-11** TheGamesDB: `USER_AGENT` da browser desktop invece di
      una stringa che si autoidentifica come app — verificare che la
      ricerca online non fallisca più con "Invalid API key" su chiavi
      valide (causa sospetta, da confermare come definitivamente risolta
      o ancora aperta al momento del test).
- [ ] **REG-12 (aperta, da monitorare)** HowLongToBeat resta l'
      integrazione più fragile dell'app (endpoint reverse-engineered,
      nessuna API pubblica) — anche se REG-04/REG-05 hanno risolto due
      cause concrete già diagnosticate, un nuovo fallimento è **atteso
      come possibile** ad ogni release (il sito può cambiare bundle/
      protezioni in qualsiasi momento senza preavviso). Non trattare un
      fallimento HowLongToBeat come automaticamente "lo stesso bug di
      prima": leggere il messaggio diagnostico in-app (`hltb_status_error`,
      include URL e `source`) e riportarlo per intero.

---

## Cronologia aggiornamenti di questo piano

- 2026-08-07 — Prima stesura, copertura di tutte le funzionalità fino alla
  Fase 8 inclusa (import Markdown, export/import backlog, HowLongToBeat,
  viste griglia, liste di sistema, fix multi-round post-device).
