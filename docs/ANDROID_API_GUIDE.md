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

## Authentication Flow

### 1. Login

`POST /api/login`

Body:
- `username`
- `password`
- `app_version_name` (optional but recommended for telemetry)
- `app_version_code` (optional but recommended for telemetry)

Success includes:
- `user`
- `csrf_token`
- optional `account_expiry`

Store:
- session cookie(s)
- CSRF token value

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

### 3. CSRF for Mutations

For these POST endpoints, include CSRF token:
- `/api/progress`
- `/api/watch_list_add`
- `/api/watch_list_remove`
- `/api/profiles_select`
- `/api/profiles_add`
- `/api/profiles_remove`

Preferred header:
- `X-CSRF-Token: <token>`

Fallback form field:
- `csrf_token=<token>`

Note: Legacy Android mutation fallback currently exists server-side (User-Agent based), but clients should still send CSRF tokens now.

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

## Playback and Discovery Endpoints

### Library Listing
`GET /api/library?lib_id=1&page=1&per_page=48&q=matrix`

Notes:
- `per_page` is clamped to `1..100`.
- `profile_id` optional.

### Search
`GET /api/search?query=matrix`

### Dashboard Rows
`GET /api/dashboard`

### Video Details
`GET /api/details?id=123`

Returns metadata, resume state, subtitle metadata, and optional skip markers (`intro_marker`, `credits_marker`).

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

PIN behavior:
- If profile PIN verification is required, API returns `403` with `code: pin_required`.

### Save Progress
`POST /api/progress`

Body:
- required: `id`, `time`
- optional: `paused`, `buffer_seconds`, `rebuffer_count`, `rebuffer_seconds`, `rebuffered`, `profile_id`

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

Update feed and behavior:
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

## Error Handling Expectations

- `400` invalid request data
- `401` unauthenticated or invalid login
- `403` forbidden / CSRF failure / `pin_required`
- `404` not found
- `405` wrong method
- `429` rate limit exceeded
- `500` server errors

When receiving `401`, clear local auth state and force re-login.
When receiving `403` + `pin_required`, prompt for profile PIN in app flow.

## Recommended Android Flow

1. Login and store cookies + CSRF token.
2. Call `/api/profiles`; let user pick profile.
3. Use `/api/library`, `/api/search`, `/api/details` for browsing.
4. Use `/api/play` to get stream/subtitle URLs.
5. Send periodic `/api/progress` updates.
6. Use watch-later endpoints for user bookmarks.

## Quick Endpoint Checklist

- `POST /api/login`
- `GET /api/profiles`
- `POST /api/profiles_select`
- `GET /api/get_libraries`
- `GET /api/library`
- `GET /api/details`
- `GET /api/play`
- `POST /api/progress`
- `POST /api/watch_list_add`
- `POST /api/watch_list_remove`
- `GET /api/watch_list`
