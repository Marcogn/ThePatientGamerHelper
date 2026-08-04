# Decisioni di implementazione — Fase 3 (Statistiche libreria)

Questo file documenta le scelte tecniche prese durante l'implementazione
della Fase 3 che non erano già esplicitate in `spec-app-recensioni-videogiochi.md`
o in `CLAUDE.md`. Vedi anche la sezione "Fase 3 — Statistiche libreria" in
`CLAUDE.md` per il riepilogo architetturale.

## Nessuna dipendenza di charting aggiunta

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

## Percentuali solo sulla ripartizione per stato

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

## Struttura dati per le aggregazioni

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

## UI e navigazione

- Nuova route `Destination.Stats` (oggetto senza parametri, coerente con
  `Destination.Library`), raggiungibile da un'icona (`Icons.Filled.BarChart`)
  nella top bar della libreria, accanto a filtri/ordinamento/export.
- `ui/stats/StatsScreen.kt` + `StatsViewModel` + `StatsUiState`: stesso
  pattern MVVM/UDF delle altre schermate (`ui/library`, `ui/detail`), con
  `ReviewRepository.observeAll()` come unica fonte dati (nessun mock).
