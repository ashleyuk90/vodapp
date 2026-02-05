package com.example.vod

import com.google.gson.annotations.SerializedName

// Generic API Response wrapper
data class ApiResponse<T>(
    val status: String,
    val message: String?,
    val data: T?
)

data class User(
    val id: Int,
    val username: String
)

data class VideoItem(
    val id: Int,
    val title: String,
    // Add this to fix the "Episode Name" issue
    @SerializedName("series_title") val seriesTitle: String? = null,

    val type: String?,
    val plot: String?,

    @SerializedName("year")
    val year: Int,

    @SerializedName("runtime")
    val runtime: Int,

    @SerializedName("poster_url")
    val posterUrl: String?,

    @SerializedName("poster_path")
    val posterPath: String?,

    // Add this for the Hero Section background
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("rotten_tomatoes")
    val rottenTomatoes: String?,
    val quality: String?,
    val rating: String?,
    val genres: String?,
    val director: String?,
    val starring: String?,
    val resume_time: Long = 0,
    val total_duration: Long = 0,
    @SerializedName("has_subtitles") val hasSubtitles: Boolean = false,
    @SerializedName("subtitle_url") val subtitleUrl: String? = null,
    @SerializedName("subtitle_language") val subtitleLanguage: String? = null,

    val episodes: List<EpisodeItem>? = null
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

// New Class for Episodes
data class EpisodeItem(
    val id: Int,
    val title: String,
    val season: Int,
    val episode: Int,
    val plot: String?,
    val runtime: Int,
    @SerializedName("poster_path") val posterPath: String?,
    val next_episode: NextEpisode? = null
)

data class NextEpisode(
    val id: Int,
    val title: String,
    val season: Int,
    val episode: Int,
    val plot: String?,
    val runtime: Int,
    val poster_url: String?
)

data class LibraryResponse(
    val status: String,
    val data: List<VideoItem>?,
    val pages: Int?
)

data class LibraryListResponse(
    val status: String,
    val data: List<LibraryItem>?
)

data class LibraryItem(
    val id: Int,
    val name: String
)

data class DetailsResponse(
    val status: String,
    val video: VideoItem?,
    val progress: Int? = 0,
    @SerializedName("poster_full") val posterFull: String? = null
)

data class PlayResponse(
    @SerializedName("stream_url") val streamUrl: String,
    @SerializedName("has_subtitles") val hasSubtitles: Boolean = false,
    @SerializedName("subtitle_url") val subtitleUrl: String? = null,
    @SerializedName("subtitle_language") val subtitleLanguage: String? = null,
    @SerializedName("next_episode") val next_episode: NextEpisode? = null
)

data class ProgressResponse(
    val status: String
)
data class WatchStatusResponse(
    val status: String,
    @SerializedName("in_watch_list") val inWatchList: Boolean
)
data class WatchListResponse(
    val status: String,
    @SerializedName("videos") val videos: List<VideoItem>?, // Maps JSON "videos" to this list
    val total: Int?,
    val page: Int?,
    val pages: Int?
)
data class DashboardResponse(
    val status: String,
    @SerializedName("continue_watching")
    val continueWatching: List<VideoItem>?,
    @SerializedName("recent_movies")
    val recentMovies: List<VideoItem>?,
    @SerializedName("recent_shows")
    val recentShows: List<VideoItem>?
)