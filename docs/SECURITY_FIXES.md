# Security & Code Quality Fixes

This document outlines security vulnerabilities, code quality issues, and recommended fixes for the VOD app codebase.

**Last Updated**: 5 February 2026

---

## 🔴 Critical Security Issues

### 1. ✅ FIXED: Credentials Stored in Plain Text

**Location**: [LoginActivity.kt](../app/src/main/java/com/example/vod/LoginActivity.kt)

**Status**: ✅ **IMPLEMENTED** - Now using `EncryptedSharedPreferences` with AES256-GCM encryption.

**Changes Made**:
- Added `androidx.security:security-crypto:1.1.0-alpha06` dependency
- Replaced `SharedPreferences` with `EncryptedSharedPreferences`
- Credentials now encrypted at rest with hardware-backed keystore

---

### 2. ✅ FIXED: Cleartext HTTP Traffic Enabled

**Location**: [AndroidManifest.xml](../app/src/main/AndroidManifest.xml), [network_security_config.xml](../app/src/main/res/xml/network_security_config.xml)

**Status**: ✅ **IMPLEMENTED** - Network Security Config now blocks cleartext by default.

**Changes Made**:
- Removed `android:usesCleartextTraffic="true"`
- Added `network_security_config.xml` with secure defaults
- Cleartext traffic blocked by default (HTTPS required)

---

### 3. ✅ FIXED: Hardcoded Server IP Address

**Location**: [NetworkModule.kt](../app/src/main/java/com/example/vod/NetworkModule.kt), [build.gradle.kts](../app/build.gradle.kts)

**Status**: ✅ **IMPLEMENTED** - URL now loaded from BuildConfig with per-environment configuration.

**Changes Made**:
- Enabled `buildFeatures.buildConfig = true`
- Added `buildConfigField` for debug and release URLs
- `NetworkModule.kt` now uses `BuildConfig.BASE_URL`

---

### 4. ✅ FIXED: ProGuard/R8 Not Enabled for Release

**Location**: [build.gradle.kts](../app/build.gradle.kts), [proguard-rules.pro](../app/proguard-rules.pro)

**Status**: ✅ **IMPLEMENTED** - Code obfuscation and shrinking enabled for release builds.

**Changes Made**:
- Enabled `isMinifyEnabled = true` and `isShrinkResources = true` for release
- Added comprehensive ProGuard rules for Retrofit, Gson, OkHttp, and Coroutines
- All model classes preserved for JSON serialization

---

## 🟠 High Priority Code Issues

### 5. ✅ FIXED: Deprecated RenderScript Usage

**Location**: [BlurTransformation.kt](../app/src/main/java/com/example/vod/BlurTransformation.kt)

**Status**: ✅ **IMPLEMENTED** - Replaced with pure Kotlin StackBlur algorithm (no external dependencies).

**Changes Made**:
- Removed deprecated `RenderScript` API
- Implemented StackBlur algorithm directly in Kotlin
- No external dependencies required - works on all Android versions (API 26+)

---

### 6. ✅ FIXED: Memory Leaks from Coroutine Scopes

**Location**: All Activities

**Status**: ✅ **IMPLEMENTED** - All coroutines now use lifecycle-aware scopes.

**Changes Made**:
- Replaced all `CoroutineScope(Dispatchers.IO).launch` with `lifecycleScope.launch`
- Coroutines automatically cancelled on activity destruction
- Files updated: `MainActivity.kt`, `DetailsActivity.kt`, `LoginActivity.kt`, `PlayerActivity.kt`

---

### 7. ✅ FIXED: Deprecated `onBackPressed()` Override

**Location**: [MainActivity.kt](../app/src/main/java/com/example/vod/MainActivity.kt)

**Status**: ✅ **IMPLEMENTED** - Now using `OnBackPressedDispatcher`.

**Changes Made**:
- Added `androidx.activity:activity-ktx:1.8.0` dependency
- Created `setupBackPressedHandler()` with `OnBackPressedCallback`
- Removed deprecated `onBackPressed()` override

---

### 8. ✅ FIXED: Missing Error Handling for Network Failures

**Location**: All Activities

**Status**: ✅ **IMPLEMENTED** - Proper error messages shown to users.

**Changes Made**:
- Added specific `IOException` handling for network errors
- User-friendly toast messages for all error types
- Files updated: `MainActivity.kt`, `DetailsActivity.kt`, `LoginActivity.kt`, `PlayerActivity.kt`

---

### 9. ✅ FIXED: Potential NullPointerException in PlayerActivity

**Location**: [PlayerActivity.kt](../app/src/main/java/com/example/vod/PlayerActivity.kt)

**Status**: ✅ **IMPLEMENTED** - Replaced forced non-null assertion with safe call.

**Changes Made**:
- Changed `player != null && player!!.isPlaying` to `player?.isPlaying == true`
- Added `onDestroy()` with proper cleanup
- Handler callbacks now properly removed

---

## 🟡 Medium Priority Issues

### 10. ✅ FIXED: No Network Connectivity Check

**Location**: [NetworkUtils.kt](../app/src/main/java/com/example/vod/utils/NetworkUtils.kt)

**Status**: ✅ **IMPLEMENTED** - Network connectivity check before all API calls.

**Changes Made**:
- Created `NetworkUtils.isNetworkAvailable()` utility function
- Added pre-flight network checks in `LoginActivity`, `MainActivity`, `DetailsActivity`, `PlayerActivity`
- User-friendly "No internet connection" message shown when offline

---

### 11. ✅ FIXED: Missing Input Validation

**Location**: [LoginActivity.kt](../app/src/main/java/com/example/vod/LoginActivity.kt)

**Status**: ✅ **IMPLEMENTED** - Username and password validation added.

**Changes Made**:
- Added `validateInput()` function with length checks
- Minimum username length: 3 characters
- Minimum password length: 4 characters
- Field-specific error messages displayed

---

### 12. ✅ FIXED: Handler on Main Looper Not Removed

**Location**: [PlayerActivity.kt](../app/src/main/java/com/example/vod/PlayerActivity.kt)

**Status**: ✅ **IMPLEMENTED** - Added proper cleanup in `onDestroy()`.

**Changes Made**:
- Added `onDestroy()` override with `progressHandler.removeCallbacksAndMessages(null)`
- Ensures all pending callbacks are cancelled

---

### 13. No Certificate Pinning

**Issue**: App doesn't verify server certificate authenticity.

**Fix**: Add certificate pinning in OkHttp:
```kotlin
val certificatePinner = CertificatePinner.Builder()
    .add("yourdomain.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
    .build()

val okHttpClient = OkHttpClient.Builder()
    .certificatePinner(certificatePinner)
    .build()
```

---

### 14. ✅ FIXED: Sensitive Data in Logs

**Location**: [NetworkModule.kt](../app/src/main/java/com/example/vod/NetworkModule.kt)

**Status**: ✅ **IMPLEMENTED** - Logging now disabled in release builds.

**Changes Made**:
- Logging level now conditional on `BuildConfig.DEBUG`
- Release builds use `Level.NONE`

---

### 15. ✅ FIXED: Missing Timeout Configuration

**Location**: [NetworkModule.kt](../app/src/main/java/com/example/vod/NetworkModule.kt)

**Status**: ✅ **IMPLEMENTED** - 30-second timeouts configured.

**Changes Made**:
- Added `connectTimeout(30, TimeUnit.SECONDS)`
- Added `readTimeout(30, TimeUnit.SECONDS)` 
- Added `writeTimeout(30, TimeUnit.SECONDS)`

---

## 🔵 Low Priority / Code Quality

### 16. ✅ FIXED: Magic Numbers

**Location**: [Constants.kt](../app/src/main/java/com/example/vod/utils/Constants.kt)

**Status**: ✅ **IMPLEMENTED** - All magic numbers extracted to centralized constants.

**Changes Made**:
- Created `Constants.kt` with all configuration values
- Includes grid settings, animation durations, focus effects, validation rules
- All activities updated to use constants

---

### 17. ✅ FIXED: Missing Null Safety in Models

**Location**: [Models.kt](../app/src/main/java/com/example/vod/Models.kt)

**Status**: ✅ **IMPLEMENTED** - All data classes now have safe defaults.

**Changes Made**:
- Added default values to all model properties
- `VideoItem`, `EpisodeItem`, `NextEpisode`, `ApiResponse`, etc. all updated
- Prevents NPE when API returns null fields

---

### 18. ✅ FIXED: Inconsistent Error Messages

**Location**: [ErrorHandler.kt](../app/src/main/java/com/example/vod/utils/ErrorHandler.kt)

**Status**: ✅ **IMPLEMENTED** - Centralized error handling with consistent messaging.

**Changes Made**:
- Created `ErrorHandler` object with `showError()` and `handleNetworkError()`
- Maps exception types to user-friendly messages
- HTTP error codes mapped to specific messages (401, 403, 404, 500, etc.)
- All activities updated to use `ErrorHandler`

---

### 19. ✅ FIXED: No Loading State Management

**Location**: [UiState.kt](../app/src/main/java/com/example/vod/utils/UiState.kt)

**Status**: ✅ **IMPLEMENTED** - Sealed class for type-safe UI state management.

**Changes Made**:
- Created `UiState<T>` sealed class with `Loading`, `Success`, `Error`, `Empty` states
- Includes helper properties (`isLoading`, `isSuccess`, `isError`)
- Ready for ViewModel integration

---

### 20. ✅ FIXED: Activity Leak in Network Callbacks

**Location**: All Activities

**Status**: ✅ **IMPLEMENTED** - WeakReference used for activity contexts in coroutines.

**Changes Made**:
- All network callbacks now use `WeakReference<Activity>`
- Pattern: `val weakActivity = WeakReference(this)` before coroutine launch
- `weakActivity.get()?.let { ... }` used in callbacks
- Files updated: `LoginActivity.kt`, `MainActivity.kt`, `DetailsActivity.kt`, `PlayerActivity.kt`

---

### 21. ⚠️ PENDING: Backup/Data Extraction Rules Not Restricted

**Location**: [AndroidManifest.xml](../app/src/main/AndroidManifest.xml), [backup_rules.xml](../app/src/main/res/xml/backup_rules.xml), [data_extraction_rules.xml](../app/src/main/res/xml/data_extraction_rules.xml)

**Issue**: `android:allowBackup="true"` is enabled, but backup rules are still the default templates. This risks backing up sensitive preferences and profile data (even if encrypted, keys may not restore correctly across devices).

**Recommended Fix**:
- Either disable backups entirely (`android:allowBackup="false"`) **or**
- Explicitly exclude sensitive shared prefs (credentials, profile data, tokens) in both backup rule files.

**Example**:
```xml
<full-backup-content>
    <exclude domain="sharedpref" path="."/>
</full-backup-content>
```

## Summary Priority Matrix

| Issue | Severity | Effort | Priority | Status |
|-------|----------|--------|----------|--------|
| Plain text credentials | Critical | Low | P0 | ✅ Done |
| Cleartext HTTP | Critical | Medium | P0 | ✅ Done |
| ProGuard disabled | High | Low | P0 | ✅ Done |
| Hardcoded server URL | High | Low | P1 | ✅ Done |
| RenderScript deprecated | Medium | Medium | P1 | ✅ Done |
| Coroutine scope leaks | Medium | Low | P1 | ✅ Done |
| Deprecated onBackPressed | Low | Low | P2 | ✅ Done |
| Handler not cleaned up | Low | Low | P2 | ✅ Done |
| Logging in production | Medium | Low | P2 | ✅ Done |
| Network timeout config | Low | Low | P3 | ✅ Done |
| Missing error handling | Medium | Medium | P2 | ✅ Done |
| NPE in PlayerActivity | Medium | Low | P2 | ✅ Done |
| Certificate pinning | Medium | Medium | P2 | Pending |
| Network connectivity check | Low | Low | P3 | ✅ Done |
| Input validation | Low | Low | P3 | ✅ Done |
| Magic numbers | Low | Low | P3 | ✅ Done |
| Null safety in models | Low | Low | P3 | ✅ Done |
| Inconsistent errors | Low | Low | P3 | ✅ Done |
| Loading state mgmt | Low | Low | P3 | ✅ Done |
| Activity leak | Medium | Low | P3 | ✅ Done |
| Backup/data extraction rules | Medium | Low | P2 | Pending |

---

## Remaining Items

### Still TODO:
1. **Certificate Pinning** - Add SSL pinning for production server (requires server certificate SHA256 hash)
2. **Backup/Data Extraction Rules** - Disable backups or explicitly exclude sensitive prefs

### Potential Future Security Enhancements:

3. **Session Token Rotation** - Refresh authentication tokens periodically to limit exposure window

4. **Biometric Authentication** - Optional fingerprint/face unlock for profile access on supported devices

5. **Rate Limiting Awareness** - Handle 429 responses gracefully with retry-after headers

6. **Secure Deep Links** - Validate deep link parameters to prevent injection attacks

7. **Content Integrity Verification** - Verify downloaded content checksums for offline mode

### All Quick Wins Completed:
1. ✅ Enable ProGuard in release build
2. ✅ Add OkHttp timeouts
3. ✅ Disable logging in release
4. ✅ Replace `CoroutineScope()` with `lifecycleScope`
5. ✅ Fix deprecated APIs (RenderScript, onBackPressed)
6. ✅ Add proper error handling with user feedback
7. ✅ Network connectivity check before API calls
8. ✅ Input validation for login
9. ✅ Magic numbers extracted to constants
10. ✅ Null safety defaults in models
11. ✅ Centralized error handling
12. ✅ UiState sealed class for state management
13. ✅ WeakReference to prevent activity leaks

## New Utility Classes

| File | Purpose |
|------|--------|
| [Constants.kt](../app/src/main/java/com/example/vod/utils/Constants.kt) | Centralized configuration values |
| [ErrorHandler.kt](../app/src/main/java/com/example/vod/utils/ErrorHandler.kt) | Consistent error messaging |
| [NetworkUtils.kt](../app/src/main/java/com/example/vod/utils/NetworkUtils.kt) | Network connectivity checks |
| [UiState.kt](../app/src/main/java/com/example/vod/utils/UiState.kt) | Type-safe UI state management |

---

## 2026-02-08 Review Addendum (Open Issues)

### A. Critical: PIN-Protected Profile Bypass

**Location**: [ProfileSelectionActivity.kt](../app/src/main/java/com/example/vod/ProfileSelectionActivity.kt)

**Issue**: The PIN branch is still TODO and selection continues without verification.

**Recommendation**:
- Block profile selection until PIN verification succeeds.
- Validate PIN server-side and rate-limit attempts.

---

### B. High: Hardcoded HTTP Image Fallbacks

**Location**: [Models.kt](../app/src/main/java/com/example/vod/Models.kt)

**Issue**: Fallback image URLs use hardcoded cleartext `http://77.74.196.120/...`.

**Recommendation**:
- Build image URLs from `BuildConfig.BASE_URL`.
- Enforce HTTPS and null-safe path handling.

---

### C. High: Credentials Cleared on Transient Network Failures

**Location**: [LoginActivity.kt](../app/src/main/java/com/example/vod/LoginActivity.kt)

**Issue**: Network errors route to a handler that clears encrypted saved credentials.

**Recommendation**:
- Clear credentials only for explicit authentication failures.
- Preserve credentials for timeout/offline/temporary server failures.
