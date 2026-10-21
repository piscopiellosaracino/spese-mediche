package it.spesemediche.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import it.spesemediche.data.Category
import it.spesemediche.data.Expense
import it.spesemediche.data.date
import java.io.File
import java.text.Normalizer
import java.time.LocalDate

/**
 * Archivio dei PDF nella memoria privata dell'app:
 *   files/documenti/<anno>/<aaaa-mm-gg>_<categoria>_<timestamp>.pdf
 */
class DocumentStorage(private val context: Context) {

    private val docsDir get() = File(context.filesDir, "documenti").apply { mkdirs() }
    private val draftsDir get() = File(docsDir, "bozze").apply { mkdirs() }
    val exportDir get() = File(context.cacheDir, "export").apply { mkdirs() }

    /** Copia un PDF (dallo scanner o importato) in una bozza temporanea. */
    fun newDraft(source: Uri): File {
        val f = File(draftsDir, "bozza_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(source)!!.use { input ->
            f.outputStream().use { input.copyTo(it) }
        }
        return f
    }

    /** Sposta la bozza nell'archivio definitivo e restituisce il percorso relativo. */
    fun archive(draft: File, date: LocalDate, category: Category): String {
        val yearDir = File(docsDir, date.year.toString()).apply { mkdirs() }
        val dest = File(yearDir, "${date}_${category.name.lowercase()}_${System.currentTimeMillis()}.pdf")
        if (!draft.renameTo(dest)) {
            draft.copyTo(dest, overwrite = true)
            draft.delete()
        }
        return dest.relativeTo(context.filesDir).path
    }

    fun fileOf(expense: Expense): File = File(context.filesDir, expense.pdfPath)

    fun delete(expense: Expense) {
        fileOf(expense).delete()
    }

    fun clearDrafts() {
        draftsDir.listFiles()?.forEach { it.delete() }
    }

    fun clearExports() {
        exportDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun copyToUri(file: File, dest: Uri) {
        context.contentResolver.openOutputStream(dest, "w")!!.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
    }

    /** Nome leggibile per il file esportato, es. 2025-03-14_farmacia-rossi_23-50.pdf */
    fun exportName(e: Expense): String {
        val cents = e.amountCents
        return "${e.date}_${slug(e.issuer).ifEmpty { e.category.name.lowercase() }}_${cents / 100}-${"%02d".format(cents % 100)}.pdf"
    }

    companion object {
        fun slug(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .take(40)
    }
}
