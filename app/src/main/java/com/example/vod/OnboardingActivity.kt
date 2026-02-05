package com.example.vod

import android.content.Context
import android.content.Intent
import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.vod.utils.AnimationHelper
import com.example.vod.utils.Constants
import com.example.vod.utils.OrientationUtils
import com.example.vod.utils.ResponsiveUtils
import com.google.android.material.button.MaterialButton

/**
 * Onboarding activity shown to first-time users.
 * Provides a tutorial with feature highlights and tips.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var btnNext: MaterialButton
    private lateinit var btnSkip: MaterialButton
    private lateinit var indicatorContainer: LinearLayout

    private val onboardingPages = listOf(
        OnboardingPage(
            iconRes = R.drawable.ic_home,
            titleRes = R.string.onboarding_welcome_title,
            descriptionRes = R.string.onboarding_welcome_desc
        ),
        OnboardingPage(
            iconRes = R.drawable.ic_navigation,
            titleRes = R.string.onboarding_navigation_title,
            descriptionRes = R.string.onboarding_navigation_desc
        ),
        OnboardingPage(
            iconRes = R.drawable.ic_movie,
            titleRes = R.string.onboarding_browsing_title,
            descriptionRes = R.string.onboarding_browsing_desc
        ),
        OnboardingPage(
            iconRes = R.drawable.ic_person,
            titleRes = R.string.onboarding_profiles_title,
            descriptionRes = R.string.onboarding_profiles_desc
        ),
        OnboardingPage(
            iconRes = R.drawable.ic_search,
            titleRes = R.string.onboarding_search_title,
            descriptionRes = R.string.onboarding_search_desc
        ),
        OnboardingPage(
            iconRes = R.drawable.ic_watch_later,
            titleRes = R.string.onboarding_watchlist_title,
            descriptionRes = R.string.onboarding_watchlist_desc
        ),
        OnboardingPage(
            iconRes = R.drawable.ic_play,
            titleRes = R.string.onboarding_playback_title,
            descriptionRes = R.string.onboarding_playback_desc
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OrientationUtils.applyPreferredOrientation(this)
        
        // If onboarding already completed, skip to Login
        if (isOnboardingCompleted(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewPager)
        btnNext = findViewById(R.id.btnNext)
        btnSkip = findViewById(R.id.btnSkip)
        indicatorContainer = findViewById(R.id.indicatorContainer)

        setupViewPager()
        setupIndicators()
        setupButtons()
        if (ResponsiveUtils.isTV(this)) {
            btnNext.post { btnNext.requestFocus() }
        }

        // Apply enter animation
        AnimationHelper.applyFadeTransition(this)
    }

    private fun setupViewPager() {
        viewPager.adapter = OnboardingAdapter(onboardingPages)
        
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicators(position)
                updateButtons(position)
            }
        })
    }

    private fun setupIndicators() {
        indicatorContainer.removeAllViews()
        
        for (i in onboardingPages.indices) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(8),
                    dpToPx(8)
                ).apply {
                    marginStart = dpToPx(4)
                    marginEnd = dpToPx(4)
                }
                setBackgroundResource(
                    if (i == 0) R.drawable.indicator_dot_active 
                    else R.drawable.indicator_dot_inactive
                )
            }
            indicatorContainer.addView(dot)
        }
    }

    private fun updateIndicators(position: Int) {
        for (i in 0 until indicatorContainer.childCount) {
            val dot = indicatorContainer.getChildAt(i)
            dot.setBackgroundResource(
                if (i == position) R.drawable.indicator_dot_active 
                else R.drawable.indicator_dot_inactive
            )
        }
    }

    private fun setupButtons() {
        btnSkip.setOnClickListener { finishOnboarding() }
        
        btnNext.setOnClickListener {
            val currentPosition = viewPager.currentItem
            if (currentPosition < onboardingPages.size - 1) {
                viewPager.currentItem = currentPosition + 1
            } else {
                finishOnboarding()
            }
        }

        // Focus handling for TV remote
        btnNext.setOnFocusChangeListener { v, hasFocus ->
            val button = v as MaterialButton
            if (hasFocus) {
                button.alpha = 1f
                AnimationHelper.scaleUp(button, Constants.FOCUS_SCALE_MEDIUM)
            } else {
                button.alpha = 0.9f
                AnimationHelper.scaleDown(button)
            }
        }

        btnSkip.setOnFocusChangeListener { v, hasFocus ->
            val button = v as MaterialButton
            if (hasFocus) {
                button.alpha = 1f
                AnimationHelper.scaleUp(button, Constants.FOCUS_SCALE_MEDIUM)
            } else {
                button.alpha = 0.7f
                AnimationHelper.scaleDown(button)
            }
        }
    }

    private fun updateButtons(position: Int) {
        if (position == onboardingPages.size - 1) {
            btnNext.text = getString(R.string.onboarding_get_started)
            btnSkip.visibility = View.GONE
        } else {
            btnNext.text = getString(R.string.onboarding_next)
            btnSkip.visibility = View.VISIBLE
        }
    }

    private fun finishOnboarding() {
        // Mark onboarding as completed
        setOnboardingCompleted(this)
        
        // Navigate to login
        startActivity(Intent(this, LoginActivity::class.java))
        AnimationHelper.applyFadeTransition(this)
        finish()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    companion object {
        private const val PREF_ONBOARDING = "onboarding_prefs"
        private const val KEY_COMPLETED = "onboarding_completed"

        fun isOnboardingCompleted(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREF_ONBOARDING, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_COMPLETED, false)
        }

        fun setOnboardingCompleted(context: Context) {
            val prefs = context.getSharedPreferences(PREF_ONBOARDING, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_COMPLETED, true).apply()
        }

        /**
         * Reset onboarding for testing purposes.
         */
        fun resetOnboarding(context: Context) {
            val prefs = context.getSharedPreferences(PREF_ONBOARDING, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_COMPLETED).apply()
        }
    }
}

/**
 * Data class for onboarding page content.
 */
data class OnboardingPage(
    val iconRes: Int,
    val titleRes: Int,
    val descriptionRes: Int
)

/**
 * Adapter for onboarding ViewPager.
 */
class OnboardingAdapter(
    private val pages: List<OnboardingPage>
) : RecyclerView.Adapter<OnboardingAdapter.PageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_onboarding_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(pages[position])
    }

    override fun getItemCount(): Int = pages.size

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgIcon: ImageView = itemView.findViewById(R.id.imgOnboardingIcon)
        private val txtTitle: TextView = itemView.findViewById(R.id.txtOnboardingTitle)
        private val txtDescription: TextView = itemView.findViewById(R.id.txtOnboardingDescription)

        fun bind(page: OnboardingPage) {
            imgIcon.setImageResource(page.iconRes)
            txtTitle.setText(page.titleRes)
            txtDescription.setText(page.descriptionRes)

            // Accessibility: combine title and description for TalkBack
            itemView.contentDescription = itemView.context.getString(page.titleRes) + ". " + 
                itemView.context.getString(page.descriptionRes)
        }
    }
}
