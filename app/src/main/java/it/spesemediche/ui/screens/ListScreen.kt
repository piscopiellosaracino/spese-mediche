package it.spesemediche.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import it.spesemediche.DATE_FMT
import it.spesemediche.ITALIAN
import it.spesemediche.MONTH_FMT
import it.spesemediche.data.Expense
import it.spesemediche.data.date
import it.spesemediche.formatEuro
import it.spesemediche.ui.AppViewModel
import it.spesemediche.ui.findActivity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListScreen(
    vm: AppViewModel,
    onOpen: (Long) -> Unit,
    onNewReady: () -> Unit,
    onReport: () -> Unit,
) {
    val context = LocalContext.current
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    var year by rememberSaveable { mutableStateOf<Int?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    val years = remember(expenses) { expenses.map { it.date.year }.distinct().sortedDescending() }
    val filtered = remember(expenses, year, query) {
        val q = query.trim().lowercase(ITALIAN)
        expenses.filter { e ->
            (year == null || e.date.year == year) &&
                (q.isEmpty() || listOf(e.issuer, e.beneficiary, e.category.label, e.notes, e.ocrText)
                    .any { it.lowercase(ITALIAN).contains(q) })
        }
    }
    val byMonth = remember(filtered) { filtered.groupBy { it.date.withDayOfMonth(1) } }

    // Scanner documenti (fotocamera, ritaglio automatico, più pagine, importazione da galleria)
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val r = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
            if (r != null) {
                vm.onScanResult(r.pdf?.uri, r.pages?.map { it.imageUri }.orEmpty(), onNewReady)
            }
        }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.onPdfImported(uri, onNewReady)
    }

    fun startScan() {
        val activity = context.findActivity() ?: return
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { vm.toast("Scanner non disponibile: ${it.message}") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spese mediche") },
                actions = {
                    IconButton(onClick = onReport) { Icon(Icons.Default.Assessment, "Rendiconto") }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = { pdfLauncher.launch(arrayOf("application/pdf")) }) {
                    Icon(Icons.Default.PictureAsPdf, "Importa PDF")
                }
                ExtendedFloatingActionButton(
                    onClick = { startScan() },
                    icon = { Icon(Icons.Default.DocumentScanner, null) },
                    text = { Text("Scansiona") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 140.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Cerca fornitore, beneficiario, testo…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (years.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { FilterChip(selected = year == null, onClick = { year = null }, label = { Text("Tutti") }) }
                        items(years) { y ->
                            FilterChip(selected = year == y, onClick = { year = y }, label = { Text("$y") })
                        }
                    }
                }
            }
            item {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (year != null) "Totale $year" else "Totale complessivo",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            formatEuro(filtered.sumOf { it.amountCents }),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("${filtered.size} documenti", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (expenses.isEmpty()) {
                item { EmptyState() }
            }
            byMonth.forEach { (month, list) ->
                stickyHeader(key = "h$month") {
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            month.format(MONTH_FMT).replaceFirstChar { it.titlecase(ITALIAN) },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(formatEuro(list.sumOf { it.amountCents }), style = MaterialTheme.typography.titleSmall)
                    }
                }
                items(list, key = { it.id }) { e -> ExpenseRow(e) { onOpen(e.id) } }
            }
        }
    }
}

@Composable
private fun ExpenseRow(e: Expense, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text("${e.date.dayOfMonth}", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.issuer, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    e.category.label + if (e.beneficiary.isNotBlank()) " · ${e.beneficiary}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(e.date.format(DATE_FMT), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Text(formatEuro(e.amountCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.DocumentScanner, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Nessuna spesa registrata", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tocca «Scansiona» per fotografare scontrino, ricevuta o fattura: " +
                "il documento viene letto, archiviato in PDF e inserito in elenco.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
