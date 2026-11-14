package com.example.data.network

import com.example.domain.ArabiziTransliterator
import com.example.domain.LrcParser
import com.example.domain.LyricsLinkExtractor
import com.example.domain.LyricsMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class OnlineLyricCandidate(
    val id: String,
    val providerName: String,
    val providerUrl: String,
    val trackTitle: String,
    val artistName: String,
    val isSynced: Boolean,
    val rawLyrics: String,
    val matchConfidence: String,
    val durationSec: Double? = null
)

class MultiSourceLyricsClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Automatic mode with strict verification:
     * 1. Queries Source 1 (LRCLIB.net) -> Source 2 (Lyrics.ovh) -> Source 3 (Lyrist Index).
     * 2. Verifies normalized title, normalized artist, and ±7s duration.
     * 3. If nothing matches and title/artist is in Latin letters, retries via LRCLIB artist-only + duration
     *    and Arabizi -> Arabic-script candidate queries, applying the exact same strict verification.
     */
    suspend fun fetchFirstVerifiedLyrics(
        trackTitle: String,
        trackArtist: String,
        trackDurationMs: Long,
        queryTitle: String = trackTitle,
        queryArtist: String = trackArtist
    ): OnlineLyricCandidate? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanQueryPart(queryTitle)
        val cleanArtist = cleanArtistPart(queryArtist)
        if (cleanTitle.isEmpty()) return@withContext null

        val arabicTitles = buildArabicCandidateList(trackTitle, queryTitle)
        val arabicArtists = buildArabicCandidateList(trackArtist, queryArtist)

        fun verify(candidate: OnlineLyricCandidate): Boolean =
            LyricsMatcher.verifyCandidate(
                trackTitle = trackTitle,
                trackArtist = trackArtist,
                trackDurationMs = trackDurationMs,
                candidate = candidate,
                customQueryTitle = queryTitle,
                customQueryArtist = queryArtist,
                arabicTitleCandidates = arabicTitles,
                arabicArtistCandidates = arabicArtists
            )

        val titleTargets = listOfNotNull(
            trackTitle.takeIf { it.isNotBlank() },
            queryTitle.takeIf { it.isNotBlank() }
        ) + arabicTitles
        val artistTargets = listOfNotNull(
            trackArtist.takeIf { LyricsMatcher.isKnownArtist(it) },
            queryArtist.takeIf { LyricsMatcher.isKnownArtist(it) }
        ) + arabicArtists

        // Source 1: LRCLIB.net
        val lrclibHits = runCatching { fetchFromLrclib(cleanTitle, cleanArtist, limit = 8) }
            .getOrDefault(emptyList())
            .filter(::verify)
            .sortedByDescending { LyricsMatcher.scoreCandidate(titleTargets, artistTargets, trackDurationMs, it) }
        if (lrclibHits.isNotEmpty()) {
            return@withContext lrclibHits.first()
        }

        // Source 2: Lyrics.ovh (requires known artist)
        if (cleanArtist.isNotEmpty()) {
            val ovhHit = runCatching { fetchFromLyricsOvh(cleanTitle, cleanArtist) }.getOrNull()
            if (ovhHit != null && verify(ovhHit)) {
                return@withContext ovhHit
            }
        }

        // Source 3: Lyrist Index
        val lyristHit = runCatching { fetchFromLyrist(cleanTitle, cleanArtist) }.getOrNull()
        if (lyristHit != null && verify(lyristHit)) {
            return@withContext lyristHit
        }

        // Arabizi / Latin fallback when nothing matched
        val hasLatin = ArabiziTransliterator.containsLatinLetters(cleanTitle) ||
            ArabiziTransliterator.containsLatinLetters(cleanArtist)
        if (hasLatin) {
            val fallbackHits = searchArabiziFallbacks(
                cleanTitle = cleanTitle,
                cleanArtist = cleanArtist,
                arabicTitles = arabicTitles,
                arabicArtists = arabicArtists,
                verify = ::verify
            ).sortedByDescending {
                LyricsMatcher.scoreCandidate(titleTargets, artistTargets, trackDurationMs, it)
            }
            if (fallbackHits.isNotEmpty()) {
                return@withContext fallbackHits.first()
            }
        }

        null
    }

    /**
     * Manual "Choose another version" picker with the same strict verification + Arabizi fallback.
     */
    suspend fun searchVerifiedTopProviders(
        trackTitle: String,
        trackArtist: String,
        trackDurationMs: Long,
        queryTitle: String = trackTitle,
        queryArtist: String = trackArtist
    ): List<OnlineLyricCandidate> = withContext(Dispatchers.IO) {
        val cleanTitle = cleanQueryPart(queryTitle)
        val cleanArtist = cleanArtistPart(queryArtist)
        if (cleanTitle.isEmpty()) return@withContext emptyList()

        val arabicTitles = buildArabicCandidateList(trackTitle, queryTitle)
        val arabicArtists = buildArabicCandidateList(trackArtist, queryArtist)

        fun verify(candidate: OnlineLyricCandidate): Boolean =
            LyricsMatcher.verifyCandidate(
                trackTitle = trackTitle,
                trackArtist = trackArtist,
                trackDurationMs = trackDurationMs,
                candidate = candidate,
                customQueryTitle = queryTitle,
                customQueryArtist = queryArtist,
                arabicTitleCandidates = arabicTitles,
                arabicArtistCandidates = arabicArtists
            )

        val collected = mutableListOf<OnlineLyricCandidate>()

        coroutineScope {
            val lrclibDeferred = async { fetchFromLrclib(cleanTitle, cleanArtist, limit = 8) }
            val lyricsOvhDeferred = async {
                if (cleanArtist.isNotEmpty()) fetchFromLyricsOvh(cleanTitle, cleanArtist) else null
            }
            val lyristDeferred = async { fetchFromLyrist(cleanTitle, cleanArtist) }

            collected.addAll(runCatching { lrclibDeferred.await() }.getOrDefault(emptyList()))
            runCatching { lyricsOvhDeferred.await() }.getOrNull()?.let { collected.add(it) }
            runCatching { lyristDeferred.await() }.getOrNull()?.let { collected.add(it) }
        }

        var verified = collected.filter(::verify)

        // Arabizi / Latin fallback if nothing passed verification
        val hasLatin = ArabiziTransliterator.containsLatinLetters(cleanTitle) ||
            ArabiziTransliterator.containsLatinLetters(cleanArtist)
        if (verified.isEmpty() && hasLatin) {
            verified = searchArabiziFallbacks(
                cleanTitle = cleanTitle,
                cleanArtist = cleanArtist,
                arabicTitles = arabicTitles,
                arabicArtists = arabicArtists,
                verify = ::verify
            )
        }

        val titleTargets = listOfNotNull(
            trackTitle.takeIf { it.isNotBlank() },
            queryTitle.takeIf { it.isNotBlank() }
        ) + arabicTitles
        val artistTargets = listOfNotNull(
            trackArtist.takeIf { LyricsMatcher.isKnownArtist(it) },
            queryArtist.takeIf { LyricsMatcher.isKnownArtist(it) }
        ) + arabicArtists

        verified
            .sortedByDescending { LyricsMatcher.scoreCandidate(titleTargets, artistTargets, trackDurationMs, it) }
            .distinctBy { it.providerName + "|" + it.rawLyrics.take(40) }
            .take(3)
    }

    /**
     * Manual fallback: loads lyrics from a pasted LRCLIB link or extracts lyrics text (best-effort)
     * from any web page URL for user preview before saving.
     */
    suspend fun fetchLyricsFromUrl(rawUrl: String): Result<OnlineLyricCandidate> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedUrl = LyricsLinkExtractor.resolveFetchUrl(rawUrl)
            require(resolvedUrl.isNotBlank()) { "Please enter a valid URL" }

            val request = Request.Builder()
                .url(resolvedUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                )
                .header("Accept", "application/json,text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Server returned HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) error("Empty response from link")

                if (LyricsLinkExtractor.isLrclibUrl(resolvedUrl)) {
                    val fromJson = LyricsLinkExtractor.parseLrclibJson(body)
                    if (fromJson != null) {
                        return@runCatching OnlineLyricCandidate(
                            id = "link_${resolvedUrl.hashCode()}",
                            providerName = fromJson.sourceLabel,
                            providerUrl = resolvedUrl,
                            trackTitle = fromJson.detectedTitle ?: "LRCLIB Link",
                            artistName = fromJson.detectedArtist ?: "",
                            isSynced = fromJson.isSynced,
                            rawLyrics = fromJson.text,
                            matchConfidence = if (fromJson.isSynced) "Synced LRC Link" else "LRCLIB Link"
                        )
                    }
                }

                val host = runCatching { URI(resolvedUrl).host?.removePrefix("www.") }.getOrNull() ?: "Web Link"
                val extracted = LyricsLinkExtractor.extractLyricsFromHtml(body, host)
                    ?: error("Could not extract lyrics from that page. Try 'Paste lyrics text' instead.")
                val isSynced = LrcParser.parse(extracted, host).isSynced

                OnlineLyricCandidate(
                    id = "link_${resolvedUrl.hashCode()}",
                    providerName = host,
                    providerUrl = resolvedUrl,
                    trackTitle = host,
                    artistName = "",
                    isSynced = isSynced,
                    rawLyrics = extracted,
                    matchConfidence = if (isSynced) "Extracted LRC" else "Extracted Web Text"
                )
            }
        }
    }

    private fun searchArabiziFallbacks(
        cleanTitle: String,
        cleanArtist: String,
        arabicTitles: List<String>,
        arabicArtists: List<String>,
        verify: (OnlineLyricCandidate) -> Boolean
    ): List<OnlineLyricCandidate> {
        val candidates = mutableListOf<OnlineLyricCandidate>()

        // 1. Search LRCLIB by artist only (Latin artist + transliterated Arabic artist) -> verified by title & ±7s duration
        val artistQueries = (listOf(cleanArtist) + arabicArtists)
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)
        for (artistQuery in artistQueries) {
            val byArtist = runCatching { fetchFromLrclibByArtist(artistQuery, limit = 20) }
                .getOrDefault(emptyList())
                .filter(verify)
            candidates.addAll(byArtist)
            if (candidates.isNotEmpty()) return candidates
        }

        // 2. Search LRCLIB & Lyrist using generated Arabizi -> Arabic script candidates
        val titleQueries = arabicTitles.take(3)
        val primaryArabicArtist = arabicArtists.firstOrNull().orEmpty()
        for (arabicTitle in titleQueries) {
            val artistVariants = listOfNotNull(
                primaryArabicArtist.takeIf { it.isNotBlank() },
                cleanArtist.takeIf { it.isNotBlank() },
                ""
            ).distinct()

            for (artistVariant in artistVariants) {
                val lrclibArabicHits = runCatching {
                    fetchFromLrclib(arabicTitle, artistVariant, limit = 8)
                }.getOrDefault(emptyList()).filter(verify)
                if (lrclibArabicHits.isNotEmpty()) {
                    candidates.addAll(lrclibArabicHits)
                    return candidates
                }
            }

            if (primaryArabicArtist.isNotBlank()) {
                val ovhArabic = runCatching {
                    fetchFromLyricsOvh(arabicTitle, primaryArabicArtist)
                }.getOrNull()
                if (ovhArabic != null && verify(ovhArabic)) {
                    candidates.add(ovhArabic)
                    return candidates
                }
            }

            val lyristArabic = runCatching {
                fetchFromLyrist(arabicTitle, primaryArabicArtist.ifBlank { cleanArtist })
            }.getOrNull()
            if (lyristArabic != null && verify(lyristArabic)) {
                candidates.add(lyristArabic)
                return candidates
            }
        }

        return candidates
    }

    private fun buildArabicCandidateList(primary: String, secondary: String): List<String> {
        val list = mutableListOf<String>()
        if (ArabiziTransliterator.containsLatinLetters(primary)) {
            list.addAll(ArabiziTransliterator.generateArabicCandidates(primary))
        }
        if (secondary != primary && ArabiziTransliterator.containsLatinLetters(secondary)) {
            list.addAll(ArabiziTransliterator.generateArabicCandidates(secondary))
        }
        return list.distinct()
    }

    private fun cleanQueryPart(value: String): String {
        return value.trim()
            .replace(Regex("^\\d{1,2}\\s*[.\\-_]+\\s*"), "")
            .trim()
    }

    private fun cleanArtistPart(value: String): String {
        val trimmed = value.trim()
        return if (!LyricsMatcher.isKnownArtist(trimmed)) "" else trimmed
    }

    private fun fetchFromLrclib(title: String, artist: String, limit: Int = 6): List<OnlineLyricCandidate> {
        val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return emptyList()
        val encodedQ = URLEncoder.encode(query, "UTF-8")
        val url = "https://lrclib.net/api/search?q=$encodedQ"
        return executeLrclibSearchUrl(url, limit)
    }

    private fun fetchFromLrclibByArtist(artist: String, limit: Int = 20): List<OnlineLyricCandidate> {
        if (artist.isBlank()) return emptyList()
        val encodedArtist = URLEncoder.encode(artist, "UTF-8")
        val directByArtist = executeLrclibSearchUrl(
            "https://lrclib.net/api/search?artist_name=$encodedArtist",
            limit
        )
        if (directByArtist.isNotEmpty()) return directByArtist
        return executeLrclibSearchUrl("https://lrclib.net/api/search?q=$encodedArtist", limit)
    }

    private fun executeLrclibSearchUrl(url: String, limit: Int): List<OnlineLyricCandidate> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MuffinMusicPlayer/1.0 (https://muffinmusic.app)")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val bodyStr = response.body?.string() ?: return emptyList()
            val jsonArray = JSONArray(bodyStr)
            val candidates = mutableListOf<OnlineLyricCandidate>()

            // Prefer synced LRC items first
            val items = (0 until jsonArray.length()).map { jsonArray.getJSONObject(it) }
                .sortedByDescending { it.optString("syncedLyrics", "").isNotBlank() }

            for (i in 0 until minOf(items.size, limit)) {
                val obj = items[i]
                val synced = obj.optString("syncedLyrics", "").trim()
                val plain = obj.optString("plainLyrics", "").trim()
                val chosenLyrics = if (synced.isNotEmpty()) synced else plain
                if (chosenLyrics.isNotEmpty()) {
                    val isSynced = synced.isNotEmpty()
                    val durationSec = obj.optDouble("duration", -1.0).takeIf { !it.isNaN() && it > 0.0 }
                    val providerLabel = if (isSynced) "LRCLIB.net (Synced LRC)" else "LRCLIB.net"
                    candidates.add(
                        OnlineLyricCandidate(
                            id = "lrclib_${obj.optInt("id", i)}_$i",
                            providerName = providerLabel,
                            providerUrl = "lrclib.net",
                            trackTitle = obj.optString("trackName", "").trim(),
                            artistName = obj.optString("artistName", "").trim(),
                            isSynced = isSynced,
                            rawLyrics = chosenLyrics,
                            matchConfidence = if (isSynced) "Synced Timestamps" else "Plain Text",
                            durationSec = durationSec
                        )
                    )
                }
            }
            return candidates
        }
    }

    private fun fetchFromLyricsOvh(title: String, artist: String): OnlineLyricCandidate? {
        if (title.isBlank() || artist.isBlank() || !LyricsMatcher.isKnownArtist(artist)) return null
        val encArtist = URLEncoder.encode(artist, "UTF-8").replace("+", "%20")
        val encTitle = URLEncoder.encode(title, "UTF-8").replace("+", "%20")
        val url = "https://api.lyrics.ovh/v1/$encArtist/$encTitle"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MuffinMusicPlayer/1.0")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyStr = response.body?.string() ?: return null
            val json = JSONObject(bodyStr)
            var lyrics = json.optString("lyrics", "").trim()
            if (lyrics.startsWith("Paroles de la chanson", ignoreCase = true)) {
                lyrics = lyrics.substringAfter("\n", "").trim()
            }
            if (lyrics.length < 30) return null
            return OnlineLyricCandidate(
                id = "ovh_${title.hashCode()}_${artist.hashCode()}",
                providerName = "Lyrics.ovh",
                providerUrl = "lyrics.ovh",
                trackTitle = title,
                artistName = artist,
                isSynced = false,
                rawLyrics = lyrics,
                matchConfidence = "Plain Lyrics",
                durationSec = null
            )
        }
    }

    private fun fetchFromLyrist(title: String, artist: String): OnlineLyricCandidate? {
        if (title.isBlank()) return null
        val encTitle = URLEncoder.encode(title, "UTF-8").replace("+", "%20")
        val url = if (artist.isNotBlank() && LyricsMatcher.isKnownArtist(artist)) {
            val encArtist = URLEncoder.encode(artist, "UTF-8").replace("+", "%20")
            "https://lyrist.vercel.app/api/$encTitle/$encArtist"
        } else {
            "https://lyrist.vercel.app/api/$encTitle"
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MuffinMusicPlayer/1.0")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyStr = response.body?.string() ?: return null
            val json = JSONObject(bodyStr)
            val lyrics = json.optString("lyrics", "").trim()
            if (lyrics.length < 30) return null
            val returnedTitle = json.optString("title", "").trim()
            val returnedArtist = json.optString("artist", "").trim()
            val durationSec = json.optDouble("duration", -1.0).takeIf { !it.isNaN() && it > 0.0 }
            return OnlineLyricCandidate(
                id = "lyrist_${title.hashCode()}_${returnedTitle.hashCode()}",
                providerName = "Lyrist Index",
                providerUrl = "lyrist.vercel.app",
                trackTitle = returnedTitle,
                artistName = returnedArtist,
                isSynced = false,
                rawLyrics = lyrics,
                matchConfidence = "Plain Lyrics",
                durationSec = durationSec
            )
        }
    }
}
