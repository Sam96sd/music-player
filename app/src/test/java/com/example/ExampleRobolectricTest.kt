package com.example

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.TrackEntity
import com.example.domain.LyricsLinkExtractor
import com.example.player.MediaItemMapper
import com.example.player.PlaybackService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Muffin Music Player", appName)
    }

    @Test
    fun `parse lrclib json link payload`() {
        val lrclibJson = """{"trackName":"Ordinary","artistName":"Alex Warren","syncedLyrics":"[00:05.00] They say the holy water's watered down"}"""
        val parsedJson = LyricsLinkExtractor.parseLrclibJson(lrclibJson)
        assertNotNull(parsedJson)
        assertTrue(parsedJson!!.isSynced)
        assertTrue(parsedJson.text.contains("holy water"))
    }

    @Test
    fun `playback service and media permissions are properly declared in manifest`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pm = context.packageManager
        val packageInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val permissions = packageInfo.requestedPermissions?.toSet().orEmpty()

        assertTrue(Manifest.permission.FOREGROUND_SERVICE in permissions)
        assertTrue(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK in permissions)
        assertTrue(Manifest.permission.WAKE_LOCK in permissions)
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in permissions)

        val serviceInfo = pm.getServiceInfo(
            ComponentName(context, PlaybackService::class.java),
            PackageManager.GET_META_DATA
        )
        assertTrue(serviceInfo.exported)
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            serviceInfo.foregroundServiceType
        )

        val mediaSessionServices = pm.queryIntentServices(
            Intent("androidx.media3.session.MediaSessionService"),
            0
        )
        assertTrue(
            mediaSessionServices.any {
                it.serviceInfo.name == PlaybackService::class.java.name
            }
        )
    }

    @Test
    fun `media item mapper preserves track metadata and lightweight artwork uri`() {
        val track = TrackEntity(
            id = 42L,
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            filePath = "/storage/emulated/0/Music/blinding.mp3",
            uriString = "content://media/external/audio/media/42",
            extension = "mp3",
            durationMs = 200_000L
        )
        val item = MediaItemMapper.toMediaItem(track)
        assertEquals("42", item.mediaId)
        assertEquals("Blinding Lights", item.mediaMetadata.title?.toString())
        assertEquals("The Weeknd", item.mediaMetadata.artist?.toString())
        assertEquals("After Hours", item.mediaMetadata.albumTitle?.toString())
        assertNotNull(item.mediaMetadata.artworkUri)
        assertEquals("muffin-art", item.mediaMetadata.artworkUri?.scheme)
    }
}
