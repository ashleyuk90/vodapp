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
    @POST("api.php?action=login")
    suspend fun login(
        @Field("username") user: String,
        @Field("password") pass: String
    ): ApiResponse<User>

    @GET("api.php?action=library")
    suspend fun getLibrary(@Query("lib_id") libId: Int,@Query("page") page: Int): LibraryResponse

    @GET("api.php?action=get_libraries")
    suspend fun getLibraries(): LibraryListResponse
    @GET("api.php?action=play")
    suspend fun getStreamInfo(@Query("id") id: Int): ApiResponse<PlayResponse>
    @GET("api.php?action=details")
    suspend fun getDetails(@Query("id") id: Int): DetailsResponse
    @FormUrlEncoded
    @POST("api.php?action=progress")
    suspend fun syncProgress(
        @Field("id") id: Int,
        @Field("time") time: Long,
        @Field("paused") paused: Int
    ): ProgressResponse
    @GET("api.php?action=dashboard")
    suspend fun getDashboard(): DashboardResponse
    @GET("api.php?action=search")
    suspend fun search(@Query("query") query: String): LibraryResponse
    @FormUrlEncoded
    @POST("api.php?action=watch_list_add")
    suspend fun addToWatchList(@Field("video_id") videoId: Int): ApiResponse<Any>
    @FormUrlEncoded
    @POST("api.php?action=watch_list_remove")
    suspend fun removeFromWatchList(@Field("video_id") videoId: Int): ApiResponse<Any>
    @GET("api.php?action=watch_list")
    suspend fun getWatchList(@Query("page") page: Int = 1): WatchListResponse
    // Using @Query map to handle optional parameters like video_id, page, search
    @GET("api.php?action=watch_list")
    suspend fun getWatchListStatus(@Query("video_id") videoId: Int): WatchStatusResponse
}

// 2. The Client Builder
object NetworkClient {
    private const val BASE_URL = "http://77.74.196.120/vod/" // UPDATE THIS!

    // Global Cookie Manager to hold the PHP Session
    val cookieManager = CookieManager().apply {
        setCookiePolicy(CookiePolicy.ACCEPT_ALL)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(cookieManager)) // <--- THE MAGIC LINE
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .build()

    val api: ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}