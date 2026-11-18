package com.example.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import com.example.R
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.Executors

/**
 * Loads embedded album artwork for the active track on demand (avoiding large artwork
 * ByteArrays inside Binder Timeline transactions) and falls back to the app icon
 * so the notification, lock screen, and Samsung Quick Panel always show artwork.
 */
@OptIn(UnstableApi::class)
class MuffinArtworkBitmapLoader(
    private val context: Context
) : BitmapLoader {

    private val delegate = DataSourceBitmapLoader.Builder(context).build()
    private val executor = Executors.newCachedThreadPool()
    private val cache = object : LruCache<String, Bitmap>(16) {}

    override fun supportsMimeType(mimeType: String): Boolean {
        return delegate.supportsMimeType(mimeType)
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        return delegate.decodeBitmap(data)
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        if (uri.scheme != "muffin-art") {
            return delegate.loadBitmap(uri)
        }
        val future = SettableFuture.create<Bitmap>()
        val audioUriString = uri.getQueryParameter("audioUri").orEmpty()
        executor.execute {
            try {
                val cached = if (audioUriString.isNotBlank()) cache.get(audioUriString) else null
                if (cached != null && !cached.isRecycled) {
                    future.set(cached)
                    return@execute
                }
                val loaded = loadEmbeddedOrFallbackArtwork(audioUriString)
                if (audioUriString.isNotBlank()) {
                    cache.put(audioUriString, loaded)
                }
                future.set(loaded)
            } catch (t: Throwable) {
                future.setException(t)
            }
        }
        return future
    }

    private fun loadEmbeddedOrFallbackArtwork(audioUriString: String): Bitmap {
        if (audioUriString.isNotBlank()) {
            val audioUri = Uri.parse(audioUriString)

            // 1. Try ID3/MP4/FLAC embedded picture via MediaMetadataRetriever
            val embedded = extractEmbeddedPicture(audioUri)
            if (embedded != null) return embedded

            // 2. Try MediaStore album/audio thumbnail on Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val thumbnail = runCatching {
                    context.contentResolver.loadThumbnail(audioUri, Size(512, 512), null)
                }.getOrNull()
                if (thumbnail != null) return thumbnail
            }
        }

        // 3. Fallback to Muffin Music Player artwork / app launcher icon
        return loadFallbackAppIconBitmap()
    }

    private fun extractEmbeddedPicture(audioUri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, audioUri)
            val bytes = retriever.embeddedPicture ?: return null
            decodeSampledBitmap(bytes, maxDimension = 512)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeSampledBitmap(bytes: ByteArray, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sampleSize = 1
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        if (maxSide > maxDimension && maxSide > 0) {
            while ((maxSide / sampleSize) > maxDimension) {
                sampleSize *= 2
            }
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun loadFallbackAppIconBitmap(): Bitmap {
        val resourceBitmap = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.img_launcher_icon_1794673274718)
        }.getOrNull()
        if (resourceBitmap != null) return resourceBitmap

        val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
        val width = (drawable?.intrinsicWidth ?: 256).coerceAtLeast(256)
        val height = (drawable?.intrinsicHeight ?: 256).coerceAtLeast(256)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable?.setBounds(0, 0, canvas.width, canvas.height)
        drawable?.draw(canvas)
        return bitmap
    }
}
