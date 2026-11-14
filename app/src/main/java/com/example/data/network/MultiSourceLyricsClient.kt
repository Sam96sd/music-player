package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
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
    val matchConfidence: String
)

class MultiSourceLyricsClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * Searches top online lyric databases concurrently:
     * 1. LRCLIB.net (Synced LRC + Plain lyrics)
     * 2. Lyrics.ovh (Official Plain lyrics)
     * 3. Lyrist / LRCLIB title-fallback (Additional online source)
     */
    suspend fun searchTopProviders(
        title: String,
        artist: String
    ): List<OnlineLyricCandidate> = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        val results = mutableListOf<OnlineLyricCandidate>()

        coroutineScope {
            val lrclibDeferred = async { fetchFromLrclib(cleanTitle, cleanArtist) }
            val lyricsOvhDeferred = async { fetchFromLyricsOvh(cleanTitle, cleanArtist) }
            val lyristDeferred = async { fetchFromLyrist(cleanTitle, cleanArtist) }

            val lrclibHits = runCatching { lrclibDeferred.await() }.getOrDefault(emptyList())
            val ovhHit = runCatching { lyricsOvhDeferred.await() }.getOrNull()
            val lyristHit = runCatching { lyristDeferred.await() }.getOrNull()

            results.addAll(lrclibHits)
            if (ovhHit != null) {
                results.add(ovhHit)
            }
            if (lyristHit != null) {
                results.add(lyristHit)
            }
        }

        // If combined artist+title search returned nothing, try LRCLIB with title only
        if (results.isEmpty() && cleanTitle.isNotEmpty() && cleanArtist.isNotEmpty()) {
            val titleOnlyHits = runCatching { fetchFromLrclib(cleanTitle, "") }.getOrDefault(emptyList())
            results.addAll(titleOnlyHits)
        }

        results.distinctBy { it.providerName + it.rawLyrics.take(40) }.take(3)
    }

    private fun fetchFromLrclib(title: String, artist: String): List<OnlineLyricCandidate> {
        val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return emptyList()
        val encodedQ = URLEncoder.encode(query, "UTF-8")
        val url = "https://lrclib.net/api/search?q=$encodedQ"
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

            for (i in 0 until minOf(jsonArray.length(), 2)) {
                val obj = jsonArray.getJSONObject(i)
                val synced = obj.optString("syncedLyrics", "").trim()
                val plain = obj.optString("plainLyrics", "").trim()
                val chosenLyrics = if (synced.isNotEmpty()) synced else plain
                if (chosenLyrics.isNotEmpty()) {
                    val isSynced = synced.isNotEmpty()
                    val providerLabel = if (i == 0) "LRCLIB.net (Synced LRC)" else "LRCLIB Alt Match"
                    candidates.add(
                        OnlineLyricCandidate(
                            id = "lrclib_${obj.optInt("id", i)}",
                            providerName = providerLabel,
                            providerUrl = "lrclib.net",
                            trackTitle = obj.optString("trackName", title),
                            artistName = obj.optString("artistName", artist),
                            isSynced = isSynced,
                            rawLyrics = chosenLyrics,
                            matchConfidence = if (isSynced) "Synced Timestamps" else "Plain Text"
                        )
                    )
                }
            }
            return candidates
        }
    }

    private fun fetchFromLyricsOvh(title: String, artist: String): OnlineLyricCandidate? {
        if (title.isBlank() || artist.isBlank() || artist.equals("<unknown>", ignoreCase = true)) return null
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
            val lyrics = json.optString("lyrics", "").trim()
            if (lyrics.isEmpty()) return null
            return OnlineLyricCandidate(
                id = "ovh_${title.hashCode()}",
                providerName = "Lyrics.ovh",
                providerUrl = "lyrics.ovh",
                trackTitle = title,
                artistName = artist,
                isSynced = false,
                rawLyrics = lyrics,
                matchConfidence = "Plain Lyrics"
            )
        }
    }

    private fun fetchFromLyrist(title: String, artist: String): OnlineLyricCandidate? {
        if (title.isBlank()) return null
        val encTitle = URLEncoder.encode(title, "UTF-8").replace("+", "%20")
        val url = if (artist.isNotBlank() && !artist.equals("<unknown>", ignoreCase = true)) {
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
            if (lyrics.isEmpty()) return null
            return OnlineLyricCandidate(
                id = "lyrist_${title.hashCode()}",
                providerName = "Lyrist Index",
                providerUrl = "lyrist.vercel.app",
                trackTitle = json.optString("title", title).ifBlank { title },
                artistName = json.optString("artist", artist).ifBlank { artist },
                isSynced = false,
                rawLyrics = lyrics,
                matchConfidence = "Plain Lyrics"
            )
        }
    }
}
