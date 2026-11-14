package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.PlaylistWithTracks
import com.example.data.local.SavedLyricsEntity
import com.example.data.local.TrackEntity
import com.example.ui.SmartPlaylistType
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.OledCarbon
import com.example.ui.theme.SignalMint
import com.example.ui.theme.SoftPurple
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioSlate
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber

@Composable
fun PlaylistsScreen(
    allCleanTracks: List<TrackEntity>,
    savedLyricsMap: Map<Long, SavedLyricsEntity>,
    customPlaylists: List<PlaylistWithTracks>,
    onPlayTrack: (TrackEntity) -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (Long, String) -> Unit,
    onAddTrackToPlaylist: (Long, Long, String) -> Unit,
    onRemoveTrackFromPlaylist: (Long, Long) -> Unit
) {
    var showCreateModal by remember { mutableStateOf(false) }
    var activeSmartType by remember { mutableStateOf<SmartPlaylistType?>(null) }
    var playlistForAddingTracks by remember { mutableStateOf<PlaylistWithTracks?>(null) }

    val recentlyAdded = remember(allCleanTracks) {
        allCleanTracks.sortedByDescending { it.dateAdded }
    }
    val mostPlayed = remember(allCleanTracks) {
        allCleanTracks.filter { it.playCount > 0 }.sortedByDescending { it.playCount }
    }
    val unplayed = remember(allCleanTracks) {
        allCleanTracks.filter { it.playCount == 0 }
    }
    val offlineLyricsTracks = remember(allCleanTracks, savedLyricsMap) {
        allCleanTracks.filter { savedLyricsMap.containsKey(it.id) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(OledCarbon)
            .testTag("playlists_lazy_column"),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 140.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Playlists & Smart Mixes",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Auto-curated studio queues & custom collections",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Button(
                    onClick = { showCreateModal = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = OledCarbon
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("new_playlist_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Playlist", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Smart Auto-Playlists Grid (2x2)
        item {
            Text(
                text = "SMART AUTO-PLAYLISTS",
                style = MaterialTheme.typography.labelLarge,
                color = ElectricCyan
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SmartPlaylistCard(
                        title = SmartPlaylistType.RECENTLY_ADDED.title,
                        count = recentlyAdded.size,
                        accent = ElectricCyan,
                        icon = Icons.Default.History,
                        onClick = { activeSmartType = SmartPlaylistType.RECENTLY_ADDED },
                        modifier = Modifier.weight(1f)
                    )
                    SmartPlaylistCard(
                        title = SmartPlaylistType.MOST_PLAYED.title,
                        count = mostPlayed.size,
                        accent = SignalMint,
                        icon = Icons.Default.TrendingUp,
                        onClick = { activeSmartType = SmartPlaylistType.MOST_PLAYED },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SmartPlaylistCard(
                        title = SmartPlaylistType.UNPLAYED.title,
                        count = unplayed.size,
                        accent = WarmAmber,
                        icon = Icons.Default.NewReleases,
                        onClick = { activeSmartType = SmartPlaylistType.UNPLAYED },
                        modifier = Modifier.weight(1f)
                    )
                    SmartPlaylistCard(
                        title = SmartPlaylistType.OFFLINE_LYRICS_READY.title,
                        count = offlineLyricsTracks.size,
                        accent = SoftPurple,
                        icon = Icons.Default.Lyrics,
                        onClick = { activeSmartType = SmartPlaylistType.OFFLINE_LYRICS_READY },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Expanded Smart Auto-Playlist View when tapped
        if (activeSmartType != null) {
            val smartTracks = when (activeSmartType!!) {
                SmartPlaylistType.RECENTLY_ADDED -> recentlyAdded
                SmartPlaylistType.MOST_PLAYED -> mostPlayed
                SmartPlaylistType.UNPLAYED -> unplayed
                SmartPlaylistType.OFFLINE_LYRICS_READY -> offlineLyricsTracks
            }
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, ElectricCyan, RoundedCornerShape(18.dp)),
                    color = StudioSlate
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = activeSmartType!!.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = ElectricCyan
                                )
                                Text(
                                    text = activeSmartType!!.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            if (smartTracks.isNotEmpty()) {
                                Button(
                                    onClick = { onPlayTrack(smartTracks.first()) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricCyan,
                                        contentColor = OledCarbon
                                    )
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Play", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (smartTracks.isEmpty()) {
                            Text(
                                text = "No tracks in this smart collection yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        } else {
                            smartTracks.forEach { track ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onPlayTrack(track) }
                                        .padding(vertical = 8.dp, horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "${track.artist} • ${track.playCount} plays",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary
                                        )
                                    }
                                    Text(
                                        text = track.extension.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SignalMint
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Custom User Playlists Header
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "YOUR CUSTOM PLAYLISTS (${customPlaylists.size})",
                style = MaterialTheme.typography.labelLarge,
                color = SignalMint
            )
        }

        items(customPlaylists, key = { it.playlist.id }) { pwt ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, StudioBorder, RoundedCornerShape(18.dp))
                    .testTag("custom_playlist_card_${pwt.playlist.id}"),
                color = StudioSlate
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = null,
                                tint = SignalMint,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = pwt.playlist.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary
                                )
                                if (pwt.playlist.description.isNotBlank()) {
                                    Text(
                                        text = pwt.playlist.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { playlistForAddingTracks = pwt },
                                modifier = Modifier.testTag("add_songs_to_playlist_${pwt.playlist.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Songs",
                                    tint = ElectricCyan
                                )
                            }
                            IconButton(
                                onClick = { onDeletePlaylist(pwt.playlist.id, pwt.playlist.name) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Playlist",
                                    tint = CrimsonDanger
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = StudioBorder)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (pwt.tracks.isEmpty()) {
                        Text(
                            text = "Empty playlist — tap '+' to add songs from your library.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    } else {
                        pwt.tracks.forEach { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onPlayTrack(track) }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = track.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${track.artist} • .${track.extension.uppercase()}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onRemoveTrackFromPlaylist(pwt.playlist.id, track.id) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RemoveCircleOutline,
                                        contentDescription = "Remove from playlist",
                                        tint = TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateModal) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateModal = false },
            containerColor = StudioSlate,
            titleContentColor = TextPrimary,
            title = { Text("Create New Playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Playlist Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_playlist_name_input")
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Description (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreatePlaylist(name, desc)
                        showCreateModal = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = OledCarbon
                    ),
                    modifier = Modifier.testTag("confirm_create_playlist_button")
                ) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateModal = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (playlistForAddingTracks != null) {
        val targetPlaylist = playlistForAddingTracks!!
        val existingIds = targetPlaylist.tracks.map { it.id }.toSet()

        AlertDialog(
            onDismissRequest = { playlistForAddingTracks = null },
            containerColor = StudioSlate,
            titleContentColor = TextPrimary,
            title = { Text("Add Songs to ${targetPlaylist.playlist.name}") },
            text = {
                LazyColumn(
                    modifier = Modifier.height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(allCleanTracks, key = { it.id }) { track ->
                        val alreadyIn = track.id in existingIds
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !alreadyIn) {
                                    onAddTrackToPlaylist(
                                        targetPlaylist.playlist.id,
                                        track.id,
                                        targetPlaylist.playlist.name
                                    )
                                },
                            color = if (alreadyIn) OledCarbon else StudioElevated
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (alreadyIn) TextMuted else TextPrimary
                                    )
                                    Text(
                                        text = track.artist,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                                Text(
                                    text = if (alreadyIn) "Added" else "+ Add",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (alreadyIn) SignalMint else ElectricCyan
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { playlistForAddingTracks = null }) {
                    Text("Done", color = ElectricCyan)
                }
            }
        )
    }
}

@Composable
private fun SmartPlaylistCard(
    title: String,
    count: Int,
    accent: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = StudioSlate
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
