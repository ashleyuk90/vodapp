package com.example.vod.utils

import com.example.vod.BuildConfig

object UrlUtils {

    /**
     * Resolve a potentially relative URL against the app's BASE_URL.
     * Handles absolute https, absolute http (optional upgrade), protocol-relative,
     * root-relative, and bare path URLs.
     *
     * @param rawUrl The URL string to resolve. Null/blank returns null.
     * @param pathPrefix Optional prefix to prepend for bare/relative paths (e.g. "images/").
     * @param upgradeHttpToHttps If true, rewrites http:// URLs to https://.
     */
    fun resolve(
        rawUrl: String?,
        pathPrefix: String? = null,
        upgradeHttpToHttps: Boolean = false
    ): String? {
        val trimmed = rawUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val baseUrl = normalizedSecureBaseUrl()

        return when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> {
                if (upgradeHttpToHttps) {
                    "https://" + trimmed.substringAfter("://")
                } else {
                    trimmed
                }
            }
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> {
                val prefix = pathPrefix?.trimEnd('/') ?: ""
                if (prefix.isNotEmpty()) "$baseUrl$prefix/${trimmed.trimStart('/')}"
                else "$baseUrl${trimmed.trimStart('/')}"
            }
            else -> {
                val prefix = pathPrefix?.trimEnd('/') ?: ""
                if (prefix.isNotEmpty()) "$baseUrl$prefix/${trimmed.trimStart('/')}"
                else "$baseUrl${trimmed.trimStart('/')}"
            }
        }
    }

    private fun normalizedSecureBaseUrl(): String {
        val raw = BuildConfig.BASE_URL.trim().ifEmpty { "https://localhost/" }
        val schemeSeparatorIndex = raw.indexOf("://")
        val withoutScheme = if (schemeSeparatorIndex >= 0) raw.substring(schemeSeparatorIndex + 3) else raw
        val withScheme = if (raw.startsWith("https://", ignoreCase = true)) raw else "https://$withoutScheme"
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }
}
