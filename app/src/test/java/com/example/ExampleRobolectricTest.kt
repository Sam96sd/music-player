package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.LyricsLinkExtractor
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
}
