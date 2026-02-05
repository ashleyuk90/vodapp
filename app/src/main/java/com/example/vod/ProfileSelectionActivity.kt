package com.example.vod

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vod.utils.AnimationHelper
import com.example.vod.utils.Constants
import com.example.vod.utils.ErrorHandler
import com.example.vod.utils.NetworkUtils
import com.example.vod.utils.OrientationUtils
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.lang.ref.WeakReference

/**
 * Profile selection screen displayed after login.
 * Allows users to select which profile to use for this session.
 * Follows Netflix-style profile picker UX.
 */
class ProfileSelectionActivity : AppCompatActivity() {

    companion object {
        /** Pass this extra as true to skip auto-login with default profile */
        const val EXTRA_SKIP_AUTO_LOGIN = "skip_auto_login"
    }

    private lateinit var progressLoading: ProgressBar
    private lateinit var txtError: TextView
    private lateinit var rvProfiles: RecyclerView
    private lateinit var btnRetry: MaterialButton

    private var profiles: List<Profile> = emptyList()
    private var skipAutoLogin: Boolean = false

    // Profile color palette for avatars
    private val profileColors = listOf(
        "#E50914", // Netflix Red
        "#1DB954", // Spotify Green
        "#5865F2", // Discord Blue
        "#FF6B35", // Orange
        "#9B59B6", // Purple
        "#3498DB", // Sky Blue
        "#E91E63", // Pink
        "#00BCD4"  // Cyan
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OrientationUtils.applyPreferredOrientation(this)
        setContentView(R.layout.activity_profile_selection)

        // Initialize ProfileManager
        ProfileManager.init(this)

        // Check if we should skip auto-login (e.g., user explicitly switching profiles)
        skipAutoLogin = intent.getBooleanExtra(EXTRA_SKIP_AUTO_LOGIN, false)

        // Initialize views
        progressLoading = findViewById(R.id.progressLoading)
        txtError = findViewById(R.id.txtError)
        rvProfiles = findViewById(R.id.rvProfiles)
        btnRetry = findViewById(R.id.btnRetry)

        // Setup RecyclerView with horizontal layout
        rvProfiles.layoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.HORIZONTAL,
            false
        )

        // Retry button click
        btnRetry.setOnClickListener {
            loadProfiles()
        }

        // Load profiles
        loadProfiles()
    }

    private fun loadProfiles() {
        // Check network
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showError(getString(R.string.profile_load_error))
            return
        }

        showLoading()

        val weakActivity = WeakReference(this)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = NetworkClient.api.getProfiles()

                withContext(Dispatchers.Main) {
                    val activity = weakActivity.get() ?: return@withContext

                    if (response.status == "success" && response.profiles.isNotEmpty()) {
                        activity.profiles = response.profiles

                        // Check for default profile and auto-login (unless explicitly skipped)
                        if (!activity.skipAutoLogin) {
                            val defaultProfileId = ProfileManager.getDefaultProfileId()
                            if (defaultProfileId != null) {
                                val defaultProfile = response.profiles.find { it.id == defaultProfileId }
                                if (defaultProfile != null && !defaultProfile.hasPin) {
                                    // Auto-select default profile (skip if PIN required)
                                    activity.selectProfile(defaultProfile)
                                    return@withContext
                                }
                            }
                        }

                        // If only one profile, auto-select it
                        if (response.profiles.size == 1) {
                            activity.selectProfile(response.profiles.first())
                        } else {
                            activity.showProfiles(response.profiles)
                        }
                    } else if (response.profiles.isEmpty()) {
                        // No profiles - create default and proceed
                        activity.proceedToMain()
                    } else {
                        activity.showError(getString(R.string.profile_load_error))
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    weakActivity.get()?.let { activity ->
                        ErrorHandler.handleNetworkError(activity, e)
                        activity.showError(getString(R.string.profile_load_error))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    weakActivity.get()?.let { activity ->
                        ErrorHandler.handleNetworkError(activity, e, getString(R.string.profile_load_error))
                        activity.showError(getString(R.string.profile_load_error))
                    }
                }
            }
        }
    }

    private fun showLoading() {
        progressLoading.isVisible = true
        txtError.isVisible = false
        rvProfiles.isVisible = false
        btnRetry.isVisible = false
    }

    private fun showError(message: String) {
        progressLoading.isVisible = false
        txtError.isVisible = true
        txtError.text = message
        rvProfiles.isVisible = false
        btnRetry.isVisible = true
    }

    private fun showProfiles(profiles: List<Profile>) {
        progressLoading.isVisible = false
        txtError.isVisible = false
        rvProfiles.isVisible = true
        btnRetry.isVisible = false

        rvProfiles.adapter = ProfileAdapter(
            profiles = profiles,
            colors = profileColors,
            onProfileClick = { profile -> selectProfile(profile) },
            onProfileLongClick = { profile -> showProfileOptions(profile) }
        )

        // Focus first profile
        rvProfiles.post {
            rvProfiles.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
        }
    }

    /**
     * Show profile options dialog (set/remove as default).
     */
    private fun showProfileOptions(profile: Profile) {
        val isDefault = ProfileManager.isDefaultProfile(profile.id)
        
        val options = if (isDefault) {
            arrayOf(getString(R.string.remove_default))
        } else {
            arrayOf(getString(R.string.set_as_default))
        }
        
        AlertDialog.Builder(this)
            .setTitle(profile.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (isDefault) {
                            ProfileManager.clearDefaultProfile()
                            Toast.makeText(this, R.string.default_profile_cleared, Toast.LENGTH_SHORT).show()
                        } else {
                            ProfileManager.setDefaultProfileId(profile.id)
                            Toast.makeText(
                                this,
                                getString(R.string.default_profile_set, profile.name),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        // Refresh adapter to update default indicator
                        rvProfiles.adapter?.notifyDataSetChanged()
                    }
                }
            }
            .show()
    }

    private fun selectProfile(profile: Profile) {
        // Check if PIN is required
        if (profile.hasPin) {
            // TODO: Show PIN entry dialog
            // For now, just proceed
        }

        showLoading()

        val weakActivity = WeakReference(this)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = NetworkClient.api.selectProfile(profile.id)

                withContext(Dispatchers.Main) {
                    val activity = weakActivity.get() ?: return@withContext

                    if (response.status == "success") {
                        // Save profile locally
                        ProfileManager.setActiveProfile(profile)

                        // Show confirmation
                        ErrorHandler.showError(
                            activity,
                            getString(R.string.profile_selected, profile.name)
                        )

                        // Proceed to main screen
                        activity.proceedToMain()
                    } else {
                        activity.showError(getString(R.string.profile_select_error))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    weakActivity.get()?.let { activity ->
                        ErrorHandler.handleNetworkError(activity, e, getString(R.string.profile_select_error))
                        activity.showProfiles(activity.profiles)
                    }
                }
            }
        }
    }

    private fun proceedToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    /**
     * Adapter for profile grid.
     */
    inner class ProfileAdapter(
        private val profiles: List<Profile>,
        private val colors: List<String>,
        private val onProfileClick: (Profile) -> Unit,
        private val onProfileLongClick: (Profile) -> Unit
    ) : RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_profile, parent, false)
            return ProfileViewHolder(view)
        }

        override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
            holder.bind(profiles[position], colors[position % colors.size])
        }

        override fun getItemCount(): Int = profiles.size

        inner class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val viewAvatarBg: View = itemView.findViewById(R.id.viewAvatarBg)
            private val txtInitial: TextView = itemView.findViewById(R.id.txtInitial)
            private val txtProfileName: TextView = itemView.findViewById(R.id.txtProfileName)
            private val txtRatingLimit: TextView = itemView.findViewById(R.id.txtRatingLimit)
            private val imgLock: View = itemView.findViewById(R.id.imgLock)

            fun bind(profile: Profile, colorHex: String) {
                txtInitial.text = profile.getInitial()
                txtProfileName.text = profile.name

                // Set avatar background color
                viewAvatarBg.background.setTint(Color.parseColor(colorHex))

                // Show rating limit if parental controls enabled
                if (profile.hasParentalControls()) {
                    txtRatingLimit.isVisible = true
                    txtRatingLimit.text = getString(R.string.max_rating_format, profile.maxContentRating)
                } else if (ProfileManager.isDefaultProfile(profile.id)) {
                    // Show default indicator
                    txtRatingLimit.isVisible = true
                    txtRatingLimit.text = "★ Default"
                } else {
                    txtRatingLimit.isVisible = false
                }

                // Show lock icon if PIN protected
                imgLock.isVisible = profile.hasPin

                // Click handler
                itemView.setOnClickListener {
                    onProfileClick(profile)
                }

                // Long click for options (set as default)
                itemView.setOnLongClickListener {
                    onProfileLongClick(profile)
                    true
                }

                // Focus animation
                itemView.setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        AnimationHelper.scaleUp(v, Constants.FOCUS_SCALE_MEDIUM)
                    } else {
                        AnimationHelper.scaleDown(v)
                    }
                    v.elevation = if (hasFocus) Constants.FOCUS_ELEVATION else Constants.DEFAULT_ELEVATION
                }

                // D-pad navigation wrap-around
                itemView.setOnKeyListener { _, keyCode, event ->
                    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false

                    val position = bindingAdapterPosition
                    if (position == RecyclerView.NO_POSITION) return@setOnKeyListener false

                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (position == profiles.size - 1) {
                                // Wrap to first
                                rvProfiles.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                                return@setOnKeyListener true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (position == 0) {
                                // Wrap to last
                                rvProfiles.findViewHolderForAdapterPosition(profiles.size - 1)?.itemView?.requestFocus()
                                return@setOnKeyListener true
                            }
                        }
                    }
                    false
                }
            }
        }
    }
}
