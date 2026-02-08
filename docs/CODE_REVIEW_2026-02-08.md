# Code Review - 2026-02-08

Scope:
- Reviewed Kotlin app code under `app/src/main/java/com/example/vod`.
- Reviewed Gradle/dependency setup in `app/build.gradle.kts`.
- Reviewed project docs for drift and formatting issues.

Validation limits:
- `./gradlew testDebugUnitTest` could not run locally because Java runtime is missing on this machine.

## Findings (Ordered by Severity)

### 1) Critical: PIN-protected profiles are not actually protected
- Location: `app/src/main/java/com/example/vod/ProfileSelectionActivity.kt:424`
- Evidence: `profile.hasPin` branch contains TODO and still proceeds with profile selection.
- Impact: Parental-control and profile privacy can be bypassed.
- Suggested fix:
  - Block selection until PIN entry succeeds.
  - Validate PIN server-side.
  - Add local retry throttling and temporary lockout.

### 2) High: Image fallback URLs are hardcoded to cleartext HTTP
- Location: `app/src/main/java/com/example/vod/Models.kt:104`
- Evidence: `getDisplayImage()` and `getBackdropImage()` return `http://77.74.196.120/...`.
- Impact:
  - Mixed/insecure transport risk.
  - Fallback images may fail under strict network security policies.
  - BuildConfig URL environment separation is bypassed.
- Suggested fix:
  - Build absolute image URLs from `BuildConfig.BASE_URL`.
  - Require HTTPS for fallback paths.
  - Return empty string when no path exists instead of `.../null`.

### 3) High: Cancelled library fetches can surface as user-facing errors
- Location: `app/src/main/java/com/example/vod/MainActivity.kt:1367`
- Evidence: `fetchJob?.cancel()` is followed by `catch (e: Exception)` that treats cancellation as a normal error.
- Impact: Spurious error messages during normal navigation (switching libraries quickly).
- Suggested fix:
  - Handle `CancellationException` separately and ignore it.
  - Keep generic error handling for actual failures only.

### 4) High: Transient login/network failures clear saved credentials
- Location: `app/src/main/java/com/example/vod/LoginActivity.kt:178`
- Evidence: IO and generic exceptions call `showLoginError(null)`, which clears prefs (`clear()`).
- Impact: Users can be logged out by temporary outages.
- Suggested fix:
  - Only clear credentials on explicit auth failure (e.g. 401/invalid credentials).
  - Keep encrypted credentials on network timeouts/offline conditions.

### 5) Medium: Duplicate/conflicting dependency declarations
- Location: `app/build.gradle.kts:48`
- Evidence:
  - `core-ktx` declared via version catalog and again explicitly.
  - `material` declared via version catalog and again explicitly with different version.
- Impact: Version drift, harder upgrades, less predictable dependency graph.
- Suggested fix:
  - Keep a single source of truth via `libs.versions.toml`.
  - Remove explicit duplicate declarations.

### 6) Medium: Runtime error handling still uses `printStackTrace()`
- Location: `app/src/main/java/com/example/vod/MainActivity.kt:1429`
- Evidence: Multiple UI/network paths print stack traces directly.
- Impact: Inconsistent observability and noisy logs in production.
- Suggested fix:
  - Replace with structured logging (`Log.e/w`) and user-safe messaging.
  - Optionally route critical errors through a crash/reporting pipeline.

### 7) Medium: README is stale and encoded as UTF-16
- Location: `README.md`
- Evidence:
  - File encoding is UTF-16 LE with CRLF.
  - Docs still reference old hardcoded HTTP base URL and outdated min SDK value.
- Impact: Poor Git/web rendering interoperability and onboarding confusion.
- Suggested fix:
  - Convert to UTF-8.
  - Align config sections with `BuildConfig.BASE_URL` and current Gradle settings.

### 8) Low: Automated test coverage is still placeholder-only
- Location: `app/src/test/java/com/example/vod/ExampleUnitTest.kt:12`
- Evidence: Default template tests only.
- Impact: Regressions are harder to catch during refactors.
- Suggested fix:
  - Add unit tests for URL building, profile PIN gate, and cancellation handling.
  - Add one instrumentation test for profile-selection auth flow.

## Suggested New Features (Post-Fix Priority)

1. PIN verification flow with lockout and optional recovery path.
2. "Manage Continue Watching" actions (hide item, mark as watched, reset progress).
3. Offline action queue for watchlist/progress sync with background retry.
4. Quality/subtitle preference persistence per profile and per series.
5. Playback diagnostics screen (last error, stream endpoint, retry status).

## Suggested Markdown/Documentation Changes

1. Update `README.md` to UTF-8 and remove hardcoded HTTP examples.
2. Add a short "Known Risks" section to `docs/SECURITY_FIXES.md` for unresolved items.
3. Add a "Near-Term (Next Sprint)" feature subsection to `docs/FEATURE_IMPROVEMENTS.md`.
