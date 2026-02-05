# Android Profiles & Playback API Addendum

This document describes the new **profile-aware playback history** and the API changes needed for Android clients to support profile selection.

If the Android app does **not** send a `profile_id`, the server automatically uses the **active profile** in the current session, so existing clients remain compatible.

`content_rating` values returned by details endpoints are stored as UK-mapped ratings (U/PG/12/12A/15/18/R18) when available.

---

## Quick Summary

- **New**: `GET /api/profiles` to list profiles and active profile.
- **New**: `POST /api/profiles_select` to set active profile.
- **New**: `max_content_rating` profile field (UK rating limit).
- **Updated**: Playback history is now stored **per profile**.
- **Updated**: `watch_list` supports optional `profile_id` for per-profile lists.
- **Optional**: Add `profile_id` to playback endpoints to override active profile.

---

## 1. List Profiles

**Endpoint:** `GET /api/profiles`

**Response:**
```json
{
  "status": "success",
  "active_profile_id": 12,
  "profiles": [
    {
      "id": 12,
      "name": "Default",
      "max_content_rating": null,
      "max_rating": null,
      "auto_skip_intro": false,
      "auto_skip_credits": false,
      "autoplay_next": true,
      "has_pin": false
    }
  ]
}
```

---

## 2. Select Active Profile

**Endpoint:** `POST /api/profiles_select`

**Request Body:**
```
profile_id=12
```

**Response:**
```json
{
  "status": "success",
  "active_profile_id": 12
}
```

---

## 3. Playback History (Per Profile)

Playback progress now keys by `(user_id, profile_id, video_id)` instead of `(user_id, video_id)`.

If `profile_id` is omitted, the server uses the **active profile**.

### 3.1 Save Progress
**Endpoint:** `POST /api/progress`

**Request Body (optional `profile_id`):**
```
id=123
time=452
paused=0
profile_id=12
```

### 3.2 Resume Time
**Endpoint:** `GET /api/details?id=123`

**Optional:** `&profile_id=12`

Response fields like `resume_time` will reflect the selected profile.

### 3.3 Library/Search Results
**Endpoints:**  
`GET /api/library`  
`GET /api/search`

**Optional:** `profile_id` query param to return resume times for a specific profile.

---

## 4. Recommended Android Flow

1. **Login** → store session cookie.
2. **GET /api/profiles** → show profile picker.
3. **POST /api/profiles_select** → set active profile.
4. **Playback / progress** calls (optionally include `profile_id`).

---

## 5. Compatibility Notes

- Existing Android clients **continue working** without changes.
- If the app does not send `profile_id`, playback progress is saved under the **active profile** (default profile after login).
- Once the app adds profile selection, progress history will be fully separated per profile.

---

## 6. Optional Enhancements

If the Android app wants to expose preferences:
- `auto_skip_intro`
- `auto_skip_credits`
- `autoplay_next`
- `max_content_rating` (UK content rating limit, e.g., 12/15/18)

Legacy: `max_rating` (IMDb-based) still appears for older profiles but should be avoided for new UI.

These are returned by `GET /api/profiles` and can be used to guide playback UI behavior.
