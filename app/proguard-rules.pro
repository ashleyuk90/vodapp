# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Gson serialization annotations
-keepattributes Signature
-keepattributes *Annotation*

# Keep all model classes for JSON parsing
-keep class com.example.vod.ApiResponse { *; }
-keep class com.example.vod.ApiErrorResponse { *; }
-keep class com.example.vod.User { *; }
-keep class com.example.vod.VideoItem { *; }
-keep class com.example.vod.EpisodeItem { *; }
-keep class com.example.vod.NextEpisode { *; }
-keep class com.example.vod.ContentMarker { *; }
-keep class com.example.vod.LibraryResponse { *; }
-keep class com.example.vod.LibraryListResponse { *; }
-keep class com.example.vod.LibraryItem { *; }
-keep class com.example.vod.DetailsResponse { *; }
-keep class com.example.vod.PlayResponse { *; }
-keep class com.example.vod.ProgressResponse { *; }
-keep class com.example.vod.PlaybackStatusResponse { *; }
-keep class com.example.vod.WatchStatusResponse { *; }
-keep class com.example.vod.WatchListResponse { *; }
-keep class com.example.vod.DashboardResponse { *; }
-keep class com.example.vod.Profile { *; }
-keep class com.example.vod.ProfilesResponse { *; }
-keep class com.example.vod.ProfileSelectResponse { *; }
-keep class com.example.vod.ProfileAddResponse { *; }
-keep class com.example.vod.ProfileRemoveResponse { *; }
-keep class com.example.vod.SessionResponse { *; }
-keep class com.example.vod.SearchFilters { *; }

# Keep fields annotated with @SerializedName
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Retrofit
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep the ApiService interface
-keep interface com.example.vod.ApiService { *; }