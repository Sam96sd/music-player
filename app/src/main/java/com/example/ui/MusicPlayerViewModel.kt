package com.example.ui

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.MusicRepository
import com.example.data.local.BlacklistedFolderEntity
import com.example.data.local.PlaylistWithTracks
import com.example.data.local.PulseWaveDatabase
import com.example.data.local.SavedLyricsEntity
import com.example.data.local.TrackEntity
import com.example.data.network.OnlineLyricCandidate
import com.example.domain.FilteredTrackItem
import com.example.domain.LrcParser
import com.example.domain.ParsedLyrics
import com.example.domain.PlaybackQueueManager
import com.example.domain.VoiceNoteFilterEngine
import com.example.player.FixedShuffleOrder
import com.example.player.MediaItemMapper
import com.example.player.PlaybackService
import com.example.ui.theme.AppThemePreset
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppTab {
    LIBRARY,
    PLAYLISTS,
    PLAYER_LYRICS,
    VOICE_SHIELD
}

enum class SmartPlaylistType(val title: String, val subtitle: String) {
    RECENTLY_ADDED("Recently Added", "Latest music arrivals"),
    MOST_PLAYED("Most Played", "Your heavy rotation tracks"),
    UNPLAYED("Unplayed Gems", "Fresh tracks with 0 plays"),
    OFFLINE_LYRICS_READY("Offline Lyrics Ready", "Songs with cached LRC synced on device")
}

data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val gentleFadeOut: Boolean = true,
    val finishCurrentTrack: Boolean = true,
    val waitingForTrackEnd: Boolean = false
)

data class LyricsSearchUiState(
    val isSheetOpen: Boolean = false,
    val isSearching: Boolean = false,
    val targetTrackId: Long? = null,
    val queryTitle: String = "",
    val queryArtist: String = "",
    val hasRememberedQuery: Boolean = false,
    val candidates: List<OnlineLyricCandidate> = emptyList(),
    val selectedCandidateId: String? = null,
    val errorMessage: String? = null
)

data class LinkLyricsUiState(
    val isSheetOpen: Boolean = false,
    val targetTrack: TrackEntity? = null,
    val urlInput: String = "",
    val isLoading: Boolean = false,
    val hasPreview: Boolean = false,
    val previewText: String = "",
    val sourceLabel: String = "",
    val isSynced: Boolean = false,
    val errorMessage: String? = null
)

data class BatchLyricsProgress(
    val isRunning: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val savedCount: Int = 0,
    val currentTrackTitle: String = ""
)

data class FilterSettingsState(
    val strictKnownFormatsOnly: Boolean = true,
    val blockWhatsAppVoiceNotes: Boolean = true,
    val searchQuery: String = ""
)

data class MusicPlayerUiState(
    val activeTab: AppTab = AppTab.LIBRARY,
    val hasAudioPermission: Boolean = false,
    val isScanning: Boolean = false,
    val selectedTrackIds: Set<Long> = emptySet(),
    val fetchingTrackIds: Set<Long> = emptySet(),
    val batchLyrics: BatchLyricsProgress = BatchLyricsProgress(),
    val visibleTracks: List<TrackEntity> = emptyList(),
    val blockedVoiceNotes: List<FilteredTrackItem> = emptyList(),
    val hiddenTracks: List<TrackEntity> = emptyList(),
    val playlists: List<PlaylistWithTracks> = emptyList(),
    val blacklistedFolders: List<BlacklistedFolderEntity> = emptyList(),
    val savedLyricsMap: Map<Long, SavedLyricsEntity> = emptyMap(),
    val currentTrack: TrackEntity? = null,
    val currentParsedLyrics: ParsedLyrics? = null,
    val activeLyricLineIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val isRepeatOneEnabled: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val playbackDurationMs: Long = 40_000L,
    val filterSettings: FilterSettingsState = FilterSettingsState(),
    val sleepTimer: SleepTimerState = SleepTimerState(),
    val lyricsSearch: LyricsSearchUiState = LyricsSearchUiState(),
    val linkLyrics: LinkLyricsUiState = LinkLyricsUiState(),
    val themePreset: AppThemePreset = AppThemePreset.PASTEL_DUSK,
    val statusBannerMessage: String? = null
) {
    val isSelectionMode: Boolean get() = selectedTrackIds.isNotEmpty()
}

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.applicationContext.getSharedPreferences(
        "muffin_preferences",
        Context.MODE_PRIVATE
    )

    private val repository: MusicRepository = MusicRepository(
        context = application.applicationContext,
        dao = PulseWaveDatabase.getInstance(application.applicationContext).musicDao()
    )

    private val _activeTab = MutableStateFlow(AppTab.LIBRARY)
    private val _hasAudioPermission = MutableStateFlow(checkAudioPermissionGranted())
    private val _isScanning = MutableStateFlow(false)
    private val _selectedTrackIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _fetchingTrackIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _batchLyrics = MutableStateFlow(BatchLyricsProgress())
    private val _currentTrackId = MutableStateFlow<Long?>(null)
    private val _isPlaying = MutableStateFlow(false)
    private val _isShuffle = MutableStateFlow(false)
    private val _isRepeatOne = MutableStateFlow(false)
    private val _playbackPositionMs = MutableStateFlow(0L)
    private val _playbackDurationMs = MutableStateFlow(40_000L)
    private val _filterSettings = MutableStateFlow(FilterSettingsState())
    private val _sleepTimer = MutableStateFlow(SleepTimerState())
    private val _lyricsSearch = MutableStateFlow(LyricsSearchUiState())
    private val _linkLyrics = MutableStateFlow(LinkLyricsUiState())
    private val _themePreset = MutableStateFlow(
        AppThemePreset.fromStorageKey(prefs.getString("app_theme_preset", null))
    )
    private val _statusBanner = MutableStateFlow<String?>(null)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var pendingControllerAction: ((MediaController) -> Unit)? = null
    private var progressTickerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var batchLyricsJob: Job? = null
    private var currentVolume: Float = 1.0f

    private val controllerPlayerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (_sleepTimer.value.waitingForTrackEnd &&
                (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
            ) {
                mediaController?.pause()
                mediaController?.seekTo(0L)
                _isPlaying.value = false
                _playbackPositionMs.value = 0L
                cancelSleepTimer()
                showBanner("Sleep Timer finished after final track")
                return
            }
            _currentTrackId.value = mediaItem?.mediaId?.toLongOrNull()
            mediaController?.let { controller ->
                _playbackPositionMs.value = controller.currentPosition.coerceAtLeast(0L)
                if (controller.duration > 0L) {
                    _playbackDurationMs.value = controller.duration
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            mediaController?.let { controller ->
                _playbackPositionMs.value = controller.currentPosition.coerceAtLeast(0L)
                if (controller.duration > 0L) {
                    _playbackDurationMs.value = controller.duration
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val controller = mediaController ?: return
            if (controller.duration > 0L) {
                _playbackDurationMs.value = controller.duration
            }
            if (playbackState == Player.STATE_ENDED && _sleepTimer.value.waitingForTrackEnd) {
                _isPlaying.value = false
                _playbackPositionMs.value = 0L
                cancelSleepTimer()
                showBanner("Sleep Timer finished after final track")
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _isShuffle.value = shuffleModeEnabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _isRepeatOne.value = (repeatMode == Player.REPEAT_MODE_ONE)
        }

        override fun onPlayerError(error: PlaybackException) {
            _isPlaying.value = false
            showBanner("Unable to play track (${error.errorCodeName}) — try rescanning library")
        }
    }

    private data class CoreDataSnapshot(
        val allTracks: List<TrackEntity>,
        val lyricsList: List<SavedLyricsEntity>,
        val playlists: List<PlaylistWithTracks>,
        val folders: List<BlacklistedFolderEntity>
    )

    private val coreDataFlow = combine(
        repository.allTracks,
        repository.allSavedLyrics,
        repository.playlistsWithTracks,
        repository.blacklistedFolders
    ) { tracks, lyrics, playlists, folders ->
        CoreDataSnapshot(tracks, lyrics, playlists, folders)
    }

    private data class PlaybackSnapshot(
        val activeTab: AppTab,
        val currentTrackId: Long?,
        val isPlaying: Boolean,
        val isShuffle: Boolean,
        val isRepeatOne: Boolean,
        val positionMs: Long,
        val durationMs: Long
    )

    private val playbackFlow = combine(
        _activeTab,
        _currentTrackId,
        _isPlaying,
        _isShuffle,
        _isRepeatOne
    ) { tab, trackId, playing, shuffle, repeat ->
        PlaybackSnapshot(tab, trackId, playing, shuffle, repeat, 0L, 0L)
    }.combine(_playbackPositionMs) { snap, pos ->
        snap.copy(positionMs = pos)
    }.combine(_playbackDurationMs) { snap, dur ->
        snap.copy(durationMs = dur)
    }

    private data class MetaSnapshot(
        val hasPermission: Boolean,
        val isScanning: Boolean,
        val statusBanner: String?,
        val selectedIds: Set<Long>,
        val fetchingIds: Set<Long>,
        val batchLyrics: BatchLyricsProgress,
        val linkLyrics: LinkLyricsUiState,
        val themePreset: AppThemePreset
    )

    private val metaFlow = combine(
        _hasAudioPermission,
        _isScanning,
        _statusBanner,
        _selectedTrackIds,
        _fetchingTrackIds
    ) { perm, scanning, banner, selected, fetching ->
        MetaSnapshot(
            perm,
            scanning,
            banner,
            selected,
            fetching,
            BatchLyricsProgress(),
            LinkLyricsUiState(),
            AppThemePreset.PASTEL_DUSK
        )
    }.combine(_batchLyrics) { meta, batch ->
        meta.copy(batchLyrics = batch)
    }.combine(_linkLyrics) { meta, linkState ->
        meta.copy(linkLyrics = linkState)
    }.combine(_themePreset) { meta, theme ->
        meta.copy(themePreset = theme)
    }

    val uiState: StateFlow<MusicPlayerUiState> = combine(
        coreDataFlow,
        playbackFlow,
        _filterSettings,
        _sleepTimer,
        _lyricsSearch
    ) { core, playback, filter, sleep, lyricsModal ->
        val lyricsMap = core.lyricsList.associateBy { it.trackId }
        val visible = mutableListOf<TrackEntity>()
        val blocked = mutableListOf<FilteredTrackItem>()
        val hidden = core.allTracks.filter { it.isHidden }

        for (track in core.allTracks) {
            if (track.isHidden) continue
            val verdict = VoiceNoteFilterEngine.evaluateTrack(
                track = track,
                blacklistedFolders = core.folders,
                strictKnownFormatsOnly = filter.strictKnownFormatsOnly,
                blockVoiceNotePatterns = filter.blockWhatsAppVoiceNotes
            )
            if (verdict.isAllowed) {
                val q = filter.searchQuery.trim().lowercase()
                val matchesQuery = q.isEmpty() ||
                    track.title.lowercase().contains(q) ||
                    track.artist.lowercase().contains(q) ||
                    track.album.lowercase().contains(q)

                if (matchesQuery) {
                    visible.add(track)
                }
            } else {
                blocked.add(
                    FilteredTrackItem(
                        track = track,
                        reason = verdict.blockedReason ?: "Excluded by filter",
                        ruleLabel = verdict.matchedRuleLabel ?: "Voice Shield"
                    )
                )
            }
        }

        val activeTrack = core.allTracks.find { it.id == playback.currentTrackId }
            ?: visible.firstOrNull()

        val savedLyricsEntity = activeTrack?.let { lyricsMap[it.id] }
        val parsedLyrics = savedLyricsEntity?.let {
            LrcParser.parse(it.rawLyrics, it.sourceName)
        }
        val activeLineIdx = if (parsedLyrics != null && activeTrack != null) {
            LrcParser.findActiveLineIndex(
                lines = parsedLyrics.lines,
                currentPositionMs = playback.positionMs,
                lyricOffsetMs = activeTrack.lyricOffsetMs
            )
        } else {
            -1
        }

        val allowedIds = core.allTracks.filter {
            !it.isHidden && VoiceNoteFilterEngine.evaluateTrack(
                it,
                core.folders,
                filter.strictKnownFormatsOnly,
                filter.blockWhatsAppVoiceNotes
            ).isAllowed
        }.map { it.id }.toSet()

        val cleanPlaylists = core.playlists.map { pwt ->
            pwt.copy(tracks = pwt.tracks.filter { it.id in allowedIds })
        }

        MusicPlayerUiState(
            activeTab = playback.activeTab,
            visibleTracks = visible,
            blockedVoiceNotes = blocked,
            hiddenTracks = hidden,
            playlists = cleanPlaylists,
            blacklistedFolders = core.folders,
            savedLyricsMap = lyricsMap,
            currentTrack = activeTrack,
            currentParsedLyrics = parsedLyrics,
            activeLyricLineIndex = activeLineIdx,
            isPlaying = playback.isPlaying,
            isShuffleEnabled = playback.isShuffle,
            isRepeatOneEnabled = playback.isRepeatOne,
            playbackPositionMs = playback.positionMs,
            playbackDurationMs = activeTrack?.durationMs ?: playback.durationMs,
            filterSettings = filter,
            sleepTimer = sleep,
            lyricsSearch = lyricsModal
        )
    }.combine(metaFlow) { state, meta ->
        val visibleIdSet = state.visibleTracks.map { it.id }.toSet()
        state.copy(
            hasAudioPermission = meta.hasPermission,
            isScanning = meta.isScanning,
            statusBannerMessage = meta.statusBanner,
            selectedTrackIds = meta.selectedIds.intersect(visibleIdSet),
            fetchingTrackIds = meta.fetchingIds,
            batchLyrics = meta.batchLyrics,
            linkLyrics = meta.linkLyrics,
            themePreset = meta.themePreset
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MusicPlayerUiState(
            hasAudioPermission = checkAudioPermissionGranted(),
            themePreset = AppThemePreset.fromStorageKey(prefs.getString("app_theme_preset", null))
        )
    )

    init {
        connectMediaController()
        viewModelScope.launch {
            repository.ensureDefaultFilterRules()
            if (checkAudioPermissionGranted()) {
                _hasAudioPermission.value = true
                scanDeviceMediaStore(silent = true)
            }
        }
        startProgressTicker()
    }

    private fun connectMediaController() {
        if (controllerFuture != null) return
        val app = getApplication<Application>()
        val sessionToken = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { controller ->
                        mediaController = controller
                        controller.addListener(controllerPlayerListener)
                        syncStateFromController(controller)
                        pendingControllerAction?.let { action ->
                            pendingControllerAction = null
                            action(controller)
                        }
                    }
                    .onFailure {
                        controllerFuture = null
                    }
            },
            ContextCompat.getMainExecutor(app)
        )
    }

    private fun syncStateFromController(controller: MediaController) {
        val activeId = controller.currentMediaItem?.mediaId?.toLongOrNull()
        if (activeId != null) {
            _currentTrackId.value = activeId
        }
        _isPlaying.value = controller.isPlaying
        _isShuffle.value = controller.shuffleModeEnabled
        _isRepeatOne.value = (controller.repeatMode == Player.REPEAT_MODE_ONE)
        _playbackPositionMs.value = controller.currentPosition.coerceAtLeast(0L)
        if (controller.duration > 0L) {
            _playbackDurationMs.value = controller.duration
        }
    }

    private fun withController(action: (MediaController) -> Unit) {
        val controller = mediaController
        if (controller != null && controller.isConnected) {
            action(controller)
        } else {
            pendingControllerAction = action
            connectMediaController()
        }
    }

    private fun checkAudioPermissionGranted(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(
            getApplication(),
            perm
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun onAudioPermissionResult(granted: Boolean) {
        _hasAudioPermission.value = granted
        if (granted) {
            scanDeviceMediaStore(silent = false)
        } else {
            showBanner("Audio permission is needed to scan songs on your device")
        }
    }

    fun selectTab(tab: AppTab) {
        _activeTab.value = tab
    }

    fun setThemePreset(preset: AppThemePreset) {
        _themePreset.value = preset
        prefs.edit().putString("app_theme_preset", preset.storageKey).apply()
        showBanner("Theme changed to ${preset.displayName}")
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    private fun showBanner(message: String) {
        _statusBanner.value = message
        viewModelScope.launch {
            delay(3800)
            if (_statusBanner.value == message) {
                _statusBanner.value = null
            }
        }
    }

    // Multi-Select Controls
    fun enterSelectionMode(trackId: Long) {
        _selectedTrackIds.update { it + trackId }
    }

    fun toggleTrackSelection(trackId: Long) {
        _selectedTrackIds.update { current ->
            if (trackId in current) current - trackId else current + trackId
        }
    }

    fun selectAllVisibleTracks() {
        val allIds = uiState.value.visibleTracks.map { it.id }.toSet()
        _selectedTrackIds.update { current ->
            if (current.size == allIds.size && allIds.isNotEmpty()) emptySet() else allIds
        }
    }

    fun clearSelection() {
        _selectedTrackIds.value = emptySet()
    }

    fun hideSelectedTracks() {
        val ids = _selectedTrackIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val currentId = uiState.value.currentTrack?.id
            if (currentId != null && currentId in ids) {
                mediaController?.pause()
                removeTracksFromControllerQueue(ids.toSet())
                _isPlaying.value = false
            } else {
                removeTracksFromControllerQueue(ids.toSet())
            }
            repository.setTracksHidden(ids, true)
            clearSelection()
            showBanner("Hidden ${ids.size} song(s) from library")
        }
    }

    fun deleteSelectedTracksFromDevice() {
        val ids = _selectedTrackIds.value
        val targets = uiState.value.visibleTracks.filter { it.id in ids }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            val currentId = uiState.value.currentTrack?.id
            if (currentId != null && currentId in ids) {
                mediaController?.pause()
                _isPlaying.value = false
                _currentTrackId.value = null
            }
            removeTracksFromControllerQueue(ids)
            repository.deleteTracksFromDevice(targets)
            clearSelection()
            showBanner("Deleted ${targets.size} file(s) from device")
        }
    }

    fun addTracksToPlaylist(playlistId: Long, trackIds: List<Long>, playlistName: String) {
        if (trackIds.isEmpty()) return
        viewModelScope.launch {
            repository.addTracksToPlaylist(playlistId, trackIds)
            clearSelection()
            showBanner("Added ${trackIds.size} song(s) to \"$playlistName\"")
        }
    }

    fun createPlaylistAndAddTracks(name: String, trackIds: List<Long>) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newId = repository.createPlaylist(name, "")
            if (trackIds.isNotEmpty()) {
                repository.addTracksToPlaylist(newId, trackIds)
            }
            clearSelection()
            showBanner("Created \"${name.trim()}\" with ${trackIds.size} song(s)")
        }
    }

    // Playback Controls via Media3 MediaController -> PlaybackService
    fun playAll(tracks: List<TrackEntity>, openPlayerTab: Boolean = false) {
        if (tracks.isEmpty()) return
        if (openPlayerTab) {
            _activeTab.value = AppTab.PLAYER_LYRICS
        }
        val mediaItems = tracks.map(MediaItemMapper::toMediaItem)
        _currentTrackId.value = tracks.first().id
        _playbackDurationMs.value = tracks.first().durationMs
        _playbackPositionMs.value = 0L
        _isShuffle.value = false

        withController { controller ->
            controller.shuffleModeEnabled = false
            controller.repeatMode = if (_isRepeatOne.value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
            controller.setMediaItems(mediaItems, 0, 0L)
            controller.volume = currentVolume
            controller.prepare()
            controller.play()
        }
    }

    fun shuffleAndPlay(tracks: List<TrackEntity>, openPlayerTab: Boolean = false) {
        if (tracks.isEmpty()) return
        if (openPlayerTab) {
            _activeTab.value = AppTab.PLAYER_LYRICS
        }
        val mediaItems = tracks.map(MediaItemMapper::toMediaItem)
        val shuffledIndices = PlaybackQueueManager.fisherYatesShuffle(tracks.indices.toList()).toIntArray()
        val firstIdx = shuffledIndices.firstOrNull() ?: 0
        val firstTrack = tracks[firstIdx]

        _currentTrackId.value = firstTrack.id
        _playbackDurationMs.value = firstTrack.durationMs
        _playbackPositionMs.value = 0L
        _isShuffle.value = true

        withController { controller ->
            controller.repeatMode = if (_isRepeatOne.value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
            controller.setMediaItems(mediaItems, firstIdx, 0L)
            controller.volume = currentVolume
            controller.sendCustomCommand(
                MediaItemMapper.CMD_PLAY_WITH_SHUFFLE_ORDER,
                Bundle().apply {
                    putIntArray(MediaItemMapper.EXTRA_SHUFFLED_INDICES, shuffledIndices)
                }
            )
        }
        showBanner("Shuffling ${tracks.size} song(s)")
    }

    fun playTrack(
        track: TrackEntity,
        openPlayerTab: Boolean = false,
        queueContext: List<TrackEntity>? = null
    ) {
        if (openPlayerTab) {
            _activeTab.value = AppTab.PLAYER_LYRICS
        }
        withController { controller ->
            val currentControllerId = controller.currentMediaItem?.mediaId?.toLongOrNull()
            val queueMatches = queueContext == null || queueMatchesController(controller, queueContext)
            if (currentControllerId == track.id && controller.mediaItemCount > 0 && queueMatches) {
                if (controller.isPlaying) {
                    controller.pause()
                } else {
                    controller.prepare()
                    controller.play()
                }
                return@withController
            }

            if (queueContext == null) {
                val existingIdx = findTrackIndexInController(controller, track.id)
                if (existingIdx >= 0) {
                    _currentTrackId.value = track.id
                    _playbackDurationMs.value = track.durationMs
                    _playbackPositionMs.value = 0L
                    controller.seekToDefaultPosition(existingIdx)
                    controller.volume = currentVolume
                    controller.prepare()
                    controller.play()
                    return@withController
                }
            }

            val sourceQueue = queueContext?.takeIf { it.isNotEmpty() }
                ?: uiState.value.visibleTracks.ifEmpty { listOf(track) }
            val targetIdx = sourceQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            val mediaItems = sourceQueue.map(MediaItemMapper::toMediaItem)

            _currentTrackId.value = track.id
            _playbackDurationMs.value = track.durationMs
            _playbackPositionMs.value = 0L

            controller.repeatMode = if (_isRepeatOne.value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
            controller.setMediaItems(mediaItems, targetIdx, 0L)
            controller.volume = currentVolume
            if (controller.shuffleModeEnabled) {
                val anchored = FixedShuffleOrder.buildAnchoredFisherYates(sourceQueue.size, targetIdx)
                controller.sendCustomCommand(
                    MediaItemMapper.CMD_ENABLE_ANCHORED_SHUFFLE,
                    Bundle().apply {
                        putIntArray(MediaItemMapper.EXTRA_SHUFFLED_INDICES, anchored)
                    }
                )
            }
            controller.prepare()
            controller.play()
        }
    }

    private fun queueMatchesController(controller: MediaController, queue: List<TrackEntity>): Boolean {
        if (controller.mediaItemCount != queue.size) return false
        for (i in queue.indices) {
            if (controller.getMediaItemAt(i).mediaId != queue[i].id.toString()) {
                return false
            }
        }
        return true
    }

    private fun findTrackIndexInController(controller: MediaController, trackId: Long): Int {
        val targetId = trackId.toString()
        for (i in 0 until controller.mediaItemCount) {
            if (controller.getMediaItemAt(i).mediaId == targetId) return i
        }
        return -1
    }

    private fun removeTracksFromControllerQueue(trackIds: Set<Long>) {
        val controller = mediaController ?: return
        val idStrings = trackIds.map { it.toString() }.toSet()
        for (i in controller.mediaItemCount - 1 downTo 0) {
            if (controller.getMediaItemAt(i).mediaId in idStrings) {
                controller.removeMediaItem(i)
            }
        }
    }

    fun togglePlayPause() {
        val current = uiState.value.currentTrack ?: return
        withController { controller ->
            if (controller.mediaItemCount == 0) {
                playTrack(current, openPlayerTab = false)
            } else if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.volume = currentVolume
                controller.prepare()
                controller.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val duration = _playbackDurationMs.value.coerceAtLeast(1L)
        val clamped = positionMs.coerceIn(0L, duration)
        _playbackPositionMs.value = clamped
        mediaController?.seekTo(clamped)
    }

    fun skipNext() {
        withController { controller ->
            if (controller.mediaItemCount == 0) {
                val visible = uiState.value.visibleTracks
                if (visible.isNotEmpty()) {
                    playTrack(visible.first(), openPlayerTab = false, queueContext = visible)
                }
                return@withController
            }
            if (controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem()
            } else {
                controller.seekToDefaultPosition(0)
            }
            controller.prepare()
            controller.play()
        }
    }

    fun skipPrevious() {
        withController { controller ->
            if (controller.mediaItemCount == 0) return@withController
            if (controller.currentPosition > 4000L) {
                controller.seekTo(0L)
                _playbackPositionMs.value = 0L
            } else if (controller.hasPreviousMediaItem()) {
                controller.seekToPreviousMediaItem()
                controller.prepare()
                controller.play()
            } else {
                controller.seekTo(0L)
                _playbackPositionMs.value = 0L
            }
        }
    }

    fun toggleShuffle() {
        withController { controller ->
            val nowShuffled = !controller.shuffleModeEnabled
            if (nowShuffled && controller.mediaItemCount == 0) {
                val visible = uiState.value.visibleTracks
                if (visible.isNotEmpty()) {
                    shuffleAndPlay(visible, openPlayerTab = false)
                    return@withController
                }
            }
            controller.shuffleModeEnabled = nowShuffled
            _isShuffle.value = nowShuffled
            showBanner(if (nowShuffled) "Shuffle enabled" else "Shuffle disabled")
        }
    }

    fun toggleRepeatOne() {
        withController { controller ->
            val enableRepeatOne = controller.repeatMode != Player.REPEAT_MODE_ONE
            controller.repeatMode = if (enableRepeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
            _isRepeatOne.value = enableRepeatOne
            showBanner(if (enableRepeatOne) "Repeat Current Track enabled" else "Repeat disabled")
        }
    }

    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = viewModelScope.launch {
            while (isActive) {
                delay(150L)
                val controller = mediaController
                if (controller != null && controller.isConnected && controller.isPlaying) {
                    _playbackPositionMs.value = controller.currentPosition.coerceAtLeast(0L)
                    if (controller.duration > 0L) {
                        _playbackDurationMs.value = controller.duration
                    }
                }
            }
        }
    }

    // Automatic & Batch Lyrics Fetching (NEVER triggers playback)
    fun autoFetchLyricsForTrack(track: TrackEntity) {
        if (track.id in _fetchingTrackIds.value) return
        viewModelScope.launch {
            _fetchingTrackIds.update { it + track.id }
            val candidate = runCatching {
                repository.fetchFirstAvailableLyrics(track)
            }.getOrNull()
            if (candidate != null) {
                repository.saveLyricsOffline(
                    trackId = track.id,
                    rawLyrics = candidate.rawLyrics,
                    isSynced = candidate.isSynced,
                    sourceName = candidate.providerName
                )
                showBanner("Saved lyrics for \"${track.title}\" (${candidate.providerName})")
            } else {
                showBanner("No matching lyrics found for \"${track.title}\"")
            }
            _fetchingTrackIds.update { it - track.id }
        }
    }

    fun fetchAllMissingLyrics() {
        val missing = uiState.value.visibleTracks.filter {
            !uiState.value.savedLyricsMap.containsKey(it.id)
        }
        if (missing.isEmpty()) {
            showBanner("All songs in your library already have saved lyrics")
            return
        }
        startBatchLyricsFetch(missing)
    }

    fun fetchLyricsForSelectedTracks() {
        val selectedIds = _selectedTrackIds.value
        val targets = uiState.value.visibleTracks.filter { it.id in selectedIds }
        clearSelection()
        if (targets.isEmpty()) return
        startBatchLyricsFetch(targets)
    }

    private fun startBatchLyricsFetch(tracks: List<TrackEntity>) {
        batchLyricsJob?.cancel()
        batchLyricsJob = viewModelScope.launch {
            var saved = 0
            _batchLyrics.value = BatchLyricsProgress(
                isRunning = true,
                processed = 0,
                total = tracks.size,
                savedCount = 0,
                currentTrackTitle = tracks.firstOrNull()?.title.orEmpty()
            )
            try {
                tracks.forEachIndexed { index, track ->
                    if (!isActive) return@launch
                    _batchLyrics.update {
                        it.copy(
                            processed = index,
                            currentTrackTitle = track.title
                        )
                    }
                    _fetchingTrackIds.update { it + track.id }
                    val candidate = runCatching {
                        repository.fetchFirstAvailableLyrics(track)
                    }.getOrNull()
                    if (candidate != null) {
                        repository.saveLyricsOffline(
                            trackId = track.id,
                            rawLyrics = candidate.rawLyrics,
                            isSynced = candidate.isSynced,
                            sourceName = candidate.providerName
                        )
                        saved++
                    }
                    _fetchingTrackIds.update { it - track.id }
                    _batchLyrics.update {
                        it.copy(
                            processed = index + 1,
                            savedCount = saved
                        )
                    }
                }
                if (saved == 0) {
                    showBanner("No matching lyrics found")
                } else {
                    showBanner("Fetched lyrics for $saved of ${tracks.size} song(s)")
                }
            } catch (_: CancellationException) {
                showBanner("Lyrics batch cancelled ($saved saved)")
            } finally {
                _fetchingTrackIds.value = emptySet()
                _batchLyrics.value = BatchLyricsProgress()
            }
        }
    }

    fun cancelBatchLyricsFetch() {
        batchLyricsJob?.cancel()
    }

    // Manual "Choose another version" Online Lyrics Picker (NEVER triggers playback)
    fun openLyricsSearchModal(track: TrackEntity? = uiState.value.currentTrack) {
        val target = track ?: return
        val cleanArtist = if (target.artist == "Unknown Artist" || target.artist == "<unknown>") "" else target.artist
        val initialTitle = target.customSearchTitle?.takeIf { it.isNotBlank() } ?: target.title
        val initialArtist = target.customSearchArtist?.takeIf { it.isNotBlank() } ?: cleanArtist
        val hasCustom = !target.customSearchTitle.isNullOrBlank() || !target.customSearchArtist.isNullOrBlank()
        _lyricsSearch.value = LyricsSearchUiState(
            isSheetOpen = true,
            isSearching = true,
            targetTrackId = target.id,
            queryTitle = initialTitle,
            queryArtist = initialArtist,
            hasRememberedQuery = hasCustom
        )
        executeOnlineLyricsSearch(initialTitle, initialArtist)
    }

    fun updateLyricsSearchQuery(title: String, artist: String) {
        _lyricsSearch.update {
            it.copy(queryTitle = title, queryArtist = artist)
        }
    }

    fun executeOnlineLyricsSearch(
        title: String = _lyricsSearch.value.queryTitle,
        artist: String = _lyricsSearch.value.queryArtist
    ) {
        val targetId = _lyricsSearch.value.targetTrackId
        val targetTrack = findTrackById(targetId) ?: uiState.value.currentTrack ?: return
        viewModelScope.launch {
            _lyricsSearch.update { it.copy(isSearching = true, errorMessage = null) }

            // Remember user-edited search title / artist (e.g. Arabic title) for this song
            val cleanOrigArtist = if (targetTrack.artist == "Unknown Artist" || targetTrack.artist == "<unknown>") {
                ""
            } else {
                targetTrack.artist
            }
            val editedTitle = title.trim()
            val editedArtist = artist.trim()
            val customTitleToStore = editedTitle.takeIf { it.isNotBlank() && it != targetTrack.title }
            val customArtistToStore = editedArtist.takeIf { it.isNotBlank() && it != cleanOrigArtist }
            val hasCustom = customTitleToStore != null || customArtistToStore != null

            if (customTitleToStore != targetTrack.customSearchTitle ||
                customArtistToStore != targetTrack.customSearchArtist
            ) {
                repository.updateCustomLyricsSearchQuery(
                    trackId = targetTrack.id,
                    customTitle = customTitleToStore,
                    customArtist = customArtistToStore
                )
            }

            runCatching {
                repository.searchOnlineLyrics(targetTrack, title, artist)
            }.onSuccess { results ->
                _lyricsSearch.update {
                    it.copy(
                        isSearching = false,
                        hasRememberedQuery = hasCustom,
                        candidates = results,
                        selectedCandidateId = results.firstOrNull()?.id,
                        errorMessage = if (results.isEmpty()) {
                            "No matching lyrics found"
                        } else {
                            null
                        }
                    )
                }
            }.onFailure { err ->
                _lyricsSearch.update {
                    it.copy(
                        isSearching = false,
                        errorMessage = "Search error: ${err.localizedMessage ?: "Check internet connection"}"
                    )
                }
            }
        }
    }

    private fun findTrackById(trackId: Long?): TrackEntity? {
        if (trackId == null) return null
        return uiState.value.visibleTracks.find { it.id == trackId }
            ?: uiState.value.hiddenTracks.find { it.id == trackId }
            ?: uiState.value.currentTrack?.takeIf { it.id == trackId }
    }

    fun selectLyricCandidate(candidateId: String) {
        _lyricsSearch.update { it.copy(selectedCandidateId = candidateId) }
    }

    fun closeLyricsSearchModal() {
        _lyricsSearch.update { it.copy(isSheetOpen = false) }
    }

    fun saveCandidateLyricsToDevice(candidate: OnlineLyricCandidate) {
        val targetId = _lyricsSearch.value.targetTrackId ?: uiState.value.currentTrack?.id ?: return
        val targetTrack = findTrackById(targetId)
        viewModelScope.launch {
            if (targetTrack != null) {
                val cleanOrigArtist = if (targetTrack.artist == "Unknown Artist" || targetTrack.artist == "<unknown>") {
                    ""
                } else {
                    targetTrack.artist
                }
                val qTitle = _lyricsSearch.value.queryTitle.trim()
                val qArtist = _lyricsSearch.value.queryArtist.trim()
                repository.updateCustomLyricsSearchQuery(
                    trackId = targetTrack.id,
                    customTitle = qTitle.takeIf { it.isNotBlank() && it != targetTrack.title },
                    customArtist = qArtist.takeIf { it.isNotBlank() && it != cleanOrigArtist }
                )
            }
            repository.saveLyricsOffline(
                trackId = targetId,
                rawLyrics = candidate.rawLyrics,
                isSynced = candidate.isSynced,
                sourceName = candidate.providerName
            )
            _lyricsSearch.update { it.copy(isSheetOpen = false) }
            showBanner("Saved lyrics from ${candidate.providerName} to device")
        }
    }

    // Paste Lyrics Link Fallback (LRCLIB direct or best-effort web extraction + preview)
    fun openLinkLyricsModal(track: TrackEntity) {
        _linkLyrics.value = LinkLyricsUiState(
            isSheetOpen = true,
            targetTrack = track
        )
    }

    fun updateLinkLyricsUrl(url: String) {
        _linkLyrics.update { it.copy(urlInput = url, errorMessage = null) }
    }

    fun updateLinkLyricsPreviewText(text: String) {
        val isSynced = LrcParser.parse(text, "Web Link").isSynced
        _linkLyrics.update { it.copy(previewText = text, isSynced = isSynced) }
    }

    fun fetchLyricsFromLink() {
        val url = _linkLyrics.value.urlInput.trim()
        if (url.isBlank()) {
            _linkLyrics.update { it.copy(errorMessage = "Please paste a lyrics website or LRCLIB link") }
            return
        }
        viewModelScope.launch {
            _linkLyrics.update { it.copy(isLoading = true, errorMessage = null) }
            repository.fetchLyricsFromUrl(url)
                .onSuccess { candidate ->
                    _linkLyrics.update {
                        it.copy(
                            isLoading = false,
                            hasPreview = true,
                            previewText = candidate.rawLyrics,
                            sourceLabel = candidate.providerName,
                            isSynced = candidate.isSynced,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { err ->
                    _linkLyrics.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Could not load lyrics from that link"
                        )
                    }
                }
        }
    }

    fun saveLinkLyricsOffline() {
        val linkState = _linkLyrics.value
        val track = linkState.targetTrack ?: return
        val rawText = linkState.previewText.trim()
        if (rawText.isBlank()) return
        val parsed = LrcParser.parse(rawText, linkState.sourceLabel.ifBlank { "Web Link" })
        val source = linkState.sourceLabel.ifBlank {
            if (parsed.isSynced) "Web LRC Link" else "Web Lyrics Link"
        }
        viewModelScope.launch {
            repository.saveLyricsOffline(
                trackId = track.id,
                rawLyrics = rawText,
                isSynced = parsed.isSynced,
                sourceName = source
            )
            _linkLyrics.value = LinkLyricsUiState()
            showBanner("Saved lyrics from $source for \"${track.title}\"")
        }
    }

    fun closeLinkLyricsModal() {
        _linkLyrics.value = LinkLyricsUiState()
    }

    fun saveManualLyricsForTrack(trackId: Long, rawText: String, customSourceName: String? = null) {
        if (rawText.isBlank()) return
        val trimmed = rawText.trim()
        val parsed = LrcParser.parse(trimmed, "Custom Local LRC")
        val source = customSourceName ?: if (parsed.isSynced) "Local Synced LRC" else "Local Plain Lyrics"
        viewModelScope.launch {
            repository.saveLyricsOffline(
                trackId = trackId,
                rawLyrics = trimmed,
                isSynced = parsed.isSynced,
                sourceName = source
            )
            showBanner("Lyrics saved offline to device")
        }
    }

    fun saveManualLyricsToDevice(rawText: String) {
        val track = uiState.value.currentTrack ?: return
        saveManualLyricsForTrack(track.id, rawText)
    }

    fun importLrcFileForTrack(trackId: Long, uri: Uri) {
        viewModelScope.launch {
            repository.readLrcTextFromUri(uri)
                .onSuccess { text ->
                    val parsed = LrcParser.parse(text, "Imported .lrc File")
                    repository.saveLyricsOffline(
                        trackId = trackId,
                        rawLyrics = text,
                        isSynced = parsed.isSynced,
                        sourceName = if (parsed.isSynced) "Imported .lrc File" else "Imported Lyrics File"
                    )
                    showBanner("Imported lyrics file & saved offline")
                }
                .onFailure { err ->
                    showBanner(err.message ?: "Failed to read selected .lrc file")
                }
        }
    }

    fun deleteOfflineLyricsForCurrentTrack() {
        val track = uiState.value.currentTrack ?: return
        viewModelScope.launch {
            repository.deleteSavedLyrics(track.id)
            showBanner("Removed cached offline lyrics for ${track.title}")
        }
    }

    fun adjustLyricOffset(deltaMs: Long) {
        val track = uiState.value.currentTrack ?: return
        val newOffset = (track.lyricOffsetMs + deltaMs).coerceIn(-5000L, 5000L)
        viewModelScope.launch {
            repository.updateLyricOffset(track.id, newOffset)
        }
    }

    fun setLyricOffsetExact(offsetMs: Long) {
        val track = uiState.value.currentTrack ?: return
        val clamped = offsetMs.coerceIn(-5000L, 5000L)
        viewModelScope.launch {
            repository.updateLyricOffset(track.id, clamped)
        }
    }

    // Filter & WhatsApp Voice-Note Shield Controls
    fun updateSearchQuery(query: String) {
        _filterSettings.update { it.copy(searchQuery = query) }
    }

    fun toggleStrictKnownFormats(enabled: Boolean) {
        _filterSettings.update { it.copy(strictKnownFormatsOnly = enabled) }
        showBanner(
            if (enabled) "Strict MP3 / M4A / FLAC filter enabled"
            else "All audio container formats allowed"
        )
    }

    fun toggleBlockWhatsAppVoiceNotes(enabled: Boolean) {
        _filterSettings.update { it.copy(blockWhatsAppVoiceNotes = enabled) }
        showBanner(
            if (enabled) "WhatsApp PTT & AUD Voice-Note Shield active"
            else "Voice-Note filename heuristic paused"
        )
    }

    fun toggleFolderBlacklistRule(pathPattern: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleFolderRule(pathPattern, enabled)
        }
    }

    fun addCustomBlacklistFolder(pathPattern: String, label: String) {
        viewModelScope.launch {
            repository.addCustomFolderRule(pathPattern, label)
            showBanner("Added folder blacklist rule: $pathPattern")
        }
    }

    fun removeCustomBlacklistFolder(pathPattern: String) {
        viewModelScope.launch {
            repository.removeCustomFolderRule(pathPattern)
            showBanner("Removed custom folder rule")
        }
    }

    // Quick Tag Editor & Dual-Option Song Deletion
    fun saveEditedTags(trackId: Long, title: String, artist: String, album: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.updateTrackTags(trackId, title, artist, album)
            val controller = mediaController
            if (controller != null && controller.isConnected) {
                val idx = findTrackIndexInController(controller, trackId)
                if (idx >= 0) {
                    val existingItem = controller.getMediaItemAt(idx)
                    val updatedMeta = existingItem.mediaMetadata.buildUpon()
                        .setTitle(title.trim())
                        .setArtist(artist.trim())
                        .setAlbumTitle(album.trim())
                        .build()
                    controller.replaceMediaItem(
                        idx,
                        existingItem.buildUpon().setMediaMetadata(updatedMeta).build()
                    )
                }
            }
            showBanner("Updated track tags for \"${title.trim()}\"")
        }
    }

    fun hideTrackFromLibrary(track: TrackEntity) {
        viewModelScope.launch {
            if (uiState.value.currentTrack?.id == track.id && _isPlaying.value) {
                skipNext()
            }
            removeTracksFromControllerQueue(setOf(track.id))
            repository.setTrackHidden(track.id, true)
            showBanner("\"${track.title}\" hidden from library (Restorable in Voice Shield)")
        }
    }

    fun restoreHiddenTrack(track: TrackEntity) {
        viewModelScope.launch {
            repository.setTrackHidden(track.id, false)
            showBanner("Restored \"${track.title}\" to library")
        }
    }

    fun deleteTrackFromDevice(track: TrackEntity) {
        viewModelScope.launch {
            if (uiState.value.currentTrack?.id == track.id) {
                mediaController?.pause()
                _isPlaying.value = false
                _currentTrackId.value = null
            }
            removeTracksFromControllerQueue(setOf(track.id))
            repository.deleteTrackFromDevice(track)
            showBanner("Removed \"${track.title}\" from device")
        }
    }

    // Playlists
    fun createNewPlaylist(name: String, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name, description)
            showBanner("Created playlist \"${name.trim()}\"")
        }
    }

    fun deleteCustomPlaylist(playlistId: Long, name: String) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            showBanner("Deleted playlist \"$name\"")
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long, playlistName: String) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, trackId)
            showBanner("Added to \"$playlistName\"")
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    // Sleep Timer with Gentle Fade-Out & Finish Current Track
    fun startSleepTimer(
        minutes: Int,
        gentleFadeOut: Boolean = _sleepTimer.value.gentleFadeOut,
        finishCurrentTrack: Boolean = _sleepTimer.value.finishCurrentTrack
    ) {
        sleepTimerJob?.cancel()
        val totalSecs = (minutes * 60).coerceAtLeast(10)
        currentVolume = 1.0f
        mediaController?.volume = 1.0f

        _sleepTimer.value = SleepTimerState(
            isActive = true,
            remainingSeconds = totalSecs,
            totalSeconds = totalSecs,
            gentleFadeOut = gentleFadeOut,
            finishCurrentTrack = finishCurrentTrack,
            waitingForTrackEnd = false
        )
        showBanner("Sleep Timer set for $minutes min")

        sleepTimerJob = viewModelScope.launch {
            var remaining = totalSecs
            while (remaining > 0 && isActive) {
                delay(1000L)
                remaining--
                _sleepTimer.update { it.copy(remainingSeconds = remaining) }

                if (_sleepTimer.value.gentleFadeOut && remaining in 1..30) {
                    val fadedVol = (remaining / 30f).coerceIn(0.08f, 1.0f)
                    currentVolume = fadedVol
                    mediaController?.volume = fadedVol
                }
            }

            if (isActive) {
                if (_sleepTimer.value.finishCurrentTrack && _isPlaying.value) {
                    _sleepTimer.update { it.copy(waitingForTrackEnd = true) }
                    showBanner("Sleep Timer: Finishing current song before stopping…")
                } else {
                    mediaController?.pause()
                    _isPlaying.value = false
                    cancelSleepTimer()
                    showBanner("Sleep Timer paused playback")
                }
            }
        }
    }

    fun updateSleepTimerOptions(gentleFadeOut: Boolean, finishCurrentTrack: Boolean) {
        _sleepTimer.update {
            it.copy(gentleFadeOut = gentleFadeOut, finishCurrentTrack = finishCurrentTrack)
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        currentVolume = 1.0f
        mediaController?.volume = 1.0f
        _sleepTimer.value = SleepTimerState()
    }

    // SAF Audio Import & Device Scan
    fun importExternalAudioUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val count = repository.importAudioUris(uris)
            showBanner("Imported $count audio file(s) into library")
        }
    }

    fun scanDeviceMediaStore(silent: Boolean = false) {
        if (!checkAudioPermissionGranted()) {
            _hasAudioPermission.value = false
            if (!silent) {
                showBanner("Please grant audio access permission to scan your music")
            }
            return
        }
        _hasAudioPermission.value = true
        if (_isScanning.value) return

        viewModelScope.launch {
            _isScanning.value = true
            val count = runCatching { repository.scanPublicMediaStore() }.getOrDefault(0)
            _isScanning.value = false
            if (!silent) {
                showBanner("Library synced • $count audio file(s) scanned cleanly")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        batchLyricsJob?.cancel()
        progressTickerJob?.cancel()
        sleepTimerJob?.cancel()
        mediaController?.removeListener(controllerPlayerListener)
        mediaController = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }
}
