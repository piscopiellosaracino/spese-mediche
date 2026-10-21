package it.spesemediche.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import it.spesemediche.DATE_FMT
import it.spesemediche.data.Expense
import it.spesemediche.data.date
import it.spesemediche.formatAmountPlain
import it.spesemediche.formatEuro
import it.spesemediche.storage.DocumentStorage
import it.spesemediche.storage.PdfUtils
import java.io.File
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Genera il rendiconto in PDF (A4): elenco cronologico, riepiloghi e, opzionalmente,
 * i documenti scansionati in coda come allegati numerati.
 */
class ReportGenerator(private val storage: DocumentStorage) {

    private data class Col(val title: String, val width: Float, val right: Boolean = false)

    private val cols = listOf(
        Col("N.", 22f),
        Col("Data", 56f),
        Col("Fornitore / emittente", 134f),
        Col("Categoria", 95f),
        Col("Beneficiario", 85f),
        Col("Pagamento", 61f),
        Col("Importo", 70f, right = true),
    )

    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK
    }
    private val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = Color.DKGRAY }
    private val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; color = Color.BLACK }
    private val bold = TextPaint(body).apply { typeface = Typeface.DEFAULT_BOLD }
    private val section = TextPaint(bold).apply { textSize = 11f }
    private val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 7.5f; color = Color.GRAY }
    private val headerFill = Paint().apply { color = Color.rgb(0xE0, 0xF2, 0xF1) }
    private val stripeFill = Paint().apply { color = Color.rgb(0xF5, 0xF5, 0xF5) }
    private val line = Paint().apply { color = Color.rgb(0x00, 0x79, 0x6B); strokeWidth = 0.8f }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val border = Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 0.5f }

    private inner class Writer(val doc: PdfDocument, val footerText: String) {
        private var page: PdfDocument.Page? = null
        private var number = 0
        var y = 0f
        val canvas: Canvas get() = page!!.canvas

        fun newPage() {
            finish()
            number++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, number).create())
            y = MARGIN
            canvas.drawText(footerText, MARGIN, H - 18f, footer)
            val p = "Pag. $number"
            canvas.drawText(p, W - MARGIN - footer.measureText(p), H - 18f, footer)
        }

        fun fits(h: Float) = page != null && y + h <= H - BOTTOM

        fun finish() {
            page?.let { doc.finishPage(it) }
            page = null
        }
    }

    fun writePdf(
        expenses: List<Expense>,
        title: String,
        subtitle: String,
        includeAttachments: Boolean,
        out: OutputStream,
    ) {
        val doc = PdfDocument()
        val generated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
        val w = Writer(doc, "$title — generato il $generated")
        try {
            w.newPage()
            w.canvas.drawText(title, MARGIN, w.y + 14f, titlePaint)
            w.y += 22f
            w.canvas.drawText(subtitle, MARGIN, w.y + 9f, subPaint)
            w.y += 14f
            w.canvas.drawLine(MARGIN, w.y, W - MARGIN, w.y, line)
            w.y += 10f

            // --- Elenco cronologico
            drawHeaderRow(w)
            expenses.forEachIndexed { i, e ->
                if (!w.fits(ROW)) {
                    w.newPage(); drawHeaderRow(w)
                }
                drawRow(
                    w,
                    listOf(
                        "${i + 1}", e.date.format(DATE_FMT), e.issuer, e.category.label,
                        e.beneficiary, e.paymentMethod.label, formatEuro(e.amountCents),
                    ),
                    body, if (i % 2 == 1) stripeFill else null,
                )
            }
            if (!w.fits(ROW + 4)) w.newPage()
            w.canvas.drawLine(MARGIN, w.y, W - MARGIN, w.y, line)
            drawRow(w, listOf("", "", "TOTALE (${expenses.size} documenti)", "", "", "", formatEuro(expenses.sumOf { it.amountCents })), bold, null)
            w.y += 16f

            // --- Riepiloghi
            summaryTable(w, "Riepilogo per categoria", expenses.groupBy { it.category.label })
            summaryTable(w, "Riepilogo per beneficiario", expenses.groupBy { it.beneficiary.ifBlank { "Non indicato" } })
            summaryTable(w, "Riepilogo per modalità di pagamento", expenses.groupBy { it.paymentMethod.label })

            // --- Allegati
            if (includeAttachments) {
                expenses.forEachIndexed { i, e -> drawAttachment(w, i + 1, e) }
            }
            w.finish()
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private fun drawHeaderRow(w: Writer) {
        drawRow(w, cols.map { it.title }, bold, headerFill)
    }

    private fun drawRow(w: Writer, values: List<String>, paint: TextPaint, fill: Paint?) {
        val top = w.y
        fill?.let { w.canvas.drawRect(MARGIN, top, W - MARGIN, top + ROW, it) }
        var x = MARGIN
        cols.forEachIndexed { i, c ->
            val txt = TextUtils.ellipsize(values[i], paint, c.width - 6f, TextUtils.TruncateAt.END).toString()
            val tx = if (c.right) x + c.width - 3f - paint.measureText(txt) else x + 3f
            w.canvas.drawText(txt, tx, top + ROW - 4f, paint)
            x += c.width
        }
        w.y += ROW
    }

    private fun summaryTable(w: Writer, heading: String, groups: Map<String, List<Expense>>) {
        if (groups.isEmpty()) return
        if (!w.fits(ROW * 3 + 20f)) w.newPage()
        w.canvas.drawText(heading, MARGIN, w.y + 11f, section)
        w.y += 18f
        groups.entries.sortedByDescending { e -> e.value.sumOf { it.amountCents } }.forEachIndexed { i, (name, list) ->
            if (!w.fits(ROW)) w.newPage()
            val top = w.y
            if (i % 2 == 1) w.canvas.drawRect(MARGIN, top, MARGIN + 350f, top + ROW, stripeFill)
            w.canvas.drawText(TextUtils.ellipsize(name, body, 220f, TextUtils.TruncateAt.END).toString(), MARGIN + 3f, top + ROW - 4f, body)
            val n = "${list.size} doc."
            w.canvas.drawText(n, MARGIN + 260f - body.measureText(n), top + ROW - 4f, body)
            val tot = formatEuro(list.sumOf { it.amountCents })
            w.canvas.drawText(tot, MARGIN + 347f - bold.measureText(tot), top + ROW - 4f, bold)
            w.y += ROW
        }
        w.y += 14f
    }

    private fun drawAttachment(w: Writer, n: Int, e: Expense) {
        val label = "Allegato n. $n — ${e.date.format(DATE_FMT)} — ${e.issuer} — ${formatEuro(e.amountCents)}"
        val file = storage.fileOf(e)
        val ok = file.exists() && runCatching {
            PdfUtils.withRenderer(file) { r ->
                for (p in 0 until r.pageCount) {
                    val bmp = PdfUtils.renderPage(r, p, ATTACHMENT_PX)
                    try {
                        w.newPage()
                        val suffix = if (r.pageCount > 1) "  (pag. ${p + 1}/${r.pageCount})" else ""
                        w.canvas.drawText(
                            TextUtils.ellipsize(label + suffix, bold, W - 2 * MARGIN, TextUtils.TruncateAt.END).toString(),
                            MARGIN, w.y + 10f, bold,
                        )
                        w.y += 18f
                        val areaW = W - 2 * MARGIN
                        val areaH = H - BOTTOM - w.y
                        val scale = minOf(areaW / bmp.width, areaH / bmp.height)
                        val dw = bmp.width * scale
                        val dh = bmp.height * scale
                        val left = MARGIN + (areaW - dw) / 2
                        val dst = RectF(left, w.y, left + dw, w.y + dh)
                        w.canvas.drawBitmap(bmp, null, dst, bitmapPaint)
                        w.canvas.drawRect(dst, border)
                    } finally {
                        bmp.recycle()
                    }
                }
            }
        }.isSuccess
        if (!ok) {
            w.newPage()
            w.canvas.drawText(label, MARGIN, w.y + 10f, bold)
            w.canvas.drawText("Documento non disponibile o non leggibile.", MARGIN, w.y + 30f, body)
        }
    }

    // --- CSV e archivio ZIP -------------------------------------------------------------------

    fun writeCsv(expenses: List<Expense>, out: OutputStream) {
        val sb = StringBuilder("﻿") // BOM: Excel riconosce l'UTF-8
        sb.append(
            listOf("N.", "Data", "Fornitore", "P.IVA", "Categoria", "Beneficiario", "Codice fiscale",
                "Pagamento", "Tracciabile", "Importo (EUR)", "Note", "File").joinToString(";"),
        ).append("\r\n")
        expenses.forEachIndexed { i, e ->
            sb.append(
                listOf(
                    "${i + 1}", e.date.format(DATE_FMT), e.issuer, e.vatNumber, e.category.label,
                    e.beneficiary, e.taxCode, e.paymentMethod.label,
                    if (e.paymentMethod.traceable) "sì" else "no",
                    formatAmountPlain(e.amountCents), e.notes, storage.exportName(e),
                ).joinToString(";") { csv(it) },
            ).append("\r\n")
        }
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
    }

    private fun csv(v: String): String =
        if (v.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    /** ZIP con rendiconto.pdf, spese.csv e tutti i documenti originali. */
    fun writeZip(expenses: List<Expense>, title: String, subtitle: String, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("rendiconto.pdf"))
            writePdf(expenses, title, subtitle, includeAttachments = false, out = NonClosing(zip))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("spese.csv"))
            writeCsv(expenses, zip)
            zip.closeEntry()
            val used = HashSet<String>()
            expenses.forEachIndexed { i, e ->
                val f: File = storage.fileOf(e)
                if (!f.exists()) return@forEachIndexed
                var name = "documenti/%03d_%s".format(i + 1, storage.exportName(e))
                if (!used.add(name)) name = name.removeSuffix(".pdf") + "_${e.id}.pdf"
                zip.putNextEntry(ZipEntry(name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private class NonClosing(private val o: OutputStream) : OutputStream() {
        override fun write(b: Int) = o.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = o.write(b, off, len)
        override fun flush() = o.flush()
        override fun close() = o.flush()
    }

    companion object {
        private const val W = 595 // A4 in punti
        private const val H = 842
        private const val MARGIN = 36f
        private const val BOTTOM = 40f
        private const val ROW = 14f
        private const val ATTACHMENT_PX = 1100
    }
}
