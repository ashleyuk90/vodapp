# API Endpoints Reference

Canonical API reference for actions handled in `includes/ApiController.php`.

## Base URL

- Action URL format: `/api/{action}`
- If `BASE_PATH=/vod`, URLs become `/vod/api/{action}`
- Clean URL and query style are both supported (`/api/login` and `/api.php?action=login`)
- Android sideload update assets (XML/APK) are static HTTPS resources and are not `/api/{action}` endpoints.

## Authentication

- `login` is public.
- All other actions require an authenticated PHP session cookie.
- Successful login returns a `csrf_token` for state-changing API requests.

## Client Metadata (Android)

Android app requests include these headers on all `/api/{action}` requests:
- `X-Client-Platform: android`
- `X-App-Package: <BuildConfig.APPLICATION_ID>`
- `X-App-Version-Name: <BuildConfig.VERSION_NAME>`
- `X-App-Version-Code: <BuildConfig.VERSION_CODE>`

Login also includes optional form fields:
- `app_version_name`
- `app_version_code`

## Security Controls

### Rate Limits
- Authenticated API actions: `120` requests / `60` seconds / user / action.
- Login brute-force protection uses:
  - `RATE_LIMIT_MAX_ATTEMPTS` (default `5`)
  - `RATE_LIMIT_WINDOW_MINUTES` (default `15`)

### Mutation Protection
These actions require:
- HTTP `POST`, and
- CSRF token or same-origin validation

Protected actions:
- `progress`
- `watch_list_add`
- `watch_list_remove`
- `profiles_select`
- `profiles_add`
- `profiles_remove`

Token can be sent as:
- Header: `X-CSRF-Token: <token>`
- Body field: `csrf_token=<token>`

Legacy Android compatibility is enabled by default for clients that do not send Origin/Referer and use Android-like User-Agent strings (`okhttp`/`ExoPlayer`). To disable this fallback, set:
- `ALLOW_LEGACY_ANDROID_API_MUTATIONS=0`

## Endpoint Matrix

| Action | Method | Auth | Admin |
|---|---|---|---|
| `login` | POST | No | No |
| `library` | GET | Yes | No |
| `details` | GET | Yes | No |
| `play` | GET | Yes | No |
| `progress` | POST | Yes | No |
| `get_libraries` | GET | Yes | No |
| `dashboard` | GET | Yes | No |
| `search` | GET | Yes | No |
| `watch_list` | GET | Yes | No |
| `watch_list_add` | POST | Yes | No |
| `watch_list_remove` | POST | Yes | No |
| `profiles` | GET | Yes | No |
| `profiles_select` | POST | Yes | No |
| `profiles_add` | POST | Yes | No |
| `profiles_remove` | POST | Yes | No |
| `validate_path` | GET | Yes | Yes |
| `list_directories` | GET | Yes | Yes |
| `fetch_libraries` | GET | Yes | Yes |
| `library_info` | GET | Yes | Yes |
| `get_server_stats` | GET | Yes | Yes |
| `diagnostics` | GET | Yes | Yes |

## Endpoint Details

### `POST /api/login`
Body:
- `username` (required)
- `password` (required)
- `app_version_name` (optional, Android client)
- `app_version_code` (optional, Android client)

Success:
- `status: success`
- `user` object (without `password_hash`)
- `csrf_token`
- `account_expiry` when configured

Errors:
- `401` invalid credentials / expired account
- `429` too many failed attempts from same IP

### `GET /api/library`
Query:
- `lib_id` optional
- `page` optional
- `per_page` optional (`1..100`, default `48`)
- `q` optional
- `profile_id` optional

Returns paginated library/search results under `data` with `pages`.

### `GET /api/details`
Query:
- `id` required
- `profile_id` optional

Returns `video` payload including:
- metadata fields
- `resume_time`
- subtitle availability (`has_subtitles`, `subtitle_url`, `subtitle_language`)
- optional `intro_marker` / `credits_marker`
- for episodes: `episodes[]` and `next_episode`

### `GET /api/play`
Query:
- `id` required
- `profile_id` optional

Returns stream metadata under `data`:
- `stream_url`
- subtitle fields
- optional `next_episode`

PIN-protected profile playback returns `403` with:
- `code: pin_required`

Concurrent playback limit exceeded returns `429` with:
- `code: playback_limit_exceeded`
- `limit`
- `active_playbacks`

### `POST /api/progress`
Body:
- `id` required (video id)
- `time` required (seconds)
- `paused` optional (`0`/`1`)
- `buffer_seconds` optional
- `rebuffer_count` optional
- `rebuffer_seconds` optional
- `rebuffered` optional (`0`/`1`)
- `profile_id` optional

Success:
- `{ "status": "saved", "stop_playback": <bool>, "limit": <int>, "active_playbacks": <int> }`

### `GET /api/playback_status`
Query:
- `id` required (video id)
- `profile_id` optional

Returns:
- `revoke` (`true` when this playback should be stopped)
- `limit`
- `active_playbacks`
- `projected_playbacks`
- `message`

### `GET /api/get_libraries`
Returns list of libraries under `data`, including synthetic `All Content` entry (`id = 0`).

### `GET /api/dashboard`
Query:
- `profile_id` optional

Returns:
- `continue_watching`
- `recent_movies`
- `recent_shows`

### `GET /api/search`
Query:
- `query` optional
- `profile_id` optional

Empty query returns empty `data` array.

### `POST /api/watch_list_add`
Body:
- `video_id` required

### `POST /api/watch_list_remove`
Body:
- `video_id` required

### `GET /api/watch_list`
Query mode A (membership check):
- `video_id` required

Returns:
- `in_watch_list`

Query mode B (paginated list):
- `library_id` optional
- `search` optional
- `page` optional

Returns:
- `videos`
- `total`
- `page`
- `pages`

Server-side page size is fixed at `50`.

### `GET /api/profiles`
Returns:
- `active_profile_id`
- `profiles[]` with:
  - `id`, `name`, `max_content_rating`, `max_rating`
  - `auto_skip_intro`, `auto_skip_credits`, `autoplay_next`
  - `has_pin`

### `POST /api/profiles_select`
Body:
- `profile_id` required

### `POST /api/profiles_add`
Body:
- `name` required
- `pin` optional
- `max_content_rating` optional (requires `pin`)
- `max_rating` optional (legacy IMDb cap)
- `auto_skip_intro` optional (`0`/`1`)
- `auto_skip_credits` optional (`0`/`1`)
- `autoplay_next` optional (`0`/`1`, defaults enabled)

### `POST /api/profiles_remove`
Body:
- `profile_id` required

Returns:
- `400` if deleting last profile
- `404` if profile not owned by the user

### Admin-only Actions

#### `GET /api/validate_path`
Query:
- `path`

Returns:
- `{ "valid": true|false }`

#### `GET /api/list_directories`
Query:
- `path`

Returns:
- `{ "subdirs": ["..."] }`

#### `GET /api/fetch_libraries`
Returns libraries with stats, active jobs, and scan metadata.

#### `GET /api/library_info`
Query:
- `id` required (library id)

Returns:
- `folders` for the target library.

#### `GET /api/get_server_stats`
Returns host metrics (CPU/memory/disk/network where available).

#### `GET /api/diagnostics`
Returns session/runtime diagnostics (session file, cookie/session settings, effective idle timeout, etc.).

## Response Notes

- `status` is usually `success` or `error`.
- IMDb ratings are returned as formatted strings in multiple endpoints.
- `profile_id` is optional in many endpoints; if omitted, active session profile is used.
- All timestamps are MySQL-style datetime strings when applicable.

## Common Error Statuses

- `400` invalid/missing params
- `401` unauthenticated / invalid credentials
- `403` forbidden / CSRF failure / PIN required
- `404` not found
- `405` wrong method for POST-only endpoint
- `429` rate limit exceeded
- `500` server-side failure

## Android Sideload Update Resources (Non-API)

These resources are intended for the prompted in-app updater and are publicly readable over HTTPS.

### `GET /vod/android/update.xml` (example path)

Returns XML metadata describing the newest APK:
- `versionCode`
- `versionName`
- `mandatory`
- `minSupportedVersionCode` (optional)
- `apkFileName`
- `apkSha256`
- `changelog.summary`
- `changelog.item[]`

Notes:
- This is a static file contract, not a PHP session endpoint.
- Android client compares XML `versionCode` with installed app `versionCode`.
- For non-mandatory updates, clients may allow skipping that exact version.

### `GET /vod/android/<apkFileName>` (example path)

Returns the APK binary referenced by `update.xml`.

Requirements:
- Must use HTTPS.
- APK must be signed with the same signing key as the installed app.
- Client should verify SHA-256 checksum (`apkSha256`) before installer launch.

## Related Docs

- `docs/ANDROID_API_GUIDE.md`
- `docs/ANDROID_IN_APP_UPDATER_SPEC.md`
- `docs/SECURITY_IMPROVEMENTS.md`
- `docs/ARCHITECTURE.md`
