package com.example.vod

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailsActivity : AppCompatActivity() {

    private lateinit var imgBackdrop: ImageView
    private lateinit var imgPoster: ImageView
    private lateinit var txtTitle: TextView
    private lateinit var txtPlot: TextView
    private lateinit var txtYear: TextView
    private lateinit var txtRuntime: TextView
    private lateinit var txtRating: TextView
    private lateinit var txtRottenTomatoes: TextView

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
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)

        // Initialize Views
        imgBackdrop = findViewById(R.id.imgDetailsBackdrop)
        imgPoster = findViewById(R.id.imgDetailPoster)
        txtTitle = findViewById(R.id.txtDetailTitle)
        txtPlot = findViewById(R.id.txtDetailPlot)
        txtYear = findViewById(R.id.txtDetailYear)
        txtRuntime = findViewById(R.id.txtDetailRuntime)
        txtRating = findViewById(R.id.txtRating)
        txtRottenTomatoes = findViewById(R.id.txtRottenTomatoes)

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
                e.printStackTrace()
            }
        }
    }

    private fun setupWatchLaterButton(id: Int) {
        btnWatchLater.setOnClickListener {
            if (isWatchLaterProcessing) return@setOnClickListener
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
                    e.printStackTrace()
                } finally {
                    withContext(Dispatchers.Main) { isWatchLaterProcessing = false }
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

            video.episodes?.let { episodes ->
                val currentEpIndex = episodes.indexOfFirst { it.id == video.id }
                if (currentEpIndex != -1 && currentEpIndex < episodes.size - 1) {
                    val nextEp = episodes[currentEpIndex + 1]
                    intent.putExtra("NEXT_EP_ID", nextEp.id)
                    intent.putExtra("NEXT_EP_TITLE", nextEp.title)
                }
            }
            startActivity(intent)
        }
    }

    private fun setupNavigation() {
        btnBack.setOnClickListener { finish() }
        btnPlay.setOnClickListener { startPlayback(0L) }
        btnResume.setOnClickListener { startPlayback(video.resume_time) }
        btnSubtitles.setOnClickListener { startPlayback(0L, true) }

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
                v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start()
                v.elevation = 10f
                v.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                v.imageTintList = ColorStateList.valueOf(Color.BLACK)
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                v.elevation = 0f
                // Fixed: Use toColorInt() extension instead of Color.parseColor
                v.backgroundTintList = ColorStateList.valueOf("#FFFFFF".toColorInt())
                v.imageTintList = ColorStateList.valueOf(Color.WHITE)
            }
            return
        }

        // Handle Material Buttons
        if (v is com.google.android.material.button.MaterialButton) {
            if (hasFocus) {
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start()
                v.elevation = 10f
                v.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                v.setTextColor(Color.BLACK)
                v.iconTint = ColorStateList.valueOf(Color.BLACK)
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                v.elevation = 0f

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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = NetworkClient.api.getDetails(id)
                withContext(Dispatchers.Main) {
                    if (response.status == "success" && response.video != null) {
                        video = response.video

                        txtTitle.text = video.title
                        txtPlot.text = video.plot
                        txtYear.text = video.year.toString()

                        txtRating.text = getString(R.string.rating_format, video.rating ?: "N/A")
                        txtDetailGenre.text = video.genres ?: "N/A"

                        // Rotten Tomatoes Logic
                        val rawScore = video.rottenTomatoes?.replace("%", "")?.trim()
                        val scoreInt = rawScore?.toIntOrNull()
                        if (scoreInt != null && scoreInt > 0) {
                            txtRottenTomatoes.text = getString(R.string.rotten_tomatoes_format, scoreInt.toString())
                            txtRottenTomatoes.visibility = View.VISIBLE
                        } else {
                            txtRottenTomatoes.visibility = View.GONE
                        }

                        btnSubtitles.visibility = if (video.hasSubtitles) View.VISIBLE else View.GONE

                        if (video.runtime > 0) {
                            val hours = video.runtime / 60
                            val mins = video.runtime % 60
                            if (hours > 0) {
                                txtRuntime.text = getString(R.string.runtime_format, hours, mins)
                            } else {
                                txtRuntime.text = getString(R.string.runtime_minutes_format, mins)
                            }
                            txtRuntime.visibility = View.VISIBLE
                        } else {
                            txtRuntime.visibility = View.GONE
                        }

                        // --- CREDITS LOGIC ---
                        val directorText = video.director
                        val hasDirector = !directorText.isNullOrBlank() && directorText != "N/A"

                        if (hasDirector) {
                            txtDirector.text = getString(R.string.director_label, directorText)
                            txtDirector.visibility = View.VISIBLE
                        } else {
                            txtDirector.visibility = View.GONE
                        }

                        val starringText = video.starring
                        val hasStarring = !starringText.isNullOrBlank() && starringText != "N/A"

                        if (hasStarring) {
                            txtStarring.text = getString(R.string.starring_label, starringText)
                            txtStarring.visibility = View.VISIBLE
                        } else {
                            txtStarring.visibility = View.GONE
                        }

                        if (hasDirector || hasStarring) {
                            layoutCredits.visibility = View.VISIBLE
                            dividerCredits.visibility = View.VISIBLE
                        } else {
                            layoutCredits.visibility = View.GONE
                            dividerCredits.visibility = View.GONE
                        }

                        // --- IMAGE LOADING SIMPLIFIED ---
                        var imageUrl = video.getDisplayImage()

                        // Handle Fallback Poster from Intent
                        if (video.posterPath.isNullOrEmpty() && video.posterUrl.isNullOrEmpty()) {
                            val fallback = intent.getStringExtra("FALLBACK_POSTER")
                            if (!fallback.isNullOrEmpty()) imageUrl = fallback
                        }

                        // Load Poster
                        imgPoster.load(imageUrl) {
                            crossfade(true)
                        }

                        // Load Backdrop using the same imageUrl
                        // The check !imageUrl.isNullOrEmpty() ensures we don't pass null to loadBlurredBackdrop
                        if (imageUrl.isNotEmpty()) {
                            loadBlurredBackdrop(imageUrl)
                        }
                        // --- END IMAGE LOADING ---

                        val isEpisodeView = intent.getBooleanExtra("IS_EPISODE_VIEW", false)
                        val seriesPosterUrl = imageUrl

                        if (video.type.equals("series", ignoreCase = true)
                            || (video.type.equals("episode", ignoreCase = true) && !isEpisodeView)) {

                            layoutActionButtons.visibility = View.VISIBLE
                            btnPlay.visibility = View.GONE
                            btnResume.visibility = View.GONE
                            btnSubtitles.visibility = View.GONE
                            btnWatchLater.visibility = View.VISIBLE
                            layoutEpisodes.visibility = View.VISIBLE

                            video.episodes?.let { episodes ->
                                if (episodes.isNotEmpty()) {
                                    rvEpisodes.adapter = EpisodeAdapter(episodes) { episode ->
                                        val intent = Intent(this@DetailsActivity, DetailsActivity::class.java)
                                        intent.putExtra("VIDEO_ID", episode.id)
                                        intent.putExtra("IS_EPISODE_VIEW", true)
                                        intent.putExtra("FALLBACK_POSTER", seriesPosterUrl)
                                        startActivity(intent)
                                    }
                                    rvEpisodes.post { rvEpisodes.requestFocus() }
                                }
                            }
                        } else {
                            layoutActionButtons.visibility = View.VISIBLE
                            layoutEpisodes.visibility = View.GONE
                            displayProgress(video)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // FIXED: Updated to use Coil's internal transformation instead of RenderScript
    private fun loadBlurredBackdrop(url: String) {
        imgBackdrop.load(url) {
            crossfade(true)
            // Blur radius of 25f matches your previous code
            transformations(BlurTransformation(this@DetailsActivity, radius = 25f))
        }
        // Keep the dimming animation
        imgBackdrop.animate().alpha(0.6f).setDuration(500).start()
    }
}