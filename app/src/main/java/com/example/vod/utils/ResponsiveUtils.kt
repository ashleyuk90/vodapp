package com.example.vod.utils

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.vod.R

/**
 * Utility class for responsive layout calculations.
 * Helps adapt UI to different screen sizes and orientations.
 */
object ResponsiveUtils {

    // Screen size breakpoints in dp
    private const val PHONE_MAX_WIDTH_DP = 600
    private const val TABLET_MAX_WIDTH_DP = 840
    
    // Grid span counts for different screen sizes
    private const val PHONE_PORTRAIT_SPAN = 2
    private const val PHONE_LANDSCAPE_SPAN = 4
    private const val TABLET_PORTRAIT_SPAN = 4
    private const val TABLET_LANDSCAPE_SPAN = 5
    private const val TV_SPAN = 6

    /**
     * Screen size category.
     */
    enum class ScreenSize {
        PHONE,
        TABLET,
        TV
    }

    /**
     * Get the current screen size category.
     */
    fun getScreenSize(context: Context): ScreenSize {
        val screenWidthDp = getScreenWidthDp(context)
        
        return when {
            screenWidthDp < PHONE_MAX_WIDTH_DP -> ScreenSize.PHONE
            screenWidthDp < TABLET_MAX_WIDTH_DP -> ScreenSize.TABLET
            else -> ScreenSize.TV
        }
    }

    /**
     * Get screen width in dp.
     */
    fun getScreenWidthDp(context: Context): Int {
        val displayMetrics = context.resources.displayMetrics
        return (displayMetrics.widthPixels / displayMetrics.density).toInt()
    }

    /**
     * Get screen height in dp.
     */
    fun getScreenHeightDp(context: Context): Int {
        val displayMetrics = context.resources.displayMetrics
        return (displayMetrics.heightPixels / displayMetrics.density).toInt()
    }

    /**
     * Check if device is in landscape orientation.
     */
    fun isLandscape(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    /**
     * Check if device is in portrait orientation.
     */
    fun isPortrait(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    /**
     * Get the recommended grid span count for the current screen configuration.
     */
    fun getGridSpanCount(context: Context): Int {
        val screenSize = getScreenSize(context)
        val isLandscape = isLandscape(context)
        
        return when (screenSize) {
            ScreenSize.PHONE -> if (isLandscape) PHONE_LANDSCAPE_SPAN else PHONE_PORTRAIT_SPAN
            ScreenSize.TABLET -> if (isLandscape) TABLET_LANDSCAPE_SPAN else TABLET_PORTRAIT_SPAN
            ScreenSize.TV -> TV_SPAN
        }
    }

    /**
     * Get the resource value for grid span count (uses resource qualifier system).
     */
    fun getGridSpanCountFromResources(context: Context): Int {
        return context.resources.getInteger(R.integer.grid_span_count)
    }

    /**
     * Calculate optimal card width based on screen width and desired span count.
     * 
     * @param context Context for display metrics
     * @param spanCount Number of cards per row
     * @param marginDp Margin between cards in dp
     * @param paddingDp Container padding in dp
     * @return Optimal card width in pixels
     */
    fun calculateCardWidth(
        context: Context, 
        spanCount: Int, 
        marginDp: Int = 12, 
        paddingDp: Int = 16
    ): Int {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val density = displayMetrics.density
        
        val marginPx = (marginDp * density).toInt()
        val paddingPx = (paddingDp * density).toInt()
        
        // Account for container padding and margins between cards
        val totalPadding = paddingPx * 2
        val totalMargins = marginPx * (spanCount + 1)
        val availableWidth = screenWidth - totalPadding - totalMargins
        
        return availableWidth / spanCount
    }

    /**
     * Check if the device has a large screen (tablet or TV).
     */
    fun isLargeScreen(context: Context): Boolean {
        return getScreenSize(context) != ScreenSize.PHONE
    }

    /**
     * Check if the device is a TV.
     */
    fun isTV(context: Context): Boolean {
        return getScreenSize(context) == ScreenSize.TV
    }

    /**
     * Get the smallest screen width in dp (sw qualifier).
     * This is the minimum of width/height and remains constant regardless of orientation.
     */
    fun getSmallestScreenWidthDp(context: Context): Int {
        return context.resources.configuration.smallestScreenWidthDp
    }
}
