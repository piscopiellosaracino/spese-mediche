package it.spesemediche

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

val ITALIAN: Locale = Locale.ITALY
val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", ITALIAN)
val MONTH_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", ITALIAN)

fun formatEuro(cents: Long): String =
    NumberFormat.getCurrencyInstance(ITALIAN).format(BigDecimal.valueOf(cents, 2))

/** Importo senza simbolo, con virgola decimale (per campi di testo e CSV). */
fun formatAmountPlain(cents: Long): String =
    String.format(ITALIAN, "%.2f", BigDecimal.valueOf(cents, 2))

/**
 * Interpreta "1.234,56", "1234,56", "12.50", "€ 12,5". Restituisce i centesimi o null.
 */
fun parseAmountToCents(text: String): Long? {
    var s = text.replace("€", "").replace("EUR", "", ignoreCase = true).replace(" ", "").trim()
    if (s.isEmpty()) return null
    s = if (s.contains(',')) {
        s.replace(".", "").replace("'", "").replace(',', '.')
    } else if (Regex("""^\d+\.\d{1,2}$""").matches(s)) {
        s
    } else {
        s.replace(".", "").replace("'", "")
    }
    return s.toBigDecimalOrNull()
        ?.setScale(2, RoundingMode.HALF_UP)
        ?.movePointRight(2)
        ?.toLong()
}

fun String.capitalizeWords(): String =
    lowercase(ITALIAN).split(' ').joinToString(" ") { w ->
        w.replaceFirstChar { it.titlecase(ITALIAN) }
    }
