package net.xiondigital.anlasoz.core

import java.util.Locale

/**
 * Offline Turkish language utilities: language guessing, syllable segmentation,
 * stress guidance and letter-by-letter pronunciation notes.
 *
 * None of this replaces the model's analysis; it adds deterministic, offline
 * structure that stays available without a network connection.
 */
object TurkishText {

    private const val VOWELS = "aeıioöuüAEIİOÖUÜ"
    private const val SPECIAL = "çğıİöşüÇĞÖŞÜ"

    private val turkishWords = setOf(
        "ve", "bir", "bu", "şu", "ben", "sen", "biz", "siz", "onlar", "çok", "daha",
        "değil", "var", "yok", "için", "ile", "ama", "fakat", "ki", "mi", "mı", "mu", "mü",
        "ne", "nasıl", "neden", "niye", "nerede", "kim", "hangi", "evet", "hayır", "tamam",
        "canım", "hayatım", "aşkım", "işte", "hadi", "bakalım", "olsun", "keşke", "inşallah",
        "vallahi", "aynen", "neyse", "güzel", "iyi", "kötü", "sevgi", "özledim", "seviyorum",
        "gel", "git", "yap", "oldum", "biliyorum", "istiyorum", "misin", "musun", "seni", "beni",
        "bugün", "yarın", "şimdi", "sonra", "sabah", "akşam", "gece", "gün", "zaman", "her",
        "de", "da", "ki", "ya", "artık", "yine", "hep", "biraz", "çünkü", "eğer", "şey",
    )

    private val englishWords = setOf(
        "the", "and", "you", "your", "are", "is", "am", "was", "were", "have", "has", "had",
        "will", "would", "can", "could", "should", "not", "don't", "doesn't", "didn't", "i'm",
        "it's", "that's", "what", "where", "when", "why", "how", "with", "about", "this",
        "that", "these", "those", "for", "from", "but", "because", "want", "need", "miss",
        "love", "please", "thanks", "thank", "sorry", "just", "really", "very", "good", "night",
    )

    /** Cheap on-device guess used to pick a direction before the model answers. */
    fun guessLanguage(text: String): String {
        if (text.isBlank()) return "unknown"
        val tokens = text.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}]+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return "unknown"

        var turkish = 0.0
        var english = 0.0
        for (ch in text) {
            if (SPECIAL.contains(ch)) turkish += 1.4
        }
        for (token in tokens) {
            if (token in turkishWords) turkish += 2.0
            if (token in englishWords) english += 2.0
            // Turkish agglutination markers that rarely appear in English words.
            if (token.length > 4 && (
                    token.endsWith("yorum") || token.endsWith("yoruz") || token.endsWith("mış") ||
                        token.endsWith("miş") || token.endsWith("dım") || token.endsWith("dim") ||
                        token.endsWith("sın") || token.endsWith("sin") || token.endsWith("lar") ||
                        token.endsWith("ler") || token.endsWith("dan") || token.endsWith("den")
                    )
            ) {
                turkish += 1.2
            }
            // Apostrophes inside Latin words, contractions, single-letter "I".
            if (token.contains("'")) english += 1.0
            if (token == "i") english += 4.0
        }
        return when {
            turkish <= 0.0 && english <= 0.0 -> "unknown"
            turkish >= english * 1.15 -> "tr"
            english > turkish * 1.15 -> "en"
            else -> "unknown"
        }
    }

    /** Splits a Turkish word into syllables using vowel-nucleus rules. */
    fun syllabify(word: String): List<String> {
        if (word.isBlank()) return emptyList()
        val chars = word.toCharArray()
        val parts = mutableListOf<String>()
        var start = 0
        var seenVowel = false
        var i = 0
        while (i < chars.size) {
            val c = chars[i]
            if (VOWELS.contains(c)) {
                seenVowel = true
                i++
                continue
            }
            if (seenVowel) {
                val next = chars.getOrNull(i + 1)
                when {
                    next == null -> { /* trailing consonant closes the word */ }
                    VOWELS.contains(next) -> {
                        // Single consonant between vowels starts the next syllable.
                        parts += String(chars, start, i - start)
                        start = i
                        seenVowel = false
                    }
                    else -> {
                        // Consonant cluster: the first one closes this syllable.
                        parts += String(chars, start, i + 1 - start)
                        start = i + 1
                        seenVowel = false
                        i++
                        continue
                    }
                }
            }
            i++
        }
        if (start < chars.size) parts += String(chars, start, chars.size - start)
        return parts.filter { it.isNotEmpty() }
    }

    /** Hyphenated syllable view of a phrase, preserving punctuation. */
    fun syllabifyPhrase(text: String): List<Pair<String, List<String>>> =
        text.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .map { raw ->
                val core = raw.trim { !it.isLetter() }
                raw to syllabify(core)
            }

    /**
     * Stress guidance. Turkish is largely final-stress, with well-known
     * exceptions (place names, a few loanwords, and the negative suffix).
     */
    fun stressNote(text: String): String {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return ""
        val target = words.last().trim { !it.isLetter() }
        val syllables = syllabify(target)
        if (syllables.isEmpty()) return ""
        val negative = words.any {
            it.lowercase(Locale("tr", "TR")).let { w ->
                w.endsWith("ma") || w.endsWith("me") || w.contains("madı") || w.contains("medi")
            }
        }
        val stressed = when {
            negative && syllables.size >= 2 -> syllables[syllables.size - 2]
            else -> syllables.last()
        }
        val note = buildString {
            append("Stress usually falls on the last syllable of a Turkish word.")
            if (negative) append(" A negative suffix (-ma/-me) pulls the stress onto the syllable before it.")
            append(" Here: ")
            append(syllables.joinToString("·"))
            append(" — lean on \"")
            append(stressed)
            append("\".")
        }
        return note
    }

    /** Pronounces Turkish letters for an English speaker, without respelling the word. */
    fun pronunciationNotes(text: String): List<Pair<Char, String>> {
        val notes = linkedMapOf<Char, String>()
        for (ch in text) {
            when (ch) {
                'ç', 'Ç' -> notes[ch] = "ch as in \"chair\""
                'ğ', 'Ğ' -> notes[ch] = "soft g: lengthens the vowel before it, almost silent between vowels"
                'ı' -> notes[ch] = "undotted i: the unrounded \"uh\" of \"sofa\""
                'İ' -> notes[ch] = "dotted i: \"ee\" as in \"machine\""
                'ö', 'Ö' -> notes[ch] = "rounded front vowel, like German \"ö\" or \"u\" in \"fur\" with rounded lips"
                'ş', 'Ş' -> notes[ch] = "sh as in \"shoe\""
                'ü', 'Ü' -> notes[ch] = "rounded front vowel, like German \"ü\" or \"ew\" with rounded lips"
                'c', 'C' -> notes[ch] = "j as in \"jam\""
                'j', 'J' -> notes[ch] = "zh as in \"vision\""
                'y', 'Y' -> notes[ch] = "y as in \"yes\"; after a vowel it glides"
                'r', 'R' -> notes[ch] = "a light tapped r, never the heavy English r"
            }
        }
        return notes.entries.take(8).map { it.key to it.value }
    }

    /** Keeps Turkish orthography intact for the TTS engine. */
    fun normalizeForSpeech(text: String): String =
        text.replace(Regex("[*_`#>]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Splits text into speakable sentences, preserving order. */
    fun sentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?…:])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun wordCount(text: String): Int =
        text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
}
