package com.example.domain

import com.example.data.local.BlacklistedFolderEntity
import com.example.data.local.TrackEntity

data class FilterVerdict(
    val isAllowed: Boolean,
    val blockedReason: String? = null,
    val matchedRuleLabel: String? = null
)

data class FilteredTrackItem(
    val track: TrackEntity,
    val reason: String,
    val ruleLabel: String
)

object VoiceNoteFilterEngine {

    val ALLOWED_MUSIC_EXTENSIONS = setOf("mp3", "m4a", "flac")

    private val SYSTEM_AUDIO_PATHS = listOf(
        "/ringtones/",
        "/notifications/",
        "/alarms/",
        "/system/media/audio/",
        "/product/media/audio/"
    )

    private val WHATSAPP_VOICE_NOTE_REGEXES = listOf(
        Regex("^ptt-\\d{8}-wa.*", RegexOption.IGNORE_CASE),
        Regex("^aud-\\d{8}-wa.*", RegexOption.IGNORE_CASE),
        Regex("^msg-\\d{8}-wa.*", RegexOption.IGNORE_CASE),
        Regex("^voice[_-]?note.*", RegexOption.IGNORE_CASE),
        Regex("^vn[_-]\\d+.*", RegexOption.IGNORE_CASE),
        Regex("^whatsapp[_\\s-]?audio.*", RegexOption.IGNORE_CASE),
        Regex("^whatsapp[_\\s-]?voice.*", RegexOption.IGNORE_CASE),
        Regex("^call[_\\s-]?recording.*", RegexOption.IGNORE_CASE)
    )

    fun isSystemAlertPath(filePath: String): Boolean {
        val normalized = filePath.lowercase()
        return SYSTEM_AUDIO_PATHS.any { normalized.contains(it) }
    }

    fun evaluateTrack(
        track: TrackEntity,
        blacklistedFolders: List<BlacklistedFolderEntity>,
        strictKnownFormatsOnly: Boolean = true,
        blockVoiceNotePatterns: Boolean = true
    ): FilterVerdict {
        if (track.isHidden) {
            return FilterVerdict(
                isAllowed = false,
                blockedReason = "Hidden from library by user",
                matchedRuleLabel = "Hidden Track"
            )
        }

        val normalizedExt = track.extension.lowercase().trim().removePrefix(".")
        val normalizedPath = track.filePath.lowercase()
        val fileName = track.filePath.substringAfterLast('/').lowercase()
        val baseName = fileName.substringBeforeLast('.')

        // 0. Always exclude ringtones, notifications, and alarms paths
        if (isSystemAlertPath(normalizedPath)) {
            return FilterVerdict(
                isAllowed = false,
                blockedReason = "System ringtone / notification / alarm path",
                matchedRuleLabel = "System Audio Guard"
            )
        }

        // 1. Check active folder blacklist rules first (catches WhatsApp Voice Notes even if saved as .m4a)
        for (rule in blacklistedFolders) {
            if (rule.isEnabled) {
                val pattern = rule.pathPattern.lowercase().trim()
                if (pattern.isNotEmpty() && normalizedPath.contains(pattern)) {
                    return FilterVerdict(
                        isAllowed = false,
                        blockedReason = "Blocked folder path (${rule.pathPattern})",
                        matchedRuleLabel = rule.label
                    )
                }
            }
        }

        // 2. Check WhatsApp / Messaging Voice Note filename heuristics (even for long .m4a files)
        if (blockVoiceNotePatterns) {
            for (regex in WHATSAPP_VOICE_NOTE_REGEXES) {
                if (regex.matches(fileName) || regex.matches(baseName) || regex.matches(track.title.lowercase())) {
                    return FilterVerdict(
                        isAllowed = false,
                        blockedReason = "Voice note filename signature ($fileName)",
                        matchedRuleLabel = "WhatsApp / Voice Note Signature"
                    )
                }
            }
        }

        // 3. Check strict music format extensions (.mp3, .m4a, .flac only)
        if (strictKnownFormatsOnly && normalizedExt !in ALLOWED_MUSIC_EXTENSIONS) {
            return FilterVerdict(
                isAllowed = false,
                blockedReason = "Unsupported non-music container (.$normalizedExt)",
                matchedRuleLabel = "Strict MP3/M4A/FLAC Filter"
            )
        }

        return FilterVerdict(isAllowed = true)
    }
}
