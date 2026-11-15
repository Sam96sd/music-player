package com.example

import com.example.data.local.BlacklistedFolderEntity
import com.example.data.local.TrackEntity
import com.example.data.network.OnlineLyricCandidate
import com.example.domain.ArabiziTransliterator
import com.example.domain.LrcParser
import com.example.domain.LyricsLinkExtractor
import com.example.domain.LyricsMatcher
import com.example.domain.PlaybackQueueManager
import com.example.domain.VoiceNoteFilterEngine
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun voiceNoteFilter_blocksLongWhatsAppVoiceNotesAndAllowsKnownMusic() {
        val rules = listOf(
            BlacklistedFolderEntity(
                pathPattern = "/whatsapp/media/whatsapp voice notes",
                label = "WhatsApp Voice Notes",
                description = "Blocks PTT",
                isEnabled = true
            )
        )

        val flacSong = TrackEntity(
            id = 1,
            title = "Neon Horizon",
            artist = "Kavinsky",
            album = "OutRun",
            filePath = "/storage/emulated/0/Music/Neon_Horizon.flac",
            uriString = "file:///Music/Neon_Horizon.flac",
            extension = "flac",
            durationMs = 240_000L
        )
        assertTrue(VoiceNoteFilterEngine.evaluateTrack(flacSong, rules).isAllowed)

        val longWhatsappM4a = TrackEntity(
            id = 2,
            title = "AUD-20261114-WA0019",
            artist = "WhatsApp Audio",
            album = "WhatsApp",
            filePath = "/storage/emulated/0/Music/AUD-20261114-WA0019.m4a",
            uriString = "file:///Music/AUD-20261114-WA0019.m4a",
            extension = "m4a",
            durationMs = 1_200_000L
        )
        assertFalse(VoiceNoteFilterEngine.evaluateTrack(longWhatsappM4a, rules).isAllowed)

        val ringtone = TrackEntity(
            id = 3,
            title = "Morning Bell",
            artist = "System",
            album = "Ringtones",
            filePath = "/storage/emulated/0/Ringtones/Morning_Bell.mp3",
            uriString = "file:///Ringtones/Morning_Bell.mp3",
            extension = "mp3",
            durationMs = 30_000L
        )
        assertFalse(VoiceNoteFilterEngine.evaluateTrack(ringtone, rules).isAllowed)
        assertTrue(VoiceNoteFilterEngine.isSystemAlertPath("/storage/emulated/0/Alarms/wake.mp3"))
    }

    @Test
    fun lrcParser_parsesSyncedTimestampsAndAppliesOffset() {
        val sampleLrc = """
            [00:02.00] First line of the song
            [00:06.50] Second line of the song
            [00:10.00] Third line of the song
        """.trimIndent()

        val parsed = LrcParser.parse(sampleLrc, "LRCLIB")
        assertTrue(parsed.isSynced)
        assertEquals(3, parsed.lines.size)
        assertEquals(6500L, parsed.lines[1].timestampMs)

        assertEquals(0, LrcParser.findActiveLineIndex(parsed.lines, 6200L, 0L))
        assertEquals(1, LrcParser.findActiveLineIndex(parsed.lines, 6200L, -500L))
    }

    @Test
    fun lyricsMatcher_normalizesArabicAndLatinAndRejectsWrongSongsOrDuration() {
        // Normalization: diacritics, tatweel, أ إ آ -> ا, ة -> ه, ى -> ي, noise words
        val normalizedAr = LyricsMatcher.normalizeForMatch("أَهْـلاً بِكُمْ فِي المَدِينَة (Official Video) [Lyrics]")
        assertEquals("اهلا بكم في المدينه", normalizedAr)
        assertEquals("علي", LyricsMatcher.normalizeForMatch("عَلَى"))

        val validCandidate = OnlineLyricCandidate(
            id = "c1",
            providerName = "LRCLIB.net",
            providerUrl = "lrclib.net",
            trackTitle = "Blinding Lights (Official Audio)",
            artistName = "The Weeknd",
            isSynced = true,
            rawLyrics = "[00:10.00] Yeah, I've been tryna call",
            matchConfidence = "Synced",
            durationSec = 202.0 // within ±7s of 200_000ms
        )
        assertTrue(
            LyricsMatcher.verifyCandidate(
                trackTitle = "Blinding Lights",
                trackArtist = "The Weeknd",
                trackDurationMs = 200_000L,
                candidate = validCandidate
            )
        )

        // Duration off by 12s (> 7s tolerance) must be rejected
        val wrongDuration = validCandidate.copy(durationSec = 212.0)
        assertFalse(
            LyricsMatcher.verifyCandidate(
                trackTitle = "Blinding Lights",
                trackArtist = "The Weeknd",
                trackDurationMs = 200_000L,
                candidate = wrongDuration
            )
        )

        // Wrong artist must be rejected even if title matches
        val wrongArtist = validCandidate.copy(artistName = "Random Cover Band")
        assertFalse(
            LyricsMatcher.verifyCandidate(
                trackTitle = "Blinding Lights",
                trackArtist = "The Weeknd",
                trackDurationMs = 200_000L,
                candidate = wrongArtist
            )
        )

        // Wrong song title must be rejected even if artist matches
        val wrongTitle = validCandidate.copy(trackTitle = "Starboy")
        assertFalse(
            LyricsMatcher.verifyCandidate(
                trackTitle = "Blinding Lights",
                trackArtist = "The Weeknd",
                trackDurationMs = 200_000L,
                candidate = wrongTitle
            )
        )
    }

    @Test
    fun arabiziTransliterator_generatesArabicCandidatesAndVerifiesCrossScript() {
        val candidates = ArabiziTransliterator.generateArabicCandidates("ala bab alsema")
        assertTrue(candidates.any { it.contains("على") && it.contains("باب") && it.contains("السيما") })

        val digitCandidates = ArabiziTransliterator.generateArabicCandidates("3a2el 7abibi")
        assertTrue(digitCandidates.isNotEmpty())
        assertTrue(digitCandidates.first().contains('ع'))
        assertTrue(digitCandidates.first().contains('ح'))
        assertTrue(digitCandidates.first().contains('ء'))

        val arabicLrclibHit = OnlineLyricCandidate(
            id = "ar1",
            providerName = "LRCLIB.net",
            providerUrl = "lrclib.net",
            trackTitle = "على باب السيما",
            artistName = "عمرو دياب",
            isSynced = true,
            rawLyrics = "[00:15.00] على باب السيما",
            matchConfidence = "Synced",
            durationSec = 235.0
        )

        assertTrue(
            LyricsMatcher.verifyCandidate(
                trackTitle = "Ala Bab Alsema",
                trackArtist = "Amr Diab",
                trackDurationMs = 232_000L,
                candidate = arabicLrclibHit,
                arabicTitleCandidates = candidates,
                arabicArtistCandidates = ArabiziTransliterator.generateArabicCandidates("Amr Diab")
            )
        )

        // Completely unrelated Arabic song by same artist should still be rejected
        val wrongArabicSong = arabicLrclibHit.copy(trackTitle = "تملي معاك")
        assertFalse(
            LyricsMatcher.verifyCandidate(
                trackTitle = "Ala Bab Alsema",
                trackArtist = "Amr Diab",
                trackDurationMs = 232_000L,
                candidate = wrongArabicSong,
                arabicTitleCandidates = candidates,
                arabicArtistCandidates = ArabiziTransliterator.generateArabicCandidates("Amr Diab")
            )
        )
    }

    @Test
    fun lyricsLinkExtractor_resolvesLrclibAndExtractsHtmlLyrics() {
        assertEquals(
            "https://lrclib.net/api/get/98765",
            LyricsLinkExtractor.resolveFetchUrl("https://lrclib.net/tracks/98765")
        )
        assertTrue(LyricsLinkExtractor.isLrclibUrl("https://lrclib.net/tracks/98765"))

        val sampleHtml = """
            <html>
              <head><style>.nav{}</style></head>
              <body>
                <nav><a href="/">Home</a><a href="/top">Top</a></nav>
                <div data-lyrics-container="true">
                  Line one of the verse<br/>
                  Line two of the verse<br/>
                  Line three of the chorus<br/>
                  Line four keeps going strong
                </div>
              </body>
            </html>
        """.trimIndent()
        val extracted = LyricsLinkExtractor.extractLyricsFromHtml(sampleHtml)
        assertNotNull(extracted)
        assertTrue(extracted!!.contains("Line one of the verse"))
        assertFalse(extracted.contains("Home"))
    }

    @Test
    fun themePresets_includePastelDefaultAndAdditionalOptions() {
        val allThemes = com.example.ui.theme.AppThemePreset.entries
        assertTrue(allThemes.size >= 3)
        assertEquals(
            com.example.ui.theme.AppThemePreset.PASTEL_DUSK,
            com.example.ui.theme.AppThemePreset.fromStorageKey(null)
        )
        assertEquals(
            com.example.ui.theme.AppThemePreset.WARM_ESPRESSO,
            com.example.ui.theme.AppThemePreset.fromStorageKey("warm_espresso")
        )
        assertTrue(com.example.ui.theme.paletteFor(com.example.ui.theme.AppThemePreset.SOFT_LINEN).isLight)
        assertFalse(com.example.ui.theme.paletteFor(com.example.ui.theme.AppThemePreset.PASTEL_DUSK).isLight)
    }

    @Test
    fun playbackQueueManager_fisherYatesShuffleHasNoRepeatsAndEverySongOnce() {
        val sampleTracks = (1L..25L).map { idx ->
            TrackEntity(
                id = idx,
                title = "Track $idx",
                artist = "Artist $idx",
                album = "Album",
                filePath = "/storage/emulated/0/Music/track_$idx.mp3",
                uriString = "file:///Music/track_$idx.mp3",
                extension = "mp3",
                durationMs = 180_000L
            )
        }

        assertEquals(emptyList<TrackEntity>(), PlaybackQueueManager.fisherYatesShuffle(emptyList<TrackEntity>()))
        assertEquals(listOf(sampleTracks.first()), PlaybackQueueManager.fisherYatesShuffle(listOf(sampleTracks.first())))

        val shuffledA = PlaybackQueueManager.fisherYatesShuffle(sampleTracks, Random(42))
        val shuffledB = PlaybackQueueManager.fisherYatesShuffle(sampleTracks, Random(99))

        assertEquals(sampleTracks.size, shuffledA.size)
        assertEquals(sampleTracks.map { it.id }.toSet(), shuffledA.map { it.id }.toSet())
        assertEquals(sampleTracks.map { it.id }.toSet(), shuffledB.map { it.id }.toSet())
        assertNotEquals(sampleTracks.map { it.id }, shuffledA.map { it.id })
        assertNotEquals(shuffledA.map { it.id }, shuffledB.map { it.id })
    }

    @Test
    fun playbackQueueManager_keepsAnchorFirstAndAvoidsImmediateRepeatOnReshuffle() {
        val sampleTracks = (1L..6L).map { idx ->
            TrackEntity(
                id = idx,
                title = "Song $idx",
                artist = "Singer $idx",
                album = "Album",
                filePath = "/storage/emulated/0/Music/song_$idx.flac",
                uriString = "file:///Music/song_$idx.flac",
                extension = "flac",
                durationMs = 200_000L
            )
        }

        val anchor = sampleTracks[3]
        val anchoredQueue = PlaybackQueueManager.buildShuffledQueueKeepingFirst(
            items = sampleTracks,
            anchorItem = anchor,
            idSelector = { it.id },
            random = Random(7)
        )
        assertEquals(sampleTracks.size, anchoredQueue.size)
        assertEquals(anchor.id, anchoredQueue.first().id)
        assertEquals(sampleTracks.map { it.id }.toSet(), anchoredQueue.map { it.id }.toSet())

        val lastPlayedId = 2L
        for (seed in 0..29) {
            val nextCycle = PlaybackQueueManager.reshuffleNextCycle(
                items = sampleTracks,
                lastPlayedId = lastPlayedId,
                idSelector = { it.id },
                random = Random(seed)
            )
            assertEquals(sampleTracks.size, nextCycle.size)
            assertEquals(sampleTracks.map { it.id }.toSet(), nextCycle.map { it.id }.toSet())
            assertNotEquals(lastPlayedId, nextCycle.first().id)
        }
    }
}
