package com.example.vod.utils

import java.util.Locale

object RatingUtils {
    fun formatImdbRating(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val value = raw.trim().toFloatOrNull() ?: return raw.trim()
        return String.format(Locale.US, "%.1f", value)
    }
}
