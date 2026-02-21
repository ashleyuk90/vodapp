package com.example.vod

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.vod.utils.AnimationHelper
import com.example.vod.utils.Constants
import androidx.appcompat.widget.SwitchCompat
import com.example.vod.update.SelfHostedUpdateManager
import com.example.vod.utils.OrientationUtils
import com.example.vod.utils.SecurePrefs
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Locale
import android.os.Build

class PlaybackPreferencesActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var txtSubtitle: TextView
    private lateinit var rowAutoSkipIntro: LinearLayout
    private lateinit var rowAutoSkipCredits: LinearLayout
    private lateinit var rowAutoplayNext: LinearLayout
    private lateinit var switchAutoSkipIntro: SwitchCompat
    private lateinit var switchAutoSkipCredits: SwitchCompat
    private lateinit var switchAutoplayNext: SwitchCompat
    private lateinit var layoutAccountExpiry: LinearLayout
    private lateinit var txtAccountExpiryValue: TextView
    private lateinit var txtInstalledVersionValue: TextView
    private lateinit var rowCheckForUpdates: LinearLayout
    private lateinit var btnLogout: MaterialButton
    private lateinit var updateManager: SelfHostedUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        OrientationUtils.applyPreferredOrientation(this)
        setContentView(R.layout.activity_playback_preferences)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Ensure singletons are initialized
        NetworkClient.init(applicationContext)
        ProfileManager.init(this)
        updateManager = SelfHostedUpdateManager(this)

        btnBack = findViewById(R.id.btnBack)
        txtSubtitle = findViewById(R.id.txtSubtitle)
        rowAutoSkipIntro = findViewById(R.id.rowAutoSkipIntro)
        rowAutoSkipCredits = findViewById(R.id.rowAutoSkipCredits)
        rowAutoplayNext = findViewById(R.id.rowAutoplayNext)
        switchAutoSkipIntro = findViewById(R.id.switchAutoSkipIntro)
        switchAutoSkipCredits = findViewById(R.id.switchAutoSkipCredits)
        switchAutoplayNext = findViewById(R.id.switchAutoplayNext)
        layoutAccountExpiry = findViewById(R.id.layoutAccountExpiry)
        txtAccountExpiryValue = findViewById(R.id.txtAccountExpiryValue)
        txtInstalledVersionValue = findViewById(R.id.txtInstalledVersionValue)
        rowCheckForUpdates = findViewById(R.id.rowCheckForUpdates)
        btnLogout = findViewById(R.id.btnLogout)

        val profile = ProfileManager.getActiveProfile()
        if (profile == null) {
            Toast.makeText(this, R.string.profile_not_selected, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        txtSubtitle.text = getString(R.string.playback_prefs_subtitle, profile.name)
        bindAccountExpiry()
        bindInstalledVersion()
        bindUpdaterActions()

        setSwitchState(switchAutoSkipIntro, profile.autoSkipIntro)
        setSwitchState(switchAutoSkipCredits, profile.autoSkipCredits)
        setSwitchState(switchAutoplayNext, profile.autoplayNext)

        bindRow(rowAutoSkipIntro, switchAutoSkipIntro)
        bindRow(rowAutoSkipCredits, switchAutoSkipCredits)
        bindRow(rowAutoplayNext, switchAutoplayNext)

        btnBack.setOnClickListener {
            finish()
            AnimationHelper.applyCloseTransition(this)
        }

        btnLogout.setOnClickListener { showLogoutConfirmation() }
        btnLogout.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                AnimationHelper.scaleUp(v, Constants.FOCUS_SCALE_SMALL)
            } else {
                AnimationHelper.scaleDown(v)
            }
        }

        rowCheckForUpdates.post { rowCheckForUpdates.requestFocus() }
    }

    override fun onResume() {
        super.onResume()
        updateManager.resumePendingInstallIfPermitted()
    }

    private fun bindAccountExpiry() {
        val prefs = SecurePrefs.get(this, Constants.PREFS_NAME)

        val rawExpiry = prefs.getString(Constants.KEY_ACCOUNT_EXPIRY, null)?.trim().orEmpty()
        if (rawExpiry.isBlank()) {
            layoutAccountExpiry.visibility = View.GONE
            return
        }

        val formattedExpiry = formatAccountExpiry(rawExpiry)
        txtAccountExpiryValue.text = getString(R.string.playback_prefs_account_expiry_value, formattedExpiry)
        layoutAccountExpiry.visibility = View.VISIBLE
    }

    private fun bindInstalledVersion() {
        val versionName = BuildConfig.VERSION_NAME.ifBlank { "N/A" }
        val versionCode = getInstalledVersionCode()
        txtInstalledVersionValue.text = getString(
            R.string.playback_prefs_installed_version_value,
            versionName,
            versionCode
        )
    }

    private fun getInstalledVersionCode(): Int {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }
    }

    private fun bindUpdaterActions() {
        rowCheckForUpdates.setOnClickListener {
            Toast.makeText(this, R.string.playback_prefs_checking_updates, Toast.LENGTH_SHORT).show()
            updateManager.checkForUpdatesIfNeeded(
                force = true,
                showErrors = true,
                showNoUpdateMessage = true
            )
        }
        rowCheckForUpdates.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                AnimationHelper.scaleUp(v, Constants.FOCUS_SCALE_SMALL)
            } else {
                AnimationHelper.scaleDown(v)
            }
        }
    }

    private fun formatAccountExpiry(rawExpiry: String): String {
        val inputFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        )
        val outputFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())

        for (inputFormat in inputFormats) {
            try {
                inputFormat.isLenient = false
                val parsed = inputFormat.parse(rawExpiry)
                if (parsed != null) return outputFormat.format(parsed)
            } catch (_: Exception) {
                // Try next known format
            }
        }

        // Fallback to raw server value if parsing fails
        return rawExpiry
    }

    private fun bindRow(row: View, toggle: SwitchCompat) {
        row.setOnClickListener { toggle.isChecked = !toggle.isChecked }
        row.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                AnimationHelper.scaleUp(v, Constants.FOCUS_SCALE_SMALL)
            } else {
                AnimationHelper.scaleDown(v)
            }
        }
    }

    private fun setSwitchState(toggle: SwitchCompat, value: Boolean) {
        toggle.setOnCheckedChangeListener(null)
        toggle.isChecked = value
        toggle.setOnCheckedChangeListener { _, _ ->
            savePreferences()
        }
    }

    private fun savePreferences() {
        ProfileManager.updatePlaybackPreferences(
            autoSkipIntro = switchAutoSkipIntro.isChecked,
            autoSkipCredits = switchAutoSkipCredits.isChecked,
            autoplayNext = switchAutoplayNext.isChecked
        )
        Toast.makeText(this, R.string.playback_prefs_saved, Toast.LENGTH_SHORT).show()
    }

    private fun showLogoutConfirmation() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_logout_confirm, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btnDialogCancel).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.btnDialogLogout).setOnClickListener {
            dialog.dismiss()
            performLogout()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun performLogout() {
        // Clear CSRF token
        NetworkClient.updateCsrfToken(null)

        // Clear saved credentials
        val prefs = SecurePrefs.get(this, Constants.PREFS_NAME)
        prefs.edit {
            remove(Constants.KEY_USER)
            remove(Constants.KEY_PASS)
            remove(Constants.KEY_CSRF_TOKEN)
            remove(Constants.KEY_ACCOUNT_EXPIRY)
        }

        // Clear active profile and default profile
        ProfileManager.clearActiveProfile()
        ProfileManager.clearDefaultProfile()

        // Clear persisted cookies
        NetworkClient.cookieJar.clear()

        // Navigate to LoginActivity and clear the back stack
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
