# Android Prompted In-App Updater (Sideloaded APK) - Draft Spec

Status: Approved design draft for implementation planning.

This document defines the self-hosted updater for Android builds distributed directly as APK files (not via Google Play).

## Scope

### In Scope
- Prompted in-app updates for sideloaded APK installs.
- Version checks against a public XML update feed.
- Display of changelog summary/items in the prompt.
- "Skip this version" behavior for non-mandatory updates.
- APK download + checksum verification + installer launch.

### Out of Scope
- Silent updates for normal app installs (requires device-owner/MDM/system privileges).
- Play Store In-App Updates API.

## App Configuration (Build-Time Environment Variables)

Because Android apps do not read OS env vars directly at runtime, CI/local env vars are mapped into `BuildConfig` values.

Required env vars:
- `VOD_UPDATE_APK_BASE_URL`
  - Example: `https://updates.example.com/vod/android/`
  - Used as base URL for `apkFileName` from XML feed.
- `VOD_UPDATE_FEED_URL`
  - Example: `https://updates.example.com/vod/android/update.xml`
  - XML feed location used for version checks.

Optional env vars:
- `VOD_UPDATE_CHECK_INTERVAL_HOURS`
  - Default: `24`
- `VOD_UPDATE_CHANNEL`
  - Default: `stable`
  - Build-time default channel. Overridden at runtime by server-provided `update_feed_url` (see below).

Recommended `BuildConfig` names:
- `UPDATE_APK_BASE_URL`
- `UPDATE_FEED_URL`
- `UPDATE_CHECK_INTERVAL_HOURS`
- `UPDATE_CHANNEL`

## Server-Driven Per-User Update Channel

The server assigns each user an update channel (`stable` or `beta`). Admins set this via Admin -> Users -> Edit User.

Both `/api/login` and `/api/session` responses include an `update_feed_url` field containing the XML feed URL for the user's assigned channel.

### App Integration

1. **After login:** Store `update_feed_url` from the login response. Use it for all subsequent update checks.
2. **On app resume:** Call `/api/session` and refresh the stored `update_feed_url` (admin may have changed the user's channel).
3. **Pre-login / no server URL:** Use the build-time `VOD_UPDATE_FEED_URL` (stable channel).
4. **Fallback:** If `update_feed_url` is absent in the API response (older server version), use the build-time `VOD_UPDATE_FEED_URL`.

This means the build-time `VOD_UPDATE_CHANNEL` env var is only used as a default. Once logged in, the server controls which feed the app checks. A single app build can receive stable or beta updates depending on the user's server-side setting.

## XML Feed Contract

The XML feed must be publicly reachable over HTTPS and should be cache-friendly.

Example:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<appUpdate schemaVersion="1">
  <channel>stable</channel>
  <latest>
    <versionCode>12</versionCode>
    <versionName>1.2.0</versionName>
    <mandatory>false</mandatory>
    <minSupportedVersionCode>9</minSupportedVersionCode>
    <apkFileName>vod-1.2.0-release.apk</apkFileName>
    <apkSha256>REPLACE_WITH_HEX_SHA256</apkSha256>
    <apkSizeBytes>45892310</apkSizeBytes>
    <publishedAt>2026-02-10T12:00:00Z</publishedAt>
    <changelog>
      <summary>Playback reliability and error clarity improvements.</summary>
      <item>Improved playback-limit handling and user messaging.</item>
      <item>Improved stream bootstrap resilience.</item>
    </changelog>
  </latest>
</appUpdate>
```

### XML Field Requirements

Required:
- `versionCode` (integer, must be greater than installed `versionCode` to trigger update)
- `versionName` (display string)
- `mandatory` (`true`/`false`)
- `apkFileName` (string, appended to `VOD_UPDATE_APK_BASE_URL`)
- `apkSha256` (lower/upper hex allowed)

Optional but recommended:
- `minSupportedVersionCode` (integer)
- `apkSizeBytes` (integer, for progress/UI validation)
- `publishedAt` (ISO-8601 UTC)
- `changelog.summary`
- `changelog.item` (repeatable)
- `channel` (string)

## Version Decision Rules

Inputs:
- `installedVersionCode` from package info
- XML `latest` values
- local `skipped_update_version_code` preference

Decision:
1. If XML cannot be fetched/parsed: fail silently (or non-blocking toast/log) and continue app usage.
2. If `latest.versionCode <= installedVersionCode`: no prompt.
3. If `latest.versionCode > installedVersionCode`: update available.
4. If `latest.mandatory = true`: prompt as mandatory, skip option hidden/disabled.
5. If `minSupportedVersionCode` exists and `installedVersionCode < minSupportedVersionCode`: treat as mandatory.
6. If non-mandatory and `skipped_update_version_code == latest.versionCode`: do not prompt.
7. Any newer `latest.versionCode` resets skip behavior automatically.

## Prompt UX Requirements

### Non-Mandatory Update Prompt
Title:
- `Update available`

Content:
- Current installed version
- New version (`versionName`)
- Changelog summary + list items (if present)

Actions:
- `Update now`
- `Later`
- `Skip this version`

### Mandatory Update Prompt
Title:
- `Update required`

Content:
- Same as above + "This update is required to continue."

Actions:
- `Update now` only

## "Skip This Version" Behavior

Persist locally:
- Key: `skipped_update_version_code` (integer)

Rules:
- Set only when user taps `Skip this version`.
- Applies only to that exact version code.
- Ignored for mandatory updates.
- Cleared when installed version catches up or when a higher version appears.

## Download and Install Flow

1. User taps `Update now`.
2. Download APK from: `UPDATE_APK_BASE_URL + apkFileName`.
3. Verify file checksum against `apkSha256` before installer launch.
4. If verification fails: delete APK and show error.
5. Launch system package installer with `FileProvider` URI.
6. If "Install unknown apps" permission is not granted:
   - open system settings for this app's install permission;
   - resume installer launch when permission is granted.

Notes:
- Installer confirmation is required for regular sideloaded apps.
- APK must be signed with the same certificate as installed app.

## Security Requirements

- HTTPS only for feed/APK URLs.
- SHA-256 checksum validation is mandatory.
- Reject downgrade installs (`latest.versionCode` must be > installed).
- Do not install if checksum mismatch or missing required fields.
- Prefer short-lived cache headers for XML feed so updates propagate quickly.

## Error Messaging Requirements

Recommended user-facing messages:
- `Unable to check for updates right now.`
- `Update file is invalid. Please try again later.`
- `Downloaded update failed verification.`
- `Install permission required to update this app.`

## Check Timing

Recommended:
- On app startup (throttled by `UPDATE_CHECK_INTERVAL_HOURS`).
- Optional manual action in settings/profile menu: `Check for updates`.

## Suggested Hosting Layout

Example:
- Feed: `https://updates.example.com/vod/android/update.xml`
- APK base URL: `https://updates.example.com/vod/android/`
- APK: `https://updates.example.com/vod/android/vod-1.2.0-release.apk`

## Acceptance Criteria (for implementation)

1. App prompts only when `latest.versionCode` is newer than installed.
2. Prompt includes changelog data from XML.
3. Non-mandatory updates allow `Later` and `Skip this version`.
4. Skip suppresses only the exact skipped version.
5. Mandatory updates cannot be skipped.
6. APK checksum is validated before install.
7. Installer launch respects Android unknown-app install permission flow.
