package it.spesemediche.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** OCR on-device (nessun dato inviato in rete). */
class OcrEngine(private val context: Context) {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun fromImageUris(uris: List<Uri>): List<OcrLine> =
        uris.flatMapIndexed { page, uri -> recognize(InputImage.fromFilePath(context, uri), page) }

    suspend fun fromBitmaps(bitmaps: List<Bitmap>): List<OcrLine> =
        bitmaps.flatMapIndexed { page, bmp -> recognize(InputImage.fromBitmap(bmp, 0), page) }

    private suspend fun recognize(image: InputImage, page: Int): List<OcrLine> {
        val result = recognizer.process(image).await()
        return result.textBlocks.flatMap { block ->
            block.lines.map { line ->
                val box = line.boundingBox
                OcrLine(
                    text = line.text,
                    page = page,
                    top = box?.top ?: 0,
                    bottom = box?.bottom ?: 0,
                    left = box?.left ?: 0,
                )
            }
        }
    }
}
