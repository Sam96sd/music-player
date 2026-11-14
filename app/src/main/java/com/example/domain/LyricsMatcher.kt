package com.example.domain

import com.example.data.network.OnlineLyricCandidate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Strictly verifies online lyric search candidates against track metadata and duration
 * so wrong songs are never saved or shown.
 */
object LyricsMatcher {

    const val DURATION_TOLERANCE_SEC = 7.0
    private const val TITLE_SIMILARITY_THRESHOLD = 0.72
    private const val ARTIST_SIMILARITY_THRESHOLD = 0.68
    private const val STRICT_TITLE_ONLY_THRESHOLD = 0.86

    private val arabicDiacriticsRegex = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")
    private val bracketedNoiseRegex = Regex("[\\[\\(]([^\\]\\)]*?(official|video|lyrics?|audio|hd|hq|4k|feat\\.?|ft\\.?|remaster(ed)?|visualizer|clip|mv|كليب|كلمات|فيديو|رسمي|حصريا)[^\\]\\)]*)[\\]\\)]", RegexOption.IGNORE_CASE)
    private val punctuationRegex = Regex("[^\\p{L}\\p{Nd}\\s]")

    private val noiseWords = setOf(
        "official", "video", "music", "audio", "lyrics", "lyric", "hd", "hq", "4k",
        "feat", "ft", "featuring", "remaster", "remastered", "version", "edit",
        "clip", "visualizer", "mv", "mp3", "m4a", "flac", "explicit", "clean", "track",
        "فيديو", "كليب", "كلمات", "رسمي", "الرسمي", "الرسميه", "حصريا", "اوديو", "صوت",
        "مترجم", "مترجمه", "نسخه"
    )

    private val unknownArtistMarkers = setOf(
        "", "unknown", "unknown artist", "<unknown>", "local artist", "various artists", "va"
    )

    fun isKnownArtist(artist: String): Boolean {
        return normalizeForMatch(artist) !in unknownArtistMarkers &&
            artist.trim().lowercase(Locale.ROOT) !in unknownArtistMarkers
    }

    /**
     * Normalizes a title or artist:
     * - lowercase
     * - removes Arabic diacritics and tatweel
     * - unifies أ إ آ ٱ -> ا, ة -> ه, ى -> ي
     * - strips noise words (official/video/lyrics/etc.) and punctuation
     */
    fun normalizeForMatch(input: String): String {
        if (input.isBlank()) return ""
        var text = input.lowercase(Locale.ROOT)
        text = bracketedNoiseRegex.replace(text, " ")
        text = arabicDiacriticsRegex.replace(text, "")
        val unified = buildString(text.length) {
            for (ch in text) {
                when (ch) {
                    'أ', 'إ', 'آ', 'ٱ' -> append('ا')
                    'ة' -> append('ه')
                    'ى' -> append('ي')
                    else -> append(ch)
                }
            }
        }
        val noPunct = punctuationRegex.replace(unified, " ")
        val tokens = noPunct.split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in noiseWords }
        return tokens.joinToString(" ").trim()
    }

    /**
     * Computes normalized string similarity in [0.0, 1.0] combining:
     * - exact match
     * - token F1 (Dice) + sorted token similarity
     * - character-level normalized Levenshtein similarity
     * - cross-script Arabizi <-> Arabic phonetic skeleton similarity when scripts differ
     */
    fun computeSimilarity(source: String, target: String): Double {
        val normA = normalizeForMatch(source)
        val normB = normalizeForMatch(target)
        if (normA.isEmpty() || normB.isEmpty()) return 0.0
        if (normA == normB) return 1.0

        val directScore = sameScriptSimilarity(normA, normB)

        val aHasLatin = ArabiziTransliterator.containsLatinLetters(normA)
        val aHasArabic = ArabiziTransliterator.containsArabicLetters(normA)
        val bHasLatin = ArabiziTransliterator.containsLatinLetters(normB)
        val bHasArabic = ArabiziTransliterator.containsArabicLetters(normB)

        val crossScriptScore = if ((aHasLatin && bHasArabic) || (aHasArabic && bHasLatin)) {
            val skelA = ArabiziTransliterator.toCrossScriptSkeleton(normA)
            val skelB = ArabiziTransliterator.toCrossScriptSkeleton(normB)
            if (skelA.isNotEmpty() && skelB.isNotEmpty()) {
                sameScriptSimilarity(skelA, skelB) * 0.96
            } else 0.0
        } else {
            0.0
        }

        return max(directScore, crossScriptScore)
    }

    private fun sameScriptSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val tokensA = a.split(" ").filter { it.isNotBlank() }
        val tokensB = b.split(" ").filter { it.isNotBlank() }
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0

        val charSim = normalizedLevenshteinSimilarity(a, b)
        val sortedCharSim = normalizedLevenshteinSimilarity(
            tokensA.sorted().joinToString(" "),
            tokensB.sorted().joinToString(" ")
        )

        val setA = tokensA.toSet()
        val setB = tokensB.toSet()
        val intersection = setA.intersect(setB).size
        val tokenF1 = (2.0 * intersection) / (setA.size + setB.size)

        // Allow strong match when one artist string includes first+last name and the other includes full name
        // e.g. "Hamaki" vs "Mohamed Hamaki" if the shorter token is meaningful (>= 4 chars)
        val containmentBonus = if (
            intersection > 0 &&
            (setA.size == 1 && setA.first().length >= 4 && setA.first() in setB ||
                setB.size == 1 && setB.first().length >= 4 && setB.first() in setA)
        ) {
            0.78
        } else {
            0.0
        }

        return maxOf(
            charSim,
            sortedCharSim,
            (tokenF1 * 0.65) + (charSim * 0.35),
            containmentBonus
        )
    }

    private fun normalizedLevenshteinSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val maxLen = max(a.length, b.length)
        if (maxLen == 0) return 1.0
        val dist = levenshteinDistance(a, b)
        return (1.0 - dist.toDouble() / maxLen.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            val ca = a[i - 1]
            for (j in 1..n) {
                val cost = if (ca == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,
                    prev[j] + 1,
                    prev[j - 1] + cost
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[n]
    }

    fun isDurationWithinTolerance(
        trackDurationMs: Long,
        candidateDurationSec: Double?,
        maxDiffSeconds: Double = DURATION_TOLERANCE_SEC
    ): Boolean {
        if (candidateDurationSec == null || candidateDurationSec <= 0.0) return true
        if (trackDurationMs <= 1000L) return true
        val trackDurationSec = trackDurationMs / 1000.0
        return abs(candidateDurationSec - trackDurationSec) <= maxDiffSeconds
    }

    /**
     * Verifies a candidate against track title/artist (plus any user-edited query or Arabizi transliteration
     * candidates) AND enforces ±7 seconds duration tolerance whenever duration metadata exists.
     */
    fun verifyCandidate(
        trackTitle: String,
        trackArtist: String,
        trackDurationMs: Long,
        candidate: OnlineLyricCandidate,
        customQueryTitle: String? = null,
        customQueryArtist: String? = null,
        arabicTitleCandidates: List<String> = emptyList(),
        arabicArtistCandidates: List<String> = emptyList()
    ): Boolean {
        if (candidate.rawLyrics.isBlank()) return false

        // 1. Duration check (±7 seconds) whenever candidate provides a duration
        if (!isDurationWithinTolerance(trackDurationMs, candidate.durationSec)) {
            return false
        }

        // 2. Title similarity against original title, user-edited title, and Arabizi->Arabic candidates
        val titleTargets = buildList {
            if (trackTitle.isNotBlank()) add(trackTitle)
            if (!customQueryTitle.isNullOrBlank()) add(customQueryTitle)
            addAll(arabicTitleCandidates)
        }.distinct()

        if (titleTargets.isEmpty() || candidate.trackTitle.isBlank()) return false

        val bestTitleSim = titleTargets.maxOfOrNull { target ->
            computeSimilarity(target, candidate.trackTitle)
        } ?: 0.0

        // 3. Artist similarity against original artist, user-edited artist, and Arabizi->Arabic candidates
        val artistTargets = buildList {
            if (isKnownArtist(trackArtist)) add(trackArtist)
            if (!customQueryArtist.isNullOrBlank() && isKnownArtist(customQueryArtist)) add(customQueryArtist)
            addAll(arabicArtistCandidates)
        }.distinct()

        return if (artistTargets.isNotEmpty()) {
            if (candidate.artistName.isBlank()) return false
            val bestArtistSim = artistTargets.maxOfOrNull { target ->
                computeSimilarity(target, candidate.artistName)
            } ?: 0.0
            bestTitleSim >= TITLE_SIMILARITY_THRESHOLD && bestArtistSim >= ARTIST_SIMILARITY_THRESHOLD
        } else {
            // Track truly has no known artist tag and user entered no artist: require stricter title match
            bestTitleSim >= STRICT_TITLE_ONLY_THRESHOLD
        }
    }

    /**
     * Ranks verified candidates cleanly (synced LRC preferred, then highest title+artist+duration match).
     */
    fun scoreCandidate(
        titleTargets: List<String>,
        artistTargets: List<String>,
        trackDurationMs: Long,
        candidate: OnlineLyricCandidate
    ): Double {
        val titleScore = titleTargets.maxOfOrNull { computeSimilarity(it, candidate.trackTitle) } ?: 0.0
        val artistScore = if (artistTargets.isNotEmpty()) {
            artistTargets.maxOfOrNull { computeSimilarity(it, candidate.artistName) } ?: 0.0
        } else 0.8
        val durationBonus = if (candidate.durationSec != null && trackDurationMs > 1000L) {
            val diff = abs(candidate.durationSec - trackDurationMs / 1000.0)
            ((DURATION_TOLERANCE_SEC - diff) / DURATION_TOLERANCE_SEC).coerceIn(0.0, 1.0) * 0.25
        } else 0.0
        val syncedBonus = if (candidate.isSynced) 0.30 else 0.0
        return titleScore + artistScore + durationBonus + syncedBonus
    }
}
