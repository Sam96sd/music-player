package com.example.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import com.example.ui.theme.AppThemePreset
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

    private var mediaPlayer: MediaPlayer? = null
    private var progressTickerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var batchLyricsJob: Job? = null
    private var currentVolume: Float = 1.0f
    private var baseQueue: List<TrackEntity> = emptyList()
    private var activeQueue: List<TrackEntity> = emptyList()
    private var activeQueueIndex: Int = -1

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
        viewModelScope.launch {
            repository.ensureDefaultFilterRules()
            if (checkAudioPermissionGranted()) {
                _hasAudioPermission.value = true
                scanDeviceMediaStore(silent = true)
            }
        }
        startProgressTicker()
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
            if (currentId != null && currentId in ids && _isPlaying.value) {
                releaseMediaPlayer()
                _isPlaying.value = false
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
                releaseMediaPlayer()
                _isPlaying.value = false
                _currentTrackId.value = null
            }
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

    // Playback Controls & Fisher-Yates Shuffled Queue Management
    fun playAll(tracks: List<TrackEntity>, openPlayerTab: Boolean = false) {
        if (tracks.isEmpty()) return
        _isShuffle.value = false
        baseQueue = tracks
        activeQueue = tracks
        activeQueueIndex = 0
        startPlayback(tracks.first(), openPlayerTab = openPlayerTab)
    }

    fun shuffleAndPlay(tracks: List<TrackEntity>, openPlayerTab: Boolean = false) {
        if (tracks.isEmpty()) return
        _isShuffle.value = true
        baseQueue = tracks
        activeQueue = PlaybackQueueManager.fisherYatesShuffle(tracks)
        activeQueueIndex = 0
        startPlayback(activeQueue.first(), openPlayerTab = openPlayerTab)
        showBanner("Shuffling ${tracks.size} song(s)")
    }

    fun playTrack(
        track: TrackEntity,
        openPlayerTab: Boolean = false,
        queueContext: List<TrackEntity>? = null
    ) {
        val visible = uiState.value.visibleTracks
        val sourceList = when {
            !queueContext.isNullOrEmpty() -> queueContext
            baseQueue.any { it.id == track.id } -> baseQueue
            else -> visible
        }

        if (queueContext != null && queueContext.isNotEmpty()) {
            baseQueue = queueContext
            activeQueue = if (_isShuffle.value) {
                PlaybackQueueManager.buildShuffledQueueKeepingFirst(queueContext, track) { it.id }
            } else {
                queueContext
            }
            activeQueueIndex = activeQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        } else if (activeQueue.none { it.id == track.id }) {
            baseQueue = sourceList
            activeQueue = if (_isShuffle.value) {
                PlaybackQueueManager.buildShuffledQueueKeepingFirst(sourceList, track) { it.id }
            } else {
                sourceList
            }
            activeQueueIndex = activeQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        } else {
            activeQueueIndex = activeQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        }

        val isSameTrack = _currentTrackId.value == track.id && mediaPlayer != null
        if (isSameTrack) {
            if (openPlayerTab) {
                _activeTab.value = AppTab.PLAYER_LYRICS
            }
            togglePlayPause()
            return
        }

        startPlayback(track, openPlayerTab = openPlayerTab)
    }

    private fun startPlayback(track: TrackEntity, openPlayerTab: Boolean) {
        _currentTrackId.value = track.id
        _playbackDurationMs.value = track.durationMs

        if (openPlayerTab) {
            _activeTab.value = AppTab.PLAYER_LYRICS
        }

        viewModelScope.launch {
            repository.incrementPlayCount(track.id)
        }

        releaseMediaPlayer()
        _playbackPositionMs.value = 0L

        runCatching {
            val mp = MediaPlayer()
            val uri = Uri.parse(track.uriString)
            mp.setDataSource(getApplication(), uri)
            mp.prepare()
            val actualDuration = mp.duration.toLong().coerceAtLeast(track.durationMs)
            _playbackDurationMs.value = actualDuration
            mp.setVolume(currentVolume, currentVolume)
            mp.start()
            _isPlaying.value = true

            mp.setOnCompletionListener {
                handleTrackCompleted()
            }
            mediaPlayer = mp
        }.onFailure {
            _isPlaying.value = false
            showBanner("Unable to play \"${track.title}\" — try rescanning library")
        }
    }

    fun togglePlayPause() {
        val current = uiState.value.currentTrack ?: return
        if (mediaPlayer == null) {
            playTrack(current, openPlayerTab = false)
            return
        }
        val mp = mediaPlayer
        if (_isPlaying.value) {
            runCatching { if (mp?.isPlaying == true) mp.pause() }
            _isPlaying.value = false
        } else {
            runCatching { mp?.start() }
            _isPlaying.value = true
        }
    }

    fun seekTo(positionMs: Long) {
        val duration = _playbackDurationMs.value.coerceAtLeast(1L)
        val clamped = positionMs.coerceIn(0L, duration)
        _playbackPositionMs.value = clamped
        runCatching {
            mediaPlayer?.seekTo(clamped.toInt())
        }
    }

    private fun ensureSyncedQueue(): List<TrackEntity> {
        val visible = uiState.value.visibleTracks
        if (visible.isEmpty()) {
            baseQueue = emptyList()
            activeQueue = emptyList()
            activeQueueIndex = -1
            return emptyList()
        }
        val visibleById = visible.associateBy { it.id }
        val filteredBase = baseQueue.mapNotNull { visibleById[it.id] }
        baseQueue = filteredBase.ifEmpty { visible }

        val filteredActive = activeQueue.mapNotNull { visibleById[it.id] }
        if (filteredActive.isEmpty()) {
            val current = uiState.value.currentTrack
            activeQueue = if (_isShuffle.value) {
                PlaybackQueueManager.buildShuffledQueueKeepingFirst(baseQueue, current) { it.id }
            } else {
                baseQueue
            }
        } else {
            activeQueue = filteredActive
        }
        return activeQueue
    }

    fun skipNext() {
        val queue = ensureSyncedQueue()
        if (queue.isEmpty()) return
        val currentId = uiState.value.currentTrack?.id
        val idx = activeQueueIndex
            .takeIf { it in queue.indices && queue[it].id == currentId }
            ?: queue.indexOfFirst { it.id == currentId }

        val nextTrack = if (idx >= 0 && idx + 1 < queue.size) {
            activeQueueIndex = idx + 1
            queue[activeQueueIndex]
        } else if (_isShuffle.value) {
            // Queue cycle finished: reshuffle a fresh Fisher-Yates cycle with no immediate repeat
            val nextCycle = PlaybackQueueManager.reshuffleNextCycle(baseQueue, currentId) { it.id }
            activeQueue = nextCycle
            activeQueueIndex = 0
            nextCycle.first()
        } else {
            activeQueueIndex = 0
            queue.first()
        }
        startPlayback(nextTrack, openPlayerTab = false)
    }

    fun skipPrevious() {
        if (_playbackPositionMs.value > 4000L) {
            seekTo(0L)
            return
        }
        val queue = ensureSyncedQueue()
        if (queue.isEmpty()) return
        val currentId = uiState.value.currentTrack?.id
        val idx = activeQueueIndex
            .takeIf { it in queue.indices && queue[it].id == currentId }
            ?: queue.indexOfFirst { it.id == currentId }
        val prevIdx = if (idx > 0) idx - 1 else queue.lastIndex
        activeQueueIndex = prevIdx
        startPlayback(queue[prevIdx], openPlayerTab = false)
    }

    fun toggleShuffle() {
        val nowShuffled = !_isShuffle.value
        _isShuffle.value = nowShuffled
        val visible = uiState.value.visibleTracks
        val source = baseQueue.ifEmpty { visible }
        baseQueue = source
        val current = uiState.value.currentTrack
        if (source.isNotEmpty()) {
            if (nowShuffled) {
                activeQueue = PlaybackQueueManager.buildShuffledQueueKeepingFirst(source, current) { it.id }
                activeQueueIndex = 0
            } else {
                activeQueue = source
                activeQueueIndex = current?.let { c -> activeQueue.indexOfFirst { it.id == c.id } }
                    ?.coerceAtLeast(0) ?: 0
            }
        }
        showBanner(if (nowShuffled) "Shuffle enabled" else "Shuffle disabled")
    }

    fun toggleRepeatOne() {
        _isRepeatOne.update { !it }
        showBanner(if (_isRepeatOne.value) "Repeat Current Track enabled" else "Repeat disabled")
    }

    private fun handleTrackCompleted() {
        if (_sleepTimer.value.waitingForTrackEnd) {
            _isPlaying.value = false
            _playbackPositionMs.value = 0L
            cancelSleepTimer()
            showBanner("Sleep Timer finished after final track")
            return
        }
        if (_isRepeatOne.value) {
            seekTo(0L)
            runCatching { mediaPlayer?.start() }
            _isPlaying.value = true
        } else {
            skipNext()
        }
    }

    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = viewModelScope.launch {
            while (isActive) {
                delay(150L)
                if (_isPlaying.value) {
                    val mp = mediaPlayer
                    if (mp != null && runCatching { mp.isPlaying }.getOrDefault(false)) {
                        _playbackPositionMs.value = mp.currentPosition.toLong()
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
            showBanner("Updated track tags for \"${title.trim()}\"")
        }
    }

    fun hideTrackFromLibrary(track: TrackEntity) {
        viewModelScope.launch {
            if (uiState.value.currentTrack?.id == track.id && _isPlaying.value) {
                skipNext()
            }
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
                releaseMediaPlayer()
                _isPlaying.value = false
                _currentTrackId.value = null
            }
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
        runCatching { mediaPlayer?.setVolume(1.0f, 1.0f) }

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
                    runCatching { mediaPlayer?.setVolume(fadedVol, fadedVol) }
                }
            }

            if (isActive) {
                if (_sleepTimer.value.finishCurrentTrack && _isPlaying.value) {
                    _sleepTimer.update { it.copy(waitingForTrackEnd = true) }
                    showBanner("Sleep Timer: Finishing current song before stopping…")
                } else {
                    runCatching { if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause() }
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
        runCatching { mediaPlayer?.setVolume(1.0f, 1.0f) }
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

    private fun releaseMediaPlayer() {
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
    }

    override fun onCleared() {
        super.onCleared()
        batchLyricsJob?.cancel()
        releaseMediaPlayer()
    }
}
