package com.example.domain

import java.util.Locale
import kotlin.math.abs

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

data class ParsedLyrics(
    val isSynced: Boolean,
    val lines: List<LyricLine>,
    val sourceName: String
)

object LrcParser {

    // Matches [01:23.45] or [01:23.456] or [01:23]
    private val LRC_LINE_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?](.*)""")

    fun parse(rawLyrics: String, sourceName: String = "Local LRC"): ParsedLyrics {
        val rawLines = rawLyrics.lines()
        val syncedLines = mutableListOf<LyricLine>()

        for (line in rawLines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val match = LRC_LINE_REGEX.matchEntire(trimmed)
            if (match != null) {
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val fractionStr = match.groupValues[3]
                val millis = when (fractionStr.length) {
                    1 -> (fractionStr.toLongOrNull() ?: 0L) * 100L
                    2 -> (fractionStr.toLongOrNull() ?: 0L) * 10L
                    3 -> fractionStr.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val text = match.groupValues[4].trim()
                if (text.isNotEmpty()) {
                    val totalMs = (minutes * 60_000L) + (seconds * 1_000L) + millis
                    syncedLines.add(LyricLine(timestampMs = totalMs, text = text))
                }
            }
        }

        if (syncedLines.size >= 2) {
            return ParsedLyrics(
                isSynced = true,
                lines = syncedLines.sortedBy { it.timestampMs },
                sourceName = sourceName
            )
        }

        val plainLines = rawLines
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("[ti:") && !it.startsWith("[ar:") }
            .mapIndexed { idx, text ->
                LyricLine(timestampMs = idx * 4000L, text = text)
            }

        return ParsedLyrics(
            isSynced = false,
            lines = plainLines,
            sourceName = sourceName
        )
    }

    /**
     * Given the current playback position and user-configured lyricOffsetMs:
     * A positive offsetMs means delays lyrics (shifts their effective trigger later),
     * or advances lyric display. We define adjustedLineTime = line.timestampMs + lyricOffsetMs.
     */
    fun findActiveLineIndex(
        lines: List<LyricLine>,
        currentPositionMs: Long,
        lyricOffsetMs: Long
    ): Int {
        if (lines.isEmpty()) return -1
        var activeIdx = 0
        for (i in lines.indices) {
            val effectiveLineTimestamp = (lines[i].timestampMs + lyricOffsetMs).coerceAtLeast(0L)
            if (currentPositionMs >= effectiveLineTimestamp) {
                activeIdx = i
            } else {
                break
            }
        }
        return activeIdx
    }

    fun formatTimestamp(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val totalSec = clamped / 1000L
        val min = totalSec / 60L
        val sec = totalSec % 60L
        return String.format(Locale.US, "%02d:%02d", min, sec)
    }

    fun formatOffset(offsetMs: Long): String {
        val seconds = offsetMs / 1000.0
        val sign = if (offsetMs > 0) "+" else if (offsetMs < 0) "-" else "±"
        return String.format(Locale.US, "%s%.1fs", sign, abs(seconds))
    }
}
