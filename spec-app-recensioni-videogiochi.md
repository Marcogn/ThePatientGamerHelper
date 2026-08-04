# Specifica funzionale e tecnica — App recensioni videoludiche

**Versione:** 0.1 (bozza di lavoro)
**Scopo del documento:** definire ambito, modello dati, funzionalità ed architettura per un'app Android personale che sostituisce/supporta il tuo attuale flusso di recensioni per r/patientgamer, con export multi-formato e backup cloud in fase futura.

---

## 1. Obiettivo e principi guida

App **single-user, offline-first**: nessun account è richiesto per l'uso base, nessun backend server è necessario per l'MVP. Tutti i dati vivono localmente sul device; il cloud entra in gioco solo come backup opzionale (Fase 4).

Il front-end tecnico è lasciato a te; in questo documento propongo uno stack di default motivato, ma nessuna scelta qui è vincolante — sono indicate come raccomandazioni con relative alternative.

---

## 2. Modello dati

Entità principale: **Recensione** (1 recensione = 1 gioco recensito).

| Campo | Tipo | Note |
|---|---|---|
| `id` | UUID | chiave primaria |
| `titolo` | stringa | nome del gioco |
| `piattaforma` | stringa/tag | libera ma con autocomplete su valori già usati |
| `genere` | stringa/tag | idem |
| `tagPersonalizzati` | lista di stringhe | tassonomia libera, tua |
| `voto` | numerico | **decisione aperta**: scala 0–10 (tipica recensioni testuali stile patientgamer) o stelle 1–5. Non presumo quale usi — è una scelta di prodotto tua |
| `dataInizio` | data | quando hai iniziato il gioco |
| `dataFine` | data | quando l'hai completato/abbandonato |
| `oreGioco` | numerico | tempo di completamento dichiarato, inserito manualmente (nessuna integrazione automatica prevista nell'MVP) |
| `stato` | enum | `completato` / `abbandonato` / `in corso` — utile per tracciare il backlog, coerente col tuo profilo di completista |
| `pro` | lista di stringhe | punti strutturati |
| `contro` | lista di stringhe | punti strutturati |
| `testoRecensione` | testo lungo (markdown) | corpo libero della recensione |
| `copertina` | URI locale (opzionale) | immagine salvata in storage interno app |
| `creatoIl` / `modificatoIl` | timestamp | metadati |

Entità di supporto: **Piattaforma** e **Genere** come tabelle di lookup separate, per garantire autocomplete coerente senza duplicare stringhe (evita "PS5" vs "Playstation 5" come tag diversi).

---

## 3. Funzionalità

### 3.1 MVP (Fase 1)
- CRUD completo sulle recensioni
- Libreria/lista con ricerca full-text e filtri combinabili (piattaforma, genere, tag, voto, stato, intervallo date)
- Ordinamento per data, voto, titolo, ore di gioco
- Vista dettaglio recensione

### 3.2 Statistiche libreria (Fase 3) ✅ completata
Dato il tuo profilo (alto tasso di completamento, attenzione a backlog e serie complete), ha senso includere:
- numero totale recensioni, voto medio, ore totali tracciate
- distribuzione per piattaforma/genere
- percentuale completato vs abbandonato

Implementata come nuova schermata Statistiche raggiungibile dalla libreria,
con le metriche sopra più la quota "in corso" (percentuale calcolata solo sul
campo stato, a scelta singola — non su piattaforma/genere, che sono
many-to-many e non sommerebbero a 100%). Dettaglio implementativo e scelte
tecniche in `CLAUDE.md` e `docs/decisioni-implementazione.md`.

### 3.3 Export (Fase 2)
- **Markdown**: formattazione compatibile con la sintassi di Reddit, per copia-incolla diretto nei tuoi post
- **JSON/CSV**: dati grezzi, per backup/portabilità e per eventuale elaborazione esterna
- **PDF**: singola recensione o intera libreria in batch
- **DOCX**: vedi nota tecnica dedicata sotto — è il formato più oneroso da generare nativamente

### 3.4 Backup cloud su Google Drive (Fase 4)
Backup manuale e automatico (periodico via WorkManager) di un archivio contenente JSON completo + immagini.

---

## 4. Architettura tecnica proposta (non vincolante)

- **Kotlin + Jetpack Compose** per la UI. È lo stack che Google raccomanda ufficialmente nella documentazione architetturale aggiornata; il sistema a View è ormai in manutenzione e non riceve più investimento su nuove funzionalità.
- **Pattern**: ViewModel con StateFlow + Unidirectional Data Flow (eventi salgono, stato scende) — è il pattern descritto nella guida ufficiale all'architettura Compose.
- **Persistenza locale**: Room come single source of truth, con Flow per l'osservabilità reattiva della UI.
- **DI**: Hilt (standard de facto sull'ecosistema Compose/Room).
- **Immagini**: storage interno dell'app, riferimento via URI in Room (non serve un content provider dedicato per un'app single-user).
- **Lavoro in background**: WorkManager per sync/backup periodici.

Se preferisci restare più vicino al tuo stack lavorativo (Spring/JHipster ti rende comunque molto a tuo agio con pattern MVC/dependency injection), l'alternativa classica MVVM con View + ViewModel resta percorribile, ma è un investimento su tecnologia che Google sta esplicitamente deprioritizzando — te lo segnalo per onestà, non per spingerti verso Compose a tutti i costi.

---

## 5. Dettaglio tecnico per l'export

### PDF
Due strade concrete:
1. **`android.graphics.pdf.PdfDocument`** — nativo, gratuito, ma basso livello: disegni manualmente ogni elemento su un Canvas. Massimo controllo, zero rischi di licenza, più codice da scrivere.
2. **Apache PDFBox (porting Android)** — libero sotto licenza Apache 2.0, API di più alto livello per testo/paragrafi/tabelle.

**Nota di onestà**: eviterei iText7 per questo progetto — è distribuito sotto licenza AGPL (uso gratuito ma con obbligo di rilasciare il codice sorgente dell'app che lo usa, a meno di licenza commerciale a pagamento). Per un'app personale non è bloccante in sé, ma è un vincolo da conoscere prima di adottarlo, non dopo.

### Markdown
Nessuna libreria necessaria: è generazione di stringhe a template, il formato più semplice dei quattro.

### CSV/JSON
`kotlinx.serialization` per JSON (idiomatico in Kotlin); per CSV un writer manuale o OpenCSV, senza particolari insidie.

### DOCX — nota di onestà tecnica
Qui va detta la cosa scomoda: **non esiste un writer DOCX leggero e maturo pensato per Android**. Apache POI (lo standard JVM per Office) ha problemi noti su Android — dipende da classi `java.awt` non disponibili sulla piattaforma e appesantisce parecchio l'APK. I wrapper Kotlin che si trovano in giro (es. DocxKtm) sono comunque costruiti sopra docx4j, che porta con sé lo stesso tipo di dipendenze pesanti.

Due opzioni realistiche, in ordine di pragmatismo:
1. **Generare il DOCX manualmente come archivio ZIP di XML** (un file .docx è tecnicamente uno ZIP con dentro `document.xml` + file di struttura OOXML). Per un documento semplice — titolo, paragrafi, elenchi puntati — è fattibile senza librerie pesanti: scrivi tu il template XML minimo. Richiede un po' di lavoro iniziale ma zero dipendenze problematiche.
2. **Posticipare il DOCX** a una fase successiva, dato che Markdown e JSON/CSV coprono già rispettivamente il caso "condivisione leggibile" e il caso "dato grezzo portabile" — il DOCX diventa un nice-to-have più che una necessità funzionale.

Consiglio la seconda opzione per l'MVP, e la prima se/quando il DOCX diventa davvero prioritario.

---

## 6. Backup cloud — dettaglio tecnico (Fase 4)

Alcuni punti che nel 2026 sono cambiati rispetto a molte guide che si trovano online, quindi verificati direttamente sulla documentazione Google:

- **Non usare `GoogleSignInClient` / `play-services-auth`**: è deprecato e in fase di rimozione dal Play Services Auth SDK. Molte guide "backup su Drive stile WhatsApp" che circolano online lo usano ancora — sono da considerare superate.
- Approccio corrente raccomandato: **Credential Manager** per l'autenticazione + **AuthorizationClient API** per l'autorizzazione specifica di accesso a Drive.
- Scope da richiedere: `drive.appdata`, che dà accesso alla **appDataFolder** — una cartella privata per-app, non visibile nell'interfaccia utente di Drive e non condivisibile. Perfetta per un backup automatico invisibile all'utente.
- API da usare: **Drive REST API v3**. La vecchia "Drive API per Android" (basata su `DriveClient`/`DriveResourceClient`) è deprecata dal 2019 e completamente disattivata dal 2023 — non è una scelta disponibile, a prescindere da preferenze.
- Formato del backup: singolo archivio con JSON completo dei dati + cartella immagini, versionato con timestamp nel nome file.

Nota pratica: dovrai comunque registrare l'app su Google Cloud Console e configurare una schermata di consenso OAuth. Per un'app a uso personale puoi restare in modalità "testing" (fino a un tetto di utenti di test), che evita il processo di verifica pubblica di Google — sufficiente per un caso d'uso a singolo utente come il tuo.

---

## 7. Fasi di sviluppo proposte

1. **Fase 1 — MVP locale** ✅: CRUD, lista, filtri, dettaglio recensione
2. **Fase 2 — Export** ✅: JSON/CSV → Markdown → PDF (in quest'ordine di complessità crescente)
3. **Fase 3 — Statistiche libreria** ✅: vedi `CLAUDE.md` per il dettaglio implementativo
4. **Fase 4 — Backup cloud Google Drive**
5. **Fase 5 (opzionale) — Export DOCX**, se resta prioritario dopo aver vissuto con gli altri tre formati

---

## 8. Punti aperti che ti lascio a decidere

Questi sono scelte di prodotto che non presumo per te:
- Scala di voto (0–10 con decimali vs stelle 1–5)
- Se lo stato "in corso" ti serve davvero o se preferisci tracciare solo giochi completati/abbandonati (recensire "a freddo" è tipico dello spirito patientgamer)
- Se vuoi un'unica recensione per gioco o la possibilità di rigiocare e aggiungere una seconda entry (replay) collegata alla stessa scheda gioco

---

## Fonti principali consultate

- developer.android.com — Recommendations for Android architecture (aggiornata 2026-04-26)
- developer.android.com — Compose UI Architecture (aggiornata 2026-06-16)
- developer.android.com — About the migration from legacy Google Sign-In (aggiornata 2026-03-06)
- developer.android.com — Store application-specific data (Drive appDataFolder, aggiornata 2026-04-20)
- developers.google.com — Drive Android API deprecation notice
- android-developers.googleblog.com — Streamlining Android authentication: Credential Manager replaces legacy APIs
- ironpdf.com / medium.com — confronto licenze iText7 (AGPL) vs alternative
- dev.to — Kotlin PDF Libraries: Free & Paid (panoramica PDFBox)
- discuss.kotlinlang.org / github.com (DocxKtm) — stato dei tool di generazione DOCX su Android/Kotlin
