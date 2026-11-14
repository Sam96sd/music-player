package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.PlaylistWithTracks
import com.example.data.local.TrackEntity
import com.example.data.network.OnlineLyricCandidate
import com.example.ui.LyricsSearchUiState
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

@Composable
fun OnlineLyricsSearchDialog(
    state: LyricsSearchUiState,
    onQueryChanged: (String, String) -> Unit,
    onSearchClicked: () -> Unit,
    onSelectCandidate: (String) -> Unit,
    onSaveCandidateOffline: (OnlineLyricCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, StudioBorder, RoundedCornerShape(24.dp)),
            color = StudioSlate
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Online Lyrics Search",
                            tint = ElectricCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Online Lyrics Finder",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = "Fetches top 2–3 lyric sites & saves offline to device",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_lyrics_modal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = state.queryTitle,
                        onValueChange = { onQueryChanged(it, state.queryArtist) },
                        label = { Text("Song Title") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("lyrics_search_title_input")
                    )
                    OutlinedTextField(
                        value = state.queryArtist,
                        onValueChange = { onQueryChanged(state.queryTitle, it) },
                        label = { Text("Artist") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("lyrics_search_artist_input")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onSearchClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("execute_online_lyrics_search_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = OledCarbon
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Search Top Lyric Providers",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (state.isSearching) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = ElectricCyan,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Querying LRCLIB.net, OpenLRC & Lyrics.ovh…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                } else if (state.candidates.isNotEmpty()) {
                    Text(
                        text = "TOP ${state.candidates.size} WEBSITE RESULTS — TAP TO PREVIEW",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricCyan
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val selectedCandidate = state.candidates.find { it.id == state.selectedCandidateId }
                        ?: state.candidates.first()

                    state.candidates.forEachIndexed { idx, candidate ->
                        val isSelected = candidate.id == selectedCandidate.id
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) StudioElevated else OledCarbon)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) ElectricCyan else StudioBorder,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { onSelectCandidate(candidate.id) }
                                .padding(12.dp)
                                .testTag("lyric_candidate_card_$idx")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${idx + 1}. ${candidate.providerName}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (candidate.isSynced) SignalMint.copy(alpha = 0.18f)
                                                    else WarmAmber.copy(alpha = 0.18f)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (candidate.isSynced) "SYNCED LRC" else "PLAIN TEXT",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (candidate.isSynced) SignalMint else WarmAmber
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${candidate.trackTitle} • ${candidate.artistName} (${candidate.matchConfidence})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "PREVIEW (${selectedCandidate.providerName})",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 110.dp, max = 180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(OledCarbon)
                            .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = selectedCandidate.rawLyrics,
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { onSaveCandidateOffline(selectedCandidate) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_lyrics_offline_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SignalMint,
                            contentColor = OledCarbon
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save to Device for Offline Sync",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (state.errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CrimsonDanger
                    )
                }
            }
        }
    }
}

@Composable
fun QuickTagEditorDialog(
    track: TrackEntity,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var album by remember(track.id) { mutableStateOf(track.album) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSlate,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        icon = {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Quick Tag Editor",
                tint = ElectricCyan
            )
        },
        title = {
            Text(
                text = "Quick Tag Editor",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Fix messy track names or artist tags so library sorting and online lyrics search match accurately.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Track Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tag_editor_title_input")
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tag_editor_artist_input")
                )
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tag_editor_album_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(title, artist, album)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = OledCarbon
                ),
                modifier = Modifier.testTag("save_tags_confirm_button")
            ) {
                Text("Save Tags", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
fun DualModeDeleteDialog(
    track: TrackEntity,
    onHideFromLibrary: () -> Unit,
    onDeleteFromDevice: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSlate,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = "Remove \"${track.title}\"?",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Choose whether to safely hide this track from Muffin Music Player or permanently delete the file from device storage:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                // Option 1: Hide from Library (Safe)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .clickable {
                            onHideFromLibrary()
                            onDismiss()
                        }
                        .testTag("hide_track_safe_button"),
                    color = StudioElevated
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = ElectricCyan
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Hide from Library (Safe)",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Excludes song from queues. Can be restored anytime in Voice Shield.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Option 2: Delete File from Device (Permanent)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, CrimsonDanger.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .clickable {
                            onDeleteFromDevice()
                            onDismiss()
                        }
                        .testTag("delete_track_device_button"),
                    color = CrimsonDanger.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = CrimsonDanger
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Delete File from Device",
                                style = MaterialTheme.typography.titleSmall,
                                color = CrimsonDanger,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Permanently erases ${track.filePath.substringAfterLast('/')} from storage.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerDialog(
    state: SleepTimerState,
    onStartTimer: (Int, Boolean, Boolean) -> Unit,
    onUpdateOptions: (Boolean, Boolean) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    var gentleFade by remember(state.gentleFadeOut) { mutableStateOf(state.gentleFadeOut) }
    var finishTrack by remember(state.finishCurrentTrack) { mutableStateOf(state.finishCurrentTrack) }
    val presets = listOf(10, 15, 30, 45, 60)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSlate,
        titleContentColor = TextPrimary,
        icon = {
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = "Sleep Timer",
                tint = WarmAmber
            )
        },
        title = {
            Text(
                text = "Sleep Timer & Fade-Out",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.isActive) {
                    val mins = state.remainingSeconds / 60
                    val secs = state.remainingSeconds % 60
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(WarmAmber.copy(alpha = 0.14f))
                            .border(1.dp, WarmAmber, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = if (state.waitingForTrackEnd) {
                                "Finishing current song before pausing…"
                            } else {
                                "Active countdown: %02d:%02d remaining".format(mins, secs)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = WarmAmber
                        )
                    }
                }

                Text(
                    text = "Select duration preset:",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { mins ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                onStartTimer(mins, gentleFade, finishTrack)
                                onDismiss()
                            },
                            label = { Text("${mins}m") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = StudioElevated,
                                labelColor = TextPrimary
                            ),
                            modifier = Modifier.testTag("sleep_preset_${mins}m")
                        )
                    }
                }

                HorizontalDivider(color = StudioBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Gentle 30s Volume Fade-Out",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Gradually lowers volume instead of stopping abruptly",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = gentleFade,
                        onCheckedChange = {
                            gentleFade = it
                            onUpdateOptions(gentleFade, finishTrack)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = ElectricCyan)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Finish Current Song",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Waits until the active track ends before pausing",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = finishTrack,
                        onCheckedChange = {
                            finishTrack = it
                            onUpdateOptions(gentleFade, finishTrack)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = SignalMint)
                    )
                }
            }
        },
        confirmButton = {
            if (state.isActive) {
                OutlinedButton(
                    onClick = {
                        onCancelTimer()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("cancel_sleep_timer_button")
                ) {
                    Text("Turn Off Timer", color = CrimsonDanger)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    )
}

@Composable
fun AddToPlaylistDialog(
    track: TrackEntity,
    playlists: List<PlaylistWithTracks>,
    onAddToPlaylist: (Long, String) -> Unit,
    onCreateAndAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newPlaylistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSlate,
        titleContentColor = TextPrimary,
        icon = {
            Icon(
                imageVector = Icons.Default.PlaylistAdd,
                contentDescription = null,
                tint = ElectricCyan
            )
        },
        title = {
            Text(
                text = "Add \"${track.title}\" to Playlist",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (playlists.isNotEmpty()) {
                    Text(
                        text = "EXISTING PLAYLISTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    playlists.forEach { pwt ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onAddToPlaylist(pwt.playlist.id, pwt.playlist.name)
                                    onDismiss()
                                },
                            color = StudioElevated
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = pwt.playlist.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${pwt.tracks.size} songs",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = StudioBorder)

                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Or create new playlist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (newPlaylistName.isNotBlank()) {
                Button(
                    onClick = {
                        onCreateAndAdd(newPlaylistName)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = OledCarbon
                    )
                ) {
                    Text("Create & Add", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    )
}
