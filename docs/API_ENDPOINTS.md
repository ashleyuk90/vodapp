# API Endpoints Reference

Complete documentation of all REST API endpoints in the VOD application.

## Base URL

- Development: `http://localhost:8000/api/`
- Production: `https://yourdomain.com/api/`

## Authentication

All endpoints except `/login` require authentication via PHP session cookie (`PHPSESSID`).

**Headers:**
```
Cookie: PHPSESSID=your_session_id
```

**Error Response (401 Unauthorized):**
```json
{
  "status": "error",
  "message": "Authentication required"
}
```

## Rate Limiting

- Per-action rate limits enforced
- Default: 60 requests per minute per action
- Exceeded limit returns HTTP 429

---

## Endpoints

### Authentication

#### POST /api/login

Authenticate user and create session.

**Request:**
```
POST /api/login
Content-Type: application/x-www-form-urlencoded

username=admin&password=password123
```

**Success Response (200 OK):**
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

**Error Response (401 Unauthorized):**
```json
{
  "status": "error",
  "message": "Invalid username or password"
}
```

---

### Watch List

#### POST /api/watch_list_add

Add a video to user's watch list.

**Request:**
```
POST /api/watch_list_add
Content-Type: application/x-www-form-urlencoded

video_id=123
```

**Success Response:**
```json
{
  "status": "success",
  "message": "Added to watch list"
}
```

**Error Response:**
```json
{
  "status": "error",
  "message": "Video ID required"
}
```

---

#### POST /api/watch_list_remove

Remove a video from user's watch list.

**Request:**
```
POST /api/watch_list_remove
Content-Type: application/x-www-form-urlencoded

video_id=123
```

**Success Response:**
```json
{
  "status": "success",
  "message": "Removed from watch list"
}
```

---

#### GET /api/watch_list

Get user's watch list or check if specific video is in watch list.

**Query Parameters:**
- `video_id` (optional): Check if specific video is in watch list
- `library_id` (optional): Filter by library
- `search` (optional): Search term
- `page` (optional): Page number (default: 1)
- `profile_id` (optional): Override active profile for watch list data

**Request (check specific video):**
```
GET /api/watch_list?video_id=123
```

**Success Response:**
```json
{
  "status": "success",
  "in_watch_list": true
}
```

**Request (get paginated list):**
```
GET /api/watch_list?page=1&search=matrix
```

**Success Response:**
```json
{
  "status": "success",
  "videos": [
    {
      "id": 123,
      "title": "The Matrix",
      "release_year": 1999,
      "poster_url": "https://...",
      "added_at": "2026-01-26 10:30:00"
    }
  ],
  "total": 1,
  "page": 1,
  "pages": 1
}
```

---

### Video Progress

#### POST /api/progress

Update video playback progress.

**Request:**
```
POST /api/progress
Content-Type: application/x-www-form-urlencoded

id=123&time=1800&paused=0
```

**Parameters:**
- `id`: Video ID (required)
- `time`: Playback position in seconds (required)
- `paused`: 1 if paused, 0 if playing (optional, default: 0)
- `buffer_seconds` (optional)
- `rebuffer_count` (optional)
- `rebuffer_seconds` (optional)
- `rebuffered` (optional, 1 if a rebuffer event occurred)
- `profile_id` (optional, overrides active profile)

**Success Response:**
```json
{
  "status": "saved"
}
```

---

### Dashboard

#### GET /api/dashboard

Get dashboard statistics and data.

**Success Response:**
```json
{
  "status": "success",
  "continue_watching": [],
  "recent_movies": [],
  "recent_shows": []
}
```

---

### Library

#### GET /api/library

Get videos in a library with pagination.

**Query Parameters:**
- `lib_id` (optional): Library ID
- `page` (optional): Page number (default: 1)
- `per_page` (optional): Results per page (default: 48)
- `q` (optional): Search term
- `profile_id` (optional): Profile override for resume times

**Request:**
```
GET /api/library?lib_id=1&page=1&q=matrix
```

**Success Response:**
```json
{
  "status": "success",
  "data": [],
  "pages": 1
}
```

---

### Video Details

#### GET /api/details

Get detailed information about a video.

**Query Parameters:**
- `id` (required): Video ID
- `profile_id` (optional): Profile override for resume time

**Notes:**
- `content_rating` is stored as a UK-mapped rating (U/PG/12/12A/15/18/R18) based on metadata when available.
- `intro_marker` / `credits_marker` are only returned for series episodes with markers detected. Otherwise `null`.
- **Intro markers**: Use `start_seconds` and `end_seconds` directly for the intro segment boundaries.
- **Credits markers**: Use `credits_duration_seconds` + `credits_end_offset_seconds` to compute the credits window:
  - `credits_start = video_duration_seconds - credits_end_offset_seconds - credits_duration_seconds`
  - `credits_end = video_duration_seconds - credits_end_offset_seconds`

**Request:**
```
GET /api/details?id=123
```

**Success Response:**
```json
{
  "status": "success",
  "video": {
    "id": 123,
    "title": "Pilot",
    "type": "episode",
    "series_title": "Example Show",
    "content_rating": "12",
    "season": 1,
    "episode": 1,
    "runtime": 42,
    "resume_time": 120,
    "intro_marker": {
      "type": "intro",
      "start_seconds": 42,
      "end_seconds": 98,
      "confidence": 0.87,
      "source": "auto",
      "library_id": 2,
      "updated_at": "2025-01-01 12:34:56"
    },
    "credits_marker": {
      "type": "credits",
      "credits_duration_seconds": 120,
      "credits_end_offset_seconds": 8,
      "confidence": 0.84,
      "source": "auto",
      "library_id": 2,
      "updated_at": "2025-01-01 12:34:56"
    }
  }
}
```

---

### Video Playback

#### GET /api/play

Get video playback information.

**Query Parameters:**
- `id` (required): Video ID

**Request:**
```
GET /api/play?id=123
```

**Success Response:**
```json
{
  "status": "success",
  "data": {
    "stream_url": "https://yourdomain.com/stream/123?token=...",
    "has_subtitles": true,
    "subtitle_url": "https://yourdomain.com/subtitle?file=subtitle_123_en.srt",
    "subtitle_language": "en",
    "next_episode": null
  }
}
```

---

### Libraries Management

#### GET /api/get_libraries

Get all libraries.

**Success Response:**
```json
{
  "status": "success",
  "data": [
    { "id": 0, "name": "All Content" },
    { "id": 1, "name": "Movies" }
  ]
}
```

---

#### GET /api/fetch_libraries

Admin-only library list with stats and scan status.

**Success Response:**
```json
{
  "status": "success",
  "libraries": [ ]
}
```

---

### Path Validation (Admin)

#### GET /api/validate_path

Check if a file system path exists and is accessible.

**Request:**
```
GET /api/validate_path?path=/media/movies
```

**Success Response:**
```json
{
  "valid": true
}
```

---

#### GET /api/list_directories

List directories at a given path.

**Request:**
```
GET /api/list_directories?path=/media
```

**Success Response:**
```json
{
  "subdirs": ["movies", "tv"]
}
```

---

### Profiles

#### GET /api/profiles

List user profiles and active profile.

**Success Response:**
```json
{
  "status": "success",
  "active_profile_id": 12,
  "profiles": [
    {
      "id": 12,
      "name": "Default",
      "max_content_rating": null,
      "max_rating": null,
      "auto_skip_intro": false,
      "auto_skip_credits": false,
      "autoplay_next": true,
      "has_pin": false
    }
  ]
}
```

**Notes:**
- `max_content_rating` is the preferred UK content rating limit for parental controls.
- `max_rating` is a legacy IMDb-based limit kept for backward compatibility.

#### POST /api/profiles_select

Set active profile.

**Request:**
```
profile_id=12
```

**Success Response:**
```json
{
  "status": "success",
  "active_profile_id": 12
}
```

---

### Library Info

#### GET /api/library_info

Get folders for a library.

**Query Parameters:**
- `id` (required): Library ID

**Success Response:**
```json
{
  "status": "success",
  "folders": ["path1", "path2"]
}
```

---

### Server Diagnostics (Admin)

#### GET /api/get_server_stats

Get server stats (admin only).

#### GET /api/diagnostics

Session/environment diagnostics (admin only).

## Error Responses

### Common Error Codes

**400 Bad Request:**
```json
{
  "status": "error",
  "message": "Missing required parameter: video_id"
}
```

**401 Unauthorized:**
```json
{
  "status": "error",
  "message": "Authentication required"
}
```

**403 Forbidden:**
```json
{
  "status": "error",
  "message": "Insufficient permissions"
}
```

**404 Not Found:**
```json
{
  "status": "error",
  "message": "Video not found"
}
```

**429 Too Many Requests:**
```json
{
  "status": "error",
  "message": "Rate limit exceeded"
}
```

**500 Internal Server Error:**
```json
{
  "status": "error",
  "message": "Database error"
}
```

---

## Usage Examples

### JavaScript (Fetch API)

```javascript
// Add to watch list
async function addToWatchList(videoId) {
  const formData = new FormData();
  formData.append('video_id', videoId);
  
  const response = await fetch('/api/watch_list_add', {
    method: 'POST',
    body: formData,
    credentials: 'include' // Include session cookie
  });
  
  const data = await response.json();
  
  if (data.status === 'success') {
    console.log('Added to watch list');
  }
}

// Get watch list
async function getWatchList() {
  const response = await fetch('/api/watch_list?page=1', {
    credentials: 'include'
  });
  
  const data = await response.json();
  console.log('Watch list:', data.videos);
}

// Update progress
async function updateProgress(videoId, position) {
  const formData = new FormData();
  formData.append('video_id', videoId);
  formData.append('position', position);
  formData.append('is_paused', 0);
  
  await fetch('/api/progress', {
    method: 'POST',
    body: formData,
    credentials: 'include'
  });
}
```

### PHP (cURL)

```php
// Login
$ch = curl_init('http://localhost:8000/api/login');
curl_setopt($ch, CURLOPT_POST, true);
curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode([
    'username' => 'admin',
    'password' => 'password123'
]));
curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
curl_setopt($ch, CURLOPT_HTTPHEADER, ['Content-Type: application/json']);
curl_setopt($ch, CURLOPT_COOKIEJAR, '/tmp/cookies.txt');

$response = curl_exec($ch);
$data = json_decode($response, true);

// Get watch list (with saved session)
$ch = curl_init('http://localhost:8000/api/watch_list');
curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
curl_setopt($ch, CURLOPT_COOKIEFILE, '/tmp/cookies.txt');

$response = curl_exec($ch);
$data = json_decode($response, true);
```

### Python (Requests)

```python
import requests

# Login
session = requests.Session()
response = session.post('http://localhost:8000/api/login', json={
    'username': 'admin',
    'password': 'password123'
})
data = response.json()

# Add to watch list
response = session.post('http://localhost:8000/api/watch_list_add', data={
    'video_id': 123
})

# Get watch list
response = session.get('http://localhost:8000/api/watch_list', params={
    'page': 1
})
watch_list = response.json()
```

---

## Notes

- All timestamps are in MySQL `DATETIME` format: `YYYY-MM-DD HH:MM:SS`
- All IDs are integers
- POST requests use `application/x-www-form-urlencoded` unless specified
- GET requests use query parameters
- Session cookies are required for authentication (set on login)
- Rate limits are per-action, not global
- Response `status` is always `success` or `error`
