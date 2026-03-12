# Android API Integration Guide

Practical guide for integrating Android clients with the current VOD API.

For the full endpoint catalog, use `docs/API_ENDPOINTS.md`.

## Base Configuration

- Base API path: `/api/{action}`
- Typical production base: `https://your-host/vod/api/`
- Auth model: PHP session cookie (`PHPSESSID`)

Recommended client setup:
- Persistent cookie jar (required)
- Form-encoded POST bodies (`application/x-www-form-urlencoded`)
- `X-CSRF-Token` header for mutation endpoints
- Security headers are returned on all responses

## Authentication Flow

### 1. Login

`POST /api/login`

Body:
- `username`
- `password`
- `app_version_name` (optional but recommended for telemetry)
- `app_version_code` (optional but recommended for telemetry)

Success includes:
- `user` (includes `update_channel`: `stable` or `beta`)
- `csrf_token`
- optional `account_expiry`
- `update_feed_url` — use this as the feed URL for update checks instead of the hardcoded build-time value. If missing (older server), fall back to build-time `VOD_UPDATE_FEED_URL`.

Store:
- session cookie(s)
- CSRF token value
- `update_feed_url` (for app update checks)

### 2. Session Usage

Send cookies on every authenticated request.

If using OkHttp, configure a cookie jar and do not clear cookies between app launches unless explicitly logging out.

Android client metadata headers (recommended on every API call):
- `X-Client-Platform: android`
- `X-App-Package`
- `X-App-Version-Name`
- `X-App-Version-Code`

These values are used for admin telemetry:
- shown on `Admin -> Live` session cards
- aggregated in `Admin -> Usage Analytics` (users by app version, last 24h)

### 3. Session Info

`GET /api/session`

Returns current session state including:
- `user` object (includes `update_channel`)
- `active_profile`
- `csrf_token` (refreshed)
- `login_time`
- `client_platform`
- `app_version_name`
- `update_feed_url` — same as login response; use to refresh the update feed URL on app resume

Use this to restore session state after app restart.

### 4. Logout

`POST /api/logout`

Terminates the server session. Clear local cookies and auth state after calling.

### 5. CSRF for Mutations

For these POST endpoints, include CSRF token:
- `/api/progress`
- `/api/watch_list_add`
- `/api/watch_list_remove`
- `/api/profiles_select`
- `/api/profiles_add`
- `/api/profiles_remove`
- `/api/verify_pin`
- `/api/logout`
- `/api/series_edit` (admin only)
- `/api/series_rescan` (admin only)

Preferred header:
- `X-CSRF-Token: <token>`

Fallback form field:
- `csrf_token=<token>`

**Important:** Legacy Android mutation fallback exists for clients that do not send Origin/Referer and use Android-like User-Agent strings (`okhttp`/`ExoPlayer`). Always send CSRF tokens for best security.

## Profiles API

### List Profiles
`GET /api/profiles`

Returns `active_profile_id` and profile preferences (`auto_skip_intro`, `auto_skip_credits`, `autoplay_next`, PIN/rating limits).

### Select Active Profile
`POST /api/profiles_select`
- `profile_id`

### Add Profile
`POST /api/profiles_add`
- required: `name`
- optional: `pin`, `max_content_rating`, `max_rating`, `auto_skip_intro`, `auto_skip_credits`, `autoplay_next`

Rule:
- If `max_content_rating` is set, `pin` is required.

### Remove Profile
`POST /api/profiles_remove`
- `profile_id`

Rules:
- Last profile cannot be deleted.
- Profile must belong to logged-in user.

### PIN Verification
`POST /api/verify_pin`

Body:
- `profile_id` (required)
- `pin` (required, max 10 characters)

Success response:
- `status: success`
- `verified_until` (Unix timestamp)

Rate limited to 5 attempts per 60 seconds. Use this when accessing PIN-protected content.

## Series Navigation (New)

The server now manages series as first-class entities. Library, dashboard, and search responses include new fields to support series-based navigation alongside backward-compatible video-based navigation.

### New Fields in Existing Endpoints

**`GET /api/library`**, **`GET /api/search`**, **`GET /api/dashboard`** responses now include:

| Field | Type | Description |
|---|---|---|
| `card_type` | `"series"` or `"video"` | Discriminator for navigation. Old clients can ignore this field. |
| `series_id` | `int` or `null` | Series table ID. Present when `card_type === "series"`. |
| `content_rating` | `string` or `null` | Content rating (e.g. `"TV-MA"`, `"PG-13"`). |

**Dashboard `continue_watching`** items for episodes now include:
| Field | Type | Description |
|---|---|---|
| `card_type` | `"series"` | Always `"series"` for grouped episode entries. |
| `series_id` | `int` | Series ID for navigation. |
| `resume_episode` | `object` | `{ id, title, season, episode }` — the most recently watched episode. |
| `total_episodes` | `int` | Total episode count in the series. |

**Dashboard `recent_shows`** items now come from the series table directly (one row per series) and include `card_type: "series"` and `series_id`.

### Migration Strategy

1. Check for `card_type` field presence in library/dashboard/search responses.
2. When `card_type === "series"` and `series_id` is present:
   - Use `series_id` to call `GET /api/series_details?id={series_id}` for the detail view.
3. When `card_type === "video"` or `card_type` is missing:
   - Use `id` to call `GET /api/details?id={video_id}` (existing flow, unchanged).
4. The `id` field on series cards is a `representative_video_id` (MIN episode id) — old clients that ignore `card_type` and call `/api/details?id={id}` will still work.

### New Dedicated Endpoints

#### `GET /api/series_details`
Full series metadata with all episodes and per-episode watch progress.

Query:
- `id` required (series id)
- `profile_id` optional

Response:
```json
{
  "status": "success",
  "series": {
    "id": 45,
    "title": "Breaking Bad",
    "plot": "A chemistry teacher diagnosed with cancer...",
    "poster_url": "https://...",
    "genre": "Crime, Drama",
    "year": 2008,
    "rating": "9.5",
    "content_rating": "18",
    "rotten_tomatoes": "96%",
    "imdb_id": "tt0903747",
    "language": "English",
    "country": "USA",
    "director": "Vince Gilligan",
    "writer": "Vince Gilligan",
    "total_episodes": 62,
    "episodes_watched": 45,
    "next_episode": { "id": 456, "title": "Ozymandias", "season": 5, "episode": 14 },
    "overall_progress_percent": 72
  },
  "episodes": [
    {
      "id": 100, "title": "Pilot", "season": 1, "episode": 1,
      "plot": "...", "runtime": 58, "poster_url": "...",
      "resume_time": 1200, "total_duration": 3480,
      "progress_percent": 34, "can_resume": true,
      "finishes_at": "2026-03-11T21:45:00+00:00", "finishes_at_label": "9:45pm",
      "has_subtitles": true, "subtitle_url": "...", "subtitle_language": "en"
    }
  ],
  "intro_marker": null,
  "credits_marker": { "type": "credits", "credits_duration_seconds": 45 }
}
```

#### `GET /api/series_episodes`
Paginated episode list (useful for series with many episodes).

Query:
- `id` required (series id)
- `page` optional (default `1`)
- `per_page` optional (default `50`)
- `profile_id` optional

Response:
```json
{
  "status": "success",
  "data": [ /* same episode shape as series_details */ ],
  "total": 62,
  "page": 1,
  "pages": 2
}
```

#### `POST /api/series_edit` (admin only)
Update series-level metadata.

Body:
- `series_id` required
- Optional: `title`, `plot`, `poster_url`, `genre`, `content_rating`, `imdb_rating`, `release_year`, `director`, `writer`, `language`, `country`, `awards`, `metascore`, `rotten_tomatoes`, `imdb_id`

#### `POST /api/series_rescan` (admin only)
Re-fetch series metadata from OMDb, optionally cascading to all episodes.

Body:
- `series_id` required
- `cascade` optional (`0`/`1`)

### Kotlin Patterns for Series Endpoints

```kotlin
// Series details
val request = Request.Builder()
    .url("$apiBaseUrl/series_details?id=$seriesId&profile_id=$profileId")
    .addHeader("X-Client-Platform", "android")
    .get()
    .build()

val response = client.newCall(request).execute()
```

```kotlin
// Series episodes (paginated)
val request = Request.Builder()
    .url("$apiBaseUrl/series_episodes?id=$seriesId&page=$page&per_page=50&profile_id=$profileId")
    .addHeader("X-Client-Platform", "android")
    .get()
    .build()
```

```kotlin
// Navigation decision based on card_type
fun onCardClicked(item: LibraryItem) {
    val seriesId = item.optInt("series_id", 0)
    val cardType = item.optString("card_type", "video")

    if (cardType == "series" && seriesId > 0) {
        // New path: load series detail view
        loadSeriesDetails(seriesId)
    } else {
        // Existing path: load video detail view
        loadVideoDetails(item.getInt("id"))
    }
}
```

## Playback and Discovery Endpoints

### Library Listing
`GET /api/library?lib_id=1&page=1&per_page=48&q=matrix`

Notes:
- `per_page` is clamped to `1..100`.
- `profile_id` optional.

### Search
`GET /api/search?query=matrix`

Notes:
- Query limited to 200 characters
- `profile_id` optional

### Dashboard Rows
`GET /api/dashboard`

### Video Details
`GET /api/details?id=123`

Returns metadata, resume state, subtitle metadata, optional skip markers (`intro_marker`, `credits_marker`), and estimated finish time (`finishes_at`, `finishes_at_label`).

The response includes an `available` boolean field. When `false`, the underlying video file is missing from disk (moved, renamed, or deleted). Use this to grey out or disable the play button before the user attempts playback.

Episode/series subtitle fields (for episode list UI):
- `video.episodes[]` now includes:
  - `has_subtitles`
  - `subtitle_url`
  - `subtitle_language`

Recommended subtitle-button rule for episode rows:
- show subtitle button when `episode.has_subtitles == true` and `episode.subtitle_url` is non-null.
- hide subtitle button otherwise.

### Playback Bootstrap
`GET /api/play?id=123`

Returns:
- `data.stream_url`
- `data.has_subtitles`
- `data.subtitle_url`
- `data.subtitle_language`
- `data.next_episode` (for episodic content)

Playback integration note:
- Use `/api/details` episode-level subtitle fields to render subtitle buttons in series/episode views.
- Use `/api/play?id=<episodeId>` when the user starts playback, and trust that response for final playback URLs.

File unavailable:
- If the video file has been moved, renamed, or deleted from disk, API returns `410` with `code: file_unavailable`.
- Display a "Media Unavailable" message to the user instead of attempting to stream.
- This is distinct from `404` (database record doesn't exist at all).

PIN behavior:
- If profile PIN verification is required, API returns `403` with `code: pin_required`.
- Call `/api/verify_pin` then retry.

### Save Progress
`POST /api/progress`

Body:
- required: `id`, `time` (0-259200 seconds)
- optional: `paused`, `buffer_seconds`, `rebuffer_count`, `rebuffer_seconds`, `rebuffered`, `profile_id`

### Playback Status Check
`GET /api/playback_status?id=123`

Returns:
- `revoke` (boolean - stop playback if true)
- `limit`, `active_playbacks`, `projected_playbacks`
- `message`

Use this to check if playback should be revoked due to concurrent limits.

## Watch Later API

### Add
`POST /api/watch_list_add`
- `video_id`

### Remove
`POST /api/watch_list_remove`
- `video_id`

### Membership Check
`GET /api/watch_list?video_id=123`

### Paginated List
`GET /api/watch_list?page=1&search=matrix&library_id=1`

Notes:
- server page size is fixed at 50
- response includes `videos`, `total`, `page`, `pages`

## Streaming and Subtitles

- Use `stream_url` returned by `/api/play`; do not construct stream URLs manually.
- Subtitle URL is returned when available.
- Subtitle endpoint serves:
  - strict SRT for Android-like clients (`okhttp`/`ExoPlayer` UA)
  - WebVTT for browsers

## App Update (Sideloaded APK)

For non-Play-Store builds, use a prompted self-hosted updater. This updater is intentionally outside the authenticated `/api/{action}` session API.

Build-time environment variables (mapped to `BuildConfig`):
- `VOD_UPDATE_APK_BASE_URL` (example: `https://updates.example.com/vod/android/`)
- `VOD_UPDATE_FEED_URL` (example: `https://updates.example.com/vod/android/update.xml`)
- optional `VOD_UPDATE_CHECK_INTERVAL_HOURS` (default `24`)

### Per-User Update Channel

The server assigns each user an update channel (`stable` or `beta`). Admins can change a user's channel from Admin -> Users -> Edit User.

Both `/api/login` and `/api/session` return:
- `update_feed_url` — the XML feed URL for this user's assigned channel

**App behavior:**
1. On login, store the `update_feed_url` from the response.
2. On app resume, call `/api/session` and refresh the stored `update_feed_url`.
3. Use the stored `update_feed_url` for all update checks instead of the build-time `VOD_UPDATE_FEED_URL`.
4. **Fallback:** If `update_feed_url` is missing (older server), use the build-time `VOD_UPDATE_FEED_URL`.
5. **Pre-login checks:** Before the user has logged in, use the build-time `VOD_UPDATE_FEED_URL` (stable channel).

This allows the admin to move any user to the beta channel server-side without requiring a different app build.

### Update feed and behavior
- XML feed provides `versionCode`, `versionName`, `mandatory`, `apkFileName`, `apkSha256`, and changelog fields.
- App compares feed version against installed version code.
- For non-mandatory updates, prompt includes:
  - `Update now`
  - `Later`
  - `Skip this version` (exact-version skip only)
- For mandatory updates, skip option is not shown.

Full contract and decision rules:
- `docs/ANDROID_IN_APP_UPDATER_SPEC.md`

## Minimal Kotlin Patterns

Assume:
```kotlin
val apiBaseUrl = "https://your-host/vod/api"
```

### Login (form POST)

```kotlin
val form = FormBody.Builder()
    .add("username", username)
    .add("password", password)
    .build()

val request = Request.Builder()
    .url("$apiBaseUrl/login")
    .post(form)
    .build()

val response = client.newCall(request).execute()
val body = response.body?.string().orEmpty()
// Parse csrf_token and persist it
```

### Authenticated Mutation Helper

```kotlin
fun postWithCsrf(url: String, csrfToken: String, fields: Map<String, String>): Response {
    val formBuilder = FormBody.Builder()
    fields.forEach { (k, v) -> formBuilder.add(k, v) }

    val request = Request.Builder()
        .url(url)
        .addHeader("X-CSRF-Token", csrfToken)
        .addHeader("X-Client-Platform", "android")
        .post(formBuilder.build())
        .build()

    return client.newCall(request).execute()
}
```

### Save Progress

```kotlin
postWithCsrf(
    "$apiBaseUrl/progress",
    csrfToken,
    mapOf(
        "id" to videoId.toString(),
        "time" to currentSeconds.toString(),
        "paused" to "0"
    )
)
```

### Verify PIN

```kotlin
postWithCsrf(
    "$apiBaseUrl/verify_pin",
    csrfToken,
    mapOf(
        "profile_id" to profileId.toString(),
        "pin" to enteredPin
    )
)
```

## Error Handling Expectations

- `400` invalid request data
- `401` unauthenticated or invalid login
- `403` forbidden / CSRF failure / `pin_required`
- `404` not found
- `405` wrong method
- `410` gone / `file_unavailable` (file missing from disk)
- `415` unsupported media type
- `429` rate limit exceeded
- `500` server errors

When receiving `401`, clear local auth state and force re-login.
When receiving `403` + `pin_required`, prompt for profile PIN in app flow.
When receiving `410` + `file_unavailable`, show "Media Unavailable" — do not retry or attempt to stream.
When receiving `429`, implement exponential backoff.

## Security Notes

1. **Always send CSRF tokens** - While legacy fallback exists, it requires both User-Agent pattern AND `X-Client-Platform` header.

2. **PIN verification is rate-limited** - 5 attempts per 60 seconds per user. Implement UI feedback for remaining attempts.

3. **Progress time validation** - Time values outside 0-259200 seconds (72 hours) are rejected.

4. **Search query limits** - Queries are capped at 200 characters.

5. **Security headers** - All responses include `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, and cache control headers.

## Recommended Android Flow

1. Login and store cookies + CSRF token.
2. Call `/api/session` on app resume to restore state.
3. Call `/api/profiles`; let user pick profile.
4. Use `/api/library`, `/api/search`, `/api/dashboard` for browsing.
5. For card navigation: check `card_type` — use `/api/series_details` for series cards, `/api/details` for video cards.
6. Use `/api/play` to get stream/subtitle URLs.
7. Send periodic `/api/progress` updates.
8. Use watch-later endpoints for user bookmarks.
9. Call `/api/logout` when user explicitly logs out.

## Quick Endpoint Checklist

- `POST /api/login`
- `POST /api/logout`
- `GET /api/session`
- `GET /api/profiles`
- `POST /api/profiles_select`
- `POST /api/verify_pin`
- `GET /api/get_libraries`
- `GET /api/library`
- `GET /api/details`
- `GET /api/series_details`
- `GET /api/series_episodes`
- `GET /api/play`
- `GET /api/playback_status`
- `POST /api/progress`
- `POST /api/watch_list_add`
- `POST /api/watch_list_remove`
- `GET /api/watch_list`
- `POST /api/series_edit` (admin)
- `POST /api/series_rescan` (admin)
