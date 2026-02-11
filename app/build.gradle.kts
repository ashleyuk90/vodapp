plugins {
    alias(libs.plugins.android.application)
}

fun envString(name: String, defaultValue: String): String {
    return System.getenv(name)?.takeIf { it.isNotBlank() } ?: defaultValue
}

fun toBuildConfigString(value: String): String {
    val escaped = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return "\"$escaped\""
}

android {
    namespace = "com.example.vod"
    compileSdk {
        version = release(36)
    }

    // Enable BuildConfig generation
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        val updateApkBaseUrl = envString(
            "VOD_UPDATE_APK_BASE_URL",
            "https://sini3.net:7443/vod/android/"
        )
        val updateFeedUrl = envString(
            "VOD_UPDATE_FEED_URL",
            "https://sini3.net:7443/vod/android/update.xml"
        )
        val updateChannel = envString("VOD_UPDATE_CHANNEL", "stable")
        val updateCheckIntervalHours = envString(
            "VOD_UPDATE_CHECK_INTERVAL_HOURS",
            "24"
        ).toIntOrNull()?.coerceAtLeast(1) ?: 24

        applicationId = "com.example.vod"
        minSdk = 24
        targetSdk = 34
        versionCode = 20200
        versionName = "2.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "UPDATE_APK_BASE_URL",
            toBuildConfigString(updateApkBaseUrl)
        )
        buildConfigField(
            "String",
            "UPDATE_FEED_URL",
            toBuildConfigString(updateFeedUrl)
        )
        buildConfigField(
            "String",
            "UPDATE_CHANNEL",
            toBuildConfigString(updateChannel)
        )
        buildConfigField(
            "int",
            "UPDATE_CHECK_INTERVAL_HOURS",
            updateCheckIntervalHours.toString()
        )
    }

    buildTypes {
        debug {
            // Development server URL
            buildConfigField("String", "BASE_URL", "\"https://sini3.net:7443/vod/\"")
        }
        release {
            // Production server URL
            buildConfigField("String", "BASE_URL", "\"https://sini3.net:7443/vod/\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // Networking (Retrofit + Gson)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.0")
    implementation("com.squareup.okhttp3:okhttp-urlconnection:4.9.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // Image Loading (Coil)
    implementation("io.coil-kt:coil:2.5.0")
    // Video Player (ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    // Security - Encrypted SharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // Activity KTX for OnBackPressedDispatcher
    implementation("androidx.activity:activity-ktx:1.8.0")
}
