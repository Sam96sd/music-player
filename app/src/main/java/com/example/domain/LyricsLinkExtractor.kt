package com.example.domain

import org.json.JSONArray
import org.json.JSONObject

data class ExtractedLinkLyrics(
    val text: String,
    val isSynced: Boolean,
    val sourceLabel: String,
    val detectedTitle: String? = null,
    val detectedArtist: String? = null
)

/**
 * Normalizes pasted lyrics URLs (detecting LRCLIB direct API/web links)
 * and extracts lyrics from LRCLIB JSON or arbitrary HTML web pages.
 */
object LyricsLinkExtractor {

    private val lrclibTrackPathRegex = Regex("""lrclib\.net/(?:api/get|tracks?)/(\d+)""", RegexOption.IGNORE_CASE)
    private val lrclibHostRegex = Regex("""(?:^|://)(?:www\.)?lrclib\.net(?:/|$)""", RegexOption.IGNORE_CASE)

    fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    fun isLrclibUrl(url: String): Boolean = lrclibHostRegex.containsMatchIn(url)

    /**
     * Converts an LRCLIB web link (e.g. https://lrclib.net/tracks/12345) into its direct API endpoint,
     * or preserves existing lrclib.net/api/... URLs.
     */
    fun resolveFetchUrl(rawUrl: String): String {
        val url = normalizeUrl(rawUrl)
        if (url.isEmpty()) return ""
        val match = lrclibTrackPathRegex.find(url)
        if (match != null) {
            val id = match.groupValues[1]
            return "https://lrclib.net/api/get/$id"
        }
        return url
    }

    fun parseLrclibJson(jsonBody: String): ExtractedLinkLyrics? {
        val trimmed = jsonBody.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val obj = if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                if (arr.length() == 0) return null
                (0 until arr.length())
                    .map { arr.getJSONObject(it) }
                    .firstOrNull {
                        it.optString("syncedLyrics", "").isNotBlank() ||
                            it.optString("plainLyrics", "").isNotBlank()
                    } ?: return null
            } else {
                JSONObject(trimmed)
            }
            val synced = obj.optString("syncedLyrics", "").trim()
            val plain = obj.optString("plainLyrics", "").trim()
            val chosen = if (synced.isNotEmpty()) synced else plain
            if (chosen.isEmpty()) return null
            val isSynced = synced.isNotEmpty() || LrcParser.parse(chosen, "LRCLIB").isSynced
            ExtractedLinkLyrics(
                text = chosen,
                isSynced = isSynced,
                sourceLabel = if (isSynced) "LRCLIB Link (Synced LRC)" else "LRCLIB Link",
                detectedTitle = obj.optString("trackName", "").takeIf { it.isNotBlank() },
                detectedArtist = obj.optString("artistName", "").takeIf { it.isNotBlank() }
            )
        }.getOrNull()
    }

    /**
     * Best-effort extraction of main lyrics text from arbitrary HTML web pages.
     */
    fun extractLyricsFromHtml(html: String, hostLabel: String = "Web Link"): String? {
        if (html.isBlank()) return null

        // Strip non-content tags completely
        var cleaned = html
            .replace(Regex("(?is)<script\\b[^>]*>.*?</script>"), "\n")
            .replace(Regex("(?is)<style\\b[^>]*>.*?</style>"), "\n")
            .replace(Regex("(?is)<noscript\\b[^>]*>.*?</noscript>"), "\n")
            .replace(Regex("(?is)<svg\\b[^>]*>.*?</svg>"), "\n")
            .replace(Regex("(?is)<nav\\b[^>]*>.*?</nav>"), "\n")
            .replace(Regex("(?is)<header\\b[^>]*>.*?</header>"), "\n")
            .replace(Regex("(?is)<footer\\b[^>]*>.*?</footer>"), "\n")
            .replace(Regex("(?is)<aside\\b[^>]*>.*?</aside>"), "\n")
            .replace(Regex("(?is)<form\\b[^>]*>.*?</form>"), "\n")

        // 1. Check AZLyrics comment marker if present
        val azMarker = Regex("(?is)<!--\\s*Usage of azlyrics\\.com.*?-->(.*?)</div>")
        azMarker.find(cleaned)?.let { match ->
            val candidate = htmlFragmentToPlainLines(match.groupValues[1])
            if (isPlausibleLyrics(candidate)) return candidate
        }

        // 2. Check Genius-style data-lyrics-container blocks (may be split across multiple divs)
        val geniusRegex = Regex("(?is)<div\\b[^>]*data-lyrics-container=\"true\"[^>]*>(.*?)</div>")
        val geniusBlocks = geniusRegex.findAll(cleaned)
            .map { htmlFragmentToPlainLines(it.groupValues[1]) }
            .filter { it.isNotBlank() }
            .toList()
        if (geniusBlocks.isNotEmpty()) {
            val combined = geniusBlocks.joinToString("\n\n").trim()
            if (isPlausibleLyrics(combined)) return combined
        }

        // 3. Check elements whose class or id mentions lyrics / song-text / parole / kalimat
        val lyricsContainerRegex = Regex(
            "(?is)<(?:div|section|article|pre|p)\\b[^>]*(?:class|id)\\s*=\\s*[\"'][^\"']*(?:lyric|song[-_ ]?text|parole|kalimat|lrc)[^\"']*[\"'][^>]*>(.*?)</(?:div|section|article|pre|p)>"
        )
        val targetedCandidates = lyricsContainerRegex.findAll(cleaned)
            .map { match ->
                val rawFrag = match.groupValues[1]
                val linkCount = Regex("(?is)<a\\b").findAll(rawFrag).count()
                val text = htmlFragmentToPlainLines(rawFrag)
                Triple(text, scoreLyricsBlock(text, linkCount), rawFrag.length)
            }
            .filter { it.first.isNotBlank() && it.second > 0.0 }
            .sortedByDescending { it.second }
            .toList()

        targetedCandidates.firstOrNull()?.let { best ->
            if (isPlausibleLyrics(best.first)) return best.first
        }

        // 4. General <pre> or <div|section|article> blocks scored by lyric-line density
        val blockRegex = Regex("(?is)<(?:pre|article|section|div)\\b[^>]*>(.*?)</(?:pre|article|section|div)>")
        val generalBest = blockRegex.findAll(cleaned)
            .map { match ->
                val rawFrag = match.groupValues[1]
                val linkCount = Regex("(?is)<a\\b").findAll(rawFrag).count()
                val text = htmlFragmentToPlainLines(rawFrag)
                text to scoreLyricsBlock(text, linkCount)
            }
            .filter { it.first.isNotBlank() && it.second > 0.0 }
            .maxByOrNull { it.second }

        if (generalBest != null && isPlausibleLyrics(generalBest.first)) {
            return generalBest.first
        }

        // 5. Last resort: strip entire body and keep contiguous lyric-like lines
        val fallbackBody = htmlFragmentToPlainLines(cleaned)
        return fallbackBody.takeIf { isPlausibleLyrics(it) }
    }

    fun htmlFragmentToPlainLines(fragment: String): String {
        val withNewlines = fragment
            .replace(Regex("(?is)<!--.*?-->"), "")
            .replace(Regex("(?is)<br\\s*/?>"), "\n")
            .replace(Regex("(?is)</(?:p|div|li|tr|h[1-6]|blockquote)>"), "\n")
            .replace(Regex("(?is)<[^>]+>"), "")

        val decoded = decodeHtmlEntities(withNewlines)
        return decoded
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .map { it.trim() }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun scoreLyricsBlock(text: String, linkCount: Int): Double {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 4) return -1.0

        val boilerplateTokens = listOf(
            "cookie", "privacy policy", "terms of use", "all rights reserved",
            "sign in", "log in", "register", "advertisement", "subscribe"
        )
        val lower = text.lowercase()
        val boilerplateHits = boilerplateTokens.count { lower.contains(it) }
        val lyricSizedLines = lines.count { it.length in 3..95 }
        val avgLen = lines.map { it.length }.average()

        if (lyricSizedLines < 4 || avgLen > 140.0) return -1.0
        val lineDensity = lyricSizedLines.toDouble()
        val linkPenalty = linkCount * 4.5
        val boilerplatePenalty = boilerplateHits * 12.0
        return lineDensity - linkPenalty - boilerplatePenalty
    }

    private fun isPlausibleLyrics(text: String): Boolean {
        val lines = text.lines().filter { it.isNotBlank() }
        return lines.size >= 3 && text.length >= 40
    }

    fun decodeHtmlEntities(input: String): String {
        var result = input
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&rsquo;", "'")
            .replace("&lsquo;", "'")
            .replace("&rdquo;", "\"")
            .replace("&ldquo;", "\"")
            .replace("&ndash;", "-")
            .replace("&mdash;", "—")
            .replace("&hellip;", "...")

        result = Regex("&#(\\d+);").replace(result) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null && code in 1..0x10FFFF) code.toChar().toString() else match.value
        }
        result = Regex("&#x([0-9a-fA-F]+);").replace(result) { match ->
            val code = match.groupValues[1].toIntOrNull(16)
            if (code != null && code in 1..0x10FFFF) code.toChar().toString() else match.value
        }
        return result
    }
}
