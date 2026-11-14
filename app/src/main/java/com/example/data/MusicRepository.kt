package com.example.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.data.audio.StudioAudioSeeder
import com.example.data.local.BlacklistedFolderEntity
import com.example.data.local.MusicDao
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackCrossRef
import com.example.data.local.PlaylistWithTracks
import com.example.data.local.SavedLyricsEntity
import com.example.data.local.TrackEntity
import com.example.data.network.MultiSourceLyricsClient
import com.example.data.network.OnlineLyricCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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

    suspend fun ensureSeeded() {
        StudioAudioSeeder.ensureSeeded(context, dao)
    }

    suspend fun updateTrackTags(trackId: Long, title: String, artist: String, album: String) {
        dao.updateTrackTags(trackId, title.trim(), artist.trim(), album.trim())
    }

    suspend fun setTrackHidden(trackId: Long, isHidden: Boolean) {
        dao.setTrackHidden(trackId, isHidden)
    }

    suspend fun deleteTrackFromDevice(track: TrackEntity) = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(track.uriString)
            if (uri.scheme == "file") {
                uri.path?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            }
        }
        dao.removeTrackFromAllPlaylists(track.id)
        dao.deleteLyricsForTrack(track.id)
        dao.deleteTrackById(track.id)
    }

    suspend fun updateLyricOffset(trackId: Long, offsetMs: Long) {
        dao.updateLyricOffset(trackId, offsetMs)
    }

    suspend fun incrementPlayCount(trackId: Long) {
        dao.incrementPlayCount(trackId)
    }

    suspend fun searchOnlineLyrics(title: String, artist: String): List<OnlineLyricCandidate> {
        return lyricsClient.searchTopProviders(title, artist)
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
                val track = TrackEntity(
                    title = baseTitle,
                    artist = "Local Artist",
                    album = "Imported Audio",
                    filePath = "/storage/emulated/0/Music/Imported/$displayName",
                    uriString = uri.toString(),
                    extension = ext,
                    durationMs = 180_000L,
                    qualityBadge = "${ext.uppercase()} • Local File"
                )
                dao.insertTrack(track)
                importedCount++
            }
        }
        importedCount
    }

    suspend fun scanPublicMediaStore(): Int = withContext(Dispatchers.IO) {
        var count = 0
        runCatching {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DISPLAY_NAME
            )
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown Title"
                    val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                    val album = cursor.getString(albumCol) ?: "Local Album"
                    val duration = cursor.getLong(durCol).coerceAtLeast(30_000L)
                    val displayName = cursor.getString(nameCol) ?: "$title.mp3"
                    val ext = displayName.substringAfterLast('.', "mp3").lowercase()
                    val contentUri = Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    dao.insertTrack(
                        TrackEntity(
                            title = title,
                            artist = artist,
                            album = album,
                            filePath = "/storage/emulated/0/Music/$displayName",
                            uriString = contentUri.toString(),
                            extension = ext,
                            durationMs = duration,
                            qualityBadge = "${ext.uppercase()} • Device Audio"
                        )
                    )
                    count++
                }
            }
        }
        count
    }
}
