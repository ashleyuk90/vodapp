package com.example.vod.utils

/**
 * Centralized constants for the VOD app.
 * Eliminates magic numbers and provides single source of truth for configuration values.
 */
object Constants {

    // ===== Grid & Layout =====
    /** Number of columns in the library grid */
    const val GRID_SPAN_COUNT = 6
    
    /** Width of horizontal card items in dp */
    const val HORIZONTAL_CARD_WIDTH_DP = 120
    
    /** Height of loading footer in px */
    const val LOADING_FOOTER_HEIGHT = 150

    // ===== Animation Durations =====
    /** Standard animation duration for UI transitions */
    const val ANIMATION_DURATION_MS = 240L

    /** Activity transition duration on mobile/tablet */
    const val TRANSITION_DURATION_MOBILE_MS = 280L

    /** Activity transition duration on TV */
    const val TRANSITION_DURATION_TV_MS = 220L
    
    /** Backdrop fade animation duration */
    const val BACKDROP_FADE_DURATION_MS = 500L
    
    /** Focus scale animation duration */
    const val FOCUS_ANIMATION_DURATION_MS = 180L

    /** Press feedback animation duration */
    const val PRESS_ANIMATION_DURATION_MS = 110L

    // ===== Player & Progress =====
    /** Interval for progress sync with server (ms) */
    const val PROGRESS_SYNC_INTERVAL_MS = 10_000L
    
    /** Interval for progress check runnable (ms) */
    const val PROGRESS_CHECK_INTERVAL_MS = 1000L
    
    /** How often to sync progress (every N ticks) */
    const val PROGRESS_SYNC_TICK_INTERVAL = 10

    /** Stop paused heartbeat/progress sync after this long (ms) */
    const val PAUSE_HEARTBEAT_TIMEOUT_MS = 60_000L
    
    /** Time before end to show "Next Episode" button (ms) */
    const val NEXT_EPISODE_THRESHOLD_MS = 30_000L
    
    /** Network request timeout (ms) */
    const val NETWORK_TIMEOUT_MS = 5000L

    // ===== UI Sizing =====
    /** Alphabet bar letter button width in dp */
    const val ALPHABET_BUTTON_WIDTH_DP = 60
    
    /** Alphabet bar letter button height in dp */
    const val ALPHABET_BUTTON_HEIGHT_DP = 50
    
    /** Alphabet bar text size in sp */
    const val ALPHABET_TEXT_SIZE_SP = 14f
    
    /** Menu button text size in sp */
    const val MENU_TEXT_SIZE_SP = 14f
    
    /** Menu button vertical padding in dp */
    const val MENU_BUTTON_PADDING_VERTICAL_DP = 12
    
    /** Menu button horizontal padding in dp */
    const val MENU_BUTTON_PADDING_HORIZONTAL_DP = 14
    
    /** Menu button margin in dp */
    const val MENU_BUTTON_MARGIN_DP = 2

    // ===== Focus Effects =====
    /** Scale factor when item has focus */
    const val FOCUS_SCALE_SMALL = 1.03f

    /** Scale factor for buttons with focus */
    const val FOCUS_SCALE_MEDIUM = 1.06f

    /** Scale factor for larger focus effect */
    const val FOCUS_SCALE_LARGE = 1.08f
    
    /** Elevation when focused */
    const val FOCUS_ELEVATION = 10f
    
    /** Default (unfocused) elevation */
    const val DEFAULT_ELEVATION = 0f

    // ===== Validation =====
    /** Minimum username length */
    const val MIN_USERNAME_LENGTH = 3
    
    /** Minimum password length */
    const val MIN_PASSWORD_LENGTH = 4

    // ===== Blur Effect =====
    /** Default blur radius for backdrop images */
    const val BACKDROP_BLUR_RADIUS = 25f
    
    /** Backdrop dimming alpha */
    const val BACKDROP_DIM_ALPHA = 0.6f

    // ===== SharedPreferences Keys =====
    const val PREFS_NAME = "VOD_PREFS_ENCRYPTED"
    const val KEY_USER = "KEY_USER"
    const val KEY_PASS = "KEY_PASS"
    const val KEY_CSRF_TOKEN = "KEY_CSRF_TOKEN"
    const val KEY_ACCOUNT_EXPIRY = "KEY_ACCOUNT_EXPIRY"
}
