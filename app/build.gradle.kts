plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.vod"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.vod"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        renderscriptTargetApi = 26
        renderscriptSupportModeEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.media3:media3-ui:1.2.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.2.0")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
}