package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.TrackEntity
import com.example.ui.AppTab
import com.example.ui.MusicPlayerUiState
import com.example.ui.MusicPlayerViewModel
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.DualModeDeleteDialog
import com.example.ui.components.OnlineLyricsSearchDialog
import com.example.ui.components.QuickTagEditorDialog
import com.example.ui.components.SleepTimerDialog
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingLyricsScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.VoiceShieldScreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.OledCarbon
import com.example.ui.theme.SignalMint
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioSlate
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                PulseWaveApp()
            }
        }
    }
}

@Composable
fun PulseWaveApp(
    viewModel: MusicPlayerViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var trackForTagEdit by remember { mutableStateOf<TrackEntity?>(null) }
    var trackForDelete by remember { mutableStateOf<TrackEntity?>(null) }
    var trackForPlaylistAdd by remember { mutableStateOf<TrackEntity?>(null) }
    var showSleepTimerModal by remember { mutableStateOf(false) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importExternalAudioUris(uris)
        }
    }

    // Handle system Back button when on non-home tabs
    if (state.activeTab != AppTab.LIBRARY) {
        BackHandler {
            viewModel.selectTab(AppTab.LIBRARY)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = OledCarbon,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Floating Mini-Player + Live Lyric Preview Bar when not on Player Tab
                if (state.activeTab != AppTab.PLAYER_LYRICS && state.currentTrack != null) {
                    MiniPlayerBar(
                        state = state,
                        onOpenFullPlayer = { viewModel.selectTab(AppTab.PLAYER_LYRICS) },
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onSkipNext = { viewModel.skipNext() },
                        onOpenOnlineLyrics = {
                            viewModel.openLyricsSearchModal(state.currentTrack)
                        }
                    )
                }

                NavigationBar(
                    containerColor = StudioSlate,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = state.activeTab == AppTab.LIBRARY,
                        onClick = { viewModel.selectTab(AppTab.LIBRARY) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.LibraryMusic,
                                contentDescription = "Library"
                            )
                        },
                        label = { Text("Library") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = OledCarbon,
                            selectedTextColor = ElectricCyan,
                            indicatorColor = ElectricCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_tab_library")
                    )

                    NavigationBarItem(
                        selected = state.activeTab == AppTab.PLAYER_LYRICS,
                        onClick = { viewModel.selectTab(AppTab.PLAYER_LYRICS) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Lyrics,
                                contentDescription = "Player & Lyrics"
                            )
                        },
                        label = { Text("Lyrics & Play") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = OledCarbon,
                            selectedTextColor = ElectricCyan,
                            indicatorColor = ElectricCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_tab_player_lyrics")
                    )

                    NavigationBarItem(
                        selected = state.activeTab == AppTab.PLAYLISTS,
                        onClick = { viewModel.selectTab(AppTab.PLAYLISTS) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = "Playlists"
                            )
                        },
                        label = { Text("Playlists") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = OledCarbon,
                            selectedTextColor = ElectricCyan,
                            indicatorColor = ElectricCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_tab_playlists")
                    )

                    NavigationBarItem(
                        selected = state.activeTab == AppTab.VOICE_SHIELD,
                        onClick = { viewModel.selectTab(AppTab.VOICE_SHIELD) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Voice Shield"
                            )
                        },
                        label = { Text("Voice Shield") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = OledCarbon,
                            selectedTextColor = SignalMint,
                            indicatorColor = SignalMint,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_tab_voice_shield")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state.activeTab) {
                AppTab.LIBRARY -> {
                    LibraryScreen(
                        tracks = state.visibleTracks,
                        blockedVoiceNotesCount = state.blockedVoiceNotes.size,
                        savedLyricsMap = state.savedLyricsMap,
                        currentTrackId = state.currentTrack?.id,
                        isPlaying = state.isPlaying,
                        filterSettings = state.filterSettings,
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onSelectFormatChip = viewModel::selectFormatChip,
                        onPlayTrack = { track ->
                            viewModel.playTrack(track, openPlayerTab = false)
                        },
                        onOpenLyricsSearch = { track ->
                            viewModel.playTrack(track, openPlayerTab = true)
                            viewModel.openLyricsSearchModal(track)
                        },
                        onOpenQuickTagEditor = { trackForTagEdit = it },
                        onOpenDeleteModal = { trackForDelete = it },
                        onOpenAddToPlaylist = { trackForPlaylistAdd = it },
                        onOpenVoiceShieldTab = { viewModel.selectTab(AppTab.VOICE_SHIELD) },
                        onImportAudioFiles = {
                            audioPickerLauncher.launch(arrayOf("audio/*"))
                        },
                        onScanDeviceMedia = {
                            viewModel.scanDeviceMediaStore()
                        }
                    )
                }

                AppTab.PLAYER_LYRICS -> {
                    NowPlayingLyricsScreen(
                        track = state.currentTrack,
                        parsedLyrics = state.currentParsedLyrics,
                        activeLyricIndex = state.activeLyricLineIndex,
                        isPlaying = state.isPlaying,
                        isShuffle = state.isShuffleEnabled,
                        isRepeatOne = state.isRepeatOneEnabled,
                        positionMs = state.playbackPositionMs,
                        durationMs = state.playbackDurationMs,
                        sleepTimer = state.sleepTimer,
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onSeekTo = viewModel::seekTo,
                        onSkipNext = viewModel::skipNext,
                        onSkipPrev = viewModel::skipPrevious,
                        onToggleShuffle = viewModel::toggleShuffle,
                        onToggleRepeatOne = viewModel::toggleRepeatOne,
                        onOpenOnlineLyricsSearch = {
                            viewModel.openLyricsSearchModal(state.currentTrack)
                        },
                        onSaveManualLyrics = viewModel::saveManualLyricsToDevice,
                        onDeleteOfflineLyrics = viewModel::deleteOfflineLyricsForCurrentTrack,
                        onAdjustLyricOffset = viewModel::adjustLyricOffset,
                        onSetLyricOffsetExact = viewModel::setLyricOffsetExact,
                        onOpenSleepTimer = { showSleepTimerModal = true },
                        onOpenQuickTagEditor = {
                            state.currentTrack?.let { trackForTagEdit = it }
                        }
                    )
                }

                AppTab.PLAYLISTS -> {
                    PlaylistsScreen(
                        allCleanTracks = state.visibleTracks,
                        savedLyricsMap = state.savedLyricsMap,
                        customPlaylists = state.playlists,
                        onPlayTrack = { track ->
                            viewModel.playTrack(track, openPlayerTab = true)
                        },
                        onCreatePlaylist = viewModel::createNewPlaylist,
                        onDeletePlaylist = viewModel::deleteCustomPlaylist,
                        onAddTrackToPlaylist = viewModel::addTrackToPlaylist,
                        onRemoveTrackFromPlaylist = viewModel::removeTrackFromPlaylist
                    )
                }

                AppTab.VOICE_SHIELD -> {
                    VoiceShieldScreen(
                        filterSettings = state.filterSettings,
                        blacklistedFolders = state.blacklistedFolders,
                        blockedVoiceNotes = state.blockedVoiceNotes,
                        hiddenTracks = state.hiddenTracks,
                        onToggleStrictFormats = viewModel::toggleStrictKnownFormats,
                        onToggleBlockVoiceNotes = viewModel::toggleBlockWhatsAppVoiceNotes,
                        onToggleFolderRule = viewModel::toggleFolderBlacklistRule,
                        onAddCustomFolderRule = viewModel::addCustomBlacklistFolder,
                        onRemoveCustomFolderRule = viewModel::removeCustomBlacklistFolder,
                        onRestoreHiddenTrack = viewModel::restoreHiddenTrack
                    )
                }
            }

            // Transient Studio Status Toast Banner
            AnimatedVisibility(
                visible = state.statusBannerMessage != null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 20.dp, end = 20.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, ElectricCyan, RoundedCornerShape(50))
                        .clickable { viewModel.dismissBanner() },
                    color = StudioElevated
                ) {
                    Text(
                        text = state.statusBannerMessage.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricCyan,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }

    // Online Lyrics Search & Top 2-3 Websites Preview Modal
    if (state.lyricsSearch.isSheetOpen) {
        OnlineLyricsSearchDialog(
            state = state.lyricsSearch,
            onQueryChanged = viewModel::updateLyricsSearchQuery,
            onSearchClicked = { viewModel.executeOnlineLyricsSearch() },
            onSelectCandidate = viewModel::selectLyricCandidate,
            onSaveCandidateOffline = viewModel::saveCandidateLyricsToDevice,
            onDismiss = viewModel::closeLyricsSearchModal
        )
    }

    // Quick Tag Editor Modal
    if (trackForTagEdit != null) {
        val target = trackForTagEdit!!
        QuickTagEditorDialog(
            track = target,
            onSave = { title, artist, album ->
                viewModel.saveEditedTags(target.id, title, artist, album)
            },
            onDismiss = { trackForTagEdit = null }
        )
    }

    // Dual-Option Remove / Delete Modal
    if (trackForDelete != null) {
        val target = trackForDelete!!
        DualModeDeleteDialog(
            track = target,
            onHideFromLibrary = { viewModel.hideTrackFromLibrary(target) },
            onDeleteFromDevice = { viewModel.deleteTrackFromDevice(target) },
            onDismiss = { trackForDelete = null }
        )
    }

    // Add to Playlist Modal
    if (trackForPlaylistAdd != null) {
        val target = trackForPlaylistAdd!!
        AddToPlaylistDialog(
            track = target,
            playlists = state.playlists,
            onAddToPlaylist = { playlistId, name ->
                viewModel.addTrackToPlaylist(playlistId, target.id, name)
            },
            onCreateAndAdd = { newName ->
                viewModel.createNewPlaylist(newName, "Custom Mix")
            },
            onDismiss = { trackForPlaylistAdd = null }
        )
    }

    // Sleep Timer Modal
    if (showSleepTimerModal) {
        SleepTimerDialog(
            state = state.sleepTimer,
            onStartTimer = viewModel::startSleepTimer,
            onUpdateOptions = viewModel::updateSleepTimerOptions,
            onCancelTimer = viewModel::cancelSleepTimer,
            onDismiss = { showSleepTimerModal = false }
        )
    }
}

@Composable
private fun MiniPlayerBar(
    state: MusicPlayerUiState,
    onOpenFullPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onOpenOnlineLyrics: () -> Unit
) {
    val track = state.currentTrack ?: return
    val activeLineText = state.currentParsedLyrics?.lines
        ?.getOrNull(state.activeLyricLineIndex)
        ?.text
    val progress = (state.playbackPositionMs.toFloat() / state.playbackDurationMs.coerceAtLeast(1L))
        .coerceIn(0f, 1f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, ElectricCyan.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .clickable { onOpenFullPlayer() }
            .testTag("mini_player_bar"),
        color = StudioElevated
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = { progress },
                color = ElectricCyan,
                trackColor = StudioBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "• ${track.artist}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    if (activeLineText != null) {
                        Text(
                            text = "♪ $activeLineText",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onOpenOnlineLyrics() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = SignalMint,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Tap to fetch & sync lyrics offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = SignalMint
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan)
                            .testTag("mini_player_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = OledCarbon
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onSkipNext,
                        modifier = Modifier.testTag("mini_player_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = TextPrimary
                        )
                    }
                }
            }
        }
    }
}
