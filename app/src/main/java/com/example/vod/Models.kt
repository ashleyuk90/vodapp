package com.example.vod

import com.google.gson.annotations.SerializedName

// Generic API Response wrapper with safe defaults
data class ApiResponse<T>(
    val status: String = "",
    val message: String? = null,
    val data: T? = null
)

data class User(
    val id: Int = 0,
    val username: String = ""
)

data class VideoItem(
    val id: Int = 0,
    val title: String = "",
    // Series title for episodes
    @SerializedName("series_title") val seriesTitle: String? = null,

    val type: String? = null,
    val plot: String? = null,

    @SerializedName("year")
    val year: Int = 0,

    @SerializedName("runtime")
    val runtime: Int = 0,

    @SerializedName("poster_url")
    val posterUrl: String? = null,

    @SerializedName("poster_path")
    val posterPath: String? = null,

    // Backdrop for Hero Section
    @SerializedName("backdrop_path") val backdropPath: String? = null,

    @SerializedName("rotten_tomatoes")
    val rottenTomatoes: String? = null,

    val quality: String? = "HD",
    val rating: String? = null,
    @SerializedName("content_rating") val contentRating: String? = null,
    val genres: String? = null,
    val director: String? = null,
    val starring: String? = null,
    val resume_time: Long = 0L,
    val total_duration: Long = 0L,

    @SerializedName("has_subtitles") val hasSubtitles: Boolean = false,
    @SerializedName("subtitle_url") val subtitleUrl: String? = null,
    @SerializedName("subtitle_language") val subtitleLanguage: String? = null,

    val episodes: List<EpisodeItem>? = null,

    // Skip intro/credits markers (for episodes)
    @SerializedName("intro_marker") val introMarker: ContentMarker? = null,
    @SerializedName("credits_marker") val creditsMarker: ContentMarker? = null
) {
    fun getDisplayImage(): String {
        return when {
            !posterUrl.isNullOrEmpty() -> posterUrl
            else -> "http://77.74.196.120/vod/images/$posterPath"
        }
    }

    // Add helper for Backdrop
    fun getBackdropImage(): String {
        return if (!backdropPath.isNullOrEmpty()) {
            "http://77.74.196.120/vod/images/$backdropPath"
        } else {
            getDisplayImage() // Fallback to poster if no backdrop
        }
    }
}

// Episode data class with safe defaults
data class EpisodeItem(
    val id: Int = 0,
    val title: String = "",
    val season: Int = 1,
    val episode: Int = 1,
    val plot: String? = null,
    val runtime: Int = 0,
    @SerializedName("poster_path") val posterPath: String? = null,
    val next_episode: NextEpisode? = null
)

data class NextEpisode(
    val id: Int = 0,
    val title: String = "",
    val season: Int = 1,
    val episode: Int = 1,
    val plot: String? = null,
    val runtime: Int = 0,
    val poster_url: String? = null
)

/**
 * Content marker for intro/credits skip detection.
 * Returned by API for episodes with detected intro/credits segments.
 *
 * For intro markers: use startSeconds and endSeconds directly.
 * For credits markers: use creditsDurationSeconds and creditsEndOffsetSeconds to calculate
 * the credits start position relative to video duration:
 *   credits_start = video_duration_seconds - creditsEndOffsetSeconds - creditsDurationSeconds
 */
data class ContentMarker(
    val type: String = "",  // "intro" or "credits"
    @SerializedName("start_seconds") val startSeconds: Int = 0,
    @SerializedName("end_seconds") val endSeconds: Int = 0,
    @SerializedName("credits_duration_seconds") val creditsDurationSeconds: Int? = null,
    @SerializedName("credits_end_offset_seconds") val creditsEndOffsetSeconds: Int? = null,
    val confidence: Float = 0f,
    val source: String? = null  // "auto" or "manual"
) {
    /**
     * Calculate credits start position given video duration in seconds.
     * Returns null if credits duration info is not available.
     */
    fun getCreditsStartSeconds(videoDurationSeconds: Int): Int? {
        val duration = creditsDurationSeconds ?: return null
        val offset = creditsEndOffsetSeconds ?: 0
        return videoDurationSeconds - offset - duration
    }
}

data class LibraryResponse(
    val status: String = "",
    val data: List<VideoItem>? = null,
    val pages: Int? = null
)

data class LibraryListResponse(
    val status: String = "",
    val data: List<LibraryItem>? = null
)

data class LibraryItem(
    val id: Int = 0,
    val name: String = ""
)

data class DetailsResponse(
    val status: String = "",
    val video: VideoItem? = null,
    val progress: Int? = 0,
    @SerializedName("poster_full") val posterFull: String? = null
)

data class PlayResponse(
    @SerializedName("stream_url") val streamUrl: String = "",
    @SerializedName("has_subtitles") val hasSubtitles: Boolean = false,
    @SerializedName("subtitle_url") val subtitleUrl: String? = null,
    @SerializedName("subtitle_language") val subtitleLanguage: String? = null,
    @SerializedName("next_episode") val next_episode: NextEpisode? = null,
    @SerializedName("intro_marker") val introMarker: ContentMarker? = null,
    @SerializedName("credits_marker") val creditsMarker: ContentMarker? = null
)

data class ProgressResponse(
    val status: String = ""
)

data class WatchStatusResponse(
    val status: String = "",
    @SerializedName("in_watch_list") val inWatchList: Boolean = false
)

data class WatchListResponse(
    val status: String = "",
    @SerializedName("videos") val videos: List<VideoItem>? = null,
    val total: Int? = null,
    val page: Int? = null,
    val pages: Int? = null
)

data class DashboardResponse(
    val status: String = "",
    @SerializedName("continue_watching")
    val continueWatching: List<VideoItem>? = null,
    @SerializedName("recent_movies")
    val recentMovies: List<VideoItem>? = null,
    @SerializedName("recent_shows")
    val recentShows: List<VideoItem>? = null
)

// ===== Profile Models =====

/**
 * User profile for multi-account support.
 * Each user can have multiple profiles with separate watch history and preferences.
 */
data class Profile(
    val id: Int = 0,
    val name: String = "Default",
    @SerializedName("max_content_rating") val maxContentRating: String? = null,
    @SerializedName("max_rating") val maxRating: String? = null,
    @SerializedName("auto_skip_intro") val autoSkipIntro: Boolean = false,
    @SerializedName("auto_skip_credits") val autoSkipCredits: Boolean = false,
    @SerializedName("autoplay_next") val autoplayNext: Boolean = true,
    @SerializedName("has_pin") val hasPin: Boolean = false
) {
    /**
     * Get the first letter/initial for avatar display.
     */
    fun getInitial(): String = name.firstOrNull()?.uppercase() ?: "?"
    
    /**
     * Check if this profile has parental controls enabled.
     */
    fun hasParentalControls(): Boolean = !maxContentRating.isNullOrEmpty()
}

/**
 * Response from GET /api/profiles endpoint.
 */
data class ProfilesResponse(
    val status: String = "",
    @SerializedName("active_profile_id") val activeProfileId: Int = 0,
    val profiles: List<Profile> = emptyList()
)

/**
 * Response from POST /api/profiles_select endpoint.
 */
data class ProfileSelectResponse(
    val status: String = "",
    @SerializedName("active_profile_id") val activeProfileId: Int = 0
)

// ===== Search Filters =====

/**
 * Filters for enhanced search functionality.
 * All filters are optional and combine with AND logic.
 */
data class SearchFilters(
    val genre: String? = null,
    val yearMin: Int? = null,
    val yearMax: Int? = null,
    val minRating: Float? = null,
    val type: String? = null  // "movie" or "series"
) {
    /**
     * Check if any filter is active.
     */
    fun hasActiveFilters(): Boolean = 
        genre != null || yearMin != null || yearMax != null || minRating != null || type != null
    
    /**
     * Clear all filters.
     */
    fun clear(): SearchFilters = SearchFilters()
}
