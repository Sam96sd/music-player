package com.example.ui

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
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
import com.example.domain.VoiceNoteFilterEngine
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
    RECENTLY_ADDED("Recently Added", "Latest MP3, M4A & FLAC arrivals"),
    MOST_PLAYED("Most Played", "Your heavy rotation studio tracks"),
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
    val queryTitle: String = "",
    val queryArtist: String = "",
    val candidates: List<OnlineLyricCandidate> = emptyList(),
    val selectedCandidateId: String? = null,
    val errorMessage: String? = null
)

data class FilterSettingsState(
    val strictKnownFormatsOnly: Boolean = true, // Only .mp3, .m4a, .flac
    val blockWhatsAppVoiceNotes: Boolean = true, // Block PTT-*, AUD-*, voice_note* even if long .m4a
    val selectedFormatChip: String = "ALL", // ALL, FLAC, MP3, M4A
    val searchQuery: String = ""
)

data class MusicPlayerUiState(
    val activeTab: AppTab = AppTab.LIBRARY,
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
    val statusBannerMessage: String? = null
)

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MusicRepository = MusicRepository(
        context = application.applicationContext,
        dao = PulseWaveDatabase.getInstance(application.applicationContext).musicDao()
    )

    private val _activeTab = MutableStateFlow(AppTab.LIBRARY)
    private val _currentTrackId = MutableStateFlow<Long?>(null)
    private val _isPlaying = MutableStateFlow(false)
    private val _isShuffle = MutableStateFlow(false)
    private val _isRepeatOne = MutableStateFlow(false)
    private val _playbackPositionMs = MutableStateFlow(0L)
    private val _playbackDurationMs = MutableStateFlow(40_000L)
    private val _filterSettings = MutableStateFlow(FilterSettingsState())
    private val _sleepTimer = MutableStateFlow(SleepTimerState())
    private val _lyricsSearch = MutableStateFlow(LyricsSearchUiState())
    private val _statusBanner = MutableStateFlow<String?>(null)

    private var mediaPlayer: MediaPlayer? = null
    private var progressTickerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var currentVolume: Float = 1.0f

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
                val matchesFormat = filter.selectedFormatChip == "ALL" ||
                    track.extension.equals(filter.selectedFormatChip, ignoreCase = true)
                val q = filter.searchQuery.trim().lowercase()
                val matchesQuery = q.isEmpty() ||
                    track.title.lowercase().contains(q) ||
                    track.artist.lowercase().contains(q) ||
                    track.album.lowercase().contains(q)

                if (matchesFormat && matchesQuery) {
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

        // Clean playlists so voice notes or hidden tracks never appear inside playlists either
        val allowedIds = visible.map { it.id }.toSet() +
            core.allTracks.filter {
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
            lyricsSearch = lyricsModal,
            statusBannerMessage = null
        )
    }.combine(_statusBanner) { state, banner ->
        state.copy(statusBannerMessage = banner)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MusicPlayerUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
        }
        startProgressTicker()
    }

    fun selectTab(tab: AppTab) {
        _activeTab.value = tab
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

    // Playback Controls
    fun playTrack(track: TrackEntity, openPlayerTab: Boolean = false) {
        val isSameTrack = _currentTrackId.value == track.id && mediaPlayer != null
        _currentTrackId.value = track.id
        _playbackDurationMs.value = track.durationMs

        if (openPlayerTab) {
            _activeTab.value = AppTab.PLAYER_LYRICS
        }

        if (isSameTrack) {
            togglePlayPause()
            return
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
            // Fallback simulated playback clock if an external SAF URI lacks read permission
            _isPlaying.value = true
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

    fun skipNext() {
        val list = uiState.value.visibleTracks
        if (list.isEmpty()) return
        val currentId = uiState.value.currentTrack?.id
        val nextTrack = if (_isShuffle.value && list.size > 1) {
            list.filter { it.id != currentId }.random()
        } else {
            val idx = list.indexOfFirst { it.id == currentId }
            if (idx == -1 || idx == list.lastIndex) list.first() else list[idx + 1]
        }
        playTrack(nextTrack, openPlayerTab = false)
    }

    fun skipPrevious() {
        if (_playbackPositionMs.value > 4000L) {
            seekTo(0L)
            return
        }
        val list = uiState.value.visibleTracks
        if (list.isEmpty()) return
        val currentId = uiState.value.currentTrack?.id
        val idx = list.indexOfFirst { it.id == currentId }
        val prevTrack = if (idx <= 0) list.last() else list[idx - 1]
        playTrack(prevTrack, openPlayerTab = false)
    }

    fun toggleShuffle() {
        _isShuffle.update { !it }
        showBanner(if (_isShuffle.value) "Shuffle enabled" else "Shuffle disabled")
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
                    } else {
                        val nextPos = _playbackPositionMs.value + 150L
                        val dur = _playbackDurationMs.value
                        if (nextPos >= dur) {
                            handleTrackCompleted()
                        } else {
                            _playbackPositionMs.value = nextPos
                        }
                    }
                }
            }
        }
    }

    // Online Lyrics Search & Local Offline Persistence
    fun openLyricsSearchModal(track: TrackEntity? = uiState.value.currentTrack) {
        val target = track ?: return
        _lyricsSearch.value = LyricsSearchUiState(
            isSheetOpen = true,
            isSearching = true,
            queryTitle = target.title,
            queryArtist = target.artist
        )
        executeOnlineLyricsSearch(target.title, target.artist)
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
        viewModelScope.launch {
            _lyricsSearch.update { it.copy(isSearching = true, errorMessage = null) }
            runCatching {
                repository.searchOnlineLyrics(title, artist)
            }.onSuccess { results ->
                _lyricsSearch.update {
                    it.copy(
                        isSearching = false,
                        candidates = results,
                        selectedCandidateId = results.firstOrNull()?.id,
                        errorMessage = if (results.isEmpty()) "No lyrics found. Try refining title or artist." else null
                    )
                }
            }.onFailure { err ->
                _lyricsSearch.update {
                    it.copy(
                        isSearching = false,
                        errorMessage = "Search error: ${err.localizedMessage ?: "Check connection"}"
                    )
                }
            }
        }
    }

    fun selectLyricCandidate(candidateId: String) {
        _lyricsSearch.update { it.copy(selectedCandidateId = candidateId) }
    }

    fun closeLyricsSearchModal() {
        _lyricsSearch.update { it.copy(isSheetOpen = false) }
    }

    fun saveCandidateLyricsToDevice(candidate: OnlineLyricCandidate) {
        val track = uiState.value.currentTrack ?: return
        viewModelScope.launch {
            repository.saveLyricsOffline(
                trackId = track.id,
                rawLyrics = candidate.rawLyrics,
                isSynced = candidate.isSynced,
                sourceName = candidate.providerName
            )
            _lyricsSearch.update { it.copy(isSheetOpen = false) }
            showBanner("Saved lyrics from ${candidate.providerName} to device for offline sync!")
        }
    }

    fun saveManualLyricsToDevice(rawText: String) {
        val track = uiState.value.currentTrack ?: return
        if (rawText.isBlank()) return
        val parsed = LrcParser.parse(rawText, "Custom Local LRC")
        viewModelScope.launch {
            repository.saveLyricsOffline(
                trackId = track.id,
                rawLyrics = rawText.trim(),
                isSynced = parsed.isSynced,
                sourceName = if (parsed.isSynced) "Local Synced LRC" else "Local Plain Lyrics"
            )
            showBanner("Lyrics saved locally for offline access")
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

    fun selectFormatChip(format: String) {
        _filterSettings.update { it.copy(selectedFormatChip = format) }
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
            showBanner("Permanently deleted \"${track.title}\" from device")
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

                // Gentle 30-second volume fade-out
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

    fun scanDeviceMediaStore() {
        viewModelScope.launch {
            val count = repository.scanPublicMediaStore()
            if (count > 0) {
                showBanner("Scanned $count device audio track(s) through Voice Shield")
            } else {
                showBanner("Library synced • All local MP3/M4A/FLAC tracks up to date")
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
        releaseMediaPlayer()
    }
}
