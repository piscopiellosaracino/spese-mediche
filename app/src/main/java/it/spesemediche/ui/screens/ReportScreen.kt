package it.spesemediche.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.spesemediche.data.date
import it.spesemediche.formatEuro
import it.spesemediche.storage.printPdf
import it.spesemediche.storage.shareFile
import it.spesemediche.ui.AppViewModel
import it.spesemediche.ui.AppViewModel.ExportKind
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val all by vm.expenses.collectAsStateWithLifecycle()
    val years = remember(all) { all.map { it.date.year }.distinct().sortedDescending() }
    val beneficiaries by vm.knownBeneficiaries.collectAsStateWithLifecycle()

    var year by rememberSaveable { mutableStateOf<Int?>(LocalDate.now().year) }
    var beneficiary by rememberSaveable { mutableStateOf<String?>(null) }
    var attachments by rememberSaveable { mutableStateOf(true) }

    // Elenco in ordine cronologico crescente, come in un rendiconto
    val selected = remember(all, year, beneficiary) {
        all.filter { (year == null || it.date.year == year) && (beneficiary == null || it.beneficiary == beneficiary) }
            .sortedWith(compareBy({ it.dateEpochDay }, { it.id }))
    }
    val title = "Rendiconto spese mediche" + (year?.let { " $it" } ?: "")
    val subtitle = buildString {
        append(if (year != null) "Periodo: 01/01/$year – 31/12/$year" else "Periodo: tutte le spese registrate")
        append(" · Beneficiario: ${beneficiary ?: "tutti"}")
        append(" · ${selected.size} documenti · Totale ${formatEuro(selected.sumOf { it.amountCents })}")
    }

    val launchers = ExportKind.entries.associateWith { kind ->
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(kind.mime)) { uri ->
            if (uri != null) vm.exportTo(uri, kind, selected, title, subtitle, attachments)
        }
    }
    fun saveAs(kind: ExportKind) {
        launchers.getValue(kind).launch("${vm.fileBase(title)}.${kind.ext}")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rendiconto") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Anno", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = year == null, onClick = { year = null }, label = { Text("Tutti") })
                (years + listOfNotNull(year)).distinct().sortedDescending().forEach { y ->
                    FilterChip(selected = year == y, onClick = { year = y }, label = { Text("$y") })
                }
            }
            if (beneficiaries.isNotEmpty()) {
                Text("Beneficiario", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = beneficiary == null, onClick = { beneficiary = null }, label = { Text("Tutti") })
                    beneficiaries.forEach { b ->
                        FilterChip(selected = beneficiary == b, onClick = { beneficiary = b }, label = { Text(b) })
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(formatEuro(selected.sumOf { it.amountCents }), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("${selected.size} documenti", style = MaterialTheme.typography.bodySmall)
                    if (selected.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    selected.groupBy { it.category }.entries
                        .sortedByDescending { en -> en.value.sumOf { it.amountCents } }
                        .forEach { (cat, list) ->
                            Row {
                                Text(cat.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(formatEuro(list.sumOf { it.amountCents }), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    val cash = selected.filter { !it.paymentMethod.traceable }
                    if (cash.isNotEmpty()) {
                        Text(
                            "Pagamenti non tracciati o non indicati: ${cash.size} (${formatEuro(cash.sumOf { it.amountCents })}). " +
                                "Verifica con il CAF / commercialista quali sono detraibili.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = attachments, onCheckedChange = { attachments = it })
                Text("Allega i documenti scansionati in coda al PDF")
            }

            val enabled = selected.isNotEmpty()
            Button(
                onClick = { vm.buildExport(ExportKind.PDF, selected, title, subtitle, attachments) { printPdf(context, it, title) } },
                enabled = enabled, modifier = Modifier.fillMaxWidth(),
            ) { ButtonContent(Icons.Default.Print, "Stampa rendiconto") }
            OutlinedButton(
                onClick = {
                    vm.buildExport(ExportKind.PDF, selected, title, subtitle, attachments) {
                        shareFile(context, vm.storage, it, "application/pdf", title)
                    }
                },
                enabled = enabled, modifier = Modifier.fillMaxWidth(),
            ) { ButtonContent(Icons.Default.Share, "Condividi PDF") }
            OutlinedButton(onClick = { saveAs(ExportKind.PDF) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                ButtonContent(Icons.Default.Download, "Scarica PDF…")
            }
            OutlinedButton(onClick = { saveAs(ExportKind.CSV) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                ButtonContent(Icons.Default.TableChart, "Esporta CSV (Excel)…")
            }
            OutlinedButton(onClick = { saveAs(ExportKind.ZIP) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                ButtonContent(Icons.Default.Archive, "Archivio ZIP (rendiconto + CSV + documenti)…")
            }
        }
    }
}

@Composable
private fun ButtonContent(icon: ImageVector, text: String) {
    Icon(icon, null)
    Text("  $text")
}
