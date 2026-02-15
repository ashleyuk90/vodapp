# Code Review - 2026-02-08

Updated: 2026-02-15 (post subtitle/playback and remediation pass).

Scope:
- Kotlin app code under `app/src/main/java/com/example/vod`.
- Gradle/dependency setup in `app/build.gradle.kts`.
- Layout XML, resource files, ProGuard config.
- Project docs and README quality.

Validation limits:
- `./gradlew testDebugUnitTest` still cannot run in this environment because Java runtime is missing.

## Status Summary

| # | Finding | Severity | Status |
| --- | --- | --- | --- |
| 1 | PIN-protected profiles are not actually protected | Critical | Resolved |
| 2 | Image fallback URLs were hardcoded to cleartext HTTP | High | Resolved |
| 3 | Cancelled library fetches could surface as user-facing errors | High | Resolved |
| 4 | Transient login/network failures cleared saved credentials | High | Resolved |
| 5 | Duplicate/conflicting dependency declarations | Medium | Resolved |
| 6 | Runtime error handling used `printStackTrace()` | Medium | Resolved |
| 7 | README stale + UTF-16 encoding | Medium | Resolved |
| 8 | Automated tests are placeholder-only | Low | Open |
| 9 | SecurePrefs falls back to unencrypted storage silently | High | Open |
| 10 | Manual cookie header construction in PlayerActivity | Medium | Resolved |
| 11 | `checkSubtitleAvailability` calls play endpoint from details screen | Medium | Resolved |
| 12 | Inconsistent TAG naming across activities | Low | Resolved |
| 13 | Missing image loading error/placeholder handling | Low | Resolved |
| 14 | `btnResume` click handler does not guard `video` initialization | Medium | Resolved |
| 15 | Hardcoded profile color palette should be in resources | Low | Resolved |

## Resolved Findings

### 1) PIN-protected profiles are not actually protected
- Fixed in `app/src/main/java/com/example/vod/ProfileSelectionActivity.kt`.
- Added `showPinDialog()` method that displays a PIN entry dialog before selecting PIN-protected profiles.
- Profile selection is blocked until PIN is entered.
- Auto-login path already correctly skips PIN profiles.
- Server-side PIN validation occurs via the profile selection API (returns 403 with `pin_required` on mismatch).

### 2) Image URL hardcoding
- Fixed in `app/src/main/java/com/example/vod/Models.kt`.
- `getDisplayImage()`/`getBackdropImage()` now resolve via secure base URL logic and return safe empty fallback.

### 3) Cancellation handling in library fetch
- Fixed in `app/src/main/java/com/example/vod/MainActivity.kt`.
- Added explicit `CancellationException` handling to avoid false user-facing errors.

### 4) Credential clearing on transient failures
- Fixed in `app/src/main/java/com/example/vod/LoginActivity.kt`.
- Credentials are now cleared only for auth failures (401/403 and explicit login failure paths), not transient network errors.

### 5) Duplicate dependency declarations
- Fixed in `app/build.gradle.kts`.
- Removed duplicate explicit `core-ktx` and `material` lines so version catalog is the source of truth.

### 6) `printStackTrace()` cleanup
- Fixed in:
  - `app/src/main/java/com/example/vod/MainActivity.kt`
  - `app/src/main/java/com/example/vod/DetailsActivity.kt`
  - `app/src/main/java/com/example/vod/WatchLaterFragment.kt`
- Replaced with structured `Log` usage and user-safe error handling where appropriate.

### 7) README encoding and drift
- Fixed in `README.md`.
- Converted to UTF-8, updated stale config notes, and aligned SDK/build/config sections with current project setup.

### 10) Manual cookie header construction in PlayerActivity
- Fixed in `app/src/main/java/com/example/vod/PlayerActivity.kt`.
- Cookie values are now sanitized by stripping `\r`, `\n`, and null bytes before header construction.
- Replaced imperative loop with functional `filter`/`joinToString` for clarity.

### 11) `checkSubtitleAvailability` calls play endpoint from details screen
- Fixed in `app/src/main/java/com/example/vod/DetailsActivity.kt`.
- Removed the `checkSubtitleAvailability()` method and its call entirely.
- Server-side fix now ensures `/api/details` returns `has_subtitles` and `subtitle_url` for all video types (movies and episodes).
- Details screen checks both `hasSubtitles` flag and `subtitleUrl` presence from the details response.

### 12) Inconsistent TAG naming across activities
- Fixed in `app/src/main/java/com/example/vod/PlayerActivity.kt`.
- Changed `TAG = "VOD_DEBUG"` to `TAG = "PlayerActivity"` for consistency with all other classes.

### 13) Missing image loading error/placeholder handling
- Fixed in `app/src/main/java/com/example/vod/DetailsActivity.kt`.
- Added `placeholder(R.drawable.ic_movie)` and `error(R.drawable.ic_movie)` to poster image loads.

### 14) `btnResume` click handler does not guard `video` initialization
- Fixed in `app/src/main/java/com/example/vod/DetailsActivity.kt`.
- Wrapped `btnResume` click lambda with `if (::video.isInitialized)` guard to prevent crash if clicked before details load.

### 15) Hardcoded profile color palette
- Fixed in `app/src/main/java/com/example/vod/ProfileSelectionActivity.kt`.
- Profile avatar colors moved from hardcoded hex strings to `res/values/colors.xml` (as `profile_*` colors) and `res/values/arrays.xml` (as typed array `profile_avatar_colors`).
- `ProfileAdapter` now accepts `List<Int>` color values loaded from resources.

## Remaining Findings

### 8) Low: Automated test coverage is still placeholder-only
- Locations:
  - `app/src/test/java/com/example/vod/ExampleUnitTest.kt`
  - `app/src/androidTest/java/com/example/vod/ExampleInstrumentedTest.kt`
- Required fix:
  - Add unit tests for URL resolution and login credential-clear behavior.
  - Add instrumentation coverage for PIN-gated profile selection flow.

### 9) High: SecurePrefs falls back to unencrypted storage silently
- Location: `app/src/main/java/com/example/vod/utils/SecurePrefs.kt`, line 30-36.
- If `EncryptedSharedPreferences` fails (keystore corruption, backup/restore, OEM issues), credentials are stored in plain `SharedPreferences` without any indication to the user.
- Recommended fix: Either fail with an error instead of falling back, or at minimum log a persistent warning and avoid storing passwords in the unencrypted fallback.

## Recommended Next Steps

1. Evaluate SecurePrefs fallback behavior and decide on fail-safe vs. fail-closed approach.
2. Add focused unit tests for fixes already delivered.
