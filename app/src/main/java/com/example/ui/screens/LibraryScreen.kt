package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.SavedLyricsEntity
import com.example.data.local.TrackEntity
import com.example.domain.LrcParser
import com.example.ui.FilterSettingsState
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

@Composable
fun LibraryScreen(
    tracks: List<TrackEntity>,
    blockedVoiceNotesCount: Int,
    savedLyricsMap: Map<Long, SavedLyricsEntity>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    filterSettings: FilterSettingsState,
    onSearchQueryChange: (String) -> Unit,
    onSelectFormatChip: (String) -> Unit,
    onPlayTrack: (TrackEntity) -> Unit,
    onOpenLyricsSearch: (TrackEntity) -> Unit,
    onOpenQuickTagEditor: (TrackEntity) -> Unit,
    onOpenDeleteModal: (TrackEntity) -> Unit,
    onOpenAddToPlaylist: (TrackEntity) -> Unit,
    onOpenVoiceShieldTab: () -> Unit,
    onImportAudioFiles: () -> Unit,
    onScanDeviceMedia: () -> Unit
) {
    val formats = listOf("ALL", "FLAC", "MP3", "M4A")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(OledCarbon)
            .testTag("library_lazy_column"),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // Studio Hero Header with Voice-Note Shield Status
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, StudioBorder, RoundedCornerShape(22.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_studio_banner_1794673275890),
                    contentDescription = "PulseWave Studio Console",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(158.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(158.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    OledCarbon.copy(alpha = 0.45f),
                                    OledCarbon.copy(alpha = 0.92f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Voice-Note Shield Active Pill
                        Surface(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(end = 8.dp)
                                .clip(RoundedCornerShape(50))
                                .clickable { onOpenVoiceShieldTab() }
                                .testTag("voice_shield_status_pill"),
                            color = SignalMint.copy(alpha = 0.18f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Voice Shield Active",
                                    tint = SignalMint,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "$blockedVoiceNotesCount Voice Notes Blocked",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SignalMint,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onImportAudioFiles() }
                                    .testTag("import_audio_button"),
                                color = StudioElevated.copy(alpha = 0.9f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = "Import Audio Files",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Import",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimary,
                                        maxLines = 1
                                    )
                                }
                            }
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onScanDeviceMedia() }
                                    .testTag("scan_device_button"),
                                color = StudioElevated.copy(alpha = 0.9f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Scan Device Music",
                                    tint = TextSecondary,
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "PulseWave Studio",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Zero ads • Strict MP3 / M4A / FLAC engine • Offline synced LRC",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        // Search & Format Filter Bar
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = filterSettings.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = "Search verified songs, artists, or albums…",
                            color = TextMuted
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = ElectricCyan
                        )
                    },
                    trailingIcon = {
                        if (filterSettings.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    tint = TextSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = StudioBorder,
                        focusedContainerColor = StudioSlate,
                        unfocusedContainerColor = StudioSlate
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("library_search_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    formats.forEach { fmt ->
                        val selected = filterSettings.selectedFormatChip == fmt
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectFormatChip(fmt) },
                            label = {
                                Text(
                                    text = if (fmt == "ALL") "All Music (${tracks.size})" else ".$fmt",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                selectedLabelColor = ElectricCyan,
                                containerColor = StudioSlate,
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("format_chip_$fmt")
                        )
                    }
                }
            }
        }

        if (tracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.AudioFile,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No matching music tracks",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Try clearing your search filter or importing local MP3, M4A, or FLAC files.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(tracks, key = { it.id }) { track ->
                val savedLyrics = savedLyricsMap[track.id]
                val isCurrent = track.id == currentTrackId
                TrackRowCard(
                    track = track,
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && isPlaying,
                    savedLyrics = savedLyrics,
                    onPlayClick = { onPlayTrack(track) },
                    onSearchLyricsClick = { onOpenLyricsSearch(track) },
                    onEditTagsClick = { onOpenQuickTagEditor(track) },
                    onDeleteClick = { onOpenDeleteModal(track) },
                    onAddToPlaylistClick = { onOpenAddToPlaylist(track) }
                )
            }
        }
    }
}

@Composable
private fun TrackRowCard(
    track: TrackEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    savedLyrics: SavedLyricsEntity?,
    onPlayClick: () -> Unit,
    onSearchLyricsClick: () -> Unit,
    onEditTagsClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isCurrent) 1.5.dp else 1.dp,
                color = if (isCurrent) ElectricCyan else StudioBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onPlayClick() }
            .testTag("track_item_card_${track.id}"),
        color = if (isCurrent) StudioElevated else StudioSlate
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Play / Equalizer Emblem
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isCurrent) ElectricCyan.copy(alpha = 0.2f)
                        else OledCarbon
                    )
                    .border(
                        width = 1.dp,
                        color = if (isCurrent) ElectricCyan else StudioBorder,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Equalizer else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Playing" else "Play",
                    tint = if (isCurrent) ElectricCyan else TextPrimary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Track Metadata & Format / Lyrics Badges
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) ElectricCyan else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} • ${track.album}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Format Badge (.FLAC / .MP3 / .M4A)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(OledCarbon)
                            .border(1.dp, StudioBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = track.extension.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }

                    // Duration
                    Text(
                        text = LrcParser.formatTimestamp(track.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )

                    // Offline Lyrics Status or One-Tap Search Button
                    if (savedLyrics != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SignalMint.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (savedLyrics.isSynced) "OFFLINE LRC" else "OFFLINE TEXT",
                                style = MaterialTheme.typography.labelSmall,
                                color = SignalMint,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(WarmAmber.copy(alpha = 0.15f))
                                .clickable { onSearchLyricsClick() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("quick_fetch_lyrics_badge_${track.id}")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "Fetch Lyrics",
                                    tint = WarmAmber,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Fetch Lyrics",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WarmAmber
                                )
                            }
                        }
                    }
                }
            }

            // Overflow Action Menu
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("track_more_button_${track.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Track Options",
                        tint = TextSecondary
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(StudioElevated)
                ) {
                    DropdownMenuItem(
                        text = { Text("Search Online Lyrics", color = TextPrimary) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lyrics,
                                contentDescription = null,
                                tint = ElectricCyan
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onSearchLyricsClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Playlist", color = TextPrimary) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PlaylistAdd,
                                contentDescription = null,
                                tint = SignalMint
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onAddToPlaylistClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Quick Edit Tags", color = TextPrimary) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = WarmAmber
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onEditTagsClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove / Delete Song", color = CrimsonDanger) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = CrimsonDanger
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDeleteClick()
                        }
                    )
                }
            }
        }
    }
}
