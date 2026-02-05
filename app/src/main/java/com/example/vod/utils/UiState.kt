package com.example.vod.utils

/**
 * Sealed class representing UI states for async operations.
 * Provides type-safe state management for loading, success, and error states.
 * 
 * Usage:
 * ```kotlin
 * sealed class UiState<out T> {
 *     object Loading : UiState<Nothing>()
 *     data class Success<T>(val data: T) : UiState<T>()
 *     data class Error(val message: String) : UiState<Nothing>()
 * }
 * 
 * // In ViewModel or Activity:
 * private var _state = MutableStateFlow<UiState<List<VideoItem>>>(UiState.Loading)
 * 
 * // Handle states:
 * when (state) {
 *     is UiState.Loading -> showLoading()
 *     is UiState.Success -> displayData(state.data)
 *     is UiState.Error -> showError(state.message)
 * }
 * ```
 */
sealed class UiState<out T> {
    /**
     * Represents a loading state - show progress indicator.
     */
    data object Loading : UiState<Nothing>()
    
    /**
     * Represents a successful operation with data.
     * @param data The successfully loaded data
     */
    data class Success<T>(val data: T) : UiState<T>()
    
    /**
     * Represents an error state.
     * @param message User-friendly error message
     * @param exception Optional exception for logging/debugging
     */
    data class Error(
        val message: String,
        val exception: Exception? = null
    ) : UiState<Nothing>()
    
    /**
     * Represents an empty state (no data available).
     */
    data object Empty : UiState<Nothing>()
    
    /**
     * Check if this state is Loading.
     */
    val isLoading: Boolean get() = this is Loading
    
    /**
     * Check if this state is Success.
     */
    val isSuccess: Boolean get() = this is Success
    
    /**
     * Check if this state is Error.
     */
    val isError: Boolean get() = this is Error
    
    /**
     * Get data if Success, or null otherwise.
     */
    fun getOrNull(): T? = (this as? Success)?.data
    
    /**
     * Map the success data to a different type.
     */
    inline fun <R> map(transform: (T) -> R): UiState<R> = when (this) {
        is Loading -> Loading
        is Success -> Success(transform(data))
        is Error -> Error(message, exception)
        is Empty -> Empty
    }
}
