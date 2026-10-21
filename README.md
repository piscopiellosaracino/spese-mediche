# Spese Mediche (Android)

App Android per registrare e rendicontare le spese sanitarie partendo dalla scansione dei documenti.

## Flusso

1. **Scansiona**: si apre lo scanner documenti (fotocamera con riconoscimento dei bordi, ritaglio,
   correzione prospettica, più pagine, eventuale importazione da galleria). In alternativa
   **Importa PDF** per fatture o scontrini già digitali.
2. **Lettura (OCR on-device)**: il testo viene riconosciuto sul telefono, senza invio in rete, e
   l'app propone data, importo totale, fornitore, P.IVA, codice fiscale, categoria e modalità di
   pagamento. I campi compilati in automatico sono contrassegnati con ✦ e vanno verificati.
3. **Archiviazione**: il documento viene salvato come PDF nella memoria privata dell'app
   (`files/documenti/<anno>/…`) e registrato nel database locale (Room).
4. **Elenco cronologico**: raggruppato per mese, filtrabile per anno, con ricerca testuale
   (anche nel testo OCR) e totali.
5. **Per ogni documento**: visualizza, condividi, stampa (servizio di stampa Android) e scarica
   (selettore file di sistema: Download, Drive, …).
6. **Rendiconto**: per anno e/o beneficiario genera un PDF A4 con l'elenco cronologico, i riepiloghi
   per categoria, beneficiario e modalità di pagamento e, se richiesto, tutti i documenti in coda come
   allegati numerati. Si può stampare, condividere o scaricare. Ci sono anche l'export **CSV**
   (separatore `;`, adatto a Excel in italiano) e l'**archivio ZIP** (rendiconto + CSV + PDF originali).

## Tecnologie

| Funzione | Libreria |
|---|---|
| UI | Kotlin, Jetpack Compose, Material 3 |
| Scansione | Google ML Kit Document Scanner (`play-services-mlkit-document-scanner`) |
| OCR | ML Kit Text Recognition (modello latino incluso nell'APK) |
| Database | Room |
| PDF | `PdfRenderer` / `PdfDocument` di Android |
| Stampa | `PrintManager` |

Requisiti: Android 8.0+ (minSdk 26) e Google Play services (servono allo scanner). Non è richiesto
il permesso fotocamera: la fotocamera è gestita dal modulo Play services.

## Compilazione

**Android Studio**: apri la cartella del progetto, attendi la sincronizzazione Gradle ed esegui `app`.

**Riga di comando** (JDK 17 e Android SDK):

```bash
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # test del parser OCR
```

**GitHub Actions**: il workflow `.github/workflows/build.yml` compila l'APK a ogni push e lo
pubblica come artifact `spese-mediche-apk`.

## Struttura

```
app/src/main/java/it/spesemediche/
├── data/        Expense (entità), DAO, database Room
├── ocr/         OcrEngine (ML Kit) e ReceiptParser (estrazione euristica dei campi)
├── storage/     archivio PDF, rendering pagine, condivisione e stampa
├── report/      generazione rendiconto PDF, CSV e ZIP
└── ui/          ViewModel, navigazione e schermate Compose
```

## Note

- Il riconoscimento dei campi è euristico e tarato su scontrini «parlanti» di farmacia, ricevute
  ticket e fatture sanitarie italiane: va sempre verificato prima di salvare.
- Categorie e modalità di pagamento servono a organizzare i dati per la dichiarazione. L'app **non**
  applica regole fiscali di detraibilità: segnala solo i pagamenti non tracciati, da verificare con
  CAF o commercialista.
- `allowBackup` è disattivato perché si tratta di dati sanitari. Per spostare i dati su un altro
  dispositivo usa l'archivio ZIP.
