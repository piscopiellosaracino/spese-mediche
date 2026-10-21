package it.spesemediche.ocr

import it.spesemediche.data.Category
import it.spesemediche.data.PaymentMethod
import it.spesemediche.parseAmountToCents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReceiptParserTest {
    private val today = LocalDate.of(2026, 10, 21)

    @Test
    fun scontrinoFarmacia() {
        val text = """
            FARMACIA ROSSI DR. MARIO
            Via Roma 12 - 70100 Bari
            P.IVA 01234567890
            DOCUMENTO COMMERCIALE
            di vendita o prestazione
            FARMACO 10% 12,50
            OTC 10% 8,90
            TOTALE COMPLESSIVO 21,40
            di cui IVA 1,95
            PAGAMENTO ELETTRONICO 21,40
            C.F. RSSMRA80A01A662X
            14-03-2025 10:32
        """.trimIndent()
        val r = ReceiptParser.parseText(text, today)
        assertEquals(LocalDate.of(2025, 3, 14), r.date)
        assertEquals(2140L, r.amountCents)
        assertEquals("Farmacia Rossi Dr. Mario", r.issuer)
        assertEquals("01234567890", r.vatNumber)
        assertEquals("RSSMRA80A01A662X", r.taxCode)
        assertEquals(Category.FARMACI, r.category)
        assertEquals(PaymentMethod.CARTA, r.paymentMethod)
    }

    @Test
    fun totaleSuColonnaSeparata() {
        // L'OCR restituisce etichetta e importo come righe distinte ma alla stessa altezza
        val lines = listOf(
            OcrLine("STUDIO DENTISTICO BIANCHI", 0, 10, 30, 10),
            OcrLine("Fattura n. 45 del 03/02/2026", 0, 50, 70, 10),
            OcrLine("Igiene orale", 0, 100, 120, 10),
            OcrLine("80,00", 0, 100, 120, 400),
            OcrLine("Totale", 0, 200, 220, 10),
            OcrLine("1.250,00", 0, 202, 221, 400),
            OcrLine("Pagamento: bonifico", 0, 260, 280, 10),
        )
        val r = ReceiptParser.parse(lines, today)
        assertEquals(125000L, r.amountCents)
        assertEquals(LocalDate.of(2026, 2, 3), r.date)
        assertEquals(Category.DENTISTA, r.category)
        assertEquals(PaymentMethod.BONIFICO, r.paymentMethod)
    }

    @Test
    fun ticketDataTestuale() {
        val text = """
            AZIENDA SANITARIA LOCALE BA
            Ricevuta pagamento ticket
            Data: 5 giugno 2025
            Importo pagato EUR 36,15
            Contanti
        """.trimIndent()
        val r = ReceiptParser.parseText(text, today)
        assertEquals(LocalDate.of(2025, 6, 5), r.date)
        assertEquals(3615L, r.amountCents)
        assertEquals(Category.TICKET, r.category)
        assertEquals(PaymentMethod.CONTANTI, r.paymentMethod)
    }

    @Test
    fun dataFuturaIgnorata() {
        val r = ReceiptParser.parseText("Scadenza 31/12/2030\nEmesso 02/01/2026\nTotale 10,00", today)
        assertEquals(LocalDate.of(2026, 1, 2), r.date)
    }

    @Test
    fun testoVuoto() {
        val r = ReceiptParser.parse(emptyList(), today)
        assertNull(r.date)
        assertNull(r.amountCents)
        assertNull(r.issuer)
    }

    @Test
    fun importi() {
        assertEquals(123456L, parseAmountToCents("1.234,56"))
        assertEquals(1250L, parseAmountToCents("12.50"))
        assertEquals(1250L, parseAmountToCents("12,5"))
        assertEquals(1200L, parseAmountToCents("€ 12"))
        assertEquals(123400L, parseAmountToCents("1.234"))
        assertNull(parseAmountToCents(""))
        assertNull(parseAmountToCents("abc"))
    }
}
