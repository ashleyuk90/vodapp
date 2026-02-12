package com.example.vod.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Creates encrypted SharedPreferences with a crash-safe fallback.
 * Keystore corruption can happen after backup/restore or OEM firmware issues.
 */
object SecurePrefs {
    private const val TAG = "SecurePrefs"

    fun get(context: Context, prefsName: String): SharedPreferences {
        val appContext = context.applicationContext
        return runCatching {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                appContext,
                prefsName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse { error ->
            Log.e(
                TAG,
                "EncryptedSharedPreferences unavailable for $prefsName; using unencrypted fallback.",
                error
            )
            appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        }
    }
}
