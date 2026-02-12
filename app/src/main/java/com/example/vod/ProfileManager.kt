package com.example.vod

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.vod.utils.SecurePrefs

/**
 * Singleton manager for user profile state.
 * Handles storing and retrieving the active profile across app sessions.
 * 
 * Usage:
 * ```kotlin
 * // Initialize once in Application or first Activity
 * ProfileManager.init(context)
 * 
 * // Set active profile after selection
 * ProfileManager.setActiveProfile(profile)
 * 
 * // Get active profile ID for API calls
 * val profileId = ProfileManager.getActiveProfileId()
 * ```
 */
object ProfileManager {

    private const val PREFS_NAME = "VOD_PROFILE_PREFS"
    private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    private const val KEY_ACTIVE_PROFILE_NAME = "active_profile_name"
    private const val KEY_AUTO_SKIP_INTRO = "auto_skip_intro"
    private const val KEY_AUTO_SKIP_CREDITS = "auto_skip_credits"
    private const val KEY_AUTOPLAY_NEXT = "autoplay_next"
    private const val KEY_MAX_CONTENT_RATING = "max_content_rating"
    private const val KEY_HAS_PIN = "has_pin"
    private const val KEY_DEFAULT_PROFILE_ID = "default_profile_id"

    private var prefs: SharedPreferences? = null
    private var activeProfile: Profile? = null

    /**
     * Initialize the ProfileManager with application context.
     * Should be called early in app lifecycle (e.g., onCreate of first Activity).
     */
    fun init(context: Context) {
        if (prefs != null) return

        prefs = SecurePrefs.get(context, PREFS_NAME)

        // Load cached profile if exists
        loadCachedProfile()
    }

    /**
     * Load the cached profile from SharedPreferences.
     */
    private fun loadCachedProfile() {
        val id = prefs?.getInt(KEY_ACTIVE_PROFILE_ID, -1) ?: -1
        if (id > 0) {
            activeProfile = Profile(
                id = id,
                name = prefs?.getString(KEY_ACTIVE_PROFILE_NAME, "Default") ?: "Default",
                autoSkipIntro = prefs?.getBoolean(KEY_AUTO_SKIP_INTRO, false) ?: false,
                autoSkipCredits = prefs?.getBoolean(KEY_AUTO_SKIP_CREDITS, false) ?: false,
                autoplayNext = prefs?.getBoolean(KEY_AUTOPLAY_NEXT, true) ?: true,
                maxContentRating = prefs?.getString(KEY_MAX_CONTENT_RATING, null),
                hasPin = prefs?.getBoolean(KEY_HAS_PIN, false) ?: false
            )
        }
    }

    /**
     * Set the active profile after user selection.
     * Persists the profile to SharedPreferences for app restarts.
     */
    fun setActiveProfile(profile: Profile) {
        activeProfile = profile
        prefs?.edit()?.apply {
            putInt(KEY_ACTIVE_PROFILE_ID, profile.id)
            putString(KEY_ACTIVE_PROFILE_NAME, profile.name)
            putBoolean(KEY_AUTO_SKIP_INTRO, profile.autoSkipIntro)
            putBoolean(KEY_AUTO_SKIP_CREDITS, profile.autoSkipCredits)
            putBoolean(KEY_AUTOPLAY_NEXT, profile.autoplayNext)
            putString(KEY_MAX_CONTENT_RATING, profile.maxContentRating)
            putBoolean(KEY_HAS_PIN, profile.hasPin)
            apply()
        }
    }

    /**
     * Get the currently active profile.
     * @return The active Profile or null if no profile is selected.
     */
    fun getActiveProfile(): Profile? = activeProfile

    /**
     * Get the active profile ID for API calls.
     * @return The profile ID or null if no profile is active.
     */
    fun getActiveProfileId(): Int? = activeProfile?.id?.takeIf { it > 0 }

    /**
     * Get the active profile name for display.
     * @return The profile name or "Default" if not set.
     */
    fun getActiveProfileName(): String = activeProfile?.name ?: "Default"

    /**
     * Check if a profile is currently active.
     */
    fun hasActiveProfile(): Boolean = activeProfile != null && (activeProfile?.id ?: 0) > 0

    /**
     * Check if auto-skip intro is enabled for current profile.
     */
    fun shouldAutoSkipIntro(): Boolean = activeProfile?.autoSkipIntro ?: false

    /**
     * Check if auto-skip credits is enabled for current profile.
     */
    fun shouldAutoSkipCredits(): Boolean = activeProfile?.autoSkipCredits ?: false

    /**
     * Check if autoplay next episode is enabled for current profile.
     */
    fun shouldAutoplayNext(): Boolean = activeProfile?.autoplayNext ?: true

    /**
     * Update playback-related preferences for the active profile.
     */
    fun updatePlaybackPreferences(
        autoSkipIntro: Boolean,
        autoSkipCredits: Boolean,
        autoplayNext: Boolean
    ) {
        val current = activeProfile ?: return
        val updated = current.copy(
            autoSkipIntro = autoSkipIntro,
            autoSkipCredits = autoSkipCredits,
            autoplayNext = autoplayNext
        )
        setActiveProfile(updated)
    }

    /**
     * Get the maximum content rating allowed for current profile.
     * @return UK rating string (U/PG/12/12A/15/18/R18) or null if no restriction.
     */
    fun getMaxContentRating(): String? = activeProfile?.maxContentRating

    /**
     * Clear the active profile (e.g., on logout or profile switch).
     */
    fun clearActiveProfile() {
        activeProfile = null
        prefs?.edit()?.apply {
            remove(KEY_ACTIVE_PROFILE_ID)
            remove(KEY_ACTIVE_PROFILE_NAME)
            remove(KEY_AUTO_SKIP_INTRO)
            remove(KEY_AUTO_SKIP_CREDITS)
            remove(KEY_AUTOPLAY_NEXT)
            remove(KEY_MAX_CONTENT_RATING)
            remove(KEY_HAS_PIN)
            apply()
        }
    }

    /**
     * Check if the active profile has parental controls enabled.
     */
    fun hasParentalControls(): Boolean = !activeProfile?.maxContentRating.isNullOrEmpty()

    /**
     * Set a profile as the default for auto-login.
     * @param profileId The profile ID to set as default, or null to clear default.
     */
    fun setDefaultProfileId(profileId: Int?) {
        prefs?.edit()?.apply {
            if (profileId != null && profileId > 0) {
                putInt(KEY_DEFAULT_PROFILE_ID, profileId)
            } else {
                remove(KEY_DEFAULT_PROFILE_ID)
            }
            apply()
        }
    }

    /**
     * Get the default profile ID for auto-login.
     * @return The default profile ID or null if not set.
     */
    fun getDefaultProfileId(): Int? {
        val id = prefs?.getInt(KEY_DEFAULT_PROFILE_ID, -1) ?: -1
        return if (id > 0) id else null
    }

    /**
     * Check if a specific profile is set as default.
     */
    fun isDefaultProfile(profileId: Int): Boolean = getDefaultProfileId() == profileId

    /**
     * Clear the default profile setting.
     */
    fun clearDefaultProfile() {
        prefs?.edit { remove(KEY_DEFAULT_PROFILE_ID) }
    }
}
