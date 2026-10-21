package it.spesemediche.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class Category(val label: String) {
    FARMACI("Farmaci e medicinali"),
    VISITA("Visita medica / specialistica"),
    ANALISI("Analisi ed esami diagnostici"),
    DENTISTA("Cure dentistiche"),
    TICKET("Ticket SSN"),
    DISPOSITIVI("Dispositivi medici / ottica"),
    FISIOTERAPIA("Fisioterapia e riabilitazione"),
    RICOVERO("Ricovero / intervento"),
    ALTRO("Altro"),
}

enum class PaymentMethod(val label: String, val traceable: Boolean) {
    NON_INDICATO("Non indicato", false),
    CONTANTI("Contanti", false),
    CARTA("Carta / Bancomat", true),
    BONIFICO("Bonifico", true),
    ALTRO_TRACCIABILE("Altro tracciabile", true),
}

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val amountCents: Long,
    val issuer: String,
    val vatNumber: String,
    val taxCode: String,
    val beneficiary: String,
    val category: Category,
    val paymentMethod: PaymentMethod,
    val notes: String,
    /** Percorso del PDF relativo a filesDir. */
    val pdfPath: String,
    val ocrText: String,
    val createdAt: Long,
)

val Expense.date: LocalDate get() = LocalDate.ofEpochDay(dateEpochDay)
