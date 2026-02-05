# VOD App Architecture

This document describes the technical architecture and design patterns used in the VOD streaming application.

## Overview

The VOD app follows a traditional Android Activity-based architecture with coroutines for async operations. It uses a simple MVC-like pattern where Activities serve as both the view and controller.

```
┌─────────────────────────────────────────────────────────────┐
│                         UI Layer                            │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────────────┐ │
│  │ LoginActivity│  │MainActivity │  │DetailsActivity      │ │
│  └──────┬──────┘  └──────┬──────┘  └──────────┬───────────┘ │
│         │                │                     │             │
│  ┌──────┴─────────────────┴─────────────────────┴──────────┐ │
│  │                    Fragments                            │ │
│  │             (WatchLaterFragment)                        │ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      Data Layer                             │
│  ┌─────────────────┐     ┌──────────────────────────────┐   │
│  │  NetworkClient  │────▶│        ApiService            │   │
│  │   (Singleton)   │     │   (Retrofit Interface)       │   │
│  └─────────────────┘     └──────────────────────────────┘   │
│          │                                                   │
│          ▼                                                   │
│  ┌─────────────────────────────────────────────────────────┐│
│  │              Data Models (Models.kt)                    ││
│  │  VideoItem, EpisodeItem, User, ApiResponse, etc.        ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

## Application Flow

### 1. Authentication Flow

```
┌────────────┐    Check Prefs     ┌─────────────────────┐
│   Launch   │──────────────────▶│   Saved Creds?      │
└────────────┘                   └──────────┬──────────┘
                                            │
                              ┌─────────────┼─────────────┐
                              ▼                           ▼
                    ┌─────────────────┐         ┌─────────────────┐
                    │  Show Login UI  │         │   Auto-Login    │
                    └────────┬────────┘         └────────┬────────┘
                             │                           │
                             ▼                           ▼
                    ┌─────────────────┐         ┌─────────────────┐
                    │   User Input    │         │   API Login     │
                    └────────┬────────┘         └────────┬────────┘
                             │                           │
                             ▼                           │
                    ┌─────────────────┐                  │
                    │   API Login     │◀─────────────────┘
                    └────────┬────────┘
                             │
              ┌──────────────┴──────────────┐
              ▼                             ▼
     ┌─────────────────┐           ┌─────────────────┐
     │ Save to Prefs   │           │  Show Error     │
     │ Start Main      │           │  Clear Prefs    │
     └─────────────────┘           └─────────────────┘
```

### 2. Main Navigation Flow

```
┌─────────────────────────────────────────────────────────────┐
│                       MainActivity                          │
│  ┌──────────────┐    ┌───────────────────────────────────┐ │
│  │   Side Menu  │    │         Content Area              │ │
│  │              │    │  ┌─────────────────────────────┐  │ │
│  │ • Search     │    │  │      Dashboard View         │  │ │
│  │ • Home       │────│──│  ┌─────────────────────────┐│  │ │
│  │ • Watch Later│    │  │  │  Hero Section           ││  │ │
│  │ ─────────── │    │  │  │  Continue Watching      ││  │ │
│  │   LIBRARY   │    │  │  │  Recent Movies          ││  │ │
│  │ • Movies    │────│──│  │  Recent TV Shows        ││  │ │
│  │ • TV Shows  │    │  │  └─────────────────────────┘│  │ │
│  │ • 4K        │    │  └─────────────────────────────┘  │ │
│  └──────────────┘    │  ┌─────────────────────────────┐  │ │
│                      │  │      Library Grid View      │  │ │
│                      │  │  [A-Z Sidebar] [Video Grid] │  │ │
│                      │  └─────────────────────────────┘  │ │
│                      │  ┌─────────────────────────────┐  │ │
│                      │  │    Fragment Container       │  │ │
│                      │  │   (WatchLaterFragment)      │  │ │
│                      │  └─────────────────────────────┘  │ │
│                      └───────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 3. Video Playback Flow

```
┌─────────────┐   ┌─────────────┐   ┌─────────────────────────┐
│   Details   │──▶│   Player    │──▶│  Get Stream URL (API)   │
│   Activity  │   │   Activity  │   └───────────┬─────────────┘
└─────────────┘   └─────────────┘               │
                                                ▼
                                  ┌─────────────────────────────┐
                                  │    Setup ExoPlayer          │
                                  │  - Configure data source    │
                                  │  - Set cookies for auth     │
                                  │  - Configure subtitles      │
                                  └───────────┬─────────────────┘
                                              │
                                              ▼
                                  ┌─────────────────────────────┐
                                  │      Start Playback         │
                                  │  - Seek to resume position  │
                                  │  - Start progress sync      │
                                  └───────────┬─────────────────┘
                                              │
                          ┌───────────────────┼───────────────────┐
                          ▼                   ▼                   ▼
              ┌─────────────────┐ ┌───────────────────┐ ┌─────────────────┐
              │  Sync Progress  │ │ Next Episode Check│ │  User Pause     │
              │  Every 10 secs  │ │  (< 30s remaining)│ │                 │
              └─────────────────┘ └───────────────────┘ └─────────────────┘
```

## Component Details

### NetworkModule.kt

The network layer uses Retrofit with OkHttp and includes:

- **Cookie Management**: PHP session cookies are stored and sent with all requests via `JavaNetCookieJar`
- **Logging Interceptor**: Full request/response logging only in debug builds
- **Singleton Pattern**: Single `NetworkClient` instance shared app-wide
- **BuildConfig URL**: Base URL configured per build type
- **Timeouts**: 30-second connect/read/write timeouts for resilience

```kotlin
object NetworkClient {
    private val BASE_URL: String = BuildConfig.BASE_URL

    val cookieManager = CookieManager().apply {
        setCookiePolicy(CookiePolicy.ACCEPT_ALL)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(cookieManager))
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}
```

### Models.kt

Data classes for JSON parsing with Gson annotations:

| Model | Purpose |
|-------|---------|
| `ApiResponse<T>` | Generic wrapper for API responses |
| `VideoItem` | Movie/episode data with metadata |
| `EpisodeItem` | Individual episode within a series |
| `NextEpisode` | Data for next episode auto-play |
| `DashboardResponse` | Home screen content |
| `LibraryResponse` | Paginated library content |
| `PlayResponse` | Stream URL and subtitle info |

### LibraryAdapter

A multi-purpose RecyclerView adapter supporting:

- **Grid Mode**: Resource-driven span count for responsive browsing
- **Horizontal Mode**: Horizontal scrolling rows for dashboard
- **Loading Footer**: Loading indicator for pagination
- **DiffUtil**: Efficient list updates

### BlurTransformation

Coil image transformation using a StackBlur implementation for backdrop blur effects (RenderScript removed). Applied to the detail page background images.

### Responsive & Orientation

- `ResponsiveUtils` drives grid span counts and typography sizing via resource qualifiers.
- `OrientationUtils` enables auto-rotation on phones/tablets while keeping TV in landscape.
- Portrait-only layout overrides live under `res/layout-port/` (e.g. details view).

## TV Navigation Architecture

The app is designed for D-pad navigation on Android TV:

### Focus Management

```
┌─────────────────────────────────────────────────────────────┐
│                     Focus Flow                               │
│                                                              │
│   Side Menu ◀───DPAD_LEFT────── Grid/Content                │
│       │                              │                       │
│       │                              │                       │
│   DPAD_RIGHT ─────────────────▶  Grid/Content               │
│       │                              │                       │
│       ▼                              ▼                       │
│   Menu Item                     Video Card                   │
│   (wraps at bottom)            (wraps at row end)           │
└─────────────────────────────────────────────────────────────┘
```

### Key Handlers

- **DPAD_LEFT from first column**: Opens side menu
- **DPAD_RIGHT from menu**: Returns to content grid
- **BACK button**: Returns to previous state (menu → dashboard → exit)

## Data Flow

### API Request Pattern

All network calls follow this pattern:

```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    try {
        val response = NetworkClient.api.someEndpoint()
        withContext(Dispatchers.Main) {
            if (response.status == "success") {
                // Update UI
            }
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            // Show error
        }
    }
}
```

### Session Management

- Login credentials stored in SharedPreferences (`VOD_PREFS`)
- PHP session maintained via cookies in `NetworkClient.cookieManager`
- Session cookie injected into video stream requests

## Resource Organization

```
res/
├── layout/           # XML layouts for activities/fragments
├── drawable/         # Vector icons, gradients, selectors
├── color/            # Color state lists for focus states
├── values/           # Strings, colors, dimensions, themes
└── xml/              # Backup rules, data extraction rules
```

### Theme

The app uses Material Components with a dark theme:

- Primary Color: `#E50914` (Netflix red)
- Background: `#121212` (Dark gray)
- Text: `#FFFFFF` (White)

## Known Limitations

1. **No ViewModel/LiveData**: Direct UI updates from coroutines
2. **No Dependency Injection**: Manual singleton creation
3. **No Repository Pattern**: Activities call API directly
4. **No Local Caching**: All data fetched fresh from server
5. **RenderScript Deprecated**: BlurTransformation uses deprecated APIs
