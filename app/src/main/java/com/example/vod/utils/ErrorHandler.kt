package com.example.vod.utils

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.vod.R
import retrofit2.HttpException
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Configuration for error state UI display.
 */
data class ErrorStateConfig(
    @DrawableRes val iconRes: Int = R.drawable.ic_error,
    @StringRes val titleRes: Int = R.string.error_title,
    val message: String,
    val showRetry: Boolean = true,
    val showGoBack: Boolean = false
)

/**
 * Centralized error handler for consistent error messaging across the app.
 * Provides user-friendly error messages for different exception types.
 */
object ErrorHandler {

    /**
     * Show a simple error toast message.
     * Uses WeakReference to prevent activity leaks.
     */
    fun showError(context: Context, message: String) {
        val weakContext = WeakReference(context)
        weakContext.get()?.let { ctx ->
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Handle network-related exceptions and show appropriate error messages.
     * @param context Application or Activity context
     * @param e The exception that occurred
     * @param defaultMessage Optional custom message for unknown errors
     */
    fun handleNetworkError(context: Context, e: Exception, defaultMessage: String? = null) {
        val message = getErrorMessage(e, defaultMessage)
        showError(context, message)
    }

    /**
     * Get a user-friendly error message for the given exception.
     */
    fun getErrorMessage(e: Exception, defaultMessage: String? = null): String {
        return when (e) {
            is UnknownHostException -> "No internet connection"
            is SocketTimeoutException -> "Connection timed out"
            is IOException -> "Network error. Check your connection."
            is HttpException -> getHttpErrorMessage(e)
            else -> defaultMessage ?: "An unexpected error occurred"
        }
    }

    /**
     * Get appropriate message for HTTP error codes.
     */
    private fun getHttpErrorMessage(e: HttpException): String {
        return when (e.code()) {
            400 -> "Bad request"
            401 -> "Invalid credentials"
            403 -> "Access denied"
            404 -> "Not found"
            408 -> "Request timed out"
            429 -> "Too many requests. Try again later."
            in 500..599 -> "Server error. Try again later."
            else -> "Server error: ${e.code()}"
        }
    }
    
    /**
     * Get error state configuration based on exception type.
     * Use this to populate error state UI layouts.
     */
    fun getErrorStateConfig(e: Exception, context: Context): ErrorStateConfig {
        return when (e) {
            is UnknownHostException -> ErrorStateConfig(
                iconRes = R.drawable.ic_no_network,
                titleRes = R.string.error_title,
                message = context.getString(R.string.error_network),
                showRetry = true
            )
            is SocketTimeoutException -> ErrorStateConfig(
                iconRes = R.drawable.ic_no_network,
                titleRes = R.string.error_title,
                message = "Connection timed out. Please try again.",
                showRetry = true
            )
            is IOException -> ErrorStateConfig(
                iconRes = R.drawable.ic_no_network,
                titleRes = R.string.error_title,
                message = context.getString(R.string.error_network),
                showRetry = true
            )
            is HttpException -> {
                val message = when (e.code()) {
                    in 500..599 -> context.getString(R.string.error_server)
                    else -> getHttpErrorMessage(e)
                }
                ErrorStateConfig(
                    iconRes = R.drawable.ic_error,
                    titleRes = R.string.error_title,
                    message = message,
                    showRetry = true
                )
            }
            else -> ErrorStateConfig(
                iconRes = R.drawable.ic_error,
                titleRes = R.string.error_title,
                message = context.getString(R.string.error_generic),
                showRetry = true
            )
        }
    }
    
    /**
     * Configure an error state view with the given configuration.
     * @param errorView The root view of the error state layout
     * @param config The error state configuration
     * @param onRetry Callback when retry button is clicked
     * @param onGoBack Optional callback when go back button is clicked
     */
    fun configureErrorView(
        errorView: View,
        config: ErrorStateConfig,
        onRetry: (() -> Unit)? = null,
        onGoBack: (() -> Unit)? = null
    ) {
        errorView.findViewById<ImageView>(R.id.imgErrorIcon)?.setImageResource(config.iconRes)
        errorView.findViewById<TextView>(R.id.txtErrorTitle)?.setText(config.titleRes)
        errorView.findViewById<TextView>(R.id.txtErrorMessage)?.text = config.message
        
        val btnRetry = errorView.findViewById<Button>(R.id.btnRetry)
        btnRetry?.visibility = if (config.showRetry) View.VISIBLE else View.GONE
        btnRetry?.setOnClickListener { onRetry?.invoke() }
        
        val btnGoBack = errorView.findViewById<Button>(R.id.btnGoBack)
        btnGoBack?.visibility = if (config.showGoBack) View.VISIBLE else View.GONE
        btnGoBack?.setOnClickListener { onGoBack?.invoke() }
        
        // Request focus on retry button for TV navigation
        if (config.showRetry) {
            btnRetry?.requestFocus()
        }
    }
}
