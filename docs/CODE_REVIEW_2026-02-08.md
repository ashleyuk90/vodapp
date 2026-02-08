# Code Review - 2026-02-08

Updated after remediation pass.

Scope:
- Kotlin app code under `app/src/main/java/com/example/vod`.
- Gradle/dependency setup in `app/build.gradle.kts`.
- Project docs and README quality.

Validation limits:
- `./gradlew testDebugUnitTest` still cannot run in this environment because Java runtime is missing.

## Status Summary

| # | Finding | Severity | Status |
| --- | --- | --- | --- |
| 1 | PIN-protected profiles are not actually protected | Critical | Open |
| 2 | Image fallback URLs were hardcoded to cleartext HTTP | High | Resolved |
| 3 | Cancelled library fetches could surface as user-facing errors | High | Resolved |
| 4 | Transient login/network failures cleared saved credentials | High | Resolved |
| 5 | Duplicate/conflicting dependency declarations | Medium | Resolved |
| 6 | Runtime error handling used `printStackTrace()` | Medium | Resolved |
| 7 | README stale + UTF-16 encoding | Medium | Resolved |
| 8 | Automated tests are placeholder-only | Low | Open |

## Resolved Findings

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

## Remaining Findings

### 1) Critical: PIN-protected profiles are not actually protected
- Location: `app/src/main/java/com/example/vod/ProfileSelectionActivity.kt`.
- Evidence: `profile.hasPin` branch is still TODO and proceeds with selection.
- Required fix:
  - Block profile selection until PIN verification succeeds.
  - Validate PIN server-side.
  - Add retry throttling/lockout behavior.

### 8) Low: Automated test coverage is still placeholder-only
- Locations:
  - `app/src/test/java/com/example/vod/ExampleUnitTest.kt`
  - `app/src/androidTest/java/com/example/vod/ExampleInstrumentedTest.kt`
- Required fix:
  - Add unit tests for URL resolution and login credential-clear behavior.
  - Add instrumentation coverage for PIN-gated profile selection flow.

## Recommended Next Steps

1. Implement PIN verification flow end-to-end (UI + backend API integration).
2. Add focused unit tests for fixes already delivered.
3. Re-run Gradle tests once Java runtime is available in this environment.
