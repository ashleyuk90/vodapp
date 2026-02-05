package com.example.vod

import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.net.CookieManager
import java.net.CookiePolicy

// 1. The API Interface
interface ApiService {
    @FormUrlEncoded
    @POST("api/login")
    suspend fun login(
        @Field("username") user: String,
        @Field("password") pass: String
    ): ApiResponse<User>

    @GET("api/library")
    suspend fun getLibrary(
        @Query("lib_id") libId: Int,
        @Query("page") page: Int,
        @Query("profile_id") profileId: Int? = null
    ): LibraryResponse

    @GET("api/get_libraries")
    suspend fun getLibraries(): LibraryListResponse

    @GET("api/play")
    suspend fun getStreamInfo(@Query("id") id: Int): ApiResponse<PlayResponse>

    @GET("api/details")
    suspend fun getDetails(
        @Query("id") id: Int,
        @Query("profile_id") profileId: Int? = null
    ): DetailsResponse

    @FormUrlEncoded
    @POST("api/progress")
    suspend fun syncProgress(
        @Field("id") id: Int,
        @Field("time") time: Long,
        @Field("paused") paused: Int,
        @Field("buffer_seconds") bufferSeconds: Int? = null,
        @Field("profile_id") profileId: Int? = null
    ): ProgressResponse

    @GET("api/dashboard")
    suspend fun getDashboard(
        @Query("profile_id") profileId: Int? = null
    ): DashboardResponse

    @GET("api/search")
    suspend fun search(
        @Query("query") query: String,
        @Query("profile_id") profileId: Int? = null,
        @Query("genre") genre: String? = null,
        @Query("year_min") yearMin: Int? = null,
        @Query("year_max") yearMax: Int? = null,
        @Query("min_rating") minRating: Float? = null,
        @Query("type") type: String? = null
    ): LibraryResponse

    @FormUrlEncoded
    @POST("api/watch_list_add")
    suspend fun addToWatchList(@Field("video_id") videoId: Int): ApiResponse<Any>

    @FormUrlEncoded
    @POST("api/watch_list_remove")
    suspend fun removeFromWatchList(@Field("video_id") videoId: Int): ApiResponse<Any>

    @GET("api/watch_list")
    suspend fun getWatchList(
        @Query("page") page: Int = 1,
        @Query("profile_id") profileId: Int? = null
    ): WatchListResponse

    // Using @Query map to handle optional parameters like video_id, page, search
    @GET("api/watch_list")
    suspend fun getWatchListStatus(@Query("video_id") videoId: Int): WatchStatusResponse

    // ===== Profile Endpoints =====

    /**
     * Get all profiles for the current user.
     * Returns list of profiles and the currently active profile ID.
     */
    @GET("api/profiles")
    suspend fun getProfiles(): ProfilesResponse

    /**
     * Select/activate a profile for the current session.
     * All subsequent playback history will be saved under this profile.
     */
    @FormUrlEncoded
    @POST("api/profiles_select")
    suspend fun selectProfile(
        @Field("profile_id") profileId: Int
    ): ProfileSelectResponse

    /**
     * Create a new profile for the current user.
     * New profile becomes active server-side on success.
     */
    @FormUrlEncoded
    @POST("api/profiles_add")
    suspend fun addProfile(
        @Field("name") name: String
    ): ProfileAddResponse

    /**
     * Delete one of the current user's profiles.
     */
    @FormUrlEncoded
    @POST("api/profiles_remove")
    suspend fun removeProfile(
        @Field("profile_id") profileId: Int
    ): ProfileRemoveResponse
}

// 2. The Client Builder
object NetworkClient {
    // URL loaded from BuildConfig - configure in build.gradle.kts per build type
    private val BASE_URL: String = BuildConfig.BASE_URL
    @Volatile
    private var csrfToken: String? = null

    // Global Cookie Manager to hold the PHP Session
    val cookieManager = CookieManager().apply {
        setCookiePolicy(CookiePolicy.ACCEPT_ALL)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(cookieManager))
        .addInterceptor { chain ->
            val request = chain.request()
            val endpoint = request.url.pathSegments.lastOrNull()
            val isPathLoginRequest = endpoint?.equals("login", ignoreCase = true) == true
            val isLegacyQueryLoginRequest =
                request.url.queryParameter("action")?.equals("login", ignoreCase = true) == true
            val isLoginRequest = isPathLoginRequest || isLegacyQueryLoginRequest
            val shouldAttachCsrf = request.method.equals("POST", ignoreCase = true) && !isLoginRequest
            val token = csrfToken?.takeIf { it.isNotBlank() }

            val requestWithSecurity = if (shouldAttachCsrf && token != null) {
                request.newBuilder()
                    .header("X-CSRF-Token", token)
                    .build()
            } else {
                request
            }

            chain.proceed(requestWithSecurity)
        }
        // Only log in debug builds to prevent credential exposure
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        })
        // Add timeouts for network resilience
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val api: ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)

    fun updateCsrfToken(token: String?) {
        csrfToken = token?.takeIf { it.isNotBlank() }
    }

    fun getCsrfToken(): String? = csrfToken
}
