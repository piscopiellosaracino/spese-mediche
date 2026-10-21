package it.spesemediche.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.spesemediche.storage.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

sealed interface PdfPages {
    data object Loading : PdfPages
    data class Ready(val pages: List<ImageBitmap>) : PdfPages
    data object Error : PdfPages
}

/** Rende le pagine di un PDF in background (per anteprima e visualizzazione). */
@Composable
fun rememberPdfPages(file: File, widthPx: Int = 1080, maxPages: Int = 30): PdfPages {
    var state by remember(file) { mutableStateOf<PdfPages>(PdfPages.Loading) }
    LaunchedEffect(file) {
        state = withContext(Dispatchers.IO) {
            val result = runCatching { PdfUtils.renderPages(file, widthPx, maxPages).map { it.asImageBitmap() } }
            val pages = result.getOrNull()
            if (pages != null) PdfPages.Ready(pages) else PdfPages.Error
        }
    }
    return state
}

@Composable
fun PdfPageImage(page: ImageBitmap, modifier: Modifier = Modifier) {
    Image(
        bitmap = page,
        contentDescription = "Pagina del documento",
        modifier = modifier.fillMaxWidth().background(Color.White),
        contentScale = ContentScale.FillWidth,
    )
}

@Composable
fun PdfThumbnail(file: File, height: Dp = 220.dp, modifier: Modifier = Modifier) {
    val pages = rememberPdfPages(file, widthPx = 700, maxPages = 1)
    Box(
        modifier.fillMaxWidth().height(height).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when (pages) {
            PdfPages.Loading -> CircularProgressIndicator()
            PdfPages.Error -> Text("Anteprima non disponibile")
            is PdfPages.Ready -> pages.pages.firstOrNull()?.let {
                Image(it, "Anteprima documento", Modifier.fillMaxWidth().height(height), contentScale = ContentScale.Fit)
            }
        }
    }
}
