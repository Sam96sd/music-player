package com.example.domain

/**
 * Generates Arabic-script candidates from Arabizi / Latin-transliterated Arabic titles and artists,
 * and provides phonetic skeleton helpers for cross-script verification.
 */
object ArabiziTransliterator {

    private val latinRegex = Regex("[a-zA-Z]")
    private val arabicRegex = Regex("[\\u0600-\\u06FF]")

    fun containsLatinLetters(text: String): Boolean = latinRegex.containsMatchIn(text)

    fun containsArabicLetters(text: String): Boolean = arabicRegex.containsMatchIn(text)

    private val commonWordReplacements: Map<String, List<String>> = mapOf(
        "ala" to listOf("على", "علا", "الا"),
        "3ala" to listOf("على", "علا"),
        "3la" to listOf("على", "علا"),
        "ela" to listOf("الى", "علا"),
        "ila" to listOf("الى"),
        "fi" to listOf("في"),
        "fe" to listOf("في"),
        "f" to listOf("في"),
        "men" to listOf("من"),
        "min" to listOf("من"),
        "mn" to listOf("من"),
        "ya" to listOf("يا"),
        "ana" to listOf("انا"),
        "enta" to listOf("انت"),
        "inta" to listOf("انت"),
        "enti" to listOf("انتي"),
        "inty" to listOf("انتي"),
        "ma" to listOf("ما"),
        "la" to listOf("لا"),
        "w" to listOf("و"),
        "wa" to listOf("و"),
        "we" to listOf("و"),
        "el" to listOf("ال"),
        "al" to listOf("ال"),
        "bn" to listOf("بن"),
        "bin" to listOf("بن"),
        "abu" to listOf("ابو"),
        "abo" to listOf("ابو"),
        "om" to listOf("ام"),
        "umm" to listOf("ام"),
        "habibi" to listOf("حبيبي"),
        "7abibi" to listOf("حبيبي"),
        "habiby" to listOf("حبيبي"),
        "7abiby" to listOf("حبيبي"),
        "galbi" to listOf("قلبي"),
        "albi" to listOf("قلبي"),
        "2albi" to listOf("قلبي"),
        "qalbi" to listOf("قلبي"),
        "roh" to listOf("روح"),
        "ro7" to listOf("روح"),
        "rouh" to listOf("روح"),
        "rou7" to listOf("روح"),
        "amr" to listOf("عمرو", "امر"),
        "diab" to listOf("دياب"),
        "sherine" to listOf("شيرين"),
        "shereen" to listOf("شيرين"),
        "tamer" to listOf("تامر"),
        "hosny" to listOf("حسني"),
        "hamaki" to listOf("حماقي"),
        "mohamed" to listOf("محمد"),
        "muhammad" to listOf("محمد"),
        "ahmed" to listOf("احمد"),
        "elissa" to listOf("اليسا"),
        "nancy" to listOf("نانسي"),
        "ajram" to listOf("عجرم"),
        "fairouz" to listOf("فيروز"),
        "fairuz" to listOf("فيروز"),
        "fayrouz" to listOf("فيروز"),
        "cairokee" to listOf("كايروكي"),
        "wegz" to listOf("ويجز"),
        "assala" to listOf("اصاله"),
        "asala" to listOf("اصاله"),
        "kadim" to listOf("كاظم"),
        "kazem" to listOf("كاظم"),
        "hussain" to listOf("حسين"),
        "hussein" to listOf("حسين"),
        "nawal" to listOf("نوال"),
        "wael" to listOf("وائل"),
        "ragheb" to listOf("راغب"),
        "alsema" to listOf("السيما", "السما", "السمه"),
        "alseema" to listOf("السيما"),
        "elsema" to listOf("السيما", "السما"),
        "alsama" to listOf("السما", "السماء"),
        "bab" to listOf("باب")
    )

    /**
     * Generates up to 4 distinct Arabic-script search candidates for an Arabizi / Latin phrase.
     * Example: "ala bab alsema" -> ["على باب السيما", "على باب السما", "علا باب السيما", ...]
     */
    fun generateArabicCandidates(latinInput: String): List<String> {
        val cleaned = latinInput.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9'\\s-]"), " ")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isEmpty() || !containsLatinLetters(cleaned)) return emptyList()

        val tokens = cleaned.split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()

        val perTokenOptions: List<List<String>> = tokens.map { token ->
            transliterateWordOptions(token)
        }

        // Cartesian combine limited to top variants to produce up to 4 strong candidates
        var combinations = listOf("")
        for (options in perTokenOptions) {
            val next = mutableListOf<String>()
            for (prefix in combinations) {
                for (opt in options.take(2)) {
                    val joined = if (prefix.isEmpty()) opt else "$prefix $opt"
                    next.add(joined)
                    if (next.size >= 8) break
                }
                if (next.size >= 8) break
            }
            combinations = next
        }

        // Also build a secondary variant using second choices where available
        val altPrimary = perTokenOptions.joinToString(" ") { it.lastOrNull() ?: it.first() }
        return (combinations + altPrimary)
            .map { it.trim() }
            .filter { it.isNotBlank() && containsArabicLetters(it) }
            .distinct()
            .take(4)
    }

    private fun transliterateWordOptions(rawWord: String): List<String> {
        commonWordReplacements[rawWord]?.let { return it }

        // Check definite article prefix ("al" or "el") attached to word >= 4 chars
        if ((rawWord.startsWith("al") || rawWord.startsWith("el")) && rawWord.length >= 4) {
            val stem = rawWord.substring(2)
            val stemMapped = commonWordReplacements[stem]
            if (stemMapped != null) {
                return stemMapped.map { if (it.startsWith("ال")) it else "ال$it" }
            }
            val primaryStem = transliterateStem(stem, includeMedialLongVowels = true)
            val shortStem = transliterateStem(stem, includeMedialLongVowels = false)
            return listOf("ال$primaryStem", "ال$shortStem").distinct()
        }

        val primary = transliterateStem(rawWord, includeMedialLongVowels = true)
        val compact = transliterateStem(rawWord, includeMedialLongVowels = false)
        return listOf(primary, compact).distinct()
    }

    private fun transliterateStem(word: String, includeMedialLongVowels: Boolean): String {
        val sb = StringBuilder()
        var i = 0
        while (i < word.length) {
            val remaining = word.substring(i)
            val atStart = i == 0
            val atEnd = i == word.lastIndex

            // Handle multi-char digraphs first
            when {
                remaining.startsWith("3'") -> { sb.append('غ'); i += 2; continue }
                remaining.startsWith("6'") -> { sb.append('ظ'); i += 2; continue }
                remaining.startsWith("9'") -> { sb.append('ض'); i += 2; continue }
                remaining.startsWith("kh") -> { sb.append('خ'); i += 2; continue }
                remaining.startsWith("gh") -> { sb.append('غ'); i += 2; continue }
                remaining.startsWith("sh") || remaining.startsWith("ch") -> { sb.append('ش'); i += 2; continue }
                remaining.startsWith("th") -> { sb.append('ث'); i += 2; continue }
                remaining.startsWith("dh") -> { sb.append('ذ'); i += 2; continue }
                remaining.startsWith("ou") || remaining.startsWith("oo") -> { sb.append('و'); i += 2; continue }
                remaining.startsWith("ee") || remaining.startsWith("ei") || remaining.startsWith("ie") -> {
                    sb.append('ي'); i += 2; continue
                }
                remaining.startsWith("aa") -> { sb.append('ا'); i += 2; continue }
                remaining.startsWith("ah") && i + 2 == word.length -> { sb.append('ه'); i += 2; continue }
            }

            val ch = word[i]
            when (ch) {
                '2' -> sb.append(if (atStart) 'ا' else 'ء')
                '3' -> sb.append('ع')
                '5' -> sb.append('خ')
                '6' -> sb.append('ط')
                '7' -> sb.append('ح')
                '8' -> sb.append('ق')
                '9' -> sb.append('ص')
                'b', 'p' -> sb.append('ب')
                't' -> sb.append('ت')
                'j', 'g' -> sb.append('ج')
                'h' -> sb.append('ه')
                'd' -> sb.append('د')
                'r' -> sb.append('ر')
                'z' -> sb.append('ز')
                's', 'c' -> sb.append('س')
                'f', 'v' -> sb.append('ف')
                'q' -> sb.append('ق')
                'k' -> sb.append('ك')
                'l' -> sb.append('ل')
                'm' -> sb.append('م')
                'n' -> sb.append('ن')
                'w' -> sb.append('و')
                'y' -> sb.append('ي')
                'a' -> {
                    if (atStart || atEnd || includeMedialLongVowels) {
                        sb.append('ا')
                    }
                }
                'e', 'i' -> {
                    if (atStart) sb.append('ا')
                    else if (atEnd || includeMedialLongVowels) sb.append('ي')
                }
                'o', 'u' -> {
                    if (atStart) sb.append('ا')
                    else if (atEnd || includeMedialLongVowels) sb.append('و')
                }
                '\'' -> { /* skip glottal separator */ }
                else -> if (ch.isDigit()) sb.append(ch)
            }
            i++
        }
        return sb.toString()
    }

    /**
     * Converts any Arabic OR Latin/Arabizi string into a comparable phonetic skeleton
     * (lowercase ASCII consonant+semi-vowel representation) so cross-script matching works reliably.
     * Example:
     *  - "على باب السيما" -> "3lbblsm" / normalized Latin skeleton
     *  - "ala bab alsema" -> "lbblsm" -> matching core consonants & vowels
     */
    fun toCrossScriptSkeleton(text: String): String {
        val cleaned = text.trim().lowercase()
        val mapped = StringBuilder()
        var i = 0
        while (i < cleaned.length) {
            val rem = cleaned.substring(i)
            when {
                rem.startsWith("3'") || rem.startsWith("gh") -> { mapped.append('g'); i += 2; continue }
                rem.startsWith("kh") -> { mapped.append('x'); i += 2; continue }
                rem.startsWith("sh") || rem.startsWith("ch") -> { mapped.append('$'); i += 2; continue }
                rem.startsWith("th") -> { mapped.append('t'); i += 2; continue }
                rem.startsWith("dh") -> { mapped.append('d'); i += 2; continue }
            }
            when (val c = cleaned[i]) {
                // Arabic diacritics & tatweel ignored
                in '\u064B'..'\u065F', '\u0670', '\u0640' -> Unit
                // Alef / ayn / hamza / initial vowels collapsed lightly
                'ا', 'أ', 'إ', 'آ', 'ٱ', 'ء', 'ع', '3', '2', 'a' -> mapped.append('a')
                'ب', 'b', 'p' -> mapped.append('b')
                'ت', 'ط', 'ث', '6', 't' -> mapped.append('t')
                'ج', 'g', 'j' -> mapped.append('j')
                'ح', 'ه', 'ة', '7', 'h' -> mapped.append('h')
                'خ', '5' -> mapped.append('x')
                'د', 'ض', 'ذ', '9', 'd' -> mapped.append('d')
                'ر', 'r' -> mapped.append('r')
                'ز', 'ظ', 'z' -> mapped.append('z')
                'س', 'ص', 'c', 's' -> mapped.append('s')
                'ش', '$' -> mapped.append('$')
                'غ' -> mapped.append('g')
                'ف', 'f', 'v' -> mapped.append('f')
                'ق', 'ك', '8', 'q', 'k' -> mapped.append('k')
                'ل', 'l' -> mapped.append('l')
                'م', 'm' -> mapped.append('m')
                'ن', 'n' -> mapped.append('n')
                'و', 'ؤ', 'w', 'o', 'u' -> mapped.append('w')
                'ي', 'ى', 'ئ', 'y', 'i', 'e' -> mapped.append('y')
                in '0'..'9' -> mapped.append(c)
                ' ' -> mapped.append(' ')
            }
            i++
        }
        // Collapse consecutive repeated letters & light internal vowel noise inside words
        return mapped.toString()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                val dedup = StringBuilder()
                for (ch in token) {
                    if (dedup.isEmpty() || dedup.last() != ch) dedup.append(ch)
                }
                val s = dedup.toString()
                // Remove weak medial 'a'/'y'/'w' in longer tokens to absorb vowel spelling variance (e.g. sema vs syma)
                if (s.length <= 2) {
                    s
                } else {
                    val out = StringBuilder()
                    s.forEachIndexed { idx, ch ->
                        val isEdge = idx == 0 || idx == s.lastIndex
                        if (isEdge || ch !in charArrayOf('a', 'y', 'w')) {
                            out.append(ch)
                        }
                    }
                    out.toString().ifEmpty { s }
                }
            }
    }
}
