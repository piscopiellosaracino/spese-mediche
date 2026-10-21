package it.spesemediche.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.spesemediche.DATE_FMT
import it.spesemediche.data.Expense
import it.spesemediche.data.date
import it.spesemediche.formatEuro
import it.spesemediche.storage.openWithExternalApp
import it.spesemediche.storage.printPdf
import it.spesemediche.storage.shareFile
import it.spesemediche.ui.AppViewModel
import it.spesemediche.ui.PdfPageImage
import it.spesemediche.ui.PdfPages
import it.spesemediche.ui.rememberPdfPages

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: AppViewModel, id: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val expense by remember(id) { vm.observeExpense(id) }.collectAsStateWithLifecycle(initialValue = null)
    var confirmDelete by remember { mutableStateOf(false) }
    val e = expense

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && e != null) vm.saveDocumentTo(e, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dettaglio spesa") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                actions = {
                    if (e != null) {
                        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Modifica") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Elimina") }
                    }
                },
            )
        },
    ) { padding ->
        if (e == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val file = vm.storage.fileOf(e)
        val pages = rememberPdfPages(file)
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { InfoCard(e) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(Icons.Default.Share, "Condividi", Modifier.weight(1f)) {
                        shareFile(context, vm.storage, file, "application/pdf", "${e.issuer} ${e.date.format(DATE_FMT)}")
                    }
                    ActionButton(Icons.Default.Print, "Stampa", Modifier.weight(1f)) {
                        printPdf(context, file, vm.storage.exportName(e).removeSuffix(".pdf"))
                    }
                    ActionButton(Icons.Default.Download, "Scarica", Modifier.weight(1f)) {
                        saveLauncher.launch(vm.storage.exportName(e))
                    }
                }
            }
            item {
                TextButton(onClick = { openWithExternalApp(context, vm.storage, file) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                    Text("  Apri con un'altra app")
                }
            }
            when (pages) {
                PdfPages.Loading -> item { CircularProgressIndicator() }
                PdfPages.Error -> item { Text("Documento PDF non disponibile") }
                is PdfPages.Ready -> items(pages.pages) { p -> Card { PdfPageImage(p) } }
            }
        }
    }

    if (confirmDelete && e != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare la spesa?") },
            text = { Text("Verranno eliminati la registrazione e il PDF archiviato. L'operazione non è reversibile.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(e, onBack) }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun ActionButton(icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null)
            Text(text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun InfoCard(e: Expense) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(e.issuer, style = MaterialTheme.typography.titleLarge)
            Text(formatEuro(e.amountCents), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            Field("Data", e.date.format(DATE_FMT))
            Field("Categoria", e.category.label)
            Field("Pagamento", e.paymentMethod.label + if (e.paymentMethod.traceable) " (tracciabile)" else "")
            if (e.beneficiary.isNotBlank()) Field("Beneficiario", e.beneficiary)
            if (e.taxCode.isNotBlank()) Field("Codice fiscale", e.taxCode)
            if (e.vatNumber.isNotBlank()) Field("P.IVA fornitore", e.vatNumber)
            if (e.notes.isNotBlank()) Field("Note", e.notes)
        }
    }
}

@Composable
private fun Field(name: String, value: String) {
    Row {
        Text(name, Modifier.weight(0.4f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.6f), style = MaterialTheme.typography.bodyMedium)
    }
}
