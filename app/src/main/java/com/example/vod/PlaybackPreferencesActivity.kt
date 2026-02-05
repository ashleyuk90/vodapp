package com.example.vod

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.vod.utils.AnimationHelper
import com.example.vod.utils.Constants
import androidx.appcompat.widget.SwitchCompat
import com.example.vod.utils.OrientationUtils

class PlaybackPreferencesActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var txtSubtitle: TextView
    private lateinit var rowAutoSkipIntro: LinearLayout
    private lateinit var rowAutoSkipCredits: LinearLayout
    private lateinit var rowAutoplayNext: LinearLayout
    private lateinit var switchAutoSkipIntro: SwitchCompat
    private lateinit var switchAutoSkipCredits: SwitchCompat
    private lateinit var switchAutoplayNext: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OrientationUtils.applyPreferredOrientation(this)
        setContentView(R.layout.activity_playback_preferences)

        // Ensure ProfileManager is initialized
        ProfileManager.init(this)

        btnBack = findViewById(R.id.btnBack)
        txtSubtitle = findViewById(R.id.txtSubtitle)
        rowAutoSkipIntro = findViewById(R.id.rowAutoSkipIntro)
        rowAutoSkipCredits = findViewById(R.id.rowAutoSkipCredits)
        rowAutoplayNext = findViewById(R.id.rowAutoplayNext)
        switchAutoSkipIntro = findViewById(R.id.switchAutoSkipIntro)
        switchAutoSkipCredits = findViewById(R.id.switchAutoSkipCredits)
        switchAutoplayNext = findViewById(R.id.switchAutoplayNext)

        val profile = ProfileManager.getActiveProfile()
        if (profile == null) {
            Toast.makeText(this, R.string.profile_not_selected, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        txtSubtitle.text = getString(R.string.playback_prefs_subtitle, profile.name)

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

        rowAutoSkipIntro.post { rowAutoSkipIntro.requestFocus() }
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
}
