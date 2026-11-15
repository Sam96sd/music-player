package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import com.example.data.local.SavedLyricsEntity
import com.example.data.local.TrackEntity
import com.example.domain.LrcParser
import com.example.ui.BatchLyricsProgress
import com.example.ui.FilterSettingsState
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MutedSage
import com.example.ui.theme.MutedSlateBlue
import com.example.ui.theme.OledCarbon
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioSlate
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    tracks: List<TrackEntity>,
    blockedVoiceNotesCount: Int,
    hasAudioPermission: Boolean,
    isScanning: Boolean,
    selectedTrackIds: Set<Long>,
    fetchingTrackIds: Set<Long>,
    batchLyrics: BatchLyricsProgress,
    savedLyricsMap: Map<Long, SavedLyricsEntity>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    filterSettings: FilterSettingsState,
    onSearchQueryChange: (String) -> Unit,
    onPlayTrack: (TrackEntity) -> Unit,
    onPlayAllTracks: () -> Unit,
    onShuffleTracks: () -> Unit,
    onEnterSelectionMode: (Long) -> Unit,
    onToggleTrackSelection: (Long) -> Unit,
    onSelectAllTracks: () -> Unit,
    onClearSelection: () -> Unit,
    onBulkAddToPlaylist: () -> Unit,
    onBulkHideFromLibrary: () -> Unit,
    onBulkDeleteFromDevice: () -> Unit,
    onBulkFetchLyrics: () -> Unit,
    onAutoFetchLyrics: (TrackEntity) -> Unit,
    onOpenManualLyricsPicker: (TrackEntity) -> Unit,
    onOpenPasteLyricsLink: (TrackEntity) -> Unit,
    onOpenPasteLyricsText: (TrackEntity) -> Unit,
    onImportLrcFile: (TrackEntity) -> Unit,
    onFetchAllMissingLyrics: () -> Unit,
    onCancelBatchLyrics: () -> Unit,
    onOpenQuickTagEditor: (TrackEntity) -> Unit,
    onOpenDeleteModal: (TrackEntity) -> Unit,
    onOpenAddToPlaylist: (TrackEntity) -> Unit,
    onOpenVoiceShieldTab: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onImportAudioFiles: () -> Unit,
    onScanDeviceMedia: () -> Unit
) {
    val isSelectionMode = selectedTrackIds.isNotEmpty()
    val missingLyricsCount = remember(tracks, savedLyricsMap) {
        tracks.count { !savedLyricsMap.containsKey(it.id) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(OledCarbon)
            .testTag("library_lazy_column"),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // Multi-Selection Top Action Bar OR Normal Studio Header
        item {
            if (isSelectionMode) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, ElectricCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .testTag("multi_select_action_bar"),
                    color = StudioElevated
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onClearSelection,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("clear_selection_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel Selection",
                                        tint = TextPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${selectedTrackIds.size} selected",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ElectricCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelectAllTracks() }
                                    .testTag("select_all_button"),
                                color = StudioSlate
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SelectAll,
                                        contentDescription = "Select all",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (selectedTrackIds.size == tracks.size && tracks.isNotEmpty()) {
                                            "Deselect all"
                                        } else {
                                            "Select all"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BulkActionChip(
                                label = "Playlist",
                                icon = Icons.Default.PlaylistAdd,
                                tint = MutedSage,
                                onClick = onBulkAddToPlaylist,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("bulk_add_playlist_button")
                            )
                            BulkActionChip(
                                label = "Lyrics",
                                icon = Icons.Default.CloudDownload,
                                tint = ElectricCyan,
                                onClick = onBulkFetchLyrics,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("bulk_fetch_lyrics_button")
                            )
                            BulkActionChip(
                                label = "Hide",
                                icon = Icons.Default.VisibilityOff,
                                tint = WarmAmber,
                                onClick = onBulkHideFromLibrary,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("bulk_hide_button")
                            )
                            BulkActionChip(
                                label = "Delete",
                                icon = Icons.Default.DeleteForever,
                                tint = CrimsonDanger,
                                onClick = onBulkDeleteFromDevice,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("bulk_delete_button")
                            )
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, StudioBorder, RoundedCornerShape(16.dp)),
                    color = StudioSlate
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        ElectricCyan.copy(alpha = 0.06f),
                                        StudioSlate
                                    )
                                )
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Muffin Music Player",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(9.dp))
                                            .clickable { onImportAudioFiles() }
                                            .testTag("import_audio_button"),
                                        color = StudioElevated
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FolderOpen,
                                                contentDescription = "Import Audio Files",
                                                tint = ElectricCyan,
                                                modifier = Modifier.size(14.dp)
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
                                            .clip(RoundedCornerShape(9.dp))
                                            .clickable(enabled = !isScanning) { onScanDeviceMedia() }
                                            .testTag("scan_device_button"),
                                        color = StudioElevated
                                    ) {
                                        if (isScanning) {
                                            CircularProgressIndicator(
                                                color = ElectricCyan,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier
                                                    .padding(6.dp)
                                                    .size(15.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Rescan Device Music",
                                                tint = ElectricCyan,
                                                modifier = Modifier
                                                    .padding(6.dp)
                                                    .size(15.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(9.dp))
                                            .clickable { onOpenSettings() }
                                            .testTag("open_settings_button"),
                                        color = StudioElevated
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Settings & Themes",
                                            tint = ElectricCyan,
                                            modifier = Modifier
                                                .padding(6.dp)
                                                .size(15.dp)
                                        )
                                    }
                                }
                            }

                            // Compact All Music + Fetch All Lyrics + Voice Shield row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LibraryMusic,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "All Music (${tracks.size})",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = ElectricCyan
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (missingLyricsCount > 0 && !batchLyrics.isRunning) {
                                        Surface(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .clickable { onFetchAllMissingLyrics() }
                                                .testTag("fetch_all_lyrics_button"),
                                            color = ElectricCyan.copy(alpha = 0.14f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CloudDownload,
                                                    contentDescription = "Fetch all lyrics",
                                                    tint = ElectricCyan,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Fetch all lyrics",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = ElectricCyan,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .clickable { onOpenVoiceShieldTab() }
                                            .testTag("voice_shield_status_pill"),
                                        color = MutedSage.copy(alpha = 0.14f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Shield,
                                                contentDescription = "Voice Shield Active",
                                                tint = MutedSage,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "$blockedVoiceNotesCount Blocked",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MutedSage,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Background Batch Lyrics Progress Indicator with Cancel Button
        if (batchLyrics.isRunning) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MutedSlateBlue.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .testTag("batch_lyrics_progress_card"),
                    color = StudioSlate
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Fetching lyrics ${batchLyrics.processed} / ${batchLyrics.total} (${batchLyrics.savedCount} saved)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = batchLyrics.currentTrackTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = onCancelBatchLyrics,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("cancel_batch_lyrics_button")
                            ) {
                                Text("Cancel", color = CrimsonDanger, fontWeight = FontWeight.Bold)
                            }
                        }
                        val progress = if (batchLyrics.total > 0) {
                            batchLyrics.processed.toFloat() / batchLyrics.total.toFloat()
                        } else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            color = ElectricCyan,
                            trackColor = StudioBorder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }

        // Audio Permission Prompt Card (when permission is not yet granted)
        if (!hasAudioPermission) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, WarmAmber.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
                    color = StudioSlate
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = WarmAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Audio Library Permission Required",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "Grant access to your device audio so Muffin Music Player can scan all your songs cleanly without WhatsApp voice notes or ringtones.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Button(
                            onClick = onRequestAudioPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = OledCarbon
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("grant_audio_permission_button")
                        ) {
                            Text("Grant Permission & Scan Music", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Compact Search Bar
        item {
            OutlinedTextField(
                value = filterSettings.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = "Search songs, artists, or albums…",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (filterSettings.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = StudioBorder,
                    focusedContainerColor = StudioSlate,
                    unfocusedContainerColor = StudioSlate
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 3.dp)
                    .testTag("library_search_input")
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (tracks.isNotEmpty() && !isSelectionMode) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPlayAllTracks,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = OledCarbon
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("library_play_all_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Play all",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, ElectricCyan.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                            .clickable { onShuffleTracks() }
                            .testTag("library_shuffle_button"),
                        color = StudioElevated
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle All Music",
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Shuffle",
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isScanning) "Scanning device music…" else "No music tracks found yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (!hasAudioPermission) {
                                "Tap Grant Permission above to load your local songs."
                            } else {
                                "Tap the refresh icon to rescan your device or use Import to pick audio files."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(tracks, key = { it.id }) { track ->
                val savedLyrics = savedLyricsMap[track.id]
                val isCurrent = track.id == currentTrackId
                val isSelected = track.id in selectedTrackIds
                val isFetchingLyrics = track.id in fetchingTrackIds

                CompactTrackRowCard(
                    track = track,
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && isPlaying,
                    isSelectionMode = isSelectionMode,
                    isSelected = isSelected,
                    isFetchingLyrics = isFetchingLyrics,
                    hasSavedLyrics = savedLyrics != null,
                    onRowClick = {
                        if (isSelectionMode) {
                            onToggleTrackSelection(track.id)
                        } else {
                            onPlayTrack(track)
                        }
                    },
                    onRowLongClick = {
                        if (isSelectionMode) {
                            onToggleTrackSelection(track.id)
                        } else {
                            onEnterSelectionMode(track.id)
                        }
                    },
                    onAutoFetchLyricsClick = { onAutoFetchLyrics(track) },
                    onManualLyricsPickerClick = { onOpenManualLyricsPicker(track) },
                    onPasteLyricsLinkClick = { onOpenPasteLyricsLink(track) },
                    onPasteLyricsTextClick = { onOpenPasteLyricsText(track) },
                    onImportLrcFileClick = { onImportLrcFile(track) },
                    onEditTagsClick = { onOpenQuickTagEditor(track) },
                    onDeleteClick = { onOpenDeleteModal(track) },
                    onAddToPlaylistClick = { onOpenAddToPlaylist(track) }
                )
            }
        }
    }
}

@Composable
private fun BulkActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable { onClick() },
        color = StudioSlate
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactTrackRowCard(
    track: TrackEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isFetchingLyrics: Boolean,
    hasSavedLyrics: Boolean,
    onRowClick: () -> Unit,
    onRowLongClick: () -> Unit,
    onAutoFetchLyricsClick: () -> Unit,
    onManualLyricsPickerClick: () -> Unit,
    onPasteLyricsLinkClick: () -> Unit,
    onPasteLyricsTextClick: () -> Unit,
    onImportLrcFileClick: () -> Unit,
    onEditTagsClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val borderColor = when {
        isSelected -> ElectricCyan
        isCurrent -> ElectricCyan.copy(alpha = 0.7f)
        else -> StudioBorder.copy(alpha = 0.6f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.5.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected || isCurrent) 1.dp else 0.7.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .combinedClickable(
                onClick = onRowClick,
                onLongClick = onRowLongClick
            )
            .testTag("track_item_card_${track.id}"),
        color = when {
            isSelected -> ElectricCyan.copy(alpha = 0.11f)
            isCurrent -> StudioElevated
            else -> StudioSlate
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Compact Thumbnail / Checkbox (34.dp)
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onRowClick() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = ElectricCyan,
                        checkmarkColor = OledCarbon,
                        uncheckedColor = TextSecondary
                    ),
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("track_checkbox_${track.id}")
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (isCurrent) ElectricCyan.copy(alpha = 0.18f)
                            else OledCarbon
                        )
                        .border(
                            width = 0.8.dp,
                            color = if (isCurrent) ElectricCyan else StudioBorder,
                            shape = RoundedCornerShape(9.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Equalizer else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Playing" else "Play",
                        tint = if (isCurrent) ElectricCyan else TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 2 Compact Lines: Title (line 1) + Artist • Duration (line 2)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent) ElectricCyan else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} • ${LrcParser.formatTimestamp(track.durationMs)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 15.sp),
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Small Lyrics Status / Auto-Fetch Icon (NEVER plays song)
            if (!isSelectionMode) {
                IconButton(
                    onClick = {
                        if (!isFetchingLyrics) {
                            onAutoFetchLyricsClick()
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("quick_fetch_lyrics_icon_${track.id}")
                ) {
                    when {
                        isFetchingLyrics -> {
                            CircularProgressIndicator(
                                color = ElectricCyan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        hasSavedLyrics -> {
                            Icon(
                                imageVector = Icons.Default.Lyrics,
                                contentDescription = "Offline lyrics saved",
                                tint = MutedSage,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Fetch lyrics automatically",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Overflow Menu
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("track_more_button_${track.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Track Options",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(StudioElevated)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Fetch Lyrics (Auto)", color = TextPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = ElectricCyan
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onAutoFetchLyricsClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Choose another version", color = TextPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lyrics,
                                    contentDescription = null,
                                    tint = MutedSlateBlue
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onManualLyricsPickerClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Paste lyrics link", color = TextPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    tint = ElectricCyan
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onPasteLyricsLinkClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Paste lyrics text", color = TextPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = null,
                                    tint = MutedSlateBlue
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onPasteLyricsTextClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import .lrc file", color = TextPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.FileOpen,
                                    contentDescription = null,
                                    tint = MutedSage
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onImportLrcFileClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to Playlist", color = TextPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.PlaylistAdd,
                                    contentDescription = null,
                                    tint = MutedSage
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
}
