package com.example

import com.example.data.local.BlacklistedFolderEntity
import com.example.data.local.TrackEntity
import com.example.domain.LrcParser
import com.example.domain.VoiceNoteFilterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        // Even a 20-minute .m4a WhatsApp voice note is blocked by PTT/AUD heuristic & folder rule
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

        // Ringtones and alarms are always excluded
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

        // At 6200ms with 0ms offset, active line is index 0
        assertEquals(0, LrcParser.findActiveLineIndex(parsed.lines, 6200L, 0L))

        // With -500ms offset (advancing lyrics by 0.5s so line 1 triggers at 6000ms), active line is index 1
        assertEquals(1, LrcParser.findActiveLineIndex(parsed.lines, 6200L, -500L))
    }
}
