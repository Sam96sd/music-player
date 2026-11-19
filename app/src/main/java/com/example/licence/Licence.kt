package com.example.licence

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.example.BuildConfig
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Device-bound activation, checked fully offline (same scheme as the Muffin TV app).
 * The activation screen shows a short device code made from ANDROID_ID; the owner turns it into a
 * 10-digit code with the private keygen. A copied APK on another phone shows another device code.
 * The key is injected at build time from the MUSIC_LICENCE_KEY secret and is not in this repository.
 */
object Licence {
    private const val PREFS = "licence"
    private const val KEY_CODE = "activation"

    private fun key(): ByteArray {
        val hex = BuildConfig.LICENCE_KEY
        return ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    @SuppressLint("HardwareIds")
    fun deviceCode(context: Context): String {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "none"
        return deviceCodeFor(id)
    }

    fun deviceCodeFor(androidId: String): String {
        val h = MessageDigest.getInstance("SHA-256").digest("muffin-music-device|$androidId".toByteArray())
        val s = (0 until 4).joinToString("") { "%02X".format(h[it].toInt() and 0xFF) }
        return s.substring(0, 4) + "-" + s.substring(4)
    }

    fun activationFor(deviceCode: String): String {
        val d = deviceCode.replace("-", "").trim().uppercase()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key(), "HmacSHA256"))
        val h = mac.doFinal("muffin-music-v1|$d".toByteArray())
        var v = 0L
        for (i in 0 until 5) v = (v shl 8) or (h[i].toLong() and 0xFF)
        val n = "%010d".format(v % 10_000_000_000L)
        return n.substring(0, 5) + "-" + n.substring(5)
    }

    fun isValid(deviceCode: String, typed: String?): Boolean {
        val t = typed?.filter { it.isDigit() } ?: return false
        val want = activationFor(deviceCode).replace("-", "")
        return t.length == 10 && MessageDigest.isEqual(t.toByteArray(), want.toByteArray())
    }

    fun isActivated(context: Context): Boolean =
        isValid(deviceCode(context), context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CODE, null))

    fun save(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_CODE, code.filter { it.isDigit() }).apply()
    }
}
