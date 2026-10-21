package it.spesemediche.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.spesemediche.DATE_FMT
import it.spesemediche.data.Category
import it.spesemediche.data.PaymentMethod
import it.spesemediche.ui.AppViewModel
import it.spesemediche.ui.ExpenseForm
import it.spesemediche.ui.PdfPageImage
import it.spesemediche.ui.PdfPages
import it.spesemediche.ui.PdfThumbnail
import it.spesemediche.ui.rememberPdfPages
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditScreen(vm: AppViewModel, onSaved: (Long) -> Unit, onClose: () -> Unit) {
    val formState by vm.form.collectAsStateWithLifecycle()
    // Solo all'ingresso: se non c'è un modulo aperto (es. app ricreata) si torna indietro.
    // Non deve scattare quando il modulo si chiude dopo il salvataggio.
    LaunchedEffect(Unit) { if (vm.form.value == null) onClose() }
    val f = formState ?: return
    val beneficiaries by vm.knownBeneficiaries.collectAsStateWithLifecycle()
    var showDate by remember { mutableStateOf(false) }
    var showDoc by remember { mutableStateOf(false) }
    var showOcr by remember { mutableStateOf(false) }

    fun cancel() {
        vm.discardForm()
        onClose()
    }
    fun update(t: (ExpenseForm) -> ExpenseForm) = vm.updateForm(t)

    BackHandler { cancel() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (f.isDraft) "Nuova spesa" else "Modifica spesa") },
                navigationIcon = { IconButton(onClick = { cancel() }) { Icon(Icons.Default.Close, "Annulla") } },
                actions = { TextButton(onClick = { vm.saveForm(onSaved) }) { Text("SALVA") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(onClick = { showDoc = true }) {
                PdfThumbnail(f.pdfFile)
            }

            if (f.autoFilled.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Text(
                        "I campi contrassegnati con ✦ sono stati letti automaticamente dal documento: verificali prima di salvare.",
                        Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            OutlinedTextField(
                value = f.date.format(DATE_FMT),
                onValueChange = {},
                readOnly = true,
                label = { Text(label("Data documento", "date" in f.autoFilled)) },
                trailingIcon = { IconButton(onClick = { showDate = true }) { Icon(Icons.Default.CalendarMonth, "Scegli data") } },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = f.amountText,
                onValueChange = { v -> update { it.copy(amountText = v.filter { c -> c.isDigit() || c == ',' || c == '.' }) } },
                label = { Text(label("Importo (€)", "amount" in f.autoFilled)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = f.issuer,
                onValueChange = { v -> update { it.copy(issuer = v) } },
                label = { Text(label("Fornitore / emittente", "issuer" in f.autoFilled)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            EnumDropdown(
                label = label("Categoria", "category" in f.autoFilled),
                options = Category.entries,
                selected = f.category,
                text = { it.label },
                onSelect = { c -> update { it.copy(category = c) } },
            )
            EnumDropdown(
                label = label("Modalità di pagamento", "payment" in f.autoFilled),
                options = PaymentMethod.entries,
                selected = f.paymentMethod,
                text = { it.label },
                onSelect = { p -> update { it.copy(paymentMethod = p) } },
            )
            OutlinedTextField(
                value = f.beneficiary,
                onValueChange = { v -> update { it.copy(beneficiary = v) } },
                label = { Text("Beneficiario (familiare)") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (beneficiaries.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    beneficiaries.forEach { b ->
                        AssistChip(onClick = { update { it.copy(beneficiary = b) } }, label = { Text(b) })
                    }
                }
            }
            OutlinedTextField(
                value = f.taxCode,
                onValueChange = { v -> update { it.copy(taxCode = v.uppercase().take(16)) } },
                label = { Text(label("Codice fiscale beneficiario", "taxCode" in f.autoFilled)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = f.vatNumber,
                onValueChange = { v -> update { it.copy(vatNumber = v.filter(Char::isDigit).take(11)) } },
                label = { Text(label("P.IVA fornitore", "vat" in f.autoFilled)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = f.notes,
                onValueChange = { v -> update { it.copy(notes = v) } },
                label = { Text("Note") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            if (f.ocrText.isNotBlank()) {
                TextButton(onClick = { showOcr = !showOcr }) {
                    Icon(Icons.Default.AutoAwesome, null)
                    Text(if (showOcr) "  Nascondi testo riconosciuto" else "  Mostra testo riconosciuto")
                }
                if (showOcr) {
                    Card {
                        Text(
                            f.ocrText,
                            Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = f.date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        update { it.copy(date = d) }
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Annulla") } },
        ) {
            DatePicker(state)
        }
    }

    if (showDoc) DocumentDialog(f.pdfFile) { showDoc = false }
}

private fun label(text: String, auto: Boolean) = if (auto) "$text ✦" else text

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    options: List<T>,
    selected: T,
    text: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = text(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(text(o)) }, onClick = { onSelect(o); expanded = false })
            }
        }
    }
}

/** Visualizzazione a schermo intero del PDF. */
@Composable
fun DocumentDialog(file: File, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxSize().padding(8.dp).clickable(onClick = onDismiss)) {
            when (val pages = rememberPdfPages(file)) {
                PdfPages.Loading -> Text("Caricamento…", Modifier.padding(24.dp))
                PdfPages.Error -> Text("Impossibile aprire il documento", Modifier.padding(24.dp))
                is PdfPages.Ready -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pages.pages) { PdfPageImage(it) }
                }
            }
        }
    }
}
