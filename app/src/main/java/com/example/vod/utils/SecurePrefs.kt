package com.example.vod.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * Creates encrypted SharedPreferences with a crash-safe recovery strategy.
 * Keystore corruption can happen after backup/restore or OEM firmware issues.
 *
 * Recovery strategy:
 * 1. Try to open encrypted prefs normally.
 * 2. If that fails (corrupted keystore/prefs), delete the corrupted prefs file and retry.
 * 3. If retry also fails, fall back to unencrypted prefs but clear any pre-existing
 *    sensitive data so stale credentials are never exposed in plaintext.
 */
object SecurePrefs {
    private const val TAG = "SecurePrefs"

    fun get(context: Context, prefsName: String): SharedPreferences {
        val appContext = context.applicationContext

        // First attempt: open encrypted prefs normally
        try {
            return createEncryptedPrefs(appContext, prefsName)
        } catch (firstError: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences failed for $prefsName, attempting recovery", firstError)
        }

        // Recovery: delete corrupted prefs file and retry
        try {
            deletePrefsFile(appContext, prefsName)
            return createEncryptedPrefs(appContext, prefsName)
        } catch (retryError: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences retry failed for $prefsName; using unencrypted fallback", retryError)
        }

        // Last resort: unencrypted fallback with sensitive data wiped
        val fallback = appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        fallback.edit()
            .remove(Constants.KEY_PASS)
            .remove(Constants.KEY_CSRF_TOKEN)
            .remove(Constants.KEY_UPDATE_FEED_URL)
            .apply()
        return fallback
    }

    private fun createEncryptedPrefs(context: Context, prefsName: String): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            prefsName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun deletePrefsFile(context: Context, prefsName: String) {
        val prefsFile = File(context.filesDir.parent, "shared_prefs/$prefsName.xml")
        if (prefsFile.exists()) {
            prefsFile.delete()
            Log.d(TAG, "Deleted corrupted prefs file: ${prefsFile.name}")
        }
    }
}
