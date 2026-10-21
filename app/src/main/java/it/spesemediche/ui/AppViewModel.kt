package it.spesemediche.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.spesemediche.SpeseApp
import it.spesemediche.data.Category
import it.spesemediche.data.Expense
import it.spesemediche.data.PaymentMethod
import it.spesemediche.data.date
import it.spesemediche.formatAmountPlain
import it.spesemediche.ocr.OcrEngine
import it.spesemediche.ocr.OcrLine
import it.spesemediche.ocr.ReceiptParser
import it.spesemediche.parseAmountToCents
import it.spesemediche.report.ReportGenerator
import it.spesemediche.storage.DocumentStorage
import it.spesemediche.storage.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.time.LocalDate

/** Stato del modulo di inserimento/modifica. */
data class ExpenseForm(
    val id: Long = 0,
    val date: LocalDate = LocalDate.now(),
    val amountText: String = "",
    val issuer: String = "",
    val vatNumber: String = "",
    val taxCode: String = "",
    val beneficiary: String = "",
    val category: Category = Category.ALTRO,
    val paymentMethod: PaymentMethod = PaymentMethod.NON_INDICATO,
    val notes: String = "",
    val pdfFile: File,
    val isDraft: Boolean,
    val ocrText: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Campi compilati automaticamente dall'OCR, da far verificare all'utente. */
    val autoFilled: Set<String> = emptySet(),
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = (app as SpeseApp).database.expenseDao()
    val storage = (app as SpeseApp).storage
    private val ocr = OcrEngine(app)
    val reports = ReportGenerator(storage)

    val expenses: StateFlow<List<Expense>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val knownBeneficiaries: StateFlow<List<String>> =
        expenses.map { list -> list.map { it.beneficiary.trim() }.filter { it.isNotEmpty() }.distinct() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _form = MutableStateFlow<ExpenseForm?>(null)
    val form: StateFlow<ExpenseForm?> = _form.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    init {
        viewModelScope.launch(Dispatchers.IO) {
            storage.clearDrafts()
            storage.clearExports()
        }
    }

    fun observeExpense(id: Long): Flow<Expense?> = dao.observeById(id)

    // --- Acquisizione ---------------------------------------------------------------------

    /** Risultato dello scanner: PDF + immagini JPEG delle pagine (usate per l'OCR). */
    fun onScanResult(pdfUri: Uri?, pageUris: List<Uri>, onReady: () -> Unit) {
        if (pdfUri == null) {
            toast("Lo scanner non ha restituito un PDF")
            return
        }
        acquire(onReady) {
            val draft = storage.newDraft(pdfUri)
            val lines = runCatching { ocr.fromImageUris(pageUris.take(MAX_OCR_PAGES)) }.getOrDefault(emptyList())
            draft to lines
        }
    }

    /** PDF già esistente (es. fattura ricevuta via email o scontrino digitale). */
    fun onPdfImported(uri: Uri, onReady: () -> Unit) {
        acquire(onReady) {
            val draft = storage.newDraft(uri)
            val lines = runCatching {
                val bitmaps = PdfUtils.renderPages(draft, OCR_RENDER_PX, MAX_OCR_PAGES)
                try {
                    ocr.fromBitmaps(bitmaps)
                } finally {
                    bitmaps.forEach { it.recycle() }
                }
            }.getOrElse { e ->
                if (e is SecurityException || e.javaClass.simpleName.contains("Password")) {
                    toast("PDF protetto: impossibile leggerlo")
                }
                emptyList()
            }
            draft to lines
        }
    }

    private fun acquire(onReady: () -> Unit, block: suspend () -> Pair<File, List<OcrLine>>) {
        viewModelScope.launch {
            _busy.value = "Lettura del documento…"
            try {
                val (draft, lines) = withContext(Dispatchers.IO) { block() }
                val parsed = ReceiptParser.parse(lines)
                val auto = buildSet {
                    if (parsed.date != null) add("date")
                    if (parsed.amountCents != null) add("amount")
                    if (parsed.issuer != null) add("issuer")
                    if (parsed.category != null) add("category")
                    if (parsed.paymentMethod != null) add("payment")
                    if (parsed.taxCode != null) add("taxCode")
                    if (parsed.vatNumber != null) add("vat")
                }
                _form.value = ExpenseForm(
                    date = parsed.date ?: LocalDate.now(),
                    amountText = parsed.amountCents?.let(::formatAmountPlain) ?: "",
                    issuer = parsed.issuer ?: "",
                    vatNumber = parsed.vatNumber ?: "",
                    taxCode = parsed.taxCode ?: "",
                    beneficiary = expenses.value.firstOrNull()?.beneficiary ?: "",
                    category = parsed.category ?: Category.ALTRO,
                    paymentMethod = parsed.paymentMethod ?: PaymentMethod.NON_INDICATO,
                    pdfFile = draft,
                    isDraft = true,
                    ocrText = lines.sortedWith(compareBy({ it.page }, { it.top }, { it.left }))
                        .joinToString("\n") { it.text },
                    autoFilled = auto,
                )
                if (lines.isEmpty()) toast("Testo non riconosciuto: compila i campi a mano")
                onReady()
            } catch (e: Exception) {
                toast("Errore nell'acquisizione: ${e.message}")
            } finally {
                _busy.value = null
            }
        }
    }

    // --- Modulo -----------------------------------------------------------------------------

    fun editExisting(id: Long, onReady: () -> Unit) {
        viewModelScope.launch {
            val e = dao.getById(id) ?: return@launch
            _form.value = ExpenseForm(
                id = e.id,
                date = e.date,
                amountText = formatAmountPlain(e.amountCents),
                issuer = e.issuer,
                vatNumber = e.vatNumber,
                taxCode = e.taxCode,
                beneficiary = e.beneficiary,
                category = e.category,
                paymentMethod = e.paymentMethod,
                notes = e.notes,
                pdfFile = storage.fileOf(e),
                isDraft = false,
                ocrText = e.ocrText,
                createdAt = e.createdAt,
            )
            onReady()
        }
    }

    fun updateForm(transform: (ExpenseForm) -> ExpenseForm) {
        _form.value = _form.value?.let(transform)
    }

    fun discardForm() {
        val f = _form.value ?: return
        _form.value = null
        if (f.isDraft) viewModelScope.launch(Dispatchers.IO) { f.pdfFile.delete() }
    }

    fun saveForm(onSaved: (Long) -> Unit) {
        val f = _form.value ?: return
        val cents = parseAmountToCents(f.amountText)
        when {
            cents == null || cents <= 0 -> return toast("Inserisci un importo valido")
            f.issuer.isBlank() -> return toast("Indica il fornitore / emittente")
            f.date.isAfter(LocalDate.now()) -> return toast("La data non può essere futura")
        }
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                val base = Expense(
                    id = f.id,
                    dateEpochDay = f.date.toEpochDay(),
                    amountCents = cents!!,
                    issuer = f.issuer.trim(),
                    vatNumber = f.vatNumber.trim(),
                    taxCode = f.taxCode.trim().uppercase(),
                    beneficiary = f.beneficiary.trim(),
                    category = f.category,
                    paymentMethod = f.paymentMethod,
                    notes = f.notes.trim(),
                    pdfPath = "",
                    ocrText = f.ocrText,
                    createdAt = f.createdAt,
                )
                if (f.isDraft) {
                    dao.insert(base.copy(pdfPath = storage.archive(f.pdfFile, f.date, f.category)))
                } else {
                    val old = dao.getById(f.id)!!
                    dao.update(base.copy(pdfPath = old.pdfPath))
                    f.id
                }
            }
            _form.value = null
            toast("Spesa salvata")
            onSaved(id)
        }
    }

    fun delete(expense: Expense, onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.delete(expense)
                storage.delete(expense)
            }
            toast("Spesa eliminata")
            onDone()
        }
    }

    // --- Esportazioni -----------------------------------------------------------------------

    enum class ExportKind(val mime: String, val ext: String) {
        PDF("application/pdf", "pdf"),
        CSV("text/csv", "csv"),
        ZIP("application/zip", "zip"),
    }

    /** Genera il file in cache e lo passa a [then] (condividi / stampa). */
    fun buildExport(
        kind: ExportKind,
        list: List<Expense>,
        title: String,
        subtitle: String,
        attachments: Boolean,
        then: (File) -> Unit,
    ) {
        viewModelScope.launch {
            _busy.value = "Generazione ${kind.ext.uppercase()}…"
            try {
                val file = withContext(Dispatchers.IO) {
                    val f = File(storage.exportDir, "${fileBase(title)}.${kind.ext}")
                    f.outputStream().use { write(kind, list, title, subtitle, attachments, it) }
                    f
                }
                then(file)
            } catch (e: Exception) {
                toast("Errore: ${e.message}")
            } finally {
                _busy.value = null
            }
        }
    }

    /** Scrive direttamente nella destinazione scelta dall'utente (Download, Drive, …). */
    fun exportTo(
        uri: Uri,
        kind: ExportKind,
        list: List<Expense>,
        title: String,
        subtitle: String,
        attachments: Boolean,
    ) {
        viewModelScope.launch {
            _busy.value = "Salvataggio…"
            try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "w")!!.use {
                        write(kind, list, title, subtitle, attachments, it)
                    }
                }
                toast("File salvato")
            } catch (e: Exception) {
                toast("Errore nel salvataggio: ${e.message}")
            } finally {
                _busy.value = null
            }
        }
    }

    fun saveDocumentTo(expense: Expense, uri: Uri) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { storage.copyToUri(storage.fileOf(expense), uri) } }
                .onSuccess { toast("Documento salvato") }
                .onFailure { toast("Errore nel salvataggio: ${it.message}") }
        }
    }

    private fun write(
        kind: ExportKind,
        list: List<Expense>,
        title: String,
        subtitle: String,
        attachments: Boolean,
        out: OutputStream,
    ) = when (kind) {
        ExportKind.PDF -> reports.writePdf(list, title, subtitle, attachments, out)
        ExportKind.CSV -> reports.writeCsv(list, out)
        ExportKind.ZIP -> reports.writeZip(list, title, subtitle, out)
    }

    fun fileBase(title: String) = DocumentStorage.slug(title).ifEmpty { "rendiconto" }

    fun toast(msg: String) {
        _messages.tryEmit(msg)
    }

    companion object {
        private const val MAX_OCR_PAGES = 3
        private const val OCR_RENDER_PX = 1600
    }
}
