package it.spesemediche.ocr

import it.spesemediche.capitalizeWords
import it.spesemediche.data.Category
import it.spesemediche.data.PaymentMethod
import it.spesemediche.parseAmountToCents
import java.time.LocalDate

/** Riga di testo riconosciuta, con la sua posizione verticale nella pagina. */
data class OcrLine(
    val text: String,
    val page: Int = 0,
    val top: Int = 0,
    val bottom: Int = 0,
    val left: Int = 0,
)

data class ParsedReceipt(
    val date: LocalDate? = null,
    val amountCents: Long? = null,
    val issuer: String? = null,
    val vatNumber: String? = null,
    val taxCode: String? = null,
    val category: Category? = null,
    val paymentMethod: PaymentMethod? = null,
)

/**
 * Estrazione euristica dei dati da scontrini, ricevute e fatture sanitarie italiane.
 * I valori trovati sono solo una proposta: l'utente li conferma nella schermata di modifica.
 */
object ReceiptParser {

    private val numericDate = Regex("""(?<!\d)(\d{1,2})\s?[/.\-]\s?(\d{1,2})\s?[/.\-]\s?(\d{4}|\d{2})(?!\d)""")
    private val textDate = Regex(
        """(?<!\d)(\d{1,2})\s+(GEN|FEB|MAR|APR|MAG|GIU|LUG|AGO|SET|OTT|NOV|DIC)[A-Z]*\.?\s+(\d{4})""",
    )
    private val months = listOf("GEN", "FEB", "MAR", "APR", "MAG", "GIU", "LUG", "AGO", "SET", "OTT", "NOV", "DIC")

    private val amount = Regex("""(?<![\d.,])(\d{1,3}(?:[.']\d{3})+|\d+)[,.](\d{2})(?![\d.,]?\d)""")

    private val taxCodeRx = Regex(
        """(?<![A-Z0-9])[A-Z]{6}[0-9LMNPQRSTUV]{2}[A-EHLMPRST][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z](?![A-Z0-9])""",
    )
    private val vatRx = Regex("""(?:P\.?\s?IVA|PARTITA\s+I\.?V\.?A\.?|P\.\s?I\.|C\.F\.\s*/\s*P\.\s?IVA)\D{0,6}(?:IT)?\s?(\d{11})(?!\d)""")

    /** In ordine di priorità: il primo che trova un importo vince. */
    private val totalKeywords = listOf(
        "TOTALE COMPLESSIVO", "TOTALE DOCUMENTO", "TOTALE FATTURA", "NETTO A PAGARE",
        "TOTALE DA PAGARE", "TOTALE A PAGARE", "IMPORTO PAGATO", "TOTALE EURO", "TOTALE EUR",
        "TOTALE", "IMPORTO TOTALE", "IMPORTO", "PAGAMENTO ELETTRONICO", "PAGAMENTO CONTANTE",
    )
    private val notTotalKeywords = listOf(
        "SUBTOTALE", "SUB TOTALE", "IMPONIBILE", "DI CUI IVA", "RESTO", "SCONTO", "ARROTONDAMENTO",
    )

    private val issuerSkip = listOf(
        "DOCUMENTO COMMERCIALE", "SCONTRINO", "FATTURA", "RICEVUTA", "DI VENDITA", "PRESTAZIONE",
        "COPIA", "DOCUMENTO", "BENVENUTI", "GRAZIE", "WWW.", "TEL", "VIA ", "PIAZZA", "CORSO ",
    )

    private val categoryKeywords: List<Pair<Category, List<String>>> = listOf(
        Category.FARMACI to listOf("FARMACIA", "FARMACO", "FARMACI", "MEDICINAL", "PARAFARMACIA", "OTC", "SOP", "TICKET FARMAC"),
        Category.TICKET to listOf("TICKET", "AZIENDA SANITARIA", "AZIENDA OSPEDALIERA", "ASL", "AUSL", "ASST", "ASP", "SSN", "SERVIZIO SANITARIO", "CUP"),
        Category.DENTISTA to listOf("ODONTO", "DENTIST", "DENTAL", "IGIENE ORALE", "ORTODON", "IMPLANT", "OTTURAZ"),
        Category.ANALISI to listOf("LABORATORIO", "ANALISI", "PRELIEVO", "DIAGNOSTIC", "RADIOLOG", "ECOGRAF", "RISONANZA", "TAC", "RX", "EMOCROMO", "ELETTROCARDIOGRAMMA"),
        Category.FISIOTERAPIA to listOf("FISIOTERAP", "RIABILITAZ", "OSTEOPAT", "LOGOPED", "TECAR", "MASSOTERAP"),
        Category.DISPOSITIVI to listOf("OTTICA", "OCCHIALI", "LENTI", "APPARECCHIO ACUSTICO", "APPARECCHI ACUSTICI", "DISPOSITIVO MEDICO", "DISPOSITIVI MEDICI", "ORTOPEDIA", "SANITARIA"),
        Category.RICOVERO to listOf("RICOVERO", "DEGENZA", "INTERVENTO CHIRURGICO", "DAY HOSPITAL", "CLINICA"),
        Category.VISITA to listOf("VISITA", "SPECIALIST", "DOTT.", "DOTT ", "DOTT.SSA", "DR.", "AMBULATORIO", "STUDIO MEDICO", "POLIAMBULATORIO", "CARDIOLOG", "DERMATOLOG", "OCULIST", "GINECOLOG", "ORTOPED", "PSICOLOG", "PSICOTERAP", "PRESTAZIONE SANITARIA"),
    )

    fun parse(lines: List<OcrLine>, today: LocalDate = LocalDate.now()): ParsedReceipt {
        val sorted = lines.sortedWith(compareBy({ it.page }, { it.top }, { it.left }))
        val upper = sorted.map { it.copy(text = it.text.uppercase()) }
        val fullText = upper.joinToString("\n") { it.text }
        return ParsedReceipt(
            date = findDate(upper, today),
            amountCents = findTotal(upper),
            issuer = findIssuer(sorted),
            vatNumber = vatRx.find(fullText)?.groupValues?.get(1),
            taxCode = taxCodeRx.findAll(fullText.replace(" ", "")).lastOrNull()?.value,
            category = findCategory(fullText),
            paymentMethod = findPayment(fullText),
        )
    }

    fun parseText(text: String, today: LocalDate = LocalDate.now()): ParsedReceipt =
        parse(text.lines().mapIndexed { i, l -> OcrLine(l, 0, i * 10, i * 10 + 8, 0) }, today)

    // --- Data -------------------------------------------------------------------------------

    private fun findDate(lines: List<OcrLine>, today: LocalDate): LocalDate? {
        fun datesIn(s: String): List<LocalDate> {
            val out = mutableListOf<LocalDate>()
            numericDate.findAll(s).forEach { m ->
                val (d, mo, y) = m.destructured
                val year = y.toInt().let { if (it < 100) 2000 + it else it }
                runCatching { LocalDate.of(year, mo.toInt(), d.toInt()) }.getOrNull()?.let(out::add)
            }
            textDate.findAll(s).forEach { m ->
                val (d, mo, y) = m.destructured
                runCatching { LocalDate.of(y.toInt(), months.indexOf(mo) + 1, d.toInt()) }.getOrNull()?.let(out::add)
            }
            return out.filter { it.year >= 2000 && !it.isAfter(today.plusDays(1)) }
        }
        // Prima le righe con "DATA", poi qualunque data plausibile
        lines.filter { "DATA" in it.text }.forEach { l ->
            datesIn(l.text).firstOrNull()?.let { return it }
            sameRow(lines, l).forEach { r -> datesIn(r.text).firstOrNull()?.let { return it } }
        }
        return lines.firstNotNullOfOrNull { datesIn(it.text).firstOrNull() }
    }

    // --- Importo ----------------------------------------------------------------------------

    private fun amountsIn(s: String): List<Long> =
        amount.findAll(s).mapNotNull { parseAmountToCents(it.value) }
            .filter { it in 1..10_000_000 }.toList()

    private fun findTotal(lines: List<OcrLine>): Long? {
        for (kw in totalKeywords) {
            for ((i, l) in lines.withIndex()) {
                if (kw !in l.text || notTotalKeywords.any { it in l.text }) continue
                // 1) stessa riga di testo
                amountsIn(l.text.substringAfter(kw)).maxOrNull()?.let { return it }
                // 2) stessa riga "visiva" (l'OCR separa spesso le colonne in blocchi diversi)
                sameRow(lines, l).flatMap { amountsIn(it.text) }.maxOrNull()?.let { return it }
                // 3) riga successiva
                lines.getOrNull(i + 1)?.let { n -> amountsIn(n.text).maxOrNull()?.let { return it } }
            }
        }
        // Fallback: l'importo più alto, escluse righe chiaramente non totali
        return lines.filterNot { l -> notTotalKeywords.any { it in l.text } }
            .flatMap { amountsIn(it.text) }
            .maxOrNull()
    }

    private fun sameRow(lines: List<OcrLine>, ref: OcrLine): List<OcrLine> {
        val h = (ref.bottom - ref.top).coerceAtLeast(1)
        return lines.filter { o ->
            o !== ref && o.page == ref.page && run {
                val overlap = minOf(o.bottom, ref.bottom) - maxOf(o.top, ref.top)
                val oh = (o.bottom - o.top).coerceAtLeast(1)
                overlap > 0.5 * minOf(h, oh)
            }
        }.sortedBy { it.left }
    }

    // --- Emittente --------------------------------------------------------------------------

    private fun findIssuer(lines: List<OcrLine>): String? =
        lines.take(8)
            .map { it.text.trim() }
            .firstOrNull { t ->
                val u = t.uppercase()
                t.count { it.isLetter() } >= 4 &&
                    t.count { it.isDigit() } < t.length / 3 &&
                    issuerSkip.none { u.startsWith(it) || u == it.trim() }
            }
            ?.take(60)
            ?.capitalizeWords()

    // --- Categoria e pagamento --------------------------------------------------------------

    private fun containsWord(text: String, kw: String): Boolean =
        if (kw.length <= 4) Regex("""(?<![A-Z])${Regex.escape(kw.trim())}(?![A-Z])""").containsMatchIn(text)
        else kw in text

    private fun findCategory(text: String): Category? =
        categoryKeywords
            .map { (cat, kws) -> cat to kws.count { containsWord(text, it) } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second } // a parità vince il primo in elenco
            ?.first

    private fun findPayment(text: String): PaymentMethod? = when {
        listOf("BONIFICO").any { it in text } -> PaymentMethod.BONIFICO
        listOf("PAGAMENTO ELETTRONICO", "CARTA", "BANCOMAT", "POS", "VISA", "MASTERCARD", "MAESTRO", "CONTACTLESS")
            .any { containsWord(text, it) } -> PaymentMethod.CARTA
        listOf("CONTANTE", "CONTANTI", "PAGAMENTO CONTANTE").any { it in text } -> PaymentMethod.CONTANTI
        else -> null
    }
}
