package com.example.vod.utils

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * A CookieJar that persists cookies to SharedPreferences.
 * Survives app backgrounding, device idle/sleep, and process death.
 *
 * Cookies are kept in memory for fast access and synced to disk on every change.
 * Thread-safe for concurrent OkHttp access.
 */
class PersistentCookieJar(context: Context) : CookieJar {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val cache = ConcurrentHashMap<String, ConcurrentHashMap<String, Cookie>>()

    init {
        loadFromDisk()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val domainCookies = cache.getOrPut(url.host) { ConcurrentHashMap() }
        for (cookie in cookies) {
            if (cookie.expiresAt < System.currentTimeMillis()) {
                domainCookies.remove(cookie.name)
            } else {
                domainCookies[cookie.name] = cookie
            }
        }
        persistToDisk()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val result = mutableListOf<Cookie>()
        val domainCookies = cache[url.host] ?: return result

        val expired = mutableListOf<String>()
        for ((name, cookie) in domainCookies) {
            if (cookie.expiresAt < now) {
                expired.add(name)
            } else if (cookie.matches(url)) {
                result.add(cookie)
            }
        }
        if (expired.isNotEmpty()) {
            expired.forEach { domainCookies.remove(it) }
            persistToDisk()
        }
        return result
    }

    /**
     * Get a specific cookie value by name across all domains.
     * Used by PlayerActivity to extract PHPSESSID for stream requests.
     */
    fun getCookieValue(name: String): String? {
        for (domainCookies in cache.values) {
            val cookie = domainCookies[name]
            if (cookie != null && cookie.expiresAt >= System.currentTimeMillis()) {
                return cookie.value
            }
        }
        return null
    }

    fun clear() {
        cache.clear()
        prefs.edit().clear().apply()
    }

    private fun persistToDisk() {
        val editor = prefs.edit()
        editor.clear()
        for ((domain, cookies) in cache) {
            for ((name, cookie) in cookies) {
                val key = "$domain|$name"
                editor.putString(key, serializeCookie(cookie))
            }
        }
        editor.apply()
    }

    private fun loadFromDisk() {
        val now = System.currentTimeMillis()
        for ((key, value) in prefs.all) {
            val parts = key.split("|", limit = 2)
            if (parts.size != 2 || value !is String) continue
            val domain = parts[0]
            val cookie = deserializeCookie(value) ?: continue
            if (cookie.expiresAt < now) continue
            cache.getOrPut(domain) { ConcurrentHashMap() }[cookie.name] = cookie
        }
    }

    private fun serializeCookie(cookie: Cookie): String {
        return listOf(
            cookie.name,
            cookie.value,
            cookie.domain,
            cookie.path,
            cookie.expiresAt.toString(),
            cookie.secure.toString(),
            cookie.httpOnly.toString()
        ).joinToString("\t")
    }

    private fun deserializeCookie(str: String): Cookie? {
        val parts = str.split("\t")
        if (parts.size < 7) return null
        return try {
            Cookie.Builder()
                .name(parts[0])
                .value(parts[1])
                .domain(parts[2])
                .path(parts[3])
                .expiresAt(parts[4].toLong())
                .apply {
                    if (parts[5].toBoolean()) secure()
                    if (parts[6].toBoolean()) httpOnly()
                }
                .build()
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "vod_cookies"
    }
}
