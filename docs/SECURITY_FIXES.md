# Security & Code Quality Fixes

This document outlines security vulnerabilities, code quality issues, and recommended fixes for the VOD app codebase.

**Last Updated**: 15 February 2026

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

### 5. ✅ FIXED: PIN-Protected Profile Bypass

**Location**: [ProfileSelectionActivity.kt](../app/src/main/java/com/example/vod/ProfileSelectionActivity.kt)

**Status**: ✅ **IMPLEMENTED** - PIN dialog now blocks profile selection until verified.

**Changes Made**:
- Added `showPinDialog()` method that displays a PIN entry dialog before selecting PIN-protected profiles
- Profile selection is blocked until PIN is entered and validated
- Auto-login path correctly skips PIN-protected profiles
- Server-side PIN validation occurs via the profile selection API (returns 403 with `pin_required` on mismatch)

---

### 6. ⚠️ OPEN: SecurePrefs Silently Falls Back to Unencrypted Storage

**Location**: [SecurePrefs.kt](../app/src/main/java/com/example/vod/utils/SecurePrefs.kt), lines 30-36

**Issue**: If `EncryptedSharedPreferences` fails (keystore corruption after backup/restore, OEM firmware issues), the fallback stores credentials (username, password, CSRF token) in plain `SharedPreferences` — readable on rooted devices or via ADB backup.

```kotlin
}.getOrElse { error ->
    Log.e(TAG, "EncryptedSharedPreferences unavailable...; using unencrypted fallback.", error)
    appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)  // UNENCRYPTED
}
```

**Recommended Fix**:
- Option A (fail-closed): Return null and force re-login instead of storing credentials unencrypted.
- Option B (partial): Fall back to unencrypted prefs but skip storing passwords — only store non-sensitive preferences.

---

## 🟠 High Priority Code Issues

### 7. ✅ FIXED: Deprecated RenderScript Usage

**Location**: [BlurTransformation.kt](../app/src/main/java/com/example/vod/BlurTransformation.kt)

**Status**: ✅ **IMPLEMENTED** - Replaced with pure Kotlin StackBlur algorithm (no external dependencies).

**Changes Made**:
- Removed deprecated `RenderScript` API
- Implemented StackBlur algorithm directly in Kotlin
- No external dependencies required - works on all Android versions (API 26+)

---

### 8. ✅ FIXED: Memory Leaks from Coroutine Scopes

**Location**: All Activities

**Status**: ✅ **IMPLEMENTED** - All coroutines now use lifecycle-aware scopes.

**Changes Made**:
- Replaced all `CoroutineScope(Dispatchers.IO).launch` with `lifecycleScope.launch`
- Coroutines automatically cancelled on activity destruction
- Files updated: `MainActivity.kt`, `DetailsActivity.kt`, `LoginActivity.kt`, `PlayerActivity.kt`

---

### 9. ✅ FIXED: Deprecated `onBackPressed()` Override

**Location**: [MainActivity.kt](../app/src/main/java/com/example/vod/MainActivity.kt)

**Status**: ✅ **IMPLEMENTED** - Now using `OnBackPressedDispatcher`.

**Changes Made**:
- Added `androidx.activity:activity-ktx:1.8.0` dependency
- Created `setupBackPressedHandler()` with `OnBackPressedCallback`
- Removed deprecated `onBackPressed()` override

---

### 10. ✅ FIXED: Missing Error Handling for Network Failures

**Location**: All Activities

**Status**: ✅ **IMPLEMENTED** - Proper error messages shown to users.

**Changes Made**:
- Added specific `IOException` handling for network errors
- User-friendly toast messages for all error types
- Files updated: `MainActivity.kt`, `DetailsActivity.kt`, `LoginActivity.kt`, `PlayerActivity.kt`

---

### 11. ✅ FIXED: Potential NullPointerException in PlayerActivity

**Location**: [PlayerActivity.kt](../app/src/main/java/com/example/vod/PlayerActivity.kt)

**Status**: ✅ **IMPLEMENTED** - Replaced forced non-null assertion with safe call.

**Changes Made**:
- Changed `player != null && player!!.isPlaying` to `player?.isPlaying == true`
- Added `onDestroy()` with proper cleanup
- Handler callbacks now properly removed

---

### 12. ✅ FIXED: Manual Cookie Header Construction in PlayerActivity

**Location**: [PlayerActivity.kt](../app/src/main/java/com/example/vod/PlayerActivity.kt)

**Status**: ✅ **IMPLEMENTED** - Cookie values now sanitized before header construction.

**Changes Made**:
- Cookie values are sanitized by stripping `\r`, `\n`, and null bytes before header construction
- Replaced imperative loop with functional `filter`/`joinToString` for clarity
- Prevents header injection via malicious cookie values

---

## 🟡 Medium Priority Issues

### 13. ✅ FIXED: No Network Connectivity Check

**Location**: [NetworkUtils.kt](../app/src/main/java/com/example/vod/utils/NetworkUtils.kt)

**Status**: ✅ **IMPLEMENTED** - Network connectivity check before all API calls.

**Changes Made**:
- Created `NetworkUtils.isNetworkAvailable()` utility function
- Added pre-flight network checks in `LoginActivity`, `MainActivity`, `DetailsActivity`, `PlayerActivity`
- User-friendly "No internet connection" message shown when offline

---

### 14. ✅ FIXED: Missing Input Validation

**Location**: [LoginActivity.kt](../app/src/main/java/com/example/vod/LoginActivity.kt)

**Status**: ✅ **IMPLEMENTED** - Username and password validation added.

**Changes Made**:
- Added `validateInput()` function with length checks
- Minimum username length: 3 characters
- Minimum password length: 4 characters
- Field-specific error messages displayed

---

### 15. ✅ FIXED: Handler on Main Looper Not Removed

**Location**: [PlayerActivity.kt](../app/src/main/java/com/example/vod/PlayerActivity.kt)

**Status**: ✅ **IMPLEMENTED** - Added proper cleanup in `onDestroy()`.

**Changes Made**:
- Added `onDestroy()` override with `progressHandler.removeCallbacksAndMessages(null)`
- Ensures all pending callbacks are cancelled

---

### 16. ⚠️ PENDING: No Certificate Pinning

**Issue**: App doesn't verify server certificate authenticity. Vulnerable to MITM attacks if a rogue CA certificate is installed on the device.

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

### 17. ✅ FIXED: Sensitive Data in Logs

**Location**: [NetworkModule.kt](../app/src/main/java/com/example/vod/NetworkModule.kt)

**Status**: ✅ **IMPLEMENTED** - Logging now disabled in release builds.

**Changes Made**:
- Logging level now conditional on `BuildConfig.DEBUG`
- Release builds use `Level.NONE`

---

### 18. ✅ FIXED: Missing Timeout Configuration

**Location**: [NetworkModule.kt](../app/src/main/java/com/example/vod/NetworkModule.kt)

**Status**: ✅ **IMPLEMENTED** - 30-second timeouts configured.

**Changes Made**:
- Added `connectTimeout(30, TimeUnit.SECONDS)`
- Added `readTimeout(30, TimeUnit.SECONDS)`
- Added `writeTimeout(30, TimeUnit.SECONDS)`

---

### 19. ✅ FIXED: `btnResume` Click Handler Missing Initialization Guard

**Location**: [DetailsActivity.kt](../app/src/main/java/com/example/vod/DetailsActivity.kt)

**Status**: ✅ **IMPLEMENTED** - Added `::video.isInitialized` guard to prevent crash.

**Changes Made**:
- Wrapped `btnResume` click lambda with `if (::video.isInitialized)` guard
- Prevents `UninitializedPropertyAccessException` if clicked before details load

---

### 20. ✅ FIXED: Play Endpoint Called From Details Screen for Subtitle Check

**Location**: [DetailsActivity.kt](../app/src/main/java/com/example/vod/DetailsActivity.kt)

**Status**: ✅ **IMPLEMENTED** - Removed the `/api/play` call from the details screen.

**Changes Made**:
- Removed the `checkSubtitleAvailability()` method and its call entirely
- Server-side fix now ensures `/api/details` returns `has_subtitles` and `subtitle_url` for all video types (movies and episodes)
- Details screen checks both `hasSubtitles` flag and `subtitleUrl` presence from the details response

---

### 21. ✅ FIXED: Backup/Data Extraction Rules Not Restricted

**Location**: [backup_rules.xml](../app/src/main/res/xml/backup_rules.xml), [data_extraction_rules.xml](../app/src/main/res/xml/data_extraction_rules.xml)

**Status**: ✅ **IMPLEMENTED** - Sensitive shared preferences excluded from backup and device transfer.

**Changes Made**:
- `backup_rules.xml` (API ≤30) now excludes `VOD_PREFS_ENCRYPTED`, `VOD_PROFILE_PREFS`, and EncryptedSharedPreferences key metadata
- `data_extraction_rules.xml` (API 31+) excludes the same files from both cloud backup and device transfer
- Non-sensitive prefs (`onboarding_prefs`, `VOD_UPDATER_PREFS`) are still backed up normally
- `allowBackup="true"` retained so users keep harmless app state across devices

---

## 🔵 Low Priority / Code Quality

### 22. ✅ FIXED: Magic Numbers

**Location**: [Constants.kt](../app/src/main/java/com/example/vod/utils/Constants.kt)

**Status**: ✅ **IMPLEMENTED** - All magic numbers extracted to centralized constants.

---

### 23. ✅ FIXED: Missing Null Safety in Models

**Location**: [Models.kt](../app/src/main/java/com/example/vod/Models.kt)

**Status**: ✅ **IMPLEMENTED** - All data classes now have safe defaults.

---

### 24. ✅ FIXED: Inconsistent Error Messages

**Location**: [ErrorHandler.kt](../app/src/main/java/com/example/vod/utils/ErrorHandler.kt)

**Status**: ✅ **IMPLEMENTED** - Centralized error handling with consistent messaging.

---

### 25. ✅ FIXED: No Loading State Management

**Location**: [UiState.kt](../app/src/main/java/com/example/vod/utils/UiState.kt)

**Status**: ✅ **IMPLEMENTED** - Sealed class for type-safe UI state management.

---

### 26. ✅ FIXED: Activity Leak in Network Callbacks

**Location**: All Activities

**Status**: ✅ **IMPLEMENTED** - WeakReference used for activity contexts in coroutines.

---

## Summary Priority Matrix

| Issue | Severity | Effort | Priority | Status |
|-------|----------|--------|----------|--------|
| Plain text credentials | Critical | Low | P0 | ✅ Done |
| Cleartext HTTP | Critical | Medium | P0 | ✅ Done |
| ProGuard disabled | High | Low | P0 | ✅ Done |
| PIN-protected profile bypass | Critical | Medium | P0 | ✅ Done |
| SecurePrefs unencrypted fallback | Critical | Low | P0 | ⚠️ Open |
| Hardcoded server URL | High | Low | P1 | ✅ Done |
| RenderScript deprecated | Medium | Medium | P1 | ✅ Done |
| Coroutine scope leaks | Medium | Low | P1 | ✅ Done |
| Manual cookie construction | Medium | Medium | P1 | ✅ Done |
| Deprecated onBackPressed | Low | Low | P2 | ✅ Done |
| Handler not cleaned up | Low | Low | P2 | ✅ Done |
| Logging in production | Medium | Low | P2 | ✅ Done |
| Certificate pinning | Medium | Medium | P2 | ⚠️ Pending |
| btnResume initialization guard | Medium | Low | P2 | ✅ Done |
| Play endpoint from details screen | Medium | Low | P2 | ✅ Done |
| Backup/data extraction rules | Medium | Low | P2 | ✅ Done |
| Network timeout config | Low | Low | P3 | ✅ Done |
| Missing error handling | Medium | Medium | P2 | ✅ Done |
| NPE in PlayerActivity | Medium | Low | P2 | ✅ Done |
| Network connectivity check | Low | Low | P3 | ✅ Done |
| Input validation | Low | Low | P3 | ✅ Done |
| Magic numbers | Low | Low | P3 | ✅ Done |
| Null safety in models | Low | Low | P3 | ✅ Done |
| Inconsistent errors | Low | Low | P3 | ✅ Done |
| Loading state mgmt | Low | Low | P3 | ✅ Done |
| Activity leak | Medium | Low | P3 | ✅ Done |

---

## Remaining Items

### Still TODO:
1. **SecurePrefs Fallback** - Decide on fail-closed vs. partial-fallback approach for encryption failures
2. **Certificate Pinning** - Add SSL pinning for production server (requires server certificate SHA256 hash)

### Recently Completed:
- ✅ **PIN Verification Flow** - Implemented PIN entry dialog with server-side validation
- ✅ **Cookie Header Validation** - Cookie values sanitized before header construction
- ✅ **btnResume Guard** - Added `::video.isInitialized` check in click listener
- ✅ **Play endpoint subtitle fallback removed** - Server now returns subtitle metadata in details response
- ✅ **Backup/Data Extraction Rules** - Sensitive prefs excluded from backup and device transfer

### Potential Future Security Enhancements:

8. **Session Token Rotation** - Refresh authentication tokens periodically to limit exposure window
9. **Biometric Authentication** - Optional fingerprint/face unlock for profile access on supported devices
10. **Rate Limiting Awareness** - Handle 429 responses gracefully with retry-after headers

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
