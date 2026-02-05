package com.example.vod.utils

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration

/**
 * Orientation helper: allow auto-rotation on phones/tablets,
 * lock to landscape on Android TV devices.
 */
object OrientationUtils {

    fun applyPreferredOrientation(activity: Activity) {
        val preferred = if (isTelevision(activity)) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        if (activity.requestedOrientation != preferred) {
            activity.requestedOrientation = preferred
        }
    }

    private fun isTelevision(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
}
