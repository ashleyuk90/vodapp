package com.example.vod.utils

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.drawable.AnimationDrawable
import android.os.Build
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.Interpolator
import com.example.vod.R

/**
 * Utility class for animation helpers throughout the app.
 * Provides standard activity transitions and view animations.
 */
object AnimationHelper {

    private fun animationsEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ValueAnimator.areAnimatorsEnabled()
        } else {
            // API 24/25 don't expose the global animator-enabled toggle.
            true
        }
    }

    private fun resolveDuration(duration: Long): Long = if (animationsEnabled()) duration else 0L

    private fun linearOutSlowIn(view: View): Interpolator =
        AnimationUtils.loadInterpolator(view.context, android.R.interpolator.linear_out_slow_in)

    private fun fastOutLinearIn(view: View): Interpolator =
        AnimationUtils.loadInterpolator(view.context, android.R.interpolator.fast_out_linear_in)

    private fun fastOutSlowIn(view: View): Interpolator =
        AnimationUtils.loadInterpolator(view.context, android.R.interpolator.fast_out_slow_in)

    /**
     * Apply slide-in-right transition for opening a new activity.
     * Call this after startActivity().
     */
    @Suppress("DEPRECATION")
    fun applyOpenTransition(activity: Activity) {
        if (!animationsEnabled()) {
            activity.overridePendingTransition(0, 0)
            return
        }
        activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    /**
     * Apply slide-out-right transition for closing an activity.
     * Call this after finish().
     */
    @Suppress("DEPRECATION")
    fun applyCloseTransition(activity: Activity) {
        if (!animationsEnabled()) {
            activity.overridePendingTransition(0, 0)
            return
        }
        activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    /**
     * Apply fade transition for activity changes.
     */
    @Suppress("DEPRECATION")
    fun applyFadeTransition(activity: Activity) {
        if (!animationsEnabled()) {
            activity.overridePendingTransition(0, 0)
            return
        }
        activity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    /**
     * Animate view appearing with fade in.
     */
    fun fadeIn(view: View, duration: Long = Constants.ANIMATION_DURATION_MS) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        val resolvedDuration = resolveDuration(duration)
        if (resolvedDuration == 0L) {
            view.alpha = 1f
            return
        }
        view.animate()
            .alpha(1f)
            .setDuration(resolvedDuration)
            .setInterpolator(fastOutSlowIn(view))
            .start()
    }

    /**
     * Animate view disappearing with fade out.
     */
    fun fadeOut(view: View, duration: Long = Constants.ANIMATION_DURATION_MS, gone: Boolean = false) {
        val resolvedDuration = resolveDuration(duration)
        if (resolvedDuration == 0L) {
            view.alpha = 0f
            view.visibility = if (gone) View.GONE else View.INVISIBLE
            return
        }
        view.animate()
            .alpha(0f)
            .setDuration(resolvedDuration)
            .setInterpolator(fastOutLinearIn(view))
            .withEndAction {
                view.visibility = if (gone) View.GONE else View.INVISIBLE
            }
            .start()
    }

    /**
     * Apply scale-up animation on focus.
     */
    fun scaleUp(view: View, scale: Float = Constants.FOCUS_SCALE_MEDIUM, duration: Long = Constants.FOCUS_ANIMATION_DURATION_MS) {
        val resolvedDuration = resolveDuration(duration)
        if (resolvedDuration == 0L) {
            view.scaleX = scale
            view.scaleY = scale
            return
        }
        view.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(resolvedDuration)
            .setInterpolator(linearOutSlowIn(view))
            .start()
    }

    /**
     * Apply scale-down animation on unfocus.
     */
    fun scaleDown(view: View, duration: Long = Constants.FOCUS_ANIMATION_DURATION_MS) {
        val resolvedDuration = resolveDuration(duration)
        if (resolvedDuration == 0L) {
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(resolvedDuration)
            .setInterpolator(fastOutLinearIn(view))
            .start()
    }

    /**
     * Start shimmer animation on a skeleton view.
     */
    fun startShimmer(view: View) {
        if (!animationsEnabled()) return
        val background = view.background
        if (background is AnimationDrawable) {
            background.start()
        }
    }

    /**
     * Stop shimmer animation on a skeleton view.
     */
    fun stopShimmer(view: View) {
        val background = view.background
        if (background is AnimationDrawable) {
            background.stop()
        }
    }

    /**
     * Apply press animation effect (scale down then back).
     */
    fun applyPressEffect(view: View) {
        val resolvedDuration = resolveDuration(Constants.PRESS_ANIMATION_DURATION_MS)
        if (resolvedDuration == 0L) return
        view.animate()
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(resolvedDuration)
            .setInterpolator(fastOutSlowIn(view))
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(resolvedDuration)
                    .setInterpolator(fastOutSlowIn(view))
                    .start()
            }
            .start()
    }

    /**
     * Run an action after a short press animation when animations are enabled.
     */
    fun runWithPressEffect(view: View, action: () -> Unit) {
        if (!animationsEnabled()) {
            action()
            return
        }
        applyPressEffect(view)
        view.postDelayed(action, Constants.PRESS_ANIMATION_DURATION_MS)
    }

    /**
     * Slide view in from bottom.
     */
    fun slideInFromBottom(view: View, duration: Long = Constants.ANIMATION_DURATION_MS) {
        view.translationY = view.height.toFloat()
        view.visibility = View.VISIBLE
        val resolvedDuration = resolveDuration(duration)
        if (resolvedDuration == 0L) {
            view.translationY = 0f
            return
        }
        view.animate()
            .translationY(0f)
            .setDuration(resolvedDuration)
            .setInterpolator(linearOutSlowIn(view))
            .start()
    }

    /**
     * Slide view out to bottom.
     */
    fun slideOutToBottom(view: View, duration: Long = Constants.ANIMATION_DURATION_MS) {
        val resolvedDuration = resolveDuration(duration)
        if (resolvedDuration == 0L) {
            view.translationY = view.height.toFloat()
            view.visibility = View.GONE
            return
        }
        view.animate()
            .translationY(view.height.toFloat())
            .setDuration(resolvedDuration)
            .setInterpolator(fastOutLinearIn(view))
            .withEndAction {
                view.visibility = View.GONE
            }
            .start()
    }
}
