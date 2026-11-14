package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.TrackEntity
import com.example.ui.AppTab
import com.example.ui.MusicPlayerUiState
import com.example.ui.MusicPlayerViewModel
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.BulkDeleteConfirmationDialog
import com.example.ui.components.DualModeDeleteDialog
import com.example.ui.components.OnlineLyricsSearchDialog
import com.example.ui.components.PasteLyricsLinkDialog
import com.example.ui.components.PasteLyricsTextDialog
import com.example.ui.components.QuickTagEditorDialog
import com.example.ui.components.SleepTimerDialog
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingLyricsScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.VoiceShieldScreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MutedSage
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.OledCarbon
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
                MuffinMusicApp()
            }
        }
    }
}

@Composable
fun MuffinMusicApp(
    viewModel: MusicPlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var trackForTagEdit by remember { mutableStateOf<TrackEntity?>(null) }
    var trackForDelete by remember { mutableStateOf<TrackEntity?>(null) }
    var trackForManualLyricsPaste by remember { mutableStateOf<TrackEntity?>(null) }
    var pendingLrcImportTrackId by remember { mutableStateOf<Long?>(null) }
    var tracksForPlaylistAdd by remember { mutableStateOf<Pair<String, List<Long>>?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showSleepTimerModal by remember { mutableStateOf(false) }

    val requiredAudioPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onAudioPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context,
            requiredAudioPermission
        ) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            viewModel.onAudioPermissionResult(true)
        } else {
            audioPermissionLauncher.launch(requiredAudioPermission)
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importExternalAudioUris(uris)
        }
    }

    val lrcPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val targetId = pendingLrcImportTrackId
        pendingLrcImportTrackId = null
        if (uri != null && targetId != null) {
            viewModel.importLrcFileForTrack(targetId, uri)
        }
    }

    // Back button exits selection mode first, otherwise returns to Library
    if (state.isSelectionMode) {
        BackHandler {
            viewModel.clearSelection()
        }
    } else if (state.activeTab != AppTab.LIBRARY) {
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
                if (state.activeTab != AppTab.PLAYER_LYRICS && state.currentTrack != null) {
                    MiniPlayerBar(
                        state = state,
                        onOpenFullPlayer = { viewModel.selectTab(AppTab.PLAYER_LYRICS) },
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onSkipNext = { viewModel.skipNext() },
                        onAutoFetchLyrics = {
                            state.currentTrack?.let { viewModel.autoFetchLyricsForTrack(it) }
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
                            selectedTextColor = MutedSage,
                            indicatorColor = MutedSage,
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
                        hasAudioPermission = state.hasAudioPermission,
                        isScanning = state.isScanning,
                        selectedTrackIds = state.selectedTrackIds,
                        fetchingTrackIds = state.fetchingTrackIds,
                        batchLyrics = state.batchLyrics,
                        savedLyricsMap = state.savedLyricsMap,
                        currentTrackId = state.currentTrack?.id,
                        isPlaying = state.isPlaying,
                        filterSettings = state.filterSettings,
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onPlayTrack = { track ->
                            viewModel.playTrack(track, openPlayerTab = false)
                        },
                        onEnterSelectionMode = viewModel::enterSelectionMode,
                        onToggleTrackSelection = viewModel::toggleTrackSelection,
                        onSelectAllTracks = viewModel::selectAllVisibleTracks,
                        onClearSelection = viewModel::clearSelection,
                        onBulkAddToPlaylist = {
                            val selected = state.selectedTrackIds.toList()
                            if (selected.isNotEmpty()) {
                                tracksForPlaylistAdd = "Add ${selected.size} song(s) to Playlist" to selected
                            }
                        },
                        onBulkHideFromLibrary = viewModel::hideSelectedTracks,
                        onBulkDeleteFromDevice = {
                            if (state.selectedTrackIds.isNotEmpty()) {
                                showBulkDeleteConfirm = true
                            }
                        },
                        onBulkFetchLyrics = viewModel::fetchLyricsForSelectedTracks,
                        onAutoFetchLyrics = viewModel::autoFetchLyricsForTrack,
                        onOpenManualLyricsPicker = viewModel::openLyricsSearchModal,
                        onOpenPasteLyricsLink = viewModel::openLinkLyricsModal,
                        onOpenPasteLyricsText = { trackForManualLyricsPaste = it },
                        onImportLrcFile = { track ->
                            pendingLrcImportTrackId = track.id
                            lrcPickerLauncher.launch(arrayOf("*/*"))
                        },
                        onFetchAllMissingLyrics = viewModel::fetchAllMissingLyrics,
                        onCancelBatchLyrics = viewModel::cancelBatchLyricsFetch,
                        onOpenQuickTagEditor = { trackForTagEdit = it },
                        onOpenDeleteModal = { trackForDelete = it },
                        onOpenAddToPlaylist = { track ->
                            tracksForPlaylistAdd = "Add \"${track.title}\" to Playlist" to listOf(track.id)
                        },
                        onOpenVoiceShieldTab = { viewModel.selectTab(AppTab.VOICE_SHIELD) },
                        onRequestAudioPermission = {
                            audioPermissionLauncher.launch(requiredAudioPermission)
                        },
                        onImportAudioFiles = {
                            audioPickerLauncher.launch(arrayOf("audio/*"))
                        },
                        onScanDeviceMedia = {
                            if (state.hasAudioPermission) {
                                viewModel.scanDeviceMediaStore(silent = false)
                            } else {
                                audioPermissionLauncher.launch(requiredAudioPermission)
                            }
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
                        onAutoFetchLyrics = {
                            state.currentTrack?.let { viewModel.autoFetchLyricsForTrack(it) }
                        },
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

    if (state.linkLyrics.isSheetOpen) {
        PasteLyricsLinkDialog(
            state = state.linkLyrics,
            onUrlChanged = viewModel::updateLinkLyricsUrl,
            onFetchClicked = viewModel::fetchLyricsFromLink,
            onPreviewTextChanged = viewModel::updateLinkLyricsPreviewText,
            onSaveOffline = viewModel::saveLinkLyricsOffline,
            onDismiss = viewModel::closeLinkLyricsModal
        )
    }

    if (trackForManualLyricsPaste != null) {
        val target = trackForManualLyricsPaste!!
        val existingLyrics = state.savedLyricsMap[target.id]?.rawLyrics.orEmpty()
        PasteLyricsTextDialog(
            track = target,
            existingRawLyrics = existingLyrics,
            onSave = { rawText ->
                viewModel.saveManualLyricsForTrack(target.id, rawText)
            },
            onDismiss = { trackForManualLyricsPaste = null }
        )
    }

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

    if (trackForDelete != null) {
        val target = trackForDelete!!
        DualModeDeleteDialog(
            track = target,
            onHideFromLibrary = { viewModel.hideTrackFromLibrary(target) },
            onDeleteFromDevice = { viewModel.deleteTrackFromDevice(target) },
            onDismiss = { trackForDelete = null }
        )
    }

    if (showBulkDeleteConfirm) {
        BulkDeleteConfirmationDialog(
            selectedCount = state.selectedTrackIds.size,
            onConfirmDelete = viewModel::deleteSelectedTracksFromDevice,
            onDismiss = { showBulkDeleteConfirm = false }
        )
    }

    if (tracksForPlaylistAdd != null) {
        val (titleLabel, trackIds) = tracksForPlaylistAdd!!
        AddToPlaylistDialog(
            titleLabel = titleLabel,
            playlists = state.playlists,
            onAddToPlaylist = { playlistId, name ->
                viewModel.addTracksToPlaylist(playlistId, trackIds, name)
            },
            onCreateAndAdd = { newName ->
                viewModel.createPlaylistAndAddTracks(newName, trackIds)
            },
            onDismiss = { tracksForPlaylistAdd = null }
        )
    }

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
    onAutoFetchLyrics: () -> Unit
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
                            modifier = Modifier.clickable { onAutoFetchLyrics() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = MutedSage,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Tap to auto-fetch lyrics",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedSage
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
