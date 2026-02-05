package com.example.vod

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
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
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

// Fix 1: Opt-in to Media3 Unstable API to fix the red errors
@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var btnNextEpisode: MaterialButton
    private var player: ExoPlayer? = null

    // Fix 2: Moved TAG to companion object and made it const (fixes capitalization warning)
    companion object {
        private const val TAG = "VOD_DEBUG"
    }

    private var videoId: Int = -1
    private var progressHandler = Handler(Looper.getMainLooper())

    private val checkInterval = 1000L
    private var tickCount = 0

    private var resumeTimeMs: Long = 0
    private var enableSubtitles: Boolean = false
    private var nextEpisodeData: NextEpisode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playerView = findViewById(R.id.player_view)
        btnNextEpisode = findViewById(R.id.btnNextEpisode)

        btnNextEpisode.setOnClickListener {
            loadNextEpisode()
        }

        videoId = intent.getIntExtra("VIDEO_ID", -1)
        resumeTimeMs = intent.getLongExtra("RESUME_TIME", 0L) * 1000
        enableSubtitles = intent.getBooleanExtra("ENABLE_SUBTITLES", false)

        if (videoId != -1) {
            initializePlayer(videoId)
        } else {
            Toast.makeText(this, "Error: No Video ID passed", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val progressRunnable = object : Runnable {
        override fun run() {
            val p = player ?: return

            if (p.isPlaying) {
                val duration = p.duration
                val currentPosMs = p.currentPosition
                if (duration > 0) {
                    val timeRemaining = duration - currentPosMs
                    if (timeRemaining <= 30000 && nextEpisodeData != null) {
                        showNextEpisodeButton()
                    } else {
                        hideNextEpisodeButton()
                    }
                }

                if (tickCount % 10 == 0) {
                    val currentPosSec = currentPosMs / 1000
                    val isPaused = 0

                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            withTimeout(5000) {
                                NetworkClient.api.syncProgress(videoId, currentPosSec, isPaused)
                            }
                        } catch (_: Exception) {
                            // Fix 3: Renamed 'e' to '_' to silence "Parameter never used" warning
                        }
                    }
                }
            }

            tickCount++
            progressHandler.postDelayed(this, checkInterval)
        }
    }

    // Fix 4: Suppress typo check for PHPSESSID
    @Suppress("SpellCheckingInspection")
    private fun initializePlayer(id: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = NetworkClient.api.getStreamInfo(id)
                val data = response.data

                if (data?.streamUrl == null) {
                    Log.e(TAG, "ERROR: Stream URL is NULL")
                    return@launch
                }

                nextEpisodeData = data.next_episode

                val cookies = NetworkClient.cookieManager.cookieStore.cookies
                val sb = StringBuilder()
                for (cookie in cookies) {
                    if (cookie.name == "PHPSESSID") {
                        sb.append(cookie.name).append("=").append(cookie.value).append(";")
                    }
                }
                val cookieString = sb.toString()

                runOnUiThread {
                    setupExoPlayer(
                        data.streamUrl,
                        cookieString,
                        data.subtitleUrl,
                        data.subtitleLanguage
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL EXCEPTION in initializePlayer", e)
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@PlayerActivity, "Error loading video", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupExoPlayer(url: String, cookieHeader: String, subUrl: String?, subLang: String?) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("okhttp/4.9.0")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("Cookie" to cookieHeader))

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val trackSelector = DefaultTrackSelector(this)
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setTunnelingEnabled(true)
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .build()

        playerView.player = player

        // Fix 5: Used KTX extension .toUri() instead of Uri.parse()
        val mediaItemBuilder = MediaItem.Builder().setUri(url.toUri())

        if (!subUrl.isNullOrEmpty()) {
            // Fix 5 (repeated): Used .toUri()
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subUrl.toUri())
                .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                .setLanguage(subLang ?: "en")
                .setSelectionFlags(
                    if (enableSubtitles) C.SELECTION_FLAG_DEFAULT else 0
                )
                .build()

            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
        }

        val mediaItem = mediaItemBuilder.build()
        player?.setMediaItem(mediaItem)

        player?.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val cause = error.cause
                if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Server Rejected: HTTP ${cause.responseCode}", Toast.LENGTH_LONG).show()
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && player?.playWhenReady == true) {
                    Log.d(TAG, "Sync: Player READY. Starting Monitor.")
                    progressHandler.removeCallbacks(progressRunnable)
                    progressHandler.post(progressRunnable)
                }
            }
        })

        if (resumeTimeMs > 0) {
            player?.seekTo(resumeTimeMs)
        }
        player?.prepare()
        player?.play()
    }

    // Fix 6: Suppress SetTextI18n (Hardcoded text) warning so you don't have to make an XML resource right now
    @SuppressLint("SetTextI18n")
    private fun showNextEpisodeButton() {
        // Fix 7: Use KTX property .isVisible instead of .visibility == View.VISIBLE
        if (!btnNextEpisode.isVisible) {
            val nextTitle = nextEpisodeData?.title ?: "Next Episode"
            btnNextEpisode.text = "Next: $nextTitle"
            btnNextEpisode.isVisible = true

            btnNextEpisode.alpha = 0f
            btnNextEpisode.animate().alpha(1f).setDuration(500).start()

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
        if (player != null && player!!.isPlaying) {
            progressHandler.post(progressRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        progressHandler.removeCallbacks(progressRunnable)
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }
}