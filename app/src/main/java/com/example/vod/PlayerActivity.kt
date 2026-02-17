package com.example.vod

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.example.vod.utils.Constants
import com.example.vod.utils.ErrorHandler
import com.example.vod.utils.AnimationHelper
import com.example.vod.utils.NetworkUtils
import com.example.vod.utils.OrientationUtils
import com.google.gson.Gson
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.lang.ref.WeakReference
import retrofit2.HttpException

// Fix 1: Opt-in to Media3 Unstable API to fix the red errors
@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var btnNextEpisode: MaterialButton
    private lateinit var btnSkipIntro: MaterialButton
    private lateinit var btnSkipCredits: MaterialButton
    private var player: ExoPlayer? = null

    companion object {
        private const val TAG = "PlayerActivity"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 2000L
        private const val MAX_RETRY_DELAY_MS = 16000L
        private const val PLAYBACK_STATUS_CHECK_TICK_INTERVAL = 10
        private const val PLAYBACK_LIMIT_EXCEEDED_CODE = "playback_limit_exceeded"
    }

    private var videoId: Int = -1
    private var progressHandler = Handler(Looper.getMainLooper())

    private val checkInterval = Constants.PROGRESS_CHECK_INTERVAL_MS
    private var tickCount = 0
    private var pausedSinceElapsedMs: Long? = null

    private var resumeTimeMs: Long = 0
    private var enableSubtitles: Boolean = false
    private var nextEpisodeData: NextEpisode? = null

    // Skip intro/credits markers
    private var introMarker: ContentMarker? = null
    private var creditsMarker: ContentMarker? = null
    private var autoSkipIntro: Boolean = false
    private var autoSkipCredits: Boolean = false
    private var autoplayNext: Boolean = true
    private var hasSkippedIntro: Boolean = false
    private var hasShownCreditsButton: Boolean = false

    // Stream error recovery state
    private var retryAttempt = 0
    private var currentRetryDelay = INITIAL_RETRY_DELAY_MS
    private var lastStreamUrl: String? = null
    private var lastCookieHeader: String? = null
    private var lastSubUrl: String? = null
    private var lastSubLang: String? = null
    private var isRetrying = false
    private var hasTerminatedForPlaybackLimit = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        OrientationUtils.applyPreferredOrientation(this)
        setContentView(R.layout.activity_player)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playerView = findViewById(R.id.player_view)
        btnNextEpisode = findViewById(R.id.btnNextEpisode)
        btnSkipIntro = findViewById(R.id.btnSkipIntro)
        btnSkipCredits = findViewById(R.id.btnSkipCredits)

        // Hide player controls by default - user must tap/click to show them
        playerView.controllerAutoShow = false
        playerView.controllerShowTimeoutMs = 5000  // Hide controls after 5 seconds of no interaction

        btnNextEpisode.setOnClickListener {
            loadNextEpisode()
        }

        btnSkipIntro.setOnClickListener {
            skipIntro()
        }

        btnSkipCredits.setOnClickListener {
            skipCredits()
        }

        // Load profile auto-skip preferences
        loadProfilePreferences()

        videoId = intent.getIntExtra("VIDEO_ID", -1)
        resumeTimeMs = intent.getLongExtra("RESUME_TIME", 0L) * 1000
        enableSubtitles = intent.getBooleanExtra("ENABLE_SUBTITLES", false)
        Log.i(
            TAG,
            "onCreate formFactor=${formFactorLabel()} videoId=$videoId resumeMs=$resumeTimeMs subtitles=$enableSubtitles"
        )

        // Load intro/credits markers from intent (passed from DetailsActivity)
        loadMarkersFromIntent()

        if (videoId != -1) {
            initializePlayer(videoId)
        } else {
            Log.e(TAG, "Player launch failed: missing VIDEO_ID extra")
            Toast.makeText(this, "Error: No Video ID passed", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * Load intro/credits markers from intent extras.
     * These are passed from DetailsActivity which gets them from the /api/details endpoint.
     */
    private fun loadMarkersFromIntent() {
        val introStart = intent.getIntExtra("INTRO_START", -1)
        val introEnd = intent.getIntExtra("INTRO_END", -1)
        val creditsStart = intent.getIntExtra("CREDITS_START", -1)

        if (introStart >= 0 && introEnd > introStart) {
            introMarker = ContentMarker(
                type = "intro",
                startSeconds = introStart,
                endSeconds = introEnd
            )
            Log.d(TAG, "Loaded intro marker from intent: ${introStart}s - ${introEnd}s")
        }

        if (creditsStart >= 0) {
            creditsMarker = ContentMarker(
                type = "credits",
                startSeconds = creditsStart,
                endSeconds = 0  // Credits go to end of video
            )
            Log.d(TAG, "Loaded credits marker from intent: ${creditsStart}s")
        }
    }

    /**
     * Load auto-skip preferences from active profile.
     */
    private fun loadProfilePreferences() {
        autoSkipIntro = ProfileManager.shouldAutoSkipIntro()
        autoSkipCredits = ProfileManager.shouldAutoSkipCredits()
        autoplayNext = ProfileManager.shouldAutoplayNext()
        Log.d(TAG, "Profile prefs: autoSkipIntro=$autoSkipIntro, autoSkipCredits=$autoSkipCredits, autoplayNext=$autoplayNext")
    }

    private val progressRunnable = object : Runnable {
        override fun run() {
            val p = player ?: return

            val currentPosMs = p.currentPosition
            val currentPosSec = (currentPosMs / 1000).toInt()
            val isPlaying = p.isPlaying
            val isPausedState = !isPlaying &&
                p.playbackState == Player.STATE_READY &&
                !p.playWhenReady

            if (isPlaying) {
                // Playback resumed; allow heartbeat/sync immediately.
                pausedSinceElapsedMs = null
            } else if (isPausedState && pausedSinceElapsedMs == null) {
                pausedSinceElapsedMs = SystemClock.elapsedRealtime()
            }

            if (isPlaying) {
                val duration = p.duration

                // Check for skip intro button visibility
                checkIntroMarker(currentPosSec)

                // Check for skip credits button visibility
                checkCreditsMarker(currentPosSec)

                if (duration > 0) {
                    val timeRemaining = duration - currentPosMs
                    if (timeRemaining <= Constants.NEXT_EPISODE_THRESHOLD_MS && nextEpisodeData != null) {
                        showNextEpisodeButton()
                    } else {
                        hideNextEpisodeButton()
                    }
                }
            }

            val shouldCheckPlaybackStatus = isPlaying &&
                tickCount % PLAYBACK_STATUS_CHECK_TICK_INTERVAL == 0
            val shouldRunProgressSync = tickCount % Constants.PROGRESS_SYNC_TICK_INTERVAL == 0

            if (shouldRunProgressSync || shouldCheckPlaybackStatus) {
                val shouldSyncPaused = if (isPausedState) {
                    val pausedAt = pausedSinceElapsedMs ?: SystemClock.elapsedRealtime()
                    val pausedDurationMs = SystemClock.elapsedRealtime() - pausedAt
                    pausedDurationMs < Constants.PAUSE_HEARTBEAT_TIMEOUT_MS
                } else {
                    false
                }
                val shouldSyncProgress = shouldRunProgressSync && (isPlaying || shouldSyncPaused)

                if (shouldSyncProgress || shouldCheckPlaybackStatus) {
                    val profileId = ProfileManager.getActiveProfileId()
                    val bufferSeconds =
                        ((p.bufferedPosition - currentPosMs).coerceAtLeast(0L) / 1000L).toInt()
                    val paused = if (isPlaying) 0 else 1

                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            if (shouldSyncProgress) {
                                val progressResponse = withTimeout(Constants.NETWORK_TIMEOUT_MS) {
                                    NetworkClient.api.syncProgress(
                                        id = videoId,
                                        time = currentPosSec.toLong(),
                                        paused = paused,
                                        bufferSeconds = bufferSeconds,
                                        profileId = profileId,
                                        deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                                    )
                                }

                                if (progressResponse.stopPlayback) {
                                    withContext(Dispatchers.Main) {
                                        handlePlaybackLimitExceeded(
                                            limit = progressResponse.limit,
                                            activePlaybacks = progressResponse.activePlaybacks,
                                            serverMessage = progressResponse.message
                                        )
                                    }
                                    return@launch
                                }
                            }

                            if (shouldCheckPlaybackStatus) {
                                val playbackStatus = withTimeout(Constants.NETWORK_TIMEOUT_MS) {
                                    NetworkClient.api.getPlaybackStatus(
                                        id = videoId,
                                        profileId = profileId
                                    )
                                }
                                if (playbackStatus.revoke) {
                                    withContext(Dispatchers.Main) {
                                        handlePlaybackLimitExceeded(
                                            limit = playbackStatus.limit,
                                            activePlaybacks = playbackStatus.activePlaybacks,
                                            serverMessage = playbackStatus.message
                                        )
                                    }
                                }
                            }
                        } catch (e: HttpException) {
                            val apiError = parseApiError(e)
                            if (isPlaybackLimitHttpError(e, apiError)) {
                                withContext(Dispatchers.Main) {
                                    handlePlaybackLimitExceeded(
                                        limit = apiError?.limit,
                                        activePlaybacks = apiError?.activePlaybacks,
                                        serverMessage = apiError?.message
                                    )
                                }
                            }
                        } catch (_: Exception) {
                            // Ignore transient sync/status failures; next tick will retry.
                        }
                    }
                }
            }

            tickCount++
            progressHandler.postDelayed(this, checkInterval)
        }
    }

    /**
     * Check if we should show/hide the Skip Intro button or auto-skip.
     */
    private fun checkIntroMarker(currentPosSec: Int) {
        val intro = introMarker ?: return

        val isInIntro = currentPosSec >= intro.startSeconds && currentPosSec < intro.endSeconds

        if (isInIntro && !hasSkippedIntro) {
            if (autoSkipIntro) {
                // Auto-skip intro
                skipIntro()
            } else {
                // Show skip intro button
                showSkipIntroButton()
            }
        } else if (!isInIntro || hasSkippedIntro) {
            hideSkipIntroButton()
        }
    }

    /**
     * Check if we should show the Skip Credits button or auto-skip.
     */
    private fun checkCreditsMarker(currentPosSec: Int) {
        val credits = creditsMarker ?: return

        val isInCredits = currentPosSec >= credits.startSeconds

        if (isInCredits && !hasShownCreditsButton) {
            hasShownCreditsButton = true
            if (autoSkipCredits && nextEpisodeData != null) {
                // Auto-skip to next episode
                loadNextEpisode()
            } else if (nextEpisodeData != null) {
                // Show skip credits button
                showSkipCreditsButton()
            }
        } else if (!isInCredits) {
            hideSkipCreditsButton()
        }
    }

    /**
     * Skip to end of intro segment.
     */
    private fun skipIntro() {
        val intro = introMarker ?: return
        hasSkippedIntro = true
        player?.seekTo(intro.endSeconds * 1000L)
        hideSkipIntroButton()
        Log.d(TAG, "Skipped intro to ${intro.endSeconds}s")
    }

    /**
     * Skip credits and load next episode.
     */
    private fun skipCredits() {
        if (nextEpisodeData != null) {
            loadNextEpisode()
        }
    }

    private fun showSkipIntroButton() {
        if (!btnSkipIntro.isVisible) {
            AnimationHelper.fadeIn(btnSkipIntro)
            btnSkipIntro.requestFocus()
        }
    }

    private fun hideSkipIntroButton() {
        if (btnSkipIntro.isVisible) {
            AnimationHelper.fadeOut(btnSkipIntro, gone = true)
        }
    }

    private fun showSkipCreditsButton() {
        if (!btnSkipCredits.isVisible) {
            AnimationHelper.fadeIn(btnSkipCredits)
        }
    }

    private fun hideSkipCreditsButton() {
        if (btnSkipCredits.isVisible) {
            btnSkipCredits.isVisible = false
        }
    }

    // Fix 4: Suppress typo check for PHPSESSID
    @Suppress("SpellCheckingInspection")
    private fun initializePlayer(id: Int) {
        Log.d(TAG, "initializePlayer start videoId=$id subtitles=$enableSubtitles")
        // Check network before loading video
        if (!NetworkUtils.isNetworkAvailable(this)) {
            ErrorHandler.showError(this, "No internet connection")
            Log.w(TAG, "initializePlayer aborted due to missing network. videoId=$id")
            finish()
            return
        }

        // Use WeakReference to prevent activity leak
        val weakActivity = WeakReference(this)

        // Use lifecycleScope instead of CoroutineScope for automatic cancellation
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val profileId = ProfileManager.getActiveProfileId()
                val response = NetworkClient.api.getStreamInfo(id, profileId)
                val data = response.data

                if (data?.streamUrl.isNullOrBlank()) {
                    Log.e(TAG, "ERROR: Stream URL is NULL")
                    withContext(Dispatchers.Main) {
                        weakActivity.get()?.let { activity ->
                            ErrorHandler.showError(
                                activity,
                                response.message ?: activity.getString(R.string.error_stream_unavailable)
                            )
                            activity.finish()
                        }
                    }
                    return@launch
                }

                weakActivity.get()?.nextEpisodeData = data.next_episode
                // Only update markers from API response if they're present (otherwise keep intent-loaded values)
                data.introMarker?.let { weakActivity.get()?.introMarker = it }
                data.creditsMarker?.let { weakActivity.get()?.creditsMarker = it }

                val finalIntro = weakActivity.get()?.introMarker
                val finalCredits = weakActivity.get()?.creditsMarker
                Log.d(TAG, "Final Markers - Intro: ${finalIntro?.let { "${it.startSeconds}-${it.endSeconds}s" } ?: "none"}, Credits: ${finalCredits?.let { "${it.startSeconds}s" } ?: "none"}")

                val sessionId = NetworkClient.cookieJar.getCookieValue("PHPSESSID")
                val cookieString = if (sessionId != null) {
                    "PHPSESSID=${sessionId.replace(Regex("[\\r\\n\\u0000]"), "")}"
                } else {
                    ""
                }

                withContext(Dispatchers.Main) {
                    weakActivity.get()?.setupExoPlayer(
                        data.streamUrl,
                        cookieString,
                        data.subtitleUrl,
                        data.subtitleLanguage
                    )
                }

            } catch (e: HttpException) {
                Log.w(TAG, "HTTP error in initializePlayer: code=${e.code()}", e)
                val apiError = parseApiError(e)
                withContext(Dispatchers.Main) {
                    weakActivity.get()?.let { activity ->
                        when {
                            e.code() == 410 -> {
                                Log.w(TAG, "Media file unavailable (410) for videoId=$id")
                                ErrorHandler.showError(
                                    activity,
                                    activity.getString(R.string.error_media_not_found)
                                )
                                activity.finish()
                            }
                            isPlaybackLimitHttpError(e, apiError) -> {
                                activity.handlePlaybackLimitExceeded(
                                    limit = apiError?.limit,
                                    activePlaybacks = apiError?.activePlaybacks,
                                    serverMessage = apiError?.message
                                )
                            }
                            else -> {
                                ErrorHandler.showError(
                                    activity,
                                    apiError?.message ?: activity.getString(R.string.error_loading_video)
                                )
                                activity.finish()
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error in initializePlayer", e)
                withContext(Dispatchers.Main) {
                    weakActivity.get()?.let { activity ->
                        ErrorHandler.handleNetworkError(activity, e)
                        activity.finish()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL EXCEPTION in initializePlayer", e)
                withContext(Dispatchers.Main) {
                    weakActivity.get()?.let { activity ->
                        ErrorHandler.handleNetworkError(
                            activity,
                            e,
                            activity.getString(R.string.error_loading_video)
                        )
                        activity.finish()
                    }
                }
            }
        }
    }

    private fun setupExoPlayer(url: String, cookieHeader: String, subUrl: String?, subLang: String?) {
        val resolvedSubtitleUrl = resolveSubtitleUrl(subUrl)
        val streamHost = runCatching { url.toUri().host.orEmpty() }.getOrDefault("")
        Log.d(
            TAG,
            "setupExoPlayer videoId=$videoId host=$streamHost resumeMs=$resumeTimeMs subtitlesEnabled=$enableSubtitles hasSubtitleUrl=${!resolvedSubtitleUrl.isNullOrEmpty()} cookieBytes=${cookieHeader.length}"
        )

        // Store stream parameters for retry
        lastStreamUrl = url
        lastCookieHeader = cookieHeader
        lastSubUrl = resolvedSubtitleUrl
        lastSubLang = subLang

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("okhttp/4.9.0")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("Cookie" to cookieHeader))

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val trackSelector = DefaultTrackSelector(this)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .build()

        playerView.player = player

        // Fix 5: Used KTX extension .toUri() instead of Uri.parse()
        val mediaItemBuilder = MediaItem.Builder().setUri(url.toUri())

        if (!resolvedSubtitleUrl.isNullOrEmpty()) {
            // Guard subtitle parsing to avoid crashes on malformed backend subtitle URLs.
            runCatching {
                val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(resolvedSubtitleUrl.toUri())
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                    .setLanguage(subLang ?: "en")
                    .setSelectionFlags(
                        if (enableSubtitles) C.SELECTION_FLAG_DEFAULT else 0
                    )
                    .build()

                mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
            }.onFailure { error ->
                Log.w(TAG, "Skipping invalid subtitle URL: $resolvedSubtitleUrl", error)
            }
        }

        val mediaItem = mediaItemBuilder.build()
        player?.setMediaItem(mediaItem)

        player?.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                handlePlaybackError(error)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && player?.playWhenReady == true) {
                    Log.d(TAG, "Sync: Player READY. Starting Monitor.")
                    // Reset retry state on successful playback
                    resetRetryState()
                    progressHandler.removeCallbacks(progressRunnable)
                    progressHandler.post(progressRunnable)
                }
                if (playbackState == Player.STATE_ENDED) {
                    if (autoplayNext && nextEpisodeData != null) {
                        loadNextEpisode()
                    } else if (nextEpisodeData != null) {
                        showNextEpisodeButton()
                    }
                }
            }
        })

        if (resumeTimeMs > 0) {
            player?.seekTo(resumeTimeMs)
        }
        player?.prepare()
        player?.play()
    }

    private fun resolveSubtitleUrl(rawUrl: String?): String? {
        val trimmed = rawUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val baseUrl = BuildConfig.BASE_URL.trim().trimEnd('/')

        return when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> "$baseUrl$trimmed"
            else -> "$baseUrl/${trimmed.trimStart('/')}"
        }
    }

    /**
     * Handle playback errors with automatic retry logic.
     * Uses exponential backoff: 2s, 4s, 8s, up to 16s max.
     */
    private fun handlePlaybackError(error: PlaybackException) {
        Log.e(TAG, "Playback error: ${error.errorCodeName}", error)

        // Save current position before we potentially lose it
        player?.currentPosition?.let { pos ->
            if (pos > 0) resumeTimeMs = pos
        }

        val cause = error.cause
        if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException &&
            cause.responseCode == 429
        ) {
            handlePlaybackLimitExceeded()
            return
        }

        val errorMessage = when {
            cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException -> {
                "Server error: HTTP ${cause.responseCode}"
            }
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> {
                "Network connection failed"
            }
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                "Connection timed out"
            }
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                "Stream unavailable"
            }
            else -> {
                "Playback error occurred"
            }
        }

        // Check if we should retry
        if (retryAttempt < MAX_RETRY_ATTEMPTS && !isRetrying) {
            retryAttempt++
            isRetrying = true

            val retryMessage = "Connection issue. Retrying ($retryAttempt/$MAX_RETRY_ATTEMPTS)..."
            Toast.makeText(this, retryMessage, Toast.LENGTH_SHORT).show()

            Log.d(TAG, "Scheduling retry attempt $retryAttempt in ${currentRetryDelay}ms")

            progressHandler.postDelayed({
                retryPlayback()
            }, currentRetryDelay)

            // Exponential backoff: double the delay for next attempt
            currentRetryDelay = (currentRetryDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
        } else {
            // Max retries exceeded - show final error with manual retry option
            showFinalError(errorMessage)
        }
    }

    /**
     * Retry playback with stored stream parameters.
     */
    private fun retryPlayback() {
        isRetrying = false

        val url = lastStreamUrl
        val cookie = lastCookieHeader

        if (url == null || cookie == null) {
            // No stored stream info - reload from API
            Log.d(TAG, "No cached stream info, reloading from API")
            player?.release()
            player = null
            initializePlayer(videoId)
            return
        }

        // Check network before retry
        if (!NetworkUtils.isNetworkAvailable(this)) {
            if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                retryAttempt++
                Toast.makeText(this, "Waiting for network ($retryAttempt/$MAX_RETRY_ATTEMPTS)...", Toast.LENGTH_SHORT).show()
                progressHandler.postDelayed({ retryPlayback() }, currentRetryDelay)
                currentRetryDelay = (currentRetryDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            } else {
                showFinalError("No internet connection")
            }
            return
        }

        Log.d(TAG, "Retrying playback attempt $retryAttempt")

        // Save current position before releasing player
        val currentPosition = player?.currentPosition ?: resumeTimeMs

        // Release old player and create new one
        player?.release()
        player = null

        // Update resumeTimeMs so we continue from where we were
        if (currentPosition > 0) {
            resumeTimeMs = currentPosition
        }

        // Recreate player with stored parameters
        setupExoPlayer(url, cookie, lastSubUrl, lastSubLang)
    }

    /**
     * Show final error with manual retry button.
     */
    private fun showFinalError(errorMessage: String) {
        Toast.makeText(
            this,
            "$errorMessage. Tap screen to retry or press back to exit.",
            Toast.LENGTH_LONG
        ).show()

        // Allow tap on player view to retry
        playerView.setOnClickListener {
            resetRetryState()
            playerView.setOnClickListener(null)
            initializePlayer(videoId)
        }
    }

    /**
     * Reset retry state after successful playback or manual retry.
     */
    private fun resetRetryState() {
        retryAttempt = 0
        currentRetryDelay = INITIAL_RETRY_DELAY_MS
        isRetrying = false
    }

    private fun parseApiError(httpException: HttpException): ApiErrorResponse? {
        return try {
            val raw = httpException.response()?.errorBody()?.string().orEmpty()
            if (raw.isBlank()) null else Gson().fromJson(raw, ApiErrorResponse::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun isPlaybackLimitHttpError(
        httpException: HttpException,
        apiError: ApiErrorResponse? = null
    ): Boolean {
        if (httpException.code() != 429) return false
        val code = apiError?.code?.trim()
        return code.isNullOrEmpty() || code.equals(PLAYBACK_LIMIT_EXCEEDED_CODE, ignoreCase = true)
    }

    private fun handlePlaybackLimitExceeded(
        limit: Int? = null,
        activePlaybacks: Int? = null,
        serverMessage: String? = null
    ) {
        if (hasTerminatedForPlaybackLimit || isFinishing || isDestroyed) return
        hasTerminatedForPlaybackLimit = true

        val message = buildPlaybackLimitMessage(limit, activePlaybacks, serverMessage)
        Log.w(TAG, "Playback ended due to concurrent limit. limit=$limit active=$activePlaybacks")

        progressHandler.removeCallbacks(progressRunnable)
        resetRetryState()
        player?.stop()
        player?.release()
        player = null

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun buildPlaybackLimitMessage(
        limit: Int?,
        activePlaybacks: Int?,
        serverMessage: String?
    ): String {
        val cleanServerMessage = serverMessage?.trim().orEmpty()
        if (cleanServerMessage.isNotEmpty()) {
            return cleanServerMessage
        }

        return when {
            limit != null && activePlaybacks != null -> {
                getString(R.string.playback_limit_reached_with_counts, limit, activePlaybacks)
            }
            limit != null -> {
                getString(R.string.playback_limit_reached_with_limit, limit)
            }
            else -> getString(R.string.playback_limit_reached_generic)
        }
    }

    // Fix 6: Suppress SetTextI18n (Hardcoded text) warning so you don't have to make an XML resource right now
    @SuppressLint("SetTextI18n")
    private fun showNextEpisodeButton() {
        // Fix 7: Use KTX property .isVisible instead of .visibility == View.VISIBLE
        if (!btnNextEpisode.isVisible) {
            val nextTitle = nextEpisodeData?.title ?: "Next Episode"
            btnNextEpisode.text = "Next: $nextTitle"
            AnimationHelper.fadeIn(btnNextEpisode, Constants.BACKDROP_FADE_DURATION_MS)

            btnNextEpisode.requestFocus()
        }
    }

    private fun hideNextEpisodeButton() {
        // Fix 7 (repeated): Use KTX property .isVisible
        if (btnNextEpisode.isVisible) {
            btnNextEpisode.isVisible = false
        }
    }

    private fun loadNextEpisode() {
        nextEpisodeData?.let { next ->
            Log.d(TAG, "Loading next episode from videoId=$videoId to nextId=${next.id} subtitles=$enableSubtitles")
            progressHandler.removeCallbacks(progressRunnable)
            player?.stop()
            player?.release()
            player = null

            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("VIDEO_ID", next.id)
            intent.putExtra("ENABLE_SUBTITLES", enableSubtitles)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume playerExists=${player != null} isPlaying=${player?.isPlaying == true}")
        // Fixed: Use safe call instead of forced non-null assertion
        if (player?.isPlaying == true) {
            progressHandler.post(progressRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause currentPositionMs=${player?.currentPosition ?: -1L}")
        progressHandler.removeCallbacks(progressRunnable)
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop releasing player for videoId=$videoId")
        player?.release()
        player = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy clearing callbacks and releasing player for videoId=$videoId")
        // Ensure all handler callbacks are removed to prevent leaks
        progressHandler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }

    private fun formFactorLabel(): String {
        return when (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) {
            Configuration.UI_MODE_TYPE_TELEVISION -> "tv"
            else -> "mobile_tablet"
        }
    }
}
