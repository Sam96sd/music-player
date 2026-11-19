package com.example.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.data.local.BlacklistedFolderEntity
import com.example.data.local.MusicDao
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackCrossRef
import com.example.data.local.PlaylistWithTracks
import com.example.data.local.SavedLyricsEntity
import com.example.data.local.TrackEntity
import com.example.data.network.MultiSourceLyricsClient
import com.example.data.network.OnlineLyricCandidate
import com.example.domain.VoiceNoteFilterEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class MusicRepository(
    private val context: Context,
    private val dao: MusicDao,
    private val lyricsClient: MultiSourceLyricsClient = MultiSourceLyricsClient()
) {
    val allTracks: Flow<List<TrackEntity>> = dao.observeAllTracks()
    val allSavedLyrics: Flow<List<SavedLyricsEntity>> = dao.observeAllSavedLyrics()
    val playlistsWithTracks: Flow<List<PlaylistWithTracks>> = dao.observePlaylistsWithTracks()
    val blacklistedFolders: Flow<List<BlacklistedFolderEntity>> = dao.observeBlacklistedFolders()

    private val scanMutex = Mutex()

    suspend fun ensureDefaultFilterRules() {
        if (dao.getBlacklistedFolderCount() == 0) {
            dao.insertBlacklistedFolders(defaultBlacklistedFolders())
        }
    }

    private fun defaultBlacklistedFolders(): List<BlacklistedFolderEntity> = listOf(
        BlacklistedFolderEntity(
            pathPattern = "/whatsapp/media/whatsapp voice notes",
            label = "WhatsApp Voice Notes",
            description = "Blocks all incoming & sent PTT voice notes (including long recordings)",
            isEnabled = true,
            isBuiltInRule = true
        ),
        BlacklistedFolderEntity(
            pathPattern = "/whatsapp/media/whatsapp audio",
            label = "WhatsApp Forwarded Audio",
            description = "Blocks AUD-* voice clips and forwarded .m4a/.opus audio in WhatsApp",
            isEnabled = true,
            isBuiltInRule = true
        ),
        BlacklistedFolderEntity(
            pathPattern = "/telegram/telegram audio",
            label = "Telegram Voice & Audio",
            description = "Blocks Telegram voice memos and chat audio attachments",
            isEnabled = true,
            isBuiltInRule = true
        ),
        BlacklistedFolderEntity(
            pathPattern = "/recordings",
            label = "Voice & Call Recordings",
            description = "Blocks system voice recorder and call recording folders",
            isEnabled = true,
            isBuiltInRule = true
        ),
        BlacklistedFolderEntity(
            pathPattern = "/notifications",
            label = "Notifications, Ringtones & Alarms",
            description = "Excludes system alert tones, ringtones, and alarms",
            isEnabled = true,
            isBuiltInRule = true
        )
    )

    suspend fun updateTrackTags(trackId: Long, title: String, artist: String, album: String) {
        dao.updateTrackTags(trackId, title.trim(), artist.trim(), album.trim())
    }

    suspend fun setTrackHidden(trackId: Long, isHidden: Boolean) {
        dao.setTrackHidden(trackId, isHidden)
    }

    suspend fun setTracksHidden(trackIds: List<Long>, isHidden: Boolean) {
        if (trackIds.isEmpty()) return
        dao.setTracksHidden(trackIds, isHidden)
    }

    suspend fun deleteTrackFromDevice(track: TrackEntity) = withContext(Dispatchers.IO) {
        deleteUnderlyingFile(track)
        dao.deleteTracksWithCleanup(listOf(track.id))
    }

    suspend fun deleteTracksFromDevice(tracks: List<TrackEntity>) = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext
        for (track in tracks) {
            deleteUnderlyingFile(track)
        }
        dao.deleteTracksWithCleanup(tracks.map { it.id })
    }

    private fun deleteUnderlyingFile(track: TrackEntity) {
        runCatching {
            val uri = Uri.parse(track.uriString)
            when (uri.scheme) {
                "file" -> uri.path?.let { path ->
                    val file = File(path)
                    if (file.exists()) file.delete()
                }
                "content" -> {
                    context.contentResolver.delete(uri, null, null)
                }
            }
        }
    }

    suspend fun updateLyricOffset(trackId: Long, offsetMs: Long) {
        dao.updateLyricOffset(trackId, offsetMs)
    }

    suspend fun incrementPlayCount(trackId: Long) {
        dao.incrementPlayCount(trackId)
    }

    suspend fun updateCustomLyricsSearchQuery(trackId: Long, customTitle: String?, customArtist: String?) {
        dao.updateCustomLyricsSearchQuery(
            trackId = trackId,
            customTitle = customTitle?.trim()?.takeIf { it.isNotEmpty() },
            customArtist = customArtist?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    suspend fun fetchFirstAvailableLyrics(track: TrackEntity): OnlineLyricCandidate? {
        val qTitle = track.customSearchTitle?.takeIf { it.isNotBlank() } ?: track.title
        val qArtist = track.customSearchArtist?.takeIf { it.isNotBlank() } ?: track.artist
        return lyricsClient.fetchFirstVerifiedLyrics(
            trackTitle = track.title,
            trackArtist = track.artist,
            trackDurationMs = track.durationMs,
            queryTitle = qTitle,
            queryArtist = qArtist
        )
    }

    suspend fun searchOnlineLyrics(
        track: TrackEntity,
        queryTitle: String,
        queryArtist: String
    ): List<OnlineLyricCandidate> {
        return lyricsClient.searchVerifiedTopProviders(
            trackTitle = track.title,
            trackArtist = track.artist,
            trackDurationMs = track.durationMs,
            queryTitle = queryTitle,
            queryArtist = queryArtist
        )
    }

    suspend fun fetchLyricsFromUrl(url: String): Result<OnlineLyricCandidate> {
        return lyricsClient.fetchLyricsFromUrl(url)
    }

    suspend fun readLrcTextFromUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openInputStream(uri)
                ?: error("Unable to open selected file")
            val bytes = stream.use { it.readBytes() }
            val text = when {
                bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                    String(bytes, Charsets.UTF_16LE)
                bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                    String(bytes, Charsets.UTF_16BE)
                else -> String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
            }.trim()
            require(text.isNotBlank()) { "Selected file is empty" }
            text
        }
    }

    suspend fun saveLyricsOffline(
        trackId: Long,
        rawLyrics: String,
        isSynced: Boolean,
        sourceName: String
    ) {
        dao.saveLyrics(
            SavedLyricsEntity(
                trackId = trackId,
                rawLyrics = rawLyrics,
                isSynced = isSynced,
                sourceName = sourceName
            )
        )
    }

    suspend fun deleteSavedLyrics(trackId: Long) {
        dao.deleteLyricsForTrack(trackId)
    }

    suspend fun createPlaylist(name: String, description: String): Long {
        return dao.insertPlaylist(
            PlaylistEntity(name = name.trim(), description = description.trim())
        )
    }

    suspend fun deletePlaylist(playlistId: Long) {
        dao.clearPlaylistCrossRefs(playlistId)
        dao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        dao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId = playlistId, trackId = trackId))
    }

    suspend fun addTracksToPlaylist(playlistId: Long, trackIds: List<Long>) {
        if (trackIds.isEmpty()) return
        dao.addTracksToPlaylist(
            trackIds.map { PlaylistTrackCrossRef(playlistId = playlistId, trackId = it) }
        )
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        dao.removeTrackFromPlaylist(playlistId = playlistId, trackId = trackId)
    }

    suspend fun toggleFolderRule(pathPattern: String, enabled: Boolean) {
        dao.setFolderRuleEnabled(pathPattern, enabled)
    }

    suspend fun addCustomFolderRule(pathPattern: String, label: String) {
        val cleanPattern = pathPattern.trim().lowercase()
        if (cleanPattern.isEmpty()) return
        dao.insertBlacklistedFolder(
            BlacklistedFolderEntity(
                pathPattern = cleanPattern,
                label = label.trim().ifEmpty { "Custom Folder ($cleanPattern)" },
                description = "User-defined excluded folder path",
                isEnabled = true,
                isBuiltInRule = false
            )
        )
    }

    suspend fun removeCustomFolderRule(pathPattern: String) {
        dao.deleteCustomFolderRule(pathPattern)
    }

    suspend fun importAudioUris(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var importedCount = 0
        val existingTracks = dao.getAllTracksOnce()
        val existingBySourceKey = existingTracks.associateBy { it.sourceKey }
        val existingByPath = existingTracks.associateBy { it.filePath.lowercase() }

        for (uri in uris) {
            runCatching {
                val resolver = context.contentResolver
                var displayName = "Imported_Track.mp3"
                resolver.query(uri, null, null, null, null)?.use { cursor: Cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx != -1) {
                            displayName = cursor.getString(nameIdx) ?: displayName
                        }
                    }
                }
                val ext = displayName.substringAfterLast('.', "mp3").lowercase()
                val baseTitle = displayName.substringBeforeLast('.')
                    .replace('_', ' ')
                    .replace('-', ' ')
                    .trim()
                val sourceKey = "saf_$uri"
                val virtualPath = "/storage/emulated/0/Music/Imported/$displayName"

                val existing = existingBySourceKey[sourceKey] ?: existingByPath[virtualPath.lowercase()]
                val track = if (existing != null) {
                    existing.copy(
                        uriString = uri.toString(),
                        extension = ext
                    )
                } else {
                    TrackEntity(
                        sourceKey = sourceKey,
                        mediaStoreId = null,
                        title = baseTitle.ifBlank { displayName },
                        artist = "Local Artist",
                        album = "Imported Audio",
                        filePath = virtualPath,
                        uriString = uri.toString(),
                        extension = ext,
                        durationMs = 180_000L,
                        qualityBadge = ext.uppercase()
                    )
                }
                dao.insertTrack(track)
                importedCount++
            }
        }
        importedCount
    }

    /**
     * Scans MediaStore.Audio.Media (IS_MUSIC != 0) on the device, excludes ringtones,
     * notifications, and alarms, deduplicates by MediaStore ID and normalized file path,
     * and atomically replaces/updates MediaStore tracks in Room without creating duplicates.
     */
    suspend fun scanPublicMediaStore(): Int = scanMutex.withLock {
        withContext(Dispatchers.IO) {
            val existingTracks = dao.getAllTracksOnce()
            val existingBySourceKey = existingTracks.associateBy { it.sourceKey }
            val existingByPath = existingTracks.associateBy { it.filePath.lowercase() }

            val projectionList = mutableListOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.IS_MUSIC,
                MediaStore.Audio.Media.IS_RINGTONE,
                MediaStore.Audio.Media.IS_NOTIFICATION,
                MediaStore.Audio.Media.IS_ALARM,
                MediaStore.Audio.Media.MIME_TYPE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                projectionList.add(MediaStore.Audio.Media.RELATIVE_PATH)
            }

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

            val seenMediaStoreIds = HashSet<Long>()
            val seenPaths = HashSet<String>()
            val scannedTracks = mutableListOf<TrackEntity>()
            val scannedSourceKeys = HashSet<String>()

            val scanOk = runCatching {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projectionList.toTypedArray(),
                    selection,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                    val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                    val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                    val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val dateAddedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                    val isRingtoneCol = cursor.getColumnIndex(MediaStore.Audio.Media.IS_RINGTONE)
                    val isNotificationCol = cursor.getColumnIndex(MediaStore.Audio.Media.IS_NOTIFICATION)
                    val isAlarmCol = cursor.getColumnIndex(MediaStore.Audio.Media.IS_ALARM)
                    val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                    val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                    } else -1

                    while (cursor.moveToNext()) {
                        val msId = cursor.getLong(idCol)
                        if (!seenMediaStoreIds.add(msId)) continue

                        val isRingtone = if (isRingtoneCol >= 0) cursor.getInt(isRingtoneCol) != 0 else false
                        val isNotification = if (isNotificationCol >= 0) cursor.getInt(isNotificationCol) != 0 else false
                        val isAlarm = if (isAlarmCol >= 0) cursor.getInt(isAlarmCol) != 0 else false
                        if (isRingtone || isNotification || isAlarm) continue

                        val displayName = (if (nameCol >= 0) cursor.getString(nameCol) else null)?.trim()
                        val rawDataPath = (if (dataCol >= 0) cursor.getString(dataCol) else null)?.trim()
                        val relPath = (if (relPathCol >= 0) cursor.getString(relPathCol) else null)?.trim()
                        val rawTitle = (if (titleCol >= 0) cursor.getString(titleCol) else null)?.trim()
                        val rawArtist = (if (artistCol >= 0) cursor.getString(artistCol) else null)?.trim()
                        val rawAlbum = (if (albumCol >= 0) cursor.getString(albumCol) else null)?.trim()
                        val duration = if (durCol >= 0) cursor.getLong(durCol).coerceAtLeast(0L) else 0L
                        val dateAddedSec = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else 0L
                        val mimeType = (if (mimeCol >= 0) cursor.getString(mimeCol) else null)?.lowercase() ?: ""

                        val fileName = displayName
                            ?: rawDataPath?.substringAfterLast('/')
                            ?: "${rawTitle ?: "Track_$msId"}.mp3"

                        val resolvedPath = when {
                            !rawDataPath.isNullOrBlank() -> rawDataPath
                            !relPath.isNullOrBlank() -> "/storage/emulated/0/${relPath.trimEnd('/')}/$fileName"
                            else -> "/storage/emulated/0/Music/$fileName"
                        }

                        // Exclude system ringtones, notifications, and alarms by path as well
                        if (VoiceNoteFilterEngine.isSystemAlertPath(resolvedPath)) continue

                        val normalizedPathKey = resolvedPath.lowercase()
                        if (!seenPaths.add(normalizedPathKey)) continue

                        val ext = resolveExtension(fileName, resolvedPath, mimeType)
                        val title = rawTitle?.takeIf { it.isNotBlank() && it != "<unknown>" }
                            ?: fileName.substringBeforeLast('.')
                        val artist = rawArtist?.takeIf { it.isNotBlank() && it != "<unknown>" }
                            ?: "Unknown Artist"
                        val album = rawAlbum?.takeIf { it.isNotBlank() && it != "<unknown>" }
                            ?: "Unknown Album"
                        val dateAddedMs = if (dateAddedSec > 0L) dateAddedSec * 1000L else System.currentTimeMillis()
                        val contentUri = Uri.withAppendedPath(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            msId.toString()
                        ).toString()

                        val sourceKey = "ms_$msId"
                        scannedSourceKeys.add(sourceKey)

                        val existing = existingBySourceKey[sourceKey]
                            ?: existingByPath[normalizedPathKey]

                        val entity = if (existing != null) {
                            existing.copy(
                                sourceKey = sourceKey,
                                mediaStoreId = msId,
                                title = if (existing.tagsEdited) existing.title else title,
                                artist = if (existing.tagsEdited) existing.artist else artist,
                                album = if (existing.tagsEdited) existing.album else album,
                                filePath = resolvedPath,
                                uriString = contentUri,
                                extension = ext,
                                durationMs = duration.coerceAtLeast(1_000L),
                                dateAdded = dateAddedMs,
                                qualityBadge = ext.uppercase()
                            )
                        } else {
                            TrackEntity(
                                sourceKey = sourceKey,
                                mediaStoreId = msId,
                                title = title,
                                artist = artist,
                                album = album,
                                filePath = resolvedPath,
                                uriString = contentUri,
                                extension = ext,
                                durationMs = duration.coerceAtLeast(1_000L),
                                dateAdded = dateAddedMs,
                                qualityBadge = ext.uppercase()
                            )
                        }
                        scannedTracks.add(entity)
                    }
                } ?: error("MediaStore query returned no cursor")
            }.isSuccess

            // A failed query (no permission yet, storage not mounted) used to look like "every song
            // was deleted": all tracks and their saved lyrics were wiped, then re-added with new ids.
            // Keep the library untouched in that case.
            if (!scanOk) return@withContext existingTracks.size
            val existingMediaStoreCount = existingTracks.count { it.sourceKey.startsWith("ms_") }
            if (scannedTracks.isEmpty() && existingMediaStoreCount > 0) return@withContext existingTracks.size

            // Identify any MediaStore tracks in DB that no longer exist on the device (or were duplicates).
            // A track re-matched by file path keeps its old row id under a new MediaStore id: it is not
            // stale, so its saved lyrics and playlist entries must not be deleted.
            val keptIds = scannedTracks.mapNotNullTo(HashSet()) { it.id.takeIf { id -> id != 0L } }
            val staleIds = existingTracks
                .filter { existing ->
                    existing.sourceKey.startsWith("ms_") && existing.sourceKey !in scannedSourceKeys
                }
                .map { it.id }
                .filter { it !in keptIds }

            dao.syncMediaStoreTracks(
                tracksToUpsert = scannedTracks,
                staleTrackIds = staleIds
            )

            scannedTracks.size
        }
    }

    private fun resolveExtension(fileName: String, path: String, mimeType: String): String {
        val fromName = fileName.substringAfterLast('.', "").lowercase()
        if (fromName.isNotBlank() && fromName != fileName.lowercase()) return fromName
        val fromPath = path.substringAfterLast('.', "").lowercase()
        if (fromPath.isNotBlank() && fromPath != path.lowercase()) return fromPath
        return when {
            mimeType.contains("flac") -> "flac"
            mimeType.contains("mp4") || mimeType.contains("m4a") || mimeType.contains("aac") -> "m4a"
            mimeType.contains("mpeg") || mimeType.contains("mp3") -> "mp3"
            mimeType.contains("opus") -> "opus"
            mimeType.contains("ogg") -> "ogg"
            else -> "mp3"
        }
    }
}
