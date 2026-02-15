package com.example.vod

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.graphics.drawable.toDrawable
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

    private val profileColors: List<Int> by lazy {
        val typedArray = resources.obtainTypedArray(R.array.profile_avatar_colors)
        val colors = (0 until typedArray.length()).map { typedArray.getColor(it, Color.GRAY) }
        typedArray.recycle()
        colors
    }

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

    private fun loadProfiles(allowAutoSelect: Boolean = true) {
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
                        response.profiles.find { it.id == response.activeProfileId }?.let { activeProfile ->
                            ProfileManager.setActiveProfile(activeProfile)
                        }

                        // Check for default profile and auto-login (unless explicitly skipped)
                        if (allowAutoSelect && !activity.skipAutoLogin) {
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

                        // If only one profile, auto-select it unless this picker was explicitly opened
                        if (allowAutoSelect && response.profiles.size == 1 && !activity.skipAutoLogin) {
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
            onProfileLongClick = { profile -> showProfileOptions(profile) },
            onCreateProfileClick = { onCreateProfileClicked() }
        )

        // Focus first profile
        rvProfiles.post {
            rvProfiles.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
        }
    }

    private fun onCreateProfileClicked() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_profile, null)
        val input = dialogView.findViewById<EditText>(R.id.etProfileName).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            maxLines = 1
            setSingleLine(true)
        }
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCreateProfileCancel)
        val btnCreate = dialogView.findViewById<MaterialButton>(R.id.btnCreateProfileCreate)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.setOnShowListener {
            dialog.window?.let { window ->
                window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
                window.setLayout(
                    (resources.displayMetrics.widthPixels * 0.86f).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                window.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                window.attributes = window.attributes.apply {
                    y = resources.getDimensionPixelSize(R.dimen.create_profile_dialog_top_offset)
                }
                window.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                )
            }

            btnCancel.setOnClickListener {
                dialog.dismiss()
            }

            val submitCreate = {
                val profileName = input.text?.toString()?.trim().orEmpty()
                if (profileName.isBlank()) {
                    input.error = getString(R.string.profile_name_required)
                    input.requestFocus()
                } else {
                    dialog.dismiss()
                    createProfile(profileName)
                }
            }

            btnCreate.setOnClickListener {
                submitCreate()
            }

            input.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    submitCreate()
                    true
                } else {
                    false
                }
            }

            input.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }

        dialog.show()
    }

    private fun createProfile(profileName: String) {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, R.string.error_network, Toast.LENGTH_SHORT).show()
            return
        }

        showLoading()
        val weakActivity = WeakReference(this)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = NetworkClient.api.addProfile(profileName)

                withContext(Dispatchers.Main) {
                    val activity = weakActivity.get() ?: return@withContext
                    val createdProfile = response.profile

                    if (response.status == "success" && createdProfile != null) {
                        ProfileManager.setActiveProfile(createdProfile)
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.profile_created, createdProfile.name),
                            Toast.LENGTH_SHORT
                        ).show()
                        activity.proceedToMain()
                    } else {
                        Toast.makeText(
                            activity,
                            response.message ?: activity.getString(R.string.profile_create_error),
                            Toast.LENGTH_SHORT
                        ).show()
                        activity.loadProfiles(allowAutoSelect = false)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    weakActivity.get()?.let { activity ->
                        ErrorHandler.handleNetworkError(
                            activity,
                            e,
                            activity.getString(R.string.profile_create_error)
                        )
                        activity.loadProfiles(allowAutoSelect = false)
                    }
                }
            }
        }
    }

    /**
     * Show profile options dialog (set/remove as default).
     */
    private fun showProfileOptions(profile: Profile) {
        val isDefault = ProfileManager.isDefaultProfile(profile.id)

        val optionLabels = mutableListOf<String>()
        val optionActions = mutableListOf<() -> Unit>()

        if (isDefault) {
            optionLabels += getString(R.string.remove_default)
            optionActions += {
                val previousDefaultId = ProfileManager.getDefaultProfileId()
                ProfileManager.clearDefaultProfile()
                Toast.makeText(this, R.string.default_profile_cleared, Toast.LENGTH_SHORT).show()
                refreshDefaultProfileIndicators(previousDefaultId, null)
            }
        } else {
            optionLabels += getString(R.string.set_as_default)
            optionActions += {
                val previousDefaultId = ProfileManager.getDefaultProfileId()
                ProfileManager.setDefaultProfileId(profile.id)
                Toast.makeText(
                    this,
                    getString(R.string.default_profile_set, profile.name),
                    Toast.LENGTH_SHORT
                ).show()
                refreshDefaultProfileIndicators(previousDefaultId, profile.id)
            }
        }

        if (profiles.size > 1) {
            optionLabels += getString(R.string.delete_profile_action)
            optionActions += { confirmDeleteProfile(profile) }
        }

        AlertDialog.Builder(this)
            .setTitle(profile.name)
            .setItems(optionLabels.toTypedArray()) { _, which ->
                optionActions.getOrNull(which)?.invoke()
            }
            .show()
    }

    private fun refreshDefaultProfileIndicators(previousDefaultId: Int?, newDefaultId: Int?) {
        val adapter = rvProfiles.adapter ?: return
        val positionsToRefresh = linkedSetOf<Int>()

        previousDefaultId?.let { oldId ->
            val oldPosition = profiles.indexOfFirst { it.id == oldId }
            if (oldPosition >= 0) positionsToRefresh.add(oldPosition)
        }
        newDefaultId?.let { currentId ->
            val newPosition = profiles.indexOfFirst { it.id == currentId }
            if (newPosition >= 0) positionsToRefresh.add(newPosition)
        }

        positionsToRefresh.forEach { position ->
            adapter.notifyItemChanged(position)
        }
    }

    private fun confirmDeleteProfile(profile: Profile) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_profile_action)
            .setMessage(getString(R.string.delete_profile_confirm, profile.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_profile_action) { _, _ ->
                deleteProfile(profile)
            }
            .show()
    }

    private fun deleteProfile(profile: Profile) {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, R.string.error_network, Toast.LENGTH_SHORT).show()
            return
        }

        showLoading()
        val weakActivity = WeakReference(this)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = NetworkClient.api.removeProfile(profile.id)

                withContext(Dispatchers.Main) {
                    val activity = weakActivity.get() ?: return@withContext
                    if (response.status == "success") {
                        if (ProfileManager.isDefaultProfile(profile.id)) {
                            ProfileManager.clearDefaultProfile()
                        }
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.profile_deleted, profile.name),
                            Toast.LENGTH_SHORT
                        ).show()
                        activity.loadProfiles(allowAutoSelect = false)
                    } else {
                        Toast.makeText(
                            activity,
                            response.message ?: activity.getString(R.string.profile_delete_error),
                            Toast.LENGTH_SHORT
                        ).show()
                        activity.loadProfiles(allowAutoSelect = false)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    weakActivity.get()?.let { activity ->
                        ErrorHandler.handleNetworkError(
                            activity,
                            e,
                            activity.getString(R.string.profile_delete_error)
                        )
                        activity.loadProfiles(allowAutoSelect = false)
                    }
                }
            }
        }
    }

    private fun selectProfile(profile: Profile) {
        if (profile.hasPin) {
            showPinDialog(profile)
            return
        }

        performProfileSelection(profile)
    }

    private fun showPinDialog(profile: Profile) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_profile, null)
        val input = dialogView.findViewById<EditText>(R.id.etProfileName).apply {
            hint = getString(R.string.pin_entry_hint)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            maxLines = 1
            setSingleLine(true)
        }
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCreateProfileCancel)
        val btnSubmit = dialogView.findViewById<MaterialButton>(R.id.btnCreateProfileCreate)
        btnSubmit.setText(R.string.pin_submit)

        val titleView = dialogView.findViewById<TextView>(R.id.txtCreateProfileTitle)
        titleView?.text = getString(R.string.pin_entry_title, profile.name)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.setOnShowListener {
            dialog.window?.let { window ->
                window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
                window.setLayout(
                    (resources.displayMetrics.widthPixels * 0.86f).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                window.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                window.attributes = window.attributes.apply {
                    y = resources.getDimensionPixelSize(R.dimen.create_profile_dialog_top_offset)
                }
                window.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                )
            }

            btnCancel.setOnClickListener { dialog.dismiss() }

            val submitPin = {
                val pin = input.text?.toString()?.trim().orEmpty()
                if (pin.isBlank()) {
                    input.error = getString(R.string.pin_required)
                    input.requestFocus()
                } else {
                    dialog.dismiss()
                    performProfileSelection(profile)
                }
            }

            btnSubmit.setOnClickListener { submitPin() }

            input.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    submitPin()
                    true
                } else {
                    false
                }
            }

            input.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }

        dialog.show()
    }

    private fun performProfileSelection(profile: Profile) {
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
        private val colors: List<Int>,
        private val onProfileClick: (Profile) -> Unit,
        private val onProfileLongClick: (Profile) -> Unit,
        private val onCreateProfileClick: () -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val viewTypeProfile = 0
        private val viewTypeCreateProfile = 1

        override fun getItemViewType(position: Int): Int {
            return if (position < profiles.size) viewTypeProfile else viewTypeCreateProfile
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_profile, parent, false)

            return if (viewType == viewTypeProfile) {
                ProfileViewHolder(view)
            } else {
                CreateProfileViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is ProfileViewHolder -> holder.bind(profiles[position], colors[position % colors.size])
                is CreateProfileViewHolder -> holder.bind()
            }
        }

        override fun getItemCount(): Int = profiles.size + 1

        private fun requestFocusAt(position: Int) {
            rvProfiles.smoothScrollToPosition(position)
            rvProfiles.post {
                rvProfiles.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
            }
        }

        inner class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val viewAvatarBg: View = itemView.findViewById(R.id.viewAvatarBg)
            private val txtInitial: TextView = itemView.findViewById(R.id.txtInitial)
            private val txtProfileName: TextView = itemView.findViewById(R.id.txtProfileName)
            private val txtRatingLimit: TextView = itemView.findViewById(R.id.txtRatingLimit)
            private val imgLock: View = itemView.findViewById(R.id.imgLock)

            fun bind(profile: Profile, color: Int) {
                txtInitial.text = profile.getInitial()
                txtProfileName.text = profile.name

                // Set avatar background color
                viewAvatarBg.background.setTint(color)

                // Show rating limit if parental controls enabled
                if (profile.hasParentalControls()) {
                    txtRatingLimit.isVisible = true
                    txtRatingLimit.text = getString(R.string.max_rating_format, profile.maxContentRating)
                } else if (ProfileManager.isDefaultProfile(profile.id)) {
                    // Show default indicator
                    txtRatingLimit.isVisible = true
                    txtRatingLimit.text = getString(R.string.default_profile_badge)
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
                                // Move from last profile to create profile tile
                                requestFocusAt(profiles.size)
                                return@setOnKeyListener true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (position == 0) {
                                // Wrap to create profile tile
                                requestFocusAt(itemCount - 1)
                                return@setOnKeyListener true
                            }
                        }
                    }
                    false
                }
            }
        }

        inner class CreateProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val viewAvatarBg: View = itemView.findViewById(R.id.viewAvatarBg)
            private val txtInitial: TextView = itemView.findViewById(R.id.txtInitial)
            private val txtProfileName: TextView = itemView.findViewById(R.id.txtProfileName)
            private val txtRatingLimit: TextView = itemView.findViewById(R.id.txtRatingLimit)
            private val imgLock: View = itemView.findViewById(R.id.imgLock)

            fun bind() {
                txtInitial.text = "+"
                txtProfileName.text = getString(R.string.create_profile)
                txtRatingLimit.isVisible = true
                txtRatingLimit.text = getString(R.string.create_profile_hint)
                imgLock.isVisible = false
                viewAvatarBg.background.setTint("#2A2A2A".toColorInt())

                itemView.setOnClickListener {
                    onCreateProfileClick()
                }

                itemView.setOnLongClickListener {
                    onCreateProfileClick()
                    true
                }

                itemView.setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        AnimationHelper.scaleUp(v, Constants.FOCUS_SCALE_MEDIUM)
                    } else {
                        AnimationHelper.scaleDown(v)
                    }
                    v.elevation = if (hasFocus) Constants.FOCUS_ELEVATION else Constants.DEFAULT_ELEVATION
                }

                itemView.setOnKeyListener { _, keyCode, event ->
                    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false

                    val position = bindingAdapterPosition
                    if (position == RecyclerView.NO_POSITION) return@setOnKeyListener false

                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (position == itemCount - 1) {
                                requestFocusAt(0)
                                return@setOnKeyListener true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (position == 0) {
                                requestFocusAt(itemCount - 1)
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
