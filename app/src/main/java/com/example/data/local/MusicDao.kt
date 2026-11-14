package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC")
    fun observeAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks")
    suspend fun getAllTracksOnce(): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun getTrackCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity): Long

    @Query("UPDATE tracks SET title = :title, artist = :artist, album = :album, tagsEdited = 1 WHERE id = :trackId")
    suspend fun updateTrackTags(trackId: Long, title: String, artist: String, album: String)

    @Query("UPDATE tracks SET isHidden = :isHidden WHERE id = :trackId")
    suspend fun setTrackHidden(trackId: Long, isHidden: Boolean)

    @Query("UPDATE tracks SET isHidden = :isHidden WHERE id IN (:trackIds)")
    suspend fun setTracksHidden(trackIds: List<Long>, isHidden: Boolean)

    @Query("UPDATE tracks SET lyricOffsetMs = :offsetMs WHERE id = :trackId")
    suspend fun updateLyricOffset(trackId: Long, offsetMs: Long)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayedAt = :timestamp WHERE id = :trackId")
    suspend fun incrementPlayCount(trackId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrackById(trackId: Long)

    @Query("DELETE FROM tracks WHERE id IN (:trackIds)")
    suspend fun deleteTracksByIds(trackIds: List<Long>)

    @Query("DELETE FROM playlist_track_cross_ref WHERE trackId = :trackId")
    suspend fun removeTrackFromAllPlaylists(trackId: Long)

    @Query("DELETE FROM playlist_track_cross_ref WHERE trackId IN (:trackIds)")
    suspend fun removeTracksFromAllPlaylists(trackIds: List<Long>)

    @Query("DELETE FROM saved_lyrics WHERE trackId = :trackId")
    suspend fun deleteLyricsForTrack(trackId: Long)

    @Query("DELETE FROM saved_lyrics WHERE trackId IN (:trackIds)")
    suspend fun deleteLyricsForTracks(trackIds: List<Long>)

    @Transaction
    suspend fun deleteTracksWithCleanup(trackIds: List<Long>) {
        if (trackIds.isEmpty()) return
        removeTracksFromAllPlaylists(trackIds)
        deleteLyricsForTracks(trackIds)
        deleteTracksByIds(trackIds)
    }

    @Transaction
    suspend fun syncMediaStoreTracks(
        tracksToUpsert: List<TrackEntity>,
        staleTrackIds: List<Long>
    ) {
        if (staleTrackIds.isNotEmpty()) {
            removeTracksFromAllPlaylists(staleTrackIds)
            deleteLyricsForTracks(staleTrackIds)
            deleteTracksByIds(staleTrackIds)
        }
        if (tracksToUpsert.isNotEmpty()) {
            insertTracks(tracksToUpsert)
        }
    }

    // Lyrics
    @Query("SELECT * FROM saved_lyrics")
    fun observeAllSavedLyrics(): Flow<List<SavedLyricsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLyrics(lyrics: SavedLyricsEntity)

    // Playlists
    @Transaction
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylistsWithTracks(): Flow<List<PlaylistWithTracks>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId")
    suspend fun clearPlaylistCrossRefs(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTrackToPlaylist(crossRef: PlaylistTrackCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTracksToPlaylist(crossRefs: List<PlaylistTrackCrossRef>)

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)

    // Blacklisted folders
    @Query("SELECT * FROM blacklisted_folders ORDER BY isBuiltInRule DESC, label ASC")
    fun observeBlacklistedFolders(): Flow<List<BlacklistedFolderEntity>>

    @Query("SELECT COUNT(*) FROM blacklisted_folders")
    suspend fun getBlacklistedFolderCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlacklistedFolders(folders: List<BlacklistedFolderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlacklistedFolder(folder: BlacklistedFolderEntity)

    @Query("UPDATE blacklisted_folders SET isEnabled = :enabled WHERE pathPattern = :pathPattern")
    suspend fun setFolderRuleEnabled(pathPattern: String, enabled: Boolean)

    @Query("DELETE FROM blacklisted_folders WHERE pathPattern = :pathPattern AND isBuiltInRule = 0")
    suspend fun deleteCustomFolderRule(pathPattern: String)
}
