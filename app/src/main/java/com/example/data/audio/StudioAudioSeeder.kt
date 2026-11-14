package com.example.data.audio

import android.content.Context
import android.net.Uri
import com.example.data.local.BlacklistedFolderEntity
import com.example.data.local.MusicDao
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackCrossRef
import com.example.data.local.SavedLyricsEntity
import com.example.data.local.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

object StudioAudioSeeder {

    suspend fun ensureSeeded(context: Context, dao: MusicDao) = withContext(Dispatchers.IO) {
        if (dao.getBlacklistedFolderCount() == 0) {
            dao.insertBlacklistedFolders(defaultBlacklistedFolders())
        }

        if (dao.getTrackCount() > 0) return@withContext

        val storageRoot = File(context.filesDir, "simulated_storage").apply { mkdirs() }
        val musicDir = File(storageRoot, "Music/StudioMasters").apply { mkdirs() }
        val whatsappVoiceDir = File(storageRoot, "WhatsApp/Media/WhatsApp Voice Notes/202645").apply { mkdirs() }
        val whatsappAudioDir = File(storageRoot, "WhatsApp/Media/WhatsApp Audio").apply { mkdirs() }
        val telegramAudioDir = File(storageRoot, "Telegram/Telegram Audio").apply { mkdirs() }
        val recordingsDir = File(storageRoot, "Recordings/VoiceRecorder").apply { mkdirs() }

        // Generate real playable audio files with distinct chord progressions
        val file1 = createHarmonicAudioFile(File(musicDir, "Blinding_Lights.flac"), durationSec = 42, baseFreq = 220.0)
        val file2 = createHarmonicAudioFile(File(musicDir, "Midnight_City.mp3"), durationSec = 40, baseFreq = 261.63)
        val file3 = createHarmonicAudioFile(File(musicDir, "Get_Lucky.m4a"), durationSec = 38, baseFreq = 293.66)
        val file4 = createHarmonicAudioFile(File(musicDir, "Neon_Horizon_Master.flac"), durationSec = 44, baseFreq = 196.00)
        val file5 = createHarmonicAudioFile(File(musicDir, "Starboy_Session.mp3"), durationSec = 36, baseFreq = 246.94)
        val file6 = createHarmonicAudioFile(File(musicDir, "Velvet_Subwoofer.m4a"), durationSec = 40, baseFreq = 174.61)

        // Also create realistic WhatsApp Voice Notes (including long .m4a voice notes!)
        val waVoice1 = createHarmonicAudioFile(File(whatsappVoiceDir, "PTT-20261114-WA0001.opus"), durationSec = 12, baseFreq = 300.0)
        val waVoiceLongM4a = createHarmonicAudioFile(File(whatsappAudioDir, "AUD-20261114-WA0019.m4a"), durationSec = 15, baseFreq = 320.0)
        val waForwardedM4a = createHarmonicAudioFile(File(whatsappVoiceDir, "PTT-20261113-WA0048.m4a"), durationSec = 10, baseFreq = 340.0)
        val telegramVn = createHarmonicAudioFile(File(telegramAudioDir, "voice_note_8821.ogg"), durationSec = 9, baseFreq = 310.0)
        val recorderMemo = createHarmonicAudioFile(File(recordingsDir, "Voice_Memo_Meeting.m4a"), durationSec = 10, baseFreq = 280.0)

        val now = System.currentTimeMillis()
        val seededTracks = listOf(
            // Real music tracks (.flac, .mp3, .m4a)
            TrackEntity(
                title = "Neon Horizon",
                artist = "Kavinsky & Pulse",
                album = "OutRun Sessions",
                filePath = "/storage/emulated/0/Music/StudioMasters/Neon_Horizon_Master.flac",
                uriString = Uri.fromFile(file4).toString(),
                extension = "flac",
                durationMs = 44_000L,
                dateAdded = now - 3_600_000L * 2,
                playCount = 18,
                lastPlayedAt = now - 1_800_000L,
                accentHex = "#00E5FF",
                qualityBadge = "FLAC • 24-bit / 96kHz"
            ),
            TrackEntity(
                title = "Blinding Lights",
                artist = "The Weeknd",
                album = "After Hours",
                filePath = "/storage/emulated/0/Music/StudioMasters/Blinding_Lights.flac",
                uriString = Uri.fromFile(file1).toString(),
                extension = "flac",
                durationMs = 42_000L,
                dateAdded = now - 3_600_000L * 12,
                playCount = 27,
                lastPlayedAt = now - 3_600_000L,
                accentHex = "#FF5252",
                qualityBadge = "FLAC • Lossless 16-bit"
            ),
            TrackEntity(
                title = "Midnight City",
                artist = "M83",
                album = "Hurry Up, We're Dreaming",
                filePath = "/storage/emulated/0/Music/StudioMasters/Midnight_City.mp3",
                uriString = Uri.fromFile(file2).toString(),
                extension = "mp3",
                durationMs = 40_000L,
                dateAdded = now - 3_600_000L * 24,
                playCount = 14,
                lastPlayedAt = now - 7_200_000L,
                accentHex = "#1DE9B6",
                qualityBadge = "MP3 • 320 kbps CBR"
            ),
            TrackEntity(
                title = "Get Lucky",
                artist = "Daft Punk",
                album = "Random Access Memories",
                filePath = "/storage/emulated/0/Music/StudioMasters/Get_Lucky.m4a",
                uriString = Uri.fromFile(file3).toString(),
                extension = "m4a",
                durationMs = 38_000L,
                dateAdded = now - 3_600_000L * 36,
                playCount = 9,
                lastPlayedAt = now - 14_400_000L,
                accentHex = "#FFB300",
                qualityBadge = "M4A • AAC 256 kbps"
            ),
            TrackEntity(
                title = "Starboy",
                artist = "The Weeknd",
                album = "Starboy",
                filePath = "/storage/emulated/0/Music/StudioMasters/Starboy_Session.mp3",
                uriString = Uri.fromFile(file5).toString(),
                extension = "mp3",
                durationMs = 36_000L,
                dateAdded = now - 3_600_000L * 5,
                playCount = 0,
                lastPlayedAt = 0L,
                accentHex = "#B388FF",
                qualityBadge = "MP3 • 320 kbps CBR"
            ),
            TrackEntity(
                title = "Velvet Subwoofer",
                artist = "Nils Frahm",
                album = "All Melody",
                filePath = "/storage/emulated/0/Music/StudioMasters/Velvet_Subwoofer.m4a",
                uriString = Uri.fromFile(file6).toString(),
                extension = "m4a",
                durationMs = 40_000L,
                dateAdded = now - 3_600_000L * 1,
                playCount = 0,
                lastPlayedAt = 0L,
                accentHex = "#00E5FF",
                qualityBadge = "M4A • ALAC Lossless"
            ),

            // Simulated WhatsApp & Voice Notes (Automatically caught by VoiceNoteFilterEngine!)
            TrackEntity(
                title = "PTT-20261114-WA0001",
                artist = "WhatsApp Voice Note",
                album = "WhatsApp Voice Notes",
                filePath = "/storage/emulated/0/WhatsApp/Media/WhatsApp Voice Notes/202645/PTT-20261114-WA0001.opus",
                uriString = Uri.fromFile(waVoice1).toString(),
                extension = "opus",
                durationMs = 42_000L,
                dateAdded = now - 1_200_000L
            ),
            TrackEntity(
                title = "AUD-20261114-WA0019 (Long Voice Note)",
                artist = "WhatsApp Audio",
                album = "WhatsApp Audio",
                filePath = "/storage/emulated/0/WhatsApp/Media/WhatsApp Audio/AUD-20261114-WA0019.m4a",
                uriString = Uri.fromFile(waVoiceLongM4a).toString(),
                extension = "m4a",
                durationMs = 845_000L, // 14m 05s long WhatsApp voice note in .m4a container!
                dateAdded = now - 900_000L
            ),
            TrackEntity(
                title = "PTT-20261113-WA0048 (Group Voice Memo)",
                artist = "WhatsApp Voice Note",
                album = "WhatsApp Voice Notes",
                filePath = "/storage/emulated/0/WhatsApp/Media/WhatsApp Voice Notes/202645/PTT-20261113-WA0048.m4a",
                uriString = Uri.fromFile(waForwardedM4a).toString(),
                extension = "m4a",
                durationMs = 312_000L, // 5m 12s .m4a voice note
                dateAdded = now - 2_500_000L
            ),
            TrackEntity(
                title = "voice_note_8821",
                artist = "Telegram Audio",
                album = "Telegram",
                filePath = "/storage/emulated/0/Telegram/Telegram Audio/voice_note_8821.ogg",
                uriString = Uri.fromFile(telegramVn).toString(),
                extension = "ogg",
                durationMs = 195_000L,
                dateAdded = now - 5_000_000L
            ),
            TrackEntity(
                title = "Call_Recording_2026_11",
                artist = "Voice Recorder",
                album = "Recordings",
                filePath = "/storage/emulated/0/Recordings/VoiceRecorder/Call_Recording_2026_11.m4a",
                uriString = Uri.fromFile(recorderMemo).toString(),
                extension = "m4a",
                durationMs = 620_000L,
                dateAdded = now - 8_000_000L
            )
        )

        val insertedIds = dao.insertTracks(seededTracks)

        // Pre-save offline synced lyrics for Track 1 ("Neon Horizon") and Track 6 ("Velvet Subwoofer")
        // Leave "Blinding Lights", "Midnight City", "Get Lucky", and "Starboy" without saved lyrics initially
        // so the user can test the one-tap "Search Online Lyrics" button right away!
        if (insertedIds.isNotEmpty()) {
            dao.saveLyrics(
                SavedLyricsEntity(
                    trackId = insertedIds[0],
                    isSynced = true,
                    sourceName = "LRCLIB.net (Offline Synced)",
                    rawLyrics = """
                        [00:00.50] Signals rising through the velvet static
                        [00:04.20] Neon reflections on the midnight glass
                        [00:08.00] Every frequency tuned to your heartbeat
                        [00:11.80] Chasing horizons where the shadows pass
                        [00:15.60] Hold the resonance, let the bassline breathe
                        [00:19.40] Nothing between us and the open sky
                        [00:23.20] Crystal waveforms in a quiet room
                        [00:27.00] Synced in the dark while the hours fly
                        [00:31.00] Turn up the gain on the silver console
                        [00:35.00] Let the final chord echo into light
                        [00:39.00] Fading smoothly into the studio horizon
                    """.trimIndent()
                )
            )
        }
        if (insertedIds.size >= 6) {
            dao.saveLyrics(
                SavedLyricsEntity(
                    trackId = insertedIds[5],
                    isSynced = true,
                    sourceName = "OpenLRC Studio (Offline Synced)",
                    rawLyrics = """
                        [00:00.50] Warm felt hammers on upright strings
                        [00:05.00] Sub-bass hum beneath the floorboards
                        [00:10.00] Breathing space between every measure
                        [00:15.00] Analog tape spinning at fifteen IPS
                        [00:20.00] No distractions, only pure resonance
                        [00:25.00] Watching the VU meters dance in cyan
                        [00:30.00] Stillness returns as the reverb decays
                    """.trimIndent()
                )
            )
        }

        // Create a starter custom playlist
        val playlistId = dao.insertPlaylist(
            PlaylistEntity(
                name = "Late Night Studio Mix",
                description = "Pure lossless & high-bitrate synth classics"
            )
        )
        if (insertedIds.size >= 3) {
            dao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId, insertedIds[0]))
            dao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId, insertedIds[1]))
            dao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId, insertedIds[2]))
        }
    }

    private fun defaultBlacklistedFolders(): List<BlacklistedFolderEntity> = listOf(
        BlacklistedFolderEntity(
            pathPattern = "/whatsapp/media/whatsapp voice notes",
            label = "WhatsApp Voice Notes",
            description = "Blocks all incoming & sent PTT voice notes (including long recordings)",
            isEnabled = true,
            isBuiltInRule = true
        ),
        BlacklistedFolderEntity(
            pathPattern = "/whatsapp/media/whatsapp audio",
            label = "WhatsApp Forwarded Audio",
            description = "Blocks AUD-* voice clips and forwarded .m4a/.opus audio in WhatsApp",
            isEnabled = true,
            isBuiltInRule = true
        ),
        BlacklistedFolderEntity(
            pathPattern = "/telegram/telegram audio",
            label = "Telegram Voice & Audio",
            description = "Blocks Telegram voice memos and chat audio attachments",
            isEnabled = true,
            isBuiltInRule = true
        ),
        BlacklistedFolderEntity(
            pathPattern = "/recordings",
            label = "Voice & Call Recordings",
            description = "Blocks system voice recorder and call recording folders",
            isEnabled = true,
            isBuiltInRule = true
        ),
        BlacklistedFolderEntity(
            pathPattern = "/notifications",
            label = "System Notifications & Ringtones",
            description = "Excludes short alert tones and notification chimes",
            isEnabled = true,
            isBuiltInRule = true
        )
    )

    /**
     * Writes a valid, playable 16-bit PCM WAV stream into the target file so Android's MediaPlayer
     * can play, seek, and fade volume smoothly regardless of file extension (.mp3/.flac/.m4a).
     */
    private fun createHarmonicAudioFile(targetFile: File, durationSec: Int, baseFreq: Double): File {
        if (targetFile.exists() && targetFile.length() > 1024) return targetFile
        val sampleRate = 11025
        val numSamples = sampleRate * durationSec
        val dataSize = numSamples * 2 // 16-bit mono

        FileOutputStream(targetFile).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16) // PCM chunk size
            header.putShort(1) // AudioFormat = 1 (PCM)
            header.putShort(1) // Channels = 1
            header.putInt(sampleRate)
            header.putInt(sampleRate * 2) // ByteRate
            header.putShort(2) // BlockAlign
            header.putShort(16) // BitsPerSample
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataSize)
            fos.write(header.array())

            val chordRatios = doubleArrayOf(1.0, 1.25, 1.5, 1.875) // Major 7th studio chord progression
            val chunkSamples = 4096
            val buffer = ByteBuffer.allocate(chunkSamples * 2).order(ByteOrder.LITTLE_ENDIAN)

            var sampleIndex = 0
            while (sampleIndex < numSamples) {
                buffer.clear()
                val count = minOf(chunkSamples, numSamples - sampleIndex)
                for (i in 0 until count) {
                    val globalSample = sampleIndex + i
                    val t = globalSample.toDouble() / sampleRate
                    val barIndex = ((t / 4.0).toInt()) % chordRatios.size
                    val root = baseFreq * (if (barIndex % 2 == 0) 1.0 else 0.84)
                    val third = root * chordRatios[1]
                    val fifth = root * chordRatios[2]

                    // Gentle rhythmic pulse envelope
                    val envelope = 0.65 + 0.35 * sin(2.0 * PI * 0.5 * t)
                    val wave = (
                        sin(2.0 * PI * root * t) * 0.45 +
                            sin(2.0 * PI * third * t) * 0.30 +
                            sin(2.0 * PI * fifth * t) * 0.25
                        ) * envelope

                    val pcmVal = (wave * 11000).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    buffer.putShort(pcmVal)
                }
                fos.write(buffer.array(), 0, count * 2)
                sampleIndex += count
            }
        }
        return targetFile
    }
}
