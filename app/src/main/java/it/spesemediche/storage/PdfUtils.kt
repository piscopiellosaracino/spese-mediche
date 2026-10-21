package it.spesemediche.storage

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/** Rendering delle pagine PDF in bitmap tramite il PdfRenderer di sistema. */
object PdfUtils {
    // PdfRenderer (pdfium) non è thread-safe: serializziamo tutti gli accessi.
    private val lock = Any()

    fun <T> withRenderer(file: File, block: (PdfRenderer) -> T): T = synchronized(lock) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer -> block(renderer) }
        }
    }

    fun renderPage(renderer: PdfRenderer, index: Int, targetWidth: Int): Bitmap =
        renderer.openPage(index).use { page ->
            val scale = targetWidth.toFloat() / page.width
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        }

    fun renderPages(file: File, targetWidth: Int, maxPages: Int = Int.MAX_VALUE): List<Bitmap> =
        withRenderer(file) { r ->
            (0 until minOf(r.pageCount, maxPages)).map { renderPage(r, it, targetWidth) }
        }

    fun pageCount(file: File): Int = withRenderer(file) { it.pageCount }
}
