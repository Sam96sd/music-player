package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.TrackEntity
import com.example.domain.LrcParser
import com.example.domain.ParsedLyrics
import com.example.ui.SleepTimerState
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.OledCarbon
import com.example.ui.theme.SignalMint
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioSlate
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import kotlin.math.roundToLong

@Composable
fun NowPlayingLyricsScreen(
    track: TrackEntity?,
    parsedLyrics: ParsedLyrics?,
    activeLyricIndex: Int,
    isPlaying: Boolean,
    isShuffle: Boolean,
    isRepeatOne: Boolean,
    positionMs: Long,
    durationMs: Long,
    sleepTimer: SleepTimerState,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeatOne: () -> Unit,
    onOpenOnlineLyricsSearch: () -> Unit,
    onSaveManualLyrics: (String) -> Unit,
    onDeleteOfflineLyrics: () -> Unit,
    onAdjustLyricOffset: (Long) -> Unit,
    onSetLyricOffsetExact: (Long) -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenQuickTagEditor: () -> Unit
) {
    if (track == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OledCarbon),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Select a track from your Library to start playback",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary
            )
        }
        return
    }

    var showOffsetCalibrator by remember { mutableStateOf(false) }
    var showManualPasteModal by remember { mutableStateOf(false) }
    val lyricListState = rememberLazyListState()

    // Auto-scroll synced lyrics as active line advances
    LaunchedEffect(activeLyricIndex, track.id) {
        if (activeLyricIndex >= 0 && parsedLyrics != null && parsedLyrics.lines.isNotEmpty()) {
            val targetIdx = (activeLyricIndex - 1).coerceAtLeast(0)
            runCatching {
                lyricListState.animateScrollToItem(targetIdx)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ElectricCyan.copy(alpha = 0.08f),
                        OledCarbon,
                        OledCarbon
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .padding(bottom = 84.dp)
    ) {
        // Top Studio Deck Header: Quality Badge + Sleep Timer + Quick Tag Edit
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ElectricCyan.copy(alpha = 0.16f))
                            .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = track.qualityBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (parsedLyrics != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SignalMint.copy(alpha = 0.16f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "SAVED OFFLINE • ${parsedLyrics.sourceName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SignalMint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} — ${track.album}",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Sleep Timer Button with active countdown pill
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenSleepTimer() }
                        .testTag("open_sleep_timer_button"),
                    color = if (sleepTimer.isActive) WarmAmber.copy(alpha = 0.2f) else StudioSlate
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = "Sleep Timer",
                            tint = if (sleepTimer.isActive) WarmAmber else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        if (sleepTimer.isActive) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "%02d:%02d".format(
                                    sleepTimer.remainingSeconds / 60,
                                    sleepTimer.remainingSeconds % 60
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = WarmAmber
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onOpenQuickTagEditor,
                    modifier = Modifier.testTag("now_playing_edit_tags_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Quick Edit Tags",
                        tint = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Lyrics Header Action Row: Search Online / Offset Slider / Manual Edit
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpenOnlineLyricsSearch,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan.copy(alpha = 0.18f),
                        contentColor = ElectricCyan
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("search_online_lyrics_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (parsedLyrics == null) "Search Online Lyrics" else "Switch Lyric Source",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (parsedLyrics != null) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                1.dp,
                                if (showOffsetCalibrator || track.lyricOffsetMs != 0L) WarmAmber else StudioBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { showOffsetCalibrator = !showOffsetCalibrator }
                            .testTag("toggle_lrc_offset_button"),
                        color = StudioSlate
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "LRC Offset",
                                tint = WarmAmber,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Sync ${LrcParser.formatOffset(track.lyricOffsetMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarmAmber
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { showManualPasteModal = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = "Paste or Edit Lyrics Manually",
                        tint = TextSecondary
                    )
                }
                if (parsedLyrics != null) {
                    IconButton(
                        onClick = onDeleteOfflineLyrics,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Remove Offline Lyrics",
                            tint = CrimsonDanger.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Collapsible LRC Timestamp Offset Calibration Panel
        AnimatedVisibility(visible = showOffsetCalibrator && parsedLyrics != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, WarmAmber.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                color = StudioSlate
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LRC Timestamp Offset (${LrcParser.formatOffset(track.lyricOffsetMs)})",
                            style = MaterialTheme.typography.labelLarge,
                            color = WarmAmber
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onAdjustLyricOffset(-200L) },
                                color = StudioElevated
                            ) {
                                Text(
                                    text = "-0.2s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onSetLyricOffsetExact(0L) },
                                color = StudioElevated
                            ) {
                                Text(
                                    text = "Reset 0s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onAdjustLyricOffset(200L) },
                                color = StudioElevated
                            ) {
                                Text(
                                    text = "+0.2s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Slider(
                        value = track.lyricOffsetMs.toFloat(),
                        onValueChange = {
                            val stepped = ((it / 100f).roundToLong() * 100L)
                            onSetLyricOffsetExact(stepped)
                        },
                        valueRange = -5000f..5000f,
                        colors = SliderDefaults.colors(
                            thumbColor = WarmAmber,
                            activeTrackColor = WarmAmber
                        ),
                        modifier = Modifier.testTag("lrc_offset_slider")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main Synced Offline Lyrics Stage (or Missing Lyrics Search Prompt)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(StudioSlate)
                .border(1.dp, StudioBorder, RoundedCornerShape(20.dp))
                .padding(14.dp)
        ) {
            if (parsedLyrics != null && parsedLyrics.lines.isNotEmpty()) {
                LazyColumn(
                    state = lyricListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("synced_lyrics_lazy_column"),
                    contentPadding = PaddingValues(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(parsedLyrics.lines) { idx, line ->
                        val isCurrentLine = idx == activeLyricIndex
                        val effectiveTimeMs = (line.timestampMs + track.lyricOffsetMs).coerceAtLeast(0L)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isCurrentLine) ElectricCyan.copy(alpha = 0.14f)
                                    else OledCarbon.copy(alpha = 0.0f)
                                )
                                .clickable { onSeekTo(effectiveTimeMs) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("lyric_line_$idx"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (parsedLyrics.isSynced) {
                                Text(
                                    text = LrcParser.formatTimestamp(effectiveTimeMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isCurrentLine) ElectricCyan else TextMuted,
                                    modifier = Modifier.width(46.dp)
                                )
                            }
                            Text(
                                text = line.text,
                                style = if (isCurrentLine) {
                                    MaterialTheme.typography.titleLarge
                                } else {
                                    MaterialTheme.typography.bodyLarge
                                },
                                color = if (isCurrentLine) ElectricCyan else TextSecondary,
                                fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            } else {
                // Empty Lyrics State with Prominent Search Online Button
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Offline Lyrics Saved Yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Search the top 2–3 online lyric databases (LRCLIB, OpenLRC, Lyrics.ovh), preview the best timestamped match, and save it to your device for permanent offline sync.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = onOpenOnlineLyricsSearch,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = OledCarbon
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("empty_state_search_lyrics_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Search & Save Online Lyrics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Seekbar & Tabular Timecodes
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = positionMs.toFloat().coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
                onValueChange = { onSeekTo(it.toLong()) },
                valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(
                    thumbColor = ElectricCyan,
                    activeTrackColor = ElectricCyan,
                    inactiveTrackColor = StudioElevated
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("player_seek_slider")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = LrcParser.formatTimestamp(positionMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan
                )
                Text(
                    text = LrcParser.formatTimestamp(durationMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Studio Transport Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleShuffle,
                modifier = Modifier.testTag("shuffle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffle) ElectricCyan else TextSecondary
                )
            }

            IconButton(
                onClick = onSkipPrev,
                modifier = Modifier
                    .size(50.dp)
                    .testTag("skip_prev_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous Track",
                    tint = TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Surface(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .clickable { onTogglePlayPause() }
                    .testTag("play_pause_main_button"),
                color = ElectricCyan
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = OledCarbon,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            IconButton(
                onClick = onSkipNext,
                modifier = Modifier
                    .size(50.dp)
                    .testTag("skip_next_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next Track",
                    tint = TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = onToggleRepeatOne,
                modifier = Modifier.testTag("repeat_one_button")
            ) {
                Icon(
                    imageVector = Icons.Default.RepeatOne,
                    contentDescription = "Repeat Current Song",
                    tint = if (isRepeatOne) SignalMint else TextSecondary
                )
            }
        }
    }

    if (showManualPasteModal) {
        var rawInput by remember {
            mutableStateOf(
                parsedLyrics?.lines?.joinToString("\n") { line ->
                    "[${LrcParser.formatTimestamp(line.timestampMs)}.00] ${line.text}"
                } ?: ""
            )
        }
        AlertDialog(
            onDismissRequest = { showManualPasteModal = false },
            containerColor = StudioSlate,
            titleContentColor = TextPrimary,
            title = { Text("Paste or Edit Offline Lyrics") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Supports [mm:ss.xx] synced LRC format or plain text:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = rawInput,
                        onValueChange = { rawInput = it },
                        minLines = 6,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveManualLyrics(rawInput)
                        showManualPasteModal = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignalMint,
                        contentColor = OledCarbon
                    )
                ) {
                    Text("Save Offline", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualPasteModal = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
