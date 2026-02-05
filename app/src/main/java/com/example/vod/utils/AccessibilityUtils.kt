package com.example.vod.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * Utility class for accessibility features.
 * Provides helpers for TalkBack, screen readers, and accessibility services.
 */
object AccessibilityUtils {

    /**
     * Check if a screen reader (TalkBack) is enabled.
     */
    fun isScreenReaderEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        
        if (!am.isEnabled) return false
        
        val serviceInfoList = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_SPOKEN
        )
        return serviceInfoList.isNotEmpty()
    }

    /**
     * Check if any accessibility service is enabled.
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return am?.isEnabled == true
    }

    /**
     * Set content description for an image with optional context.
     * 
     * @param imageView The ImageView to describe
     * @param description Main description of the image
     * @param context Additional context (e.g., "movie poster", "episode thumbnail")
     */
    fun setImageDescription(imageView: ImageView, description: String, context: String? = null) {
        imageView.contentDescription = if (context != null) {
            "$description, $context"
        } else {
            description
        }
    }

    /**
     * Make a view announce changes to accessibility services.
     * 
     * @param view The view to announce
     * @param announcement The text to announce
     */
    fun announceForAccessibility(view: View, announcement: String) {
        view.announceForAccessibility(announcement)
    }

    /**
     * Set up a custom action for accessibility.
     * Useful for providing additional context about what will happen when an item is activated.
     * 
     * @param view The view to add action to
     * @param actionLabel Label for the action (e.g., "Opens movie details")
     */
    fun setAccessibilityAction(view: View, actionLabel: String) {
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_CLICK,
                        actionLabel
                    )
                )
            }
        })
    }

    /**
     * Set heading role for section titles.
     * Helps screen readers navigate between sections.
     */
    fun setAsHeading(textView: TextView) {
        ViewCompat.setAccessibilityHeading(textView, true)
    }

    /**
     * Create a combined content description for a video card.
     * 
     * @param title Video title
     * @param year Release year
     * @param rating Rating score
     * @param quality Video quality (HD, 4K, etc.)
     * @param hasProgress Whether user has started watching
     * @param progressPercent Watch progress percentage
     * @param hasEpisodeProgress Whether this series has watched episodes
     */
    fun createVideoCardDescription(
        title: String,
        year: Int,
        rating: String?,
        quality: String?,
        hasProgress: Boolean = false,
        progressPercent: Int = 0,
        hasEpisodeProgress: Boolean = false
    ): String {
        val parts = mutableListOf<String>()
        parts.add(title)
        if (year > 0) parts.add(year.toString())
        if (!rating.isNullOrBlank()) parts.add("rated $rating")
        if (!quality.isNullOrBlank()) parts.add(quality)
        
        if (hasProgress && progressPercent > 0) {
            parts.add("$progressPercent% watched")
        } else if (hasEpisodeProgress) {
            parts.add("watched episodes")
        }
        
        return parts.joinToString(", ")
    }

    /**
     * Create a content description for a button with state.
     * 
     * @param buttonText Button label text
     * @param stateDescription Current state (e.g., "added to watchlist")
     */
    fun createButtonDescription(buttonText: String, stateDescription: String? = null): String {
        return if (stateDescription != null) {
            "$buttonText, $stateDescription"
        } else {
            buttonText
        }
    }

    /**
     * Configure live region for dynamic content updates.
     * 
     * @param view View that will have dynamic content
     * @param mode ACCESSIBILITY_LIVE_REGION_POLITE or ACCESSIBILITY_LIVE_REGION_ASSERTIVE
     */
    fun setLiveRegion(view: View, mode: Int = View.ACCESSIBILITY_LIVE_REGION_POLITE) {
        view.accessibilityLiveRegion = mode
    }

    /**
     * Format runtime for accessibility announcement.
     * 
     * @param runtimeMinutes Runtime in minutes
     * @return Formatted string like "1 hour 30 minutes"
     */
    fun formatRuntimeForAccessibility(runtimeMinutes: Int): String {
        val hours = runtimeMinutes / 60
        val minutes = runtimeMinutes % 60
        
        return when {
            hours > 0 && minutes > 0 -> "$hours hour${if (hours > 1) "s" else ""} $minutes minute${if (minutes > 1) "s" else ""}"
            hours > 0 -> "$hours hour${if (hours > 1) "s" else ""}"
            else -> "$minutes minute${if (minutes > 1) "s" else ""}"
        }
    }
}
