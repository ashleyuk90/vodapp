package com.example.vod

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.vod.utils.Constants
import com.example.vod.utils.ErrorHandler
import com.example.vod.utils.NetworkUtils
import com.example.vod.utils.AnimationHelper
import com.example.vod.utils.OrientationUtils
import com.example.vod.utils.RatingUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.lang.ref.WeakReference
import java.text.NumberFormat

class DetailsActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "DetailsActivity"
    }


    private lateinit var imgBackdrop: ImageView
    private lateinit var imgPoster: ImageView
    private lateinit var txtTitle: TextView
    private lateinit var txtPlot: TextView
    private lateinit var txtYear: TextView
    private lateinit var txtRuntime: TextView
    private lateinit var txtRating: TextView
    private lateinit var txtRottenTomatoes: TextView
    private lateinit var txtContentRating: TextView

    // Buttons
    private lateinit var btnPlay: com.google.android.material.button.MaterialButton
    private lateinit var btnResume: com.google.android.material.button.MaterialButton
    private lateinit var btnWatchLater: com.google.android.material.button.MaterialButton
    private lateinit var btnSubtitles: com.google.android.material.button.MaterialButton
    private lateinit var btnBack: ImageButton

    // Credits
    private lateinit var dividerCredits: View
    private lateinit var layoutCredits: LinearLayout
    private lateinit var txtDirector: TextView
    private lateinit var txtStarring: TextView
    private lateinit var txtDetailGenre: TextView

    private lateinit var layoutActionButtons: LinearLayout
    private lateinit var layoutEpisodes: LinearLayout
    private lateinit var rvEpisodes: RecyclerView

    private lateinit var video: VideoItem
    private var isInWatchList = false
    private var isWatchLaterProcessing = false
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        OrientationUtils.applyPreferredOrientation(this)
        setContentView(R.layout.activity_details)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Views
        imgBackdrop = findViewById(R.id.imgDetailsBackdrop)
        imgPoster = findViewById(R.id.imgDetailPoster)
        txtTitle = findViewById(R.id.txtDetailTitle)
        txtPlot = findViewById(R.id.txtDetailPlot)
        txtYear = findViewById(R.id.txtDetailYear)
        txtRuntime = findViewById(R.id.txtDetailRuntime)
        txtRating = findViewById(R.id.txtRating)
        txtRottenTomatoes = findViewById(R.id.txtRottenTomatoes)
        txtContentRating = findViewById(R.id.txtDetailContentRating)

        btnPlay = findViewById(R.id.btnPlay)
        btnResume = findViewById(R.id.btnResume)
        btnWatchLater = findViewById(R.id.btnWatchLater)
        btnSubtitles = findViewById(R.id.btnSubtitles)
        btnBack = findViewById(R.id.btnBack)

        dividerCredits = findViewById(R.id.dividerCredits)
        layoutCredits = findViewById(R.id.layoutCredits)
        txtDirector = findViewById(R.id.txtDirector)
        txtStarring = findViewById(R.id.txtStarring)
        txtDetailGenre = findViewById(R.id.txtDetailGenres)

        layoutActionButtons = findViewById(R.id.layoutActionButtons)
        layoutEpisodes = findViewById(R.id.layoutEpisodes)
        rvEpisodes = findViewById(R.id.rvEpisodes)
        rvEpisodes.layoutManager = LinearLayoutManager(this)

        // Initialize ProfileManager
        ProfileManager.init(this)

        val videoId = intent.getIntExtra("VIDEO_ID", -1)

        if (videoId != -1) {
            loadDetails(videoId)
            checkWatchListStatus(videoId)
            setupWatchLaterButton(videoId)
        } else {
            finish()
        }

        setupNavigation()
    }

    private fun checkWatchListStatus(id: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = NetworkClient.api.getWatchListStatus(id)
                withContext(Dispatchers.Main) {
                    isInWatchList = response.inWatchList
                    updateWatchLaterUI()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load watch list status for videoId=$id", e)
            }
        }
    }

    private fun setupWatchLaterButton(id: Int) {
        btnWatchLater.setOnClickListener {
            if (isWatchLaterProcessing) return@setOnClickListener
            AnimationHelper.runWithPressEffect(btnWatchLater) {
                isWatchLaterProcessing = true

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val response = if (isInWatchList) {
                            NetworkClient.api.removeFromWatchList(id)
                        } else {
                            NetworkClient.api.addToWatchList(id)
                        }

                        if (response.status == "success") {
                            isInWatchList = !isInWatchList
                            withContext(Dispatchers.Main) {
                                updateWatchLaterUI()
                                val msgRes = if (isInWatchList) R.string.toast_added_watchlist else R.string.toast_removed_watchlist
                                Toast.makeText(this@DetailsActivity, msgRes, Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update watch list for videoId=$id", e)
                        withContext(Dispatchers.Main) {
                            ErrorHandler.handleNetworkError(
                                this@DetailsActivity,
                                e,
                                getString(R.string.watch_list_error)
                            )
                        }
                    } finally {
                        withContext(Dispatchers.Main) { isWatchLaterProcessing = false }
                    }
                }
            }
        }
    }

    private fun updateWatchLaterUI() {
        if (isInWatchList) {
            btnWatchLater.setText(R.string.btn_in_watch_list)
            btnWatchLater.setIconResource(R.drawable.ic_check)
        } else {
            btnWatchLater.setText(R.string.btn_watch_later)
            btnWatchLater.setIconResource(R.drawable.ic_add)
        }

        applyFocusStyle(btnWatchLater, btnWatchLater.hasFocus())
    }

    private fun startPlayback(startTime: Long, enableSubtitles: Boolean = false) {
        if (::video.isInitialized) {
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("VIDEO_ID", video.id)
            intent.putExtra("RESUME_TIME", startTime)
            intent.putExtra("ENABLE_SUBTITLES", enableSubtitles)

            // Pass intro/credits markers if available
            video.introMarker?.let { marker ->
                intent.putExtra("INTRO_START", marker.startSeconds)
                intent.putExtra("INTRO_END", marker.endSeconds)
            }
            video.creditsMarker?.let { marker ->
                // Credits marker uses duration from end - calculate actual start position
                val videoDurationSeconds = (video.total_duration.takeIf { it > 0 } ?: (video.runtime * 60L)).toInt()
                val creditsStart = marker.getCreditsStartSeconds(videoDurationSeconds)
                    ?: marker.startSeconds  // Fallback to legacy startSeconds if new fields not available
                if (creditsStart > 0) {
                    intent.putExtra("CREDITS_START", creditsStart)
                }
            }

            video.episodes?.let { episodes ->
                val currentEpIndex = episodes.indexOfFirst { it.id == video.id }
                if (currentEpIndex != -1 && currentEpIndex < episodes.size - 1) {
                    val nextEp = episodes[currentEpIndex + 1]
                    intent.putExtra("NEXT_EP_ID", nextEp.id)
                    intent.putExtra("NEXT_EP_TITLE", nextEp.title)
                }
            }
            startActivity(intent)
            AnimationHelper.applyOpenTransition(this)
        } else {
            Log.w(TAG, "Ignoring playback request before video is initialized. subtitles=$enableSubtitles")
        }
    }

    private fun startEpisodePlayback(episodeId: Int, enableSubtitles: Boolean = false) {
        if (episodeId <= 0) {
            Log.w(TAG, "Ignoring episode playback request with invalid id: $episodeId")
            Toast.makeText(this, R.string.error_loading_video, Toast.LENGTH_SHORT).show()
            return
        }
        Log.d(TAG, "Starting episode playback episodeId=$episodeId subtitles=$enableSubtitles")
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("VIDEO_ID", episodeId)
        intent.putExtra("RESUME_TIME", 0L)
        intent.putExtra("ENABLE_SUBTITLES", enableSubtitles)
        startActivity(intent)
        AnimationHelper.applyOpenTransition(this)
    }

    private fun setupNavigation() {
        btnBack.setOnClickListener { 
            AnimationHelper.runWithPressEffect(btnBack) {
                finish()
                AnimationHelper.applyCloseTransition(this)
            }
        }
        btnPlay.setOnClickListener { 
            AnimationHelper.runWithPressEffect(btnPlay) { startPlayback(0L) }
        }
        btnResume.setOnClickListener {
            if (::video.isInitialized) {
                AnimationHelper.runWithPressEffect(btnResume) { startPlayback(video.resume_time) }
            }
        }
        btnSubtitles.setOnClickListener { 
            AnimationHelper.runWithPressEffect(btnSubtitles) { startPlayback(0L, true) }
        }

        val focusListener = View.OnFocusChangeListener { v, hasFocus ->
            applyFocusStyle(v, hasFocus)
        }

        btnPlay.onFocusChangeListener = focusListener
        btnResume.onFocusChangeListener = focusListener
        btnSubtitles.onFocusChangeListener = focusListener
        btnWatchLater.onFocusChangeListener = focusListener
        btnBack.onFocusChangeListener = focusListener
    }

    private fun applyFocusStyle(v: View, hasFocus: Boolean) {
        // Handle Back Button
        if (v is ImageButton) {
            if (hasFocus) {
                AnimationHelper.scaleUp(v, Constants.FOCUS_SCALE_LARGE)
                v.elevation = Constants.FOCUS_ELEVATION
                v.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                v.imageTintList = ColorStateList.valueOf(Color.BLACK)
            } else {
                AnimationHelper.scaleDown(v)
                v.elevation = Constants.DEFAULT_ELEVATION
                // Fixed: Use toColorInt() extension instead of Color.parseColor
                v.backgroundTintList = ColorStateList.valueOf("#FFFFFF".toColorInt())
                v.imageTintList = ColorStateList.valueOf(Color.WHITE)
            }
            return
        }

        // Handle Material Buttons
        if (v is com.google.android.material.button.MaterialButton) {
            if (hasFocus) {
                AnimationHelper.scaleUp(v, Constants.FOCUS_SCALE_MEDIUM)
                v.elevation = Constants.FOCUS_ELEVATION
                v.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                v.setTextColor(Color.BLACK)
                v.iconTint = ColorStateList.valueOf(Color.BLACK)
            } else {
                AnimationHelper.scaleDown(v)
                v.elevation = Constants.DEFAULT_ELEVATION

                // Fixed: Use toColorInt() extension
                v.backgroundTintList = ColorStateList.valueOf("#262626".toColorInt())
                v.setTextColor(Color.WHITE)
                v.iconTint = ColorStateList.valueOf(Color.WHITE)
            }
        }
    }

    private fun displayProgress(video: VideoItem) {
        val detailProgress: ProgressBar = findViewById(R.id.detailProgress)

        if (video.resume_time > 0 && video.total_duration > 0) {
            val progressPercent = (video.resume_time.toDouble() / video.total_duration.toDouble() * 100).toInt()
            detailProgress.visibility = View.VISIBLE
            detailProgress.progress = progressPercent

            btnResume.visibility = View.VISIBLE
            btnPlay.visibility = View.VISIBLE
            btnPlay.setText(R.string.btn_restart) // Fixed: Use resource
            btnPlay.setIconResource(R.drawable.ic_replay)

            btnResume.requestFocus()
        } else {
            detailProgress.visibility = View.GONE
            btnResume.visibility = View.GONE
            btnPlay.visibility = View.VISIBLE
            btnPlay.setText(R.string.btn_play) // Fixed: Use resource
            btnPlay.setIconResource(R.drawable.ic_play)

            btnPlay.post { btnPlay.requestFocus() }
        }

        applyFocusStyle(btnPlay, btnPlay.hasFocus())
        applyFocusStyle(btnResume, btnResume.hasFocus())
    }

    private fun loadDetails(id: Int) {
        // Check network before loading details
        if (!NetworkUtils.isNetworkAvailable(this)) {
            ErrorHandler.showError(this, "No internet connection")
            finish()
            return
        }

        // Use WeakReference to prevent activity leak
        val weakActivity = WeakReference(this)

        // Use lifecycleScope for automatic cancellation on activity destruction
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val profileId = ProfileManager.getActiveProfileId()
                val response = NetworkClient.api.getDetails(id, profileId)
                withContext(Dispatchers.Main) {
                    val activity = weakActivity.get() ?: return@withContext
                    
                    if (response.status == "success" && response.video != null) {
                        activity.video = response.video

                        activity.txtTitle.text = activity.video.title
                        activity.txtPlot.text = activity.video.plot
                        activity.txtYear.text = NumberFormat.getIntegerInstance().format(activity.video.year)

                        val formattedRating = RatingUtils.formatImdbRating(activity.video.rating) ?: "N/A"
                        activity.txtRating.text = activity.getString(R.string.rating_format, formattedRating)
                        activity.txtDetailGenre.text = activity.video.genres ?: "N/A"

                        val contentRating = activity.video.contentRating?.trim()
                        if (!contentRating.isNullOrEmpty() && contentRating != "N/A") {
                            activity.txtContentRating.text = contentRating
                            activity.txtContentRating.visibility = View.VISIBLE
                        } else {
                            activity.txtContentRating.visibility = View.GONE
                        }

                        // Rotten Tomatoes Logic
                        val rawScore = activity.video.rottenTomatoes?.replace("%", "")?.trim()
                        val scoreInt = rawScore?.toIntOrNull()
                        if (scoreInt != null && scoreInt > 0) {
                            activity.txtRottenTomatoes.text = activity.getString(R.string.rotten_tomatoes_format, scoreInt.toString())
                            activity.txtRottenTomatoes.visibility = View.VISIBLE
                        } else {
                            activity.txtRottenTomatoes.visibility = View.GONE
                        }

                        val hasSubtitles = activity.video.hasSubtitles || !activity.video.subtitleUrl.isNullOrBlank()
                        activity.btnSubtitles.visibility =
                            if (hasSubtitles) View.VISIBLE else View.GONE

                        if (activity.video.runtime > 0) {
                            val hours = activity.video.runtime / 60
                            val mins = activity.video.runtime % 60
                            if (hours > 0) {
                                activity.txtRuntime.text = activity.getString(R.string.runtime_format, hours, mins)
                            } else {
                                activity.txtRuntime.text = activity.getString(R.string.runtime_minutes_format, mins)
                            }
                            activity.txtRuntime.visibility = View.VISIBLE
                        } else {
                            activity.txtRuntime.visibility = View.GONE
                        }

                        // --- CREDITS LOGIC ---
                        val directorText = activity.video.director
                        val hasDirector = !directorText.isNullOrBlank() && directorText != "N/A"

                        if (hasDirector) {
                            activity.txtDirector.text = activity.getString(R.string.director_label, directorText)
                            activity.txtDirector.visibility = View.VISIBLE
                        } else {
                            activity.txtDirector.visibility = View.GONE
                        }

                        val starringText = activity.video.starring
                        val hasStarring = !starringText.isNullOrBlank() && starringText != "N/A"

                        if (hasStarring) {
                            activity.txtStarring.text = activity.getString(R.string.starring_label, starringText)
                            activity.txtStarring.visibility = View.VISIBLE
                        } else {
                            activity.txtStarring.visibility = View.GONE
                        }

                        if (hasDirector || hasStarring) {
                            activity.layoutCredits.visibility = View.VISIBLE
                            activity.dividerCredits.visibility = View.VISIBLE
                        } else {
                            activity.layoutCredits.visibility = View.GONE
                            activity.dividerCredits.visibility = View.GONE
                        }

                        // --- IMAGE LOADING SIMPLIFIED ---
                        var imageUrl = activity.video.getDisplayImage()

                        // Handle Fallback Poster from Intent
                        if (activity.video.posterPath.isNullOrEmpty() && activity.video.posterUrl.isNullOrEmpty()) {
                            val fallback = activity.intent.getStringExtra("FALLBACK_POSTER")
                            if (!fallback.isNullOrEmpty()) imageUrl = fallback
                        }

                        // Load Poster
                        activity.imgPoster.load(imageUrl) {
                            crossfade(true)
                            placeholder(R.drawable.ic_movie)
                            error(R.drawable.ic_movie)
                        }

                        // Load Backdrop using the same imageUrl
                        if (imageUrl.isNotEmpty()) {
                            activity.loadBlurredBackdrop(imageUrl)
                        }
                        // --- END IMAGE LOADING ---

                        val isEpisodeView = activity.intent.getBooleanExtra("IS_EPISODE_VIEW", false)
                        val seriesPosterUrl = imageUrl

                        if (activity.video.type.equals("series", ignoreCase = true)
                            || (activity.video.type.equals("episode", ignoreCase = true) && !isEpisodeView)) {

                            activity.layoutActionButtons.visibility = View.VISIBLE
                            activity.btnPlay.visibility = View.GONE
                            activity.btnResume.visibility = View.GONE
                            activity.btnSubtitles.visibility = View.GONE
                            activity.btnWatchLater.visibility = View.VISIBLE
                            activity.layoutEpisodes.visibility = View.VISIBLE

                            activity.video.episodes?.let { episodes ->
                                if (episodes.isNotEmpty()) {
                                    Log.d(TAG, "Binding episodes list count=${episodes.size} for seriesId=${activity.video.id}")
                                    activity.rvEpisodes.adapter = EpisodeAdapter(
                                        allEpisodes = episodes,
                                        onEpisodeClick = { episode ->
                                            val intent = Intent(activity, DetailsActivity::class.java)
                                            intent.putExtra("VIDEO_ID", episode.id)
                                            intent.putExtra("IS_EPISODE_VIEW", true)
                                            intent.putExtra("FALLBACK_POSTER", seriesPosterUrl)
                                            activity.startActivity(intent)
                                            AnimationHelper.applyOpenTransition(activity)
                                        },
                                        onEpisodePlayClick = { episode ->
                                            activity.startEpisodePlayback(episode.id, enableSubtitles = false)
                                        },
                                        onEpisodePlayWithSubtitlesClick = { episode ->
                                            if (episode.hasSubtitles && !episode.subtitleUrl.isNullOrBlank()) {
                                                activity.startEpisodePlayback(episode.id, enableSubtitles = true)
                                            } else {
                                                Log.w(
                                                    TAG,
                                                    "Subtitle play requested without subtitle metadata for episodeId=${episode.id}. Falling back to regular playback."
                                                )
                                                activity.startEpisodePlayback(episode.id, enableSubtitles = false)
                                            }
                                        }
                                    )
                                    activity.rvEpisodes.post { activity.rvEpisodes.requestFocus() }
                                }
                            }
                        } else {
                            activity.layoutActionButtons.visibility = View.VISIBLE
                            activity.layoutEpisodes.visibility = View.GONE
                            activity.displayProgress(activity.video)

                        }
                    } else {
                        ErrorHandler.showError(activity, "Failed to load details")
                        activity.finish()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    weakActivity.get()?.let { activity ->
                        ErrorHandler.handleNetworkError(activity, e)
                        activity.finish()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    weakActivity.get()?.let { activity ->
                        ErrorHandler.handleNetworkError(activity, e, "Error loading details")
                        activity.finish()
                    }
                }
            }
        }
    }

    // FIXED: Updated to use Coil's internal transformation instead of RenderScript
    private fun loadBlurredBackdrop(url: String) {
        imgBackdrop.load(url) {
            crossfade(true)
            transformations(BlurTransformation(this@DetailsActivity, radius = Constants.BACKDROP_BLUR_RADIUS))
        }
        // Keep the dimming animation
        imgBackdrop.animate().alpha(Constants.BACKDROP_DIM_ALPHA).setDuration(Constants.BACKDROP_FADE_DURATION_MS).start()
    }
}
