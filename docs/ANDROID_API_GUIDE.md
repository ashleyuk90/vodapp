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

### 3. Session Info

`GET /api/session`

Returns current session state including:
- `user` object
- `active_profile` 
- `csrf_token` (refreshed)
- `login_time`
- `client_platform`
- `app_version_name`

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

Returns metadata, resume state, subtitle metadata, and optional skip markers (`intro_marker`, `credits_marker`).

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
4. Use `/api/library`, `/api/search`, `/api/details` for browsing.
5. Use `/api/play` to get stream/subtitle URLs.
6. Send periodic `/api/progress` updates.
7. Use watch-later endpoints for user bookmarks.
8. Call `/api/logout` when user explicitly logs out.

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
- `GET /api/play`
- `GET /api/playback_status`
- `POST /api/progress`
- `POST /api/watch_list_add`
- `POST /api/watch_list_remove`
- `GET /api/watch_list`
