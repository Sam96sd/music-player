package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["sourceKey"], unique = true)
    ]
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceKey: String = "",
    val mediaStoreId: Long? = null,
    val title: String,
    val artist: String,
    val album: String,
    val filePath: String,
    val uriString: String,
    val extension: String, // mp3, m4a, flac, opus, ogg, etc.
    val durationMs: Long,
    val dateAdded: Long = System.currentTimeMillis(),
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L,
    val isHidden: Boolean = false,
    val lyricOffsetMs: Long = 0L,
    val tagsEdited: Boolean = false,
    val customSearchTitle: String? = null,
    val customSearchArtist: String? = null,
    val accentHex: String = "#00E5FF",
    val qualityBadge: String = "Audio"
)

@Entity(tableName = "saved_lyrics")
data class SavedLyricsEntity(
    @PrimaryKey val trackId: Long,
    val rawLyrics: String,
    val isSynced: Boolean,
    val sourceName: String,
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_track_cross_ref",
    primaryKeys = ["playlistId", "trackId"]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

data class PlaylistWithTracks(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PlaylistTrackCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "trackId"
        )
    )
    val tracks: List<TrackEntity>
)

@Entity(tableName = "blacklisted_folders")
data class BlacklistedFolderEntity(
    @PrimaryKey val pathPattern: String,
    val label: String,
    val description: String,
    val isEnabled: Boolean = true,
    val isBuiltInRule: Boolean = true
)
