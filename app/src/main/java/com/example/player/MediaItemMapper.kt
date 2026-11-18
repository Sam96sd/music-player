package com.example.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.SessionCommand
import com.example.data.local.TrackEntity

object MediaItemMapper {

    const val ACTION_PLAY_WITH_SHUFFLE_ORDER = "com.example.player.ACTION_PLAY_WITH_SHUFFLE_ORDER"
    const val ACTION_ENABLE_ANCHORED_SHUFFLE = "com.example.player.ACTION_ENABLE_ANCHORED_SHUFFLE"
    const val EXTRA_SHUFFLED_INDICES = "extra_shuffled_indices"

    val CMD_PLAY_WITH_SHUFFLE_ORDER = SessionCommand(ACTION_PLAY_WITH_SHUFFLE_ORDER, Bundle.EMPTY)
    val CMD_ENABLE_ANCHORED_SHUFFLE = SessionCommand(ACTION_ENABLE_ANCHORED_SHUFFLE, Bundle.EMPTY)

    fun buildArtworkUri(trackId: Long, audioUriString: String): Uri {
        return Uri.Builder()
            .scheme("muffin-art")
            .authority("track")
            .appendPath(trackId.toString())
            .appendQueryParameter("audioUri", audioUriString)
            .build()
    }

    fun toMediaItem(track: TrackEntity): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(buildArtworkUri(track.id, track.uriString))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(Uri.parse(track.uriString))
            .setMediaMetadata(metadata)
            .build()
    }
}
