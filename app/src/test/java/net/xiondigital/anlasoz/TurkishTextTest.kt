package net.xiondigital.anlasoz

import net.xiondigital.anlasoz.core.PracticeBuilder
import net.xiondigital.anlasoz.core.TurkishText
import net.xiondigital.anlasoz.data.model.VocabularyEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurkishTextTest {

    @Test
    fun `syllables follow turkish vowel rules`() {
        assertEquals(listOf("öz", "le", "dim"), TurkishText.syllabify("özledim"))
        assertEquals(listOf("se", "ni"), TurkishText.syllabify("seni"))
        assertEquals(listOf("an", "la", "şa", "lım"), TurkishText.syllabify("anlaşalım"))
        assertEquals(listOf("keş", "ke"), TurkishText.syllabify("keşke"))
        assertEquals(listOf("bir"), TurkishText.syllabify("bir"))
        assertEquals(emptyList<String>(), TurkishText.syllabify(""))
    }

    @Test
    fun `language guessing separates turkish and english`() {
        assertEquals("tr", TurkishText.guessLanguage("Seni çok özledim, ne yapıyorsun?"))
        assertEquals("en", TurkishText.guessLanguage("I miss you, what are you doing tonight?"))
        assertEquals("en", TurkishText.guessLanguage("I wish I could be there"))
    }

    @Test
    fun `stress note targets a real syllable`() {
        val note = TurkishText.stressNote("Seni çok özledim.")
        assertTrue(note.contains("öz"))
        assertTrue(note.contains("·"))
    }

    @Test
    fun `pronunciation notes cover turkish special letters`() {
        val notes = TurkishText.pronunciationNotes("çğıöşü")
        assertTrue(notes.any { it.first == 'ç' })
        assertTrue(notes.any { it.first == 'ğ' })
        assertTrue(notes.any { it.first == 'ı' })
        assertTrue(notes.any { it.first == 'ş' })
    }

    @Test
    fun `cloze keeps the sentence and blanks the saved word`() {
        val entry = VocabularyEntry(
            id = 1,
            turkish = "özledim",
            english = "I missed",
            example = "Seni çok özledim.",
        )
        val cloze = PracticeBuilder.clozeSentence(entry)
        assertNotNull(cloze)
        assertTrue(cloze!!.startsWith("Seni çok _____"))
        assertTrue(cloze.contains("I missed"))
    }

    @Test
    fun `cloze falls back to recall when the example does not contain the word`() {
        val entry = VocabularyEntry(
            id = 2,
            turkish = "özlemek",
            english = "to miss",
            example = "Seni çok özledim.",
        )
        assertEquals(null, PracticeBuilder.clozeSentence(entry))
        val deck = PracticeBuilder.build(listOf(entry), 5)
        assertEquals(1, deck.size)
        assertEquals(net.xiondigital.anlasoz.core.PracticeCard.Mode.RECALL, deck.first().mode)
    }
}
