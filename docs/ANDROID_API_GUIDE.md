# Android App API Integration Guide

Complete guide for integrating the VOD API with Android applications.

**Profiles:** See `docs/ANDROID_PROFILE_API.md` for profile selection and per-profile playback updates.

## Base Configuration

**Base URL:**
```
https://yourdomain.com/api/
```

**Required Headers:**
```
User-Agent: YourAppName/1.0 (Android; okhttp/4.9.0)
Content-Type: application/x-www-form-urlencoded
Cookie: PHPSESSID=session_id_here
```

**Note:** The server detects Android clients via `okhttp` or `ExoPlayer` in the User-Agent for optimized subtitle format delivery (SRT instead of WebVTT).

---

## Authentication Flow

### 1. Login

**Endpoint:** `POST /api/login`

**Request Body:**
```
username=admin&password=password123
```

**Android (OkHttp) Example:**
```kotlin
val client = OkHttpClient()
val formBody = FormBody.Builder()
    .add("username", "admin")
    .add("password", "password123")
    .build()

val request = Request.Builder()
    .url("https://yourdomain.com/api/login")
    .post(formBody)
    .build()

client.newCall(request).execute().use { response ->
    if (response.isSuccessful) {
        // Save session cookie
        val cookies = response.headers("Set-Cookie")
        val sessionId = cookies.firstOrNull { it.startsWith("PHPSESSID=") }
        // Store sessionId for subsequent requests
    }
}
```

**Response (Success):**
```json
{
  "status": "success",
  "message": "Login successful",
  "user": {
    "id": 1,
    "username": "admin",
    "role": "admin"
  }
}
```

**Response (Error):**
```json
{
  "status": "error",
  "message": "Invalid username or password"
}
```

### 2. Session Management

Save the `PHPSESSID` cookie from login response and include it in all subsequent requests:

```kotlin
// Using CookieJar with OkHttp
val cookieJar = object : CookieJar {
    private val cookieStore = mutableMapOf<String, List<Cookie>>()
    
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookieStore[url.host] = cookies
    }
    
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookieStore[url.host] ?: emptyList()
    }
}

val client = OkHttpClient.Builder()
    .cookieJar(cookieJar)
    .build()
```

---

## Watch Later API

### Add to Watch Later

**Endpoint:** `POST /api/watch_list_add`

**Request Body:**
```
video_id=123
```

**Android Example:**
```kotlin
val formBody = FormBody.Builder()
    .add("video_id", videoId.toString())
    .build()

val request = Request.Builder()
    .url("$baseUrl/watch_list_add")
    .post(formBody)
    .build()

client.newCall(request).execute().use { response ->
    val json = JSONObject(response.body?.string() ?: "")
    if (json.getString("status") == "success") {
        // Update UI - show as added to watch later
        showToast("Added to watch later")
    }
}
```

**Response:**
```json
{
  "status": "success",
  "message": "Added to watch list"
}
```

---

### Remove from Watch Later

**Endpoint:** `POST /api/watch_list_remove`

**Request Body:**
```
video_id=123
```

**Android Example:**
```kotlin
val formBody = FormBody.Builder()
    .add("video_id", videoId.toString())
    .build()

val request = Request.Builder()
    .url("$baseUrl/watch_list_remove")
    .post(formBody)
    .build()

client.newCall(request).execute().use { response ->
    val json = JSONObject(response.body?.string() ?: "")
    if (json.getString("status") == "success") {
        showToast("Removed from watch later")
    }
}
```

**Response:**
```json
{
  "status": "success",
  "message": "Removed from watch list"
}
```

---

### Check if Video in Watch Later

**Endpoint:** `GET /api/watch_list?video_id=123`

**Android Example:**
```kotlin
val url = "$baseUrl/watch_list?video_id=$videoId"
val request = Request.Builder()
    .url(url)
    .get()
    .build()

client.newCall(request).execute().use { response ->
    val json = JSONObject(response.body?.string() ?: "")
    val inWatchList = json.getBoolean("in_watch_list")
    // Update UI - show button state
    updateWatchLaterButton(inWatchList)
}
```

**Response:**
```json
{
  "status": "success",
  "in_watch_list": true
}
```

---

### Get Watch Later List (Paginated)

**Endpoint:** `GET /api/watch_list?page=1&search=matrix`

**Query Parameters:**
- `page` (optional): Page number (default: 1)
- `search` (optional): Search term
- `library_id` (optional): Filter by library

**Android Example:**
```kotlin
val url = HttpUrl.Builder()
    .scheme("https")
    .host("yourdomain.com")
    .addPathSegments("api/watch_list")
    .addQueryParameter("page", page.toString())
    .addQueryParameter("search", searchQuery)
    .build()

val request = Request.Builder()
    .url(url)
    .get()
    .build()

client.newCall(request).execute().use { response ->
    val json = JSONObject(response.body?.string() ?: "")
    val videos = json.getJSONArray("videos")
    val total = json.getInt("total")
    
    // Parse videos array
    val videoList = mutableListOf<Video>()
    for (i in 0 until videos.length()) {
        val video = videos.getJSONObject(i)
        videoList.add(Video(
            id = video.getInt("id"),
            title = video.getString("title"),
            posterUrl = video.getString("poster_url"),
            releaseYear = video.getInt("release_year")
        ))
    }
    
    // Update RecyclerView
    adapter.submitList(videoList)
}
```

**Response:**
```json
{
  "status": "success",
  "videos": [
    {
      "id": 123,
      "title": "The Matrix",
      "release_year": 1999,
      "poster_url": "https://...",
      "added_at": "2026-01-26 10:30:00",
      "type": "movie",
      "imdb_rating": "8.7",
      "runtime_minutes": 136
    }
  ],
  "total": 1,
  "page": 1,
  "pages": 1
}
```

---

## Complete Android Example (Kotlin)

### Watch Later Manager Class

```kotlin
class WatchLaterManager(private val baseUrl: String) {
    private val client = OkHttpClient.Builder()
        .cookieJar(PersistentCookieJar())
        .build()
    
    private val json = MediaType.get("application/json; charset=utf-8")
    
    suspend fun addToWatchLater(videoId: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("video_id", videoId.toString())
                .build()
            
            val request = Request.Builder()
                .url("$baseUrl/watch_list_add")
                .post(formBody)
                .build()
            
            client.newCall(request).execute().use { response ->
                val jsonResponse = JSONObject(response.body?.string() ?: "")
                if (jsonResponse.getString("status") == "success") {
                    Result.success(jsonResponse.getString("message"))
                } else {
                    Result.failure(Exception(jsonResponse.getString("message")))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun removeFromWatchLater(videoId: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("video_id", videoId.toString())
                .build()
            
            val request = Request.Builder()
                .url("$baseUrl/watch_list_remove")
                .post(formBody)
                .build()
            
            client.newCall(request).execute().use { response ->
                val jsonResponse = JSONObject(response.body?.string() ?: "")
                if (jsonResponse.getString("status") == "success") {
                    Result.success(jsonResponse.getString("message"))
                } else {
                    Result.failure(Exception(jsonResponse.getString("message")))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun isInWatchLater(videoId: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/watch_list?video_id=$videoId"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                val jsonResponse = JSONObject(response.body?.string() ?: "")
                Result.success(jsonResponse.getBoolean("in_watch_list"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getWatchLaterList(page: Int = 1, search: String = ""): Result<WatchLaterResponse> = 
        withContext(Dispatchers.IO) {
            try {
                val urlBuilder = HttpUrl.parse("$baseUrl/watch_list")!!.newBuilder()
                    .addQueryParameter("page", page.toString())
                
                if (search.isNotEmpty()) {
                    urlBuilder.addQueryParameter("search", search)
                }
                
                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .get()
                    .build()
                
                client.newCall(request).execute().use { response ->
                    val jsonResponse = JSONObject(response.body?.string() ?: "")
                    val videos = mutableListOf<Video>()
                    val videosArray = jsonResponse.getJSONArray("videos")
                    
                    for (i in 0 until videosArray.length()) {
                        val video = videosArray.getJSONObject(i)
                        videos.add(Video(
                            id = video.getInt("id"),
                            title = video.getString("title"),
                            posterUrl = video.optString("poster_url"),
                            releaseYear = video.optInt("release_year"),
                            type = video.optString("type", "movie"),
                            imdbRating = video.optString("imdb_rating"),
                            runtimeMinutes = video.optInt("runtime_minutes")
                        ))
                    }
                    
                    Result.success(WatchLaterResponse(
                        videos = videos,
                        total = jsonResponse.getInt("total"),
                        page = jsonResponse.getInt("page"),
                        pages = jsonResponse.getInt("pages")
                    ))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

data class WatchLaterResponse(
    val videos: List<Video>,
    val total: Int,
    val page: Int,
    val pages: Int
)

data class Video(
    val id: Int,
    val title: String,
    val posterUrl: String,
    val releaseYear: Int,
    val type: String,
    val imdbRating: String,
    val runtimeMinutes: Int
)
```

### Usage in Activity/Fragment

```kotlin
class VideoDetailActivity : AppCompatActivity() {
    private val watchLaterManager = WatchLaterManager("https://yourdomain.com/api")
    private var isInWatchLater = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_detail)
        
        val videoId = intent.getIntExtra("video_id", 0)
        
        // Check initial state
        lifecycleScope.launch {
            watchLaterManager.isInWatchLater(videoId).onSuccess { inList ->
                isInWatchLater = inList
                updateWatchLaterButton()
            }
        }
        
        // Toggle watch later
        btnWatchLater.setOnClickListener {
            toggleWatchLater(videoId)
        }
    }
    
    private fun toggleWatchLater(videoId: Int) {
        lifecycleScope.launch {
            if (isInWatchLater) {
                watchLaterManager.removeFromWatchLater(videoId).onSuccess {
                    isInWatchLater = false
                    updateWatchLaterButton()
                    Toast.makeText(this@VideoDetailActivity, "Removed from watch later", Toast.LENGTH_SHORT).show()
                }
            } else {
                watchLaterManager.addToWatchLater(videoId).onSuccess {
                    isInWatchLater = true
                    updateWatchLaterButton()
                    Toast.makeText(this@VideoDetailActivity, "Added to watch later", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun updateWatchLaterButton() {
        if (isInWatchLater) {
            btnWatchLater.text = "In Watch List"
            btnWatchLater.setBackgroundColor(Color.parseColor("#28a745"))
        } else {
            btnWatchLater.text = "Watch Later"
            btnWatchLater.setBackgroundColor(Color.parseColor("#6c757d"))
        }
    }
}
```

### Watch Later List Activity

```kotlin
class WatchLaterActivity : AppCompatActivity() {
    private val watchLaterManager = WatchLaterManager("https://yourdomain.com/api")
    private lateinit var adapter: VideoAdapter
    private var currentPage = 1
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watch_later)
        
        setupRecyclerView()
        loadWatchLaterList()
    }
    
    private fun setupRecyclerView() {
        adapter = VideoAdapter { video ->
            // Open video detail
            val intent = Intent(this, VideoDetailActivity::class.java)
            intent.putExtra("video_id", video.id)
            startActivity(intent)
        }
        
        recyclerView.adapter = adapter
        recyclerView.layoutManager = GridLayoutManager(this, 3)
    }
    
    private fun loadWatchLaterList(page: Int = 1) {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            
            watchLaterManager.getWatchLaterList(page).onSuccess { response ->
                adapter.submitList(response.videos)
                currentPage = page
                
                // Update pagination UI
                tvPageInfo.text = "Page ${response.page} of ${response.pages}"
                btnPrevPage.isEnabled = page > 1
                btnNextPage.isEnabled = page < response.pages
            }.onFailure { error ->
                Toast.makeText(this@WatchLaterActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
            
            progressBar.visibility = View.GONE
        }
    }
}
```

---

## Other Essential Endpoints for Android

### Get Video Details

**Endpoint:** `GET /api/details?id=123`

```kotlin
suspend fun getVideoDetails(videoId: Int): Result<VideoDetail> = withContext(Dispatchers.IO) {
    try {
        val request = Request.Builder()
            .url("$baseUrl/details?id=$videoId")
            .get()
            .build()
        
        client.newCall(request).execute().use { response ->
            val json = JSONObject(response.body?.string() ?: "")
            val video = json.getJSONObject("video")
            val introMarker = video.optJSONObject("intro_marker")
            val creditsMarker = video.optJSONObject("credits_marker")
            val creditsDuration = creditsMarker?.optInt("credits_duration_seconds")
            val creditsEndOffset = creditsMarker?.optInt("credits_end_offset_seconds")
            Result.success(VideoDetail(
                id = video.getInt("id"),
                title = video.getString("title"),
                plot = video.optString("plot"),
                posterUrl = video.optString("poster_url"),
                fullPath = video.getString("full_path"),
                introStart = introMarker?.optInt("start_seconds"),
                introEnd = introMarker?.optInt("end_seconds"),
                creditsStart = creditsMarker?.optInt("start_seconds"),
                creditsEnd = creditsMarker?.optInt("end_seconds"),
                creditsDurationSeconds = creditsDuration,
                creditsEndOffsetSeconds = creditsEndOffset
                // ... other fields
            ))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

**Notes:**
- `intro_marker` / `credits_marker` are optional. They are only present for series episodes when markers exist.
- `content_rating` may be returned (UK-mapped rating such as U/PG/12/12A/15/18/R18) and can be displayed in the details metadata pills.
- For **intro markers**: use `start_seconds` and `end_seconds` directly.
- For **credits markers**: use `credits_duration_seconds` + `credits_end_offset_seconds` to compute:
  - `creditsStart = videoDuration - creditsEndOffsetSeconds - creditsDurationSeconds`
  - `creditsEnd = videoDuration - creditsEndOffsetSeconds`
- The app's `ContentMarker` model includes a helper method `getCreditsStartSeconds(videoDurationSeconds)` that performs this calculation.

### Update Progress

**Endpoint:** `POST /api/progress`

```kotlin
suspend fun updateProgress(videoId: Int, position: Int, isPaused: Boolean = false): Result<Unit> = 
    withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("video_id", videoId.toString())
                .add("position", position.toString())
                .add("is_paused", if (isPaused) "1" else "0")
                .build()
            
            val request = Request.Builder()
                .url("$baseUrl/progress")
                .post(formBody)
                .build()
            
            client.newCall(request).execute().use { response ->
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
```

### Get Library Videos

**Endpoint:** `GET /api/library?library_id=1&page=1`

```kotlin
suspend fun getLibraryVideos(libraryId: Int, page: Int = 1): Result<LibraryResponse> = 
    withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/library?library_id=$libraryId&page=$page"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            // Parse response similar to watch list
            // ...
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
```

---

## Video Streaming

The server automatically detects Android clients and optimizes subtitle format (SRT instead of WebVTT).

**Stream URL:**
```
https://yourdomain.com/stream/{video_id}
```

**ExoPlayer Integration:**
```kotlin
val player = ExoPlayer.Builder(context).build()
val mediaItem = MediaItem.Builder()
    .setUri("https://yourdomain.com/stream/$videoId")
    .setSubtitleConfigurations(
        listOf(
            MediaItem.SubtitleConfiguration.Builder(Uri.parse("https://yourdomain.com/subtitle?id=$videoId"))
                .setMimeType(MimeTypes.APPLICATION_SUBRIP) // SRT format
                .setLanguage("en")
                .build()
        )
    )
    .build()

player.setMediaItem(mediaItem)
player.prepare()
player.play()
```

---

## Error Handling

All API responses follow this structure:

**Success:**
```json
{
  "status": "success",
  "data": { /* response data */ }
}
```

**Error:**
```json
{
  "status": "error",
  "message": "Human-readable error message"
}
```

**Common HTTP Status Codes:**
- `200 OK` - Success
- `400 Bad Request` - Missing/invalid parameters
- `401 Unauthorized` - Not authenticated
- `403 Forbidden` - Insufficient permissions
- `404 Not Found` - Resource not found
- `429 Too Many Requests` - Rate limit exceeded
- `500 Internal Server Error` - Server error

---

## Testing

Use Postman or curl to test endpoints before implementing in Android:

```bash
# Login
curl -X POST http://localhost:8000/api/login \
  -d "username=admin&password=password123" \
  -c cookies.txt

# Add to watch later
curl -X POST http://localhost:8000/api/watch_list_add \
  -d "video_id=123" \
  -b cookies.txt

# Check watch later status
curl http://localhost:8000/api/watch_list?video_id=123 \
  -b cookies.txt

# Get watch later list
curl http://localhost:8000/api/watch_list?page=1 \
  -b cookies.txt
```

---

## Security Notes

1. **Always use HTTPS** in production
2. **Store session cookies securely** (use EncryptedSharedPreferences)
3. **Never log sensitive data** (passwords, session IDs)
4. **Implement certificate pinning** for API calls
5. **Handle session expiration** gracefully (redirect to login)
6. **Validate all user input** before sending to API
7. **Use ProGuard/R8** to obfuscate API calls

---

## Rate Limiting

- Default: 60 requests per minute per endpoint
- Exceeded limit returns HTTP 429
- Implement exponential backoff in Android app

```kotlin
private suspend fun <T> retryWithBackoff(
    times: Int = 3,
    initialDelay: Long = 100,
    maxDelay: Long = 1000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Exception) {
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
    }
    return block() // last attempt
}
```

---

## Complete API Endpoint List

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/login` | POST | Authenticate user |
| `/api/watch_list_add` | POST | Add to watch later |
| `/api/watch_list_remove` | POST | Remove from watch later |
| `/api/watch_list` | GET | Get watch later list / check status |
| `/api/progress` | POST | Update playback progress |
| `/api/details` | GET | Get video details |
| `/api/library` | GET | Get library videos |
| `/api/get_libraries` | GET | Get all libraries |
| `/api/dashboard` | GET | Get dashboard stats |
| `/stream/{id}` | GET | Stream video file |
| `/subtitle?id={id}` | GET | Get subtitle file (SRT for Android) |

---

## Support

For issues or questions:
1. Check server logs table for API errors
2. Verify session cookie is being sent
3. Test endpoints with curl first
4. Check [API_ENDPOINTS.md](API_ENDPOINTS.md) for detailed docs
