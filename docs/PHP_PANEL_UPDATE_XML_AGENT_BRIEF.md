# PHP Panel Agent Brief: Generate Android Update XML Feed

Purpose: this is the implementation brief for the PHP-side panel agent that will generate and publish the Android updater XML feed used by the app.

Related app-side contract:
- `docs/ANDROID_IN_APP_UPDATER_SPEC.md`
- `docs/ANDROID_API_GUIDE.md`

## Goal

Implement panel functionality to publish a valid `update.xml` file over HTTPS for the Android prompted in-app updater (sideloaded APK flow).

The Android app will compare installed `versionCode` with the XML `latest.versionCode` and prompt users accordingly.

## Public Output Contract

The panel must publish XML compatible with this structure:

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

## Required Fields

Required for valid feed:
- `versionCode` (integer > 0)
- `versionName` (string)
- `mandatory` (`true`/`false`)
- `apkFileName` (string)
- `apkSha256` (64-char hex SHA-256)

Optional but recommended:
- `minSupportedVersionCode`
- `apkSizeBytes`
- `publishedAt` (ISO-8601 UTC, `YYYY-MM-DDTHH:MM:SSZ`)
- `channel`
- `changelog.summary`
- one or more `changelog.item`

## Panel Features To Build

1. Add/update form in admin panel for Android release metadata:
- Channel (`stable` default, optional `beta`)
- Version code
- Version name
- Mandatory flag
- Min supported version code (optional)
- Changelog summary
- Changelog items (repeatable list)
- APK file selection/upload or existing APK picker

2. APK handling:
- Store APK in configured updates directory (or reference an existing file).
- Compute SHA-256 server-side from stored APK.
- Compute APK size in bytes.
- Persist `apkFileName`, hash, size.

3. Publish action:
- Generate XML from current published release metadata.
- Write file atomically (temp file + rename).
- Publish to public HTTPS path referenced by app env var `VOD_UPDATE_FEED_URL`.

4. Optional channel support:
- Either one feed per channel (recommended): `update-stable.xml`, `update-beta.xml`
- Or one feed containing `<channel>` and panel ensures app points to the correct feed URL.

## Validation Rules

Enforce at save/publish time:
- `versionCode` integer and greater than current published version for same channel.
- `minSupportedVersionCode` <= `versionCode` when provided.
- `apkFileName` exists and file is readable.
- SHA-256 computed and valid hex length 64.
- Reject publish if required fields missing.
- XML generation must escape special characters (`&`, `<`, `>`, quotes).

## Security + Operational Requirements

- Admin-authenticated access only.
- HTTPS only for serving XML and APK.
- Restrict upload to `.apk` extension and valid MIME checks.
- Keep previous APKs available during rollout to avoid broken links.
- Use UTF-8 XML without BOM.
- Use atomic write to avoid partial XML reads.

## Suggested Data Model (minimal)

Table: `app_updates`
- `id` (pk)
- `channel` (string, default `stable`)
- `version_code` (int)
- `version_name` (string)
- `mandatory` (bool)
- `min_supported_version_code` (int nullable)
- `apk_file_name` (string)
- `apk_sha256` (char(64))
- `apk_size_bytes` (bigint nullable)
- `changelog_summary` (text nullable)
- `changelog_items_json` (json/text nullable)
- `published_at` (datetime UTC)
- `is_published` (bool)

## XML Generation Rules

- Emit exactly one `<latest>` node for the currently published release per channel.
- Serialize booleans as `true` / `false` (lowercase).
- If optional values are null, omit the node (do not emit empty invalid placeholders).
- If changelog items are empty, keep `<changelog><summary>...</summary></changelog>` or omit `changelog` entirely.

## Example Publish Paths

- Feed URL: `https://updates.example.com/vod/android/update.xml`
- APK URL base: `https://updates.example.com/vod/android/`
- APK file: `https://updates.example.com/vod/android/vod-1.2.0-release.apk`

## Acceptance Tests (must pass)

1. XML validity:
- `curl -fsSL https://.../update.xml`
- XML parses without errors (e.g., `xmllint --noout update.xml`)

2. Field correctness:
- `versionCode` matches panel release.
- `apkFileName` exists at public URL.
- `apkSha256` matches downloaded file hash.

3. Version behavior:
- Publishing higher version updates XML immediately.
- Mandatory flag toggles in XML correctly.
- Channel isolation works if channels are enabled.

4. Failure behavior:
- Invalid metadata blocks publish with clear admin error.
- Partial writes never exposed publicly.

## Non-Goals

- Do not implement Android app code in this task.
- Do not implement silent update/device-owner logic.

## Deliverables From PHP Agent

1. Panel UI/form for release metadata.
2. Server-side validation + persistence.
3. XML generator and publish flow.
4. Public route/file serving for XML and APK.
5. Short README/update in panel docs with deployment and environment notes.
