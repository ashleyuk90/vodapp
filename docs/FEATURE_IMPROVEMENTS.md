# Feature Improvements

This document outlines recommended feature enhancements for the VOD app, organized by priority and complexity.

---

## High Priority

### 1. Offline Download Support

**Description**: Allow users to download videos for offline viewing.

**Benefits**:
- Watch content without internet connection
- Reduce data usage on mobile networks
- Better user experience during travel

**Implementation**:
- Use Media3's `DownloadService` for background downloads
- Store downloads in app-specific external storage
- Track download progress and status in local database
- Add download queue management UI
- Implement DRM if required by content licensing

**Complexity**: High

---

### 2. User Profiles / Multi-Account Support ✅ IMPLEMENTED

**Description**: Support multiple user profiles per account (like Netflix).

**Status**: ✅ **IMPLEMENTED**

**Implementation Details**:
- `ProfileSelectionActivity.kt` with horizontal profile picker
- `ProfileManager.kt` for profile state management
- Profile switching via side menu button
- Default profile with auto-login (long-press to set)
- `EXTRA_SKIP_AUTO_LOGIN` flag when explicitly switching profiles
- Profile-specific watch history and progress

**Complexity**: Medium-High

---

### 3. Improved Search with Filters ✅ IMPLEMENTED

**Description**: Enhance search with genre, year, and rating filters.

**Status**: ✅ **IMPLEMENTED**

**Implementation Details**:
- Filter chips below search bar
- Content type filters: Movies, Series
- Genre filters: Action, Comedy, Drama, Horror, Sci-Fi, Animation
- Year range filters: 2020s, 2010s, 2000s, Before 2000
- Minimum rating filters: 6.0+, 7.0+, 8.0+
- Clear Filters button with red styling
- Filters persist during search session
- Filters are applied when the user submits a search query

**Complexity**: Low-Medium

---

### 4. Continue Watching Progress Bar ✅ IMPLEMENTED

**Description**: Show resume position as a visual progress bar on video cards.

**Status**: ✅ **IMPLEMENTED**

**Implementation Details**:
- Progress bar shown on video cards in Continue Watching row
- Yellow progress indicator with dark background
- Percentage-based width calculation
- Syncs with backend playback position

**Complexity**: Low

---

## Medium Priority

### 5. Picture-in-Picture (PiP) Mode

**Description**: Enable floating video window when navigating away from player.

**Benefits**:
- Multitask while watching
- Quick content browsing without stopping playback

**Implementation**:
```kotlin
// In PlayerActivity
override fun onUserLeaveHint() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        enterPictureInPictureMode(
            PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
        )
    }
}
```

**Complexity**: Low

---

### 6. Chromecast / Cast Support

**Description**: Cast videos to TV via Google Cast protocol.

**Benefits**:
- Watch on TV from mobile device
- Share viewing experience

**Implementation**:
- Integrate Google Cast SDK
- Add Cast button to player controls
- Handle remote playback control
- Sync progress from cast session

**Complexity**: Medium-High

---

### 7. Video Quality Selection

**Description**: Allow users to manually select video quality (Auto, 1080p, 720p, 480p).

**Benefits**:
- Control data usage
- Consistent quality preference

**Implementation**:
```kotlin
// Add to PlayerActivity
private fun showQualitySelector() {
    val trackSelector = player?.trackSelector as? DefaultTrackSelector
    val qualities = listOf("Auto", "1080p", "720p", "480p")
    // Show dialog and apply track selection
}
```

**Complexity**: Medium

---

### 8. Audio Track Selection

**Description**: Allow switching between audio tracks (language, commentary, etc.).

**Benefits**:
- Multi-language support
- Accessibility for different audio tracks

**Implementation**:
- Read available audio tracks from ExoPlayer
- Show selection dialog in player UI
- Remember preference per user/content

**Complexity**: Medium

---

### 9. Parental Controls

**Description**: Content ratings filter and PIN protection for mature content.

**Benefits**:
- Family-friendly experience
- Content age restrictions

**Status**: ⚠️ **PARTIAL**

**Current State**:
- Profile model already includes `max_content_rating` and `has_pin`
- UI only shows rating limit badge on profile cards
- Details view now surfaces `content_rating` in metadata pills
- **Missing**: PIN entry dialog + enforcement of rating limits on playback/search

**Implementation**:
- Add maturity ratings to content model
- PIN entry for restricted content
- Profile-level maturity settings

**Complexity**: Medium

---

### 10. Recommendations Row

**Description**: Add "Because you watched X" and "Trending" rows to dashboard.

**Benefits**:
- Better content discovery
- Personalized experience

**Implementation**:
- Backend recommendation API
- New dashboard sections
- Algorithm based on watch history

**Complexity**: Medium (requires backend support)

---

## Lower Priority / Nice to Have

### 11. Watch Party / Co-Viewing

**Description**: Synchronized viewing with friends.

**Benefits**:
- Social viewing experience
- Remote shared watching

**Complexity**: High

---

### 12. Skip Intro / Credits Detection ✅ IMPLEMENTED

**Description**: Auto-detect and offer skip buttons for intros and credits.

**Status**: ✅ **IMPLEMENTED**

**Implementation Details**:
- Skip Intro button appears during intro segment (based on API markers)
- Skip Credits button appears when credits start (if next episode available)
- Next Episode button appears near end-of-playback threshold when a next episode exists
- Auto-skip option per profile (`auto_skip_intro`, `auto_skip_credits`)
- Smooth fade-in/out animations for buttons
- Focus handling for D-pad navigation
- Intro markers: API returns `intro_marker` with `start_seconds`/`end_seconds`
- Credits markers: API returns `credits_marker` with `credits_duration_seconds` and `credits_end_offset_seconds`
  - Credits start is calculated as: `video_duration - credits_end_offset_seconds - credits_duration_seconds`
  - ContentMarker model includes `getCreditsStartSeconds(videoDurationSeconds)` helper method
- ContentMarker model added to Models.kt with support for both legacy and new fields

**Complexity**: Medium (requires backend metadata)

---

### 13. Multiple Audio/Subtitle Tracks

**Description**: Expand subtitle support beyond single SRT file.

**Implementation**:
- Support multiple subtitle languages
- Language preference memory
- Font size/style customization

**Complexity**: Medium

---

### 14. Favorites/Collections

**Description**: User-created collections beyond Watch Later.

**Benefits**:
- Organize content by user preference
- Quick access to curated content

**Complexity**: Medium

---

### 15. Playback Preferences UI (Auto-skip & Autoplay Next) ✅ IMPLEMENTED

**Description**: Expose profile playback settings in a UI screen (auto-skip intro/credits, autoplay next episode).

**Status**: ✅ **IMPLEMENTED**

**Implementation Details**:
- Added `PlaybackPreferencesActivity` with TV-friendly toggle rows
- Preferences persist via `ProfileManager` (encrypted prefs)
- Player now honors `autoplay_next` on playback end
- Access: long-press the profile button in the side menu

**Complexity**: Low-Medium

---

### 16. Voice Search

**Description**: Voice input for search on Android TV.

**Implementation**:
```kotlin
// Add to search handling
val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
}
```

**Complexity**: Low

---

### 17. Recently Played History

**Description**: Dedicated screen showing full watch history.

**Benefits**:
- Find previously watched content
- Track viewing habits

**Complexity**: Low

---

### 18. Content Ratings & Reviews

**Description**: User ratings and reviews for content.

**Benefits**:
- Community engagement
- Help users decide what to watch

**Complexity**: Medium

---

### 19. Video Thumbnails Preview

**Description**: Show thumbnail previews when scrubbing through video timeline.

**Implementation**:
- Generate thumbnail sprites on server
- Display in ExoPlayer scrubber

**Complexity**: Medium-High

---

### 20. Playback Speed Control

**Description**: 0.5x, 1x, 1.25x, 1.5x, 2x playback speeds.

**Implementation**:
```kotlin
player?.setPlaybackSpeed(1.5f)
```

**Complexity**: Low

---

### 21. Background Audio

**Description**: Continue audio playback when app is backgrounded (for music/podcasts).

**Implementation**:
- MediaSession integration
- Foreground service for playback
- Media notification controls

**Complexity**: Medium

---

## UI/UX Enhancements

### 21. Smooth Animations ✅ IMPLEMENTED

**Status**: Implemented

- Page transitions between activities (slide in/out, fade)
- Card focus animations with scale effects
- Loading skeleton screens with shimmer animation
- Animation utilities in `AnimationHelper.kt`

**Implementation Details**:
- Created `slide_in_right.xml`, `slide_out_left.xml`, `slide_in_left.xml`, `slide_out_right.xml` animations
- Added `fade_in.xml`, `fade_out.xml` for smooth transitions
- Skeleton layouts: `item_skeleton_card.xml`, `layout_skeleton_row.xml`
- Shimmer animation drawables

### 22. Landscape/Portrait Support ✅ IMPLEMENTED

**Status**: Implemented

- Responsive layouts for phone, tablet, and TV
- Phone portrait optimized for readability and touch (2-column grid)
- Phone landscape tuned for density (3-column grid)
- TV locked to landscape; phones/tablets auto-rotate
- Portrait-specific Details layout for mobile (`layout-port/activity_details.xml`)
- Mobile side menu is collapsible; alphabet bar hidden on phones
- Watch Later grid uses the same responsive span counts and padding as the main library

**Implementation Details**:
- Created `values-sw600dp/dimens.xml` for 7"+ tablets
- Created `values-sw720dp/dimens.xml` for 10"+ tablets/TV
- Created `values-land/dimens.xml` for phone landscape tuning
- Added `ResponsiveUtils.kt` for screen size calculations
- Added `OrientationUtils.kt` to allow phone rotation while keeping TV landscape
- Grid adapts to screen size: phone portrait 2 cols; phone landscape 3 cols; tablet portrait 5 cols; tablet landscape 6 cols; TV 6–7 cols

### 23. Dark/Light Theme Toggle

- System theme following
- Manual override option

### 24. Accessibility Improvements ✅ IMPLEMENTED

**Status**: Implemented

- TalkBack support with content descriptions
- Accessibility headings for sections
- Screen reader utilities in `AccessibilityUtils.kt`
- High contrast mode strings prepared

**Implementation Details**:
- Added content descriptions to all interactive elements
- Section headers marked as accessibility headings
- Video card descriptions for TalkBack
- `AccessibilityUtils.kt` with helpers for dynamic descriptions

### 25. Onboarding Flow ✅ IMPLEMENTED

**Status**: Implemented

- First-time user tutorial with ViewPager2
- 7 pages covering all app features
- Tutorial-focused content (not marketing)

**Implementation Details**:
- `OnboardingActivity.kt` with paged introduction
- Pages: Welcome, Navigation, Browsing, Profiles, Search, Watch Later, Playback
- Explains D-pad navigation, profile auto-login, search filters, playback controls
- Indicator dots and Next/Skip navigation
- SharedPreferences for completion tracking
- App launches to onboarding first, then login

---

## Additional Feature Ideas

### 26. Sleep Timer

**Description**: Auto-pause playback after a set duration.

**Benefits**:
- Avoid falling asleep with content playing
- Save bandwidth and power

**Implementation**:
- Timer options: 15, 30, 45, 60, 90 minutes
- Countdown overlay in player
- "Still watching?" prompt option

**Complexity**: Low

---

### 27. Binge Mode / Auto-Play Settings

**Description**: Configurable auto-play behavior for series.

**Benefits**:
- Customizable viewing experience
- Option to disable auto-play for mindful viewing

**Implementation**:
- Settings: Auto-play next episode (On/Off)
- Countdown duration: 5s, 10s, 15s, Off
- Skip intro automatically option

**Complexity**: Low

---

### 28. Content Bookmarks

**Description**: Mark specific timestamps within videos for later reference.

**Benefits**:
- Return to favorite moments
- Educational content reference points

**Implementation**:
- Bookmark button in player controls
- Named bookmarks with timestamps
- Bookmark list in details view

**Complexity**: Medium

---

### 29. Data Saver Mode

**Description**: Prefer lower quality streams to reduce bandwidth.

**Benefits**:
- Mobile data conservation
- Works better on slow connections

**Implementation**:
- Settings toggle for Data Saver
- Auto-select 480p/720p max when enabled
- Show data usage estimates

**Complexity**: Low-Medium

---

### 30. Improved Subtitle Styling

**Description**: Customizable subtitle appearance.

**Benefits**:
- Accessibility for different visual needs
- Personal preference support

**Implementation**:
- Font size: Small, Medium, Large, Extra Large
- Text color and background options
- Position: Top, Bottom
- Caption style presets

**Complexity**: Medium

---

### 31. Keyboard Remote Shortcuts

**Description**: Number key shortcuts for Android TV remotes.

**Benefits**:
- Faster navigation for power users
- Jump to specific playback positions

**Implementation**:
- Numbers 1-9 jump to 10%-90% of video
- 0 jumps to beginning
- Number keys for menu navigation

**Complexity**: Low

---

### 32. Stream Error Recovery ✅ IMPLEMENTED

**Description**: Automatic retry and fallback for failed streams.

**Status**: ✅ **IMPLEMENTED**

**Implementation Details**:
- Auto-retry with exponential backoff (2s → 4s → 8s → 16s max)
- Maximum 3 retry attempts before showing final error
- Saves playback position before retry to resume seamlessly
- Network-aware: waits for connection before retry
- Clear error messages for different failure types (HTTP errors, timeout, connection failed)
- Manual retry option: tap screen after max retries to try again
- Handles: network failures, HTTP errors, connection timeouts

**Complexity**: Medium

---

## Implementation Priority Matrix

| Feature | Impact | Effort | Priority | Status |
|---------|--------|--------|----------|--------|
| User Profiles | High | High | P1 | ✅ Done |
| Search Filters | Medium | Low | P1 | ⚠️ Partial |
| Continue Watching | Medium | Low | P1 | ✅ Done |
| Onboarding Tutorial | Medium | Medium | P1 | ✅ Done |
| Smooth Animations | Medium | Medium | P1 | ✅ Done |
| Responsive Layouts | Medium | Medium | P1 | ✅ Done |
| Accessibility | High | Medium | P1 | ✅ Done |
| Offline Downloads | High | High | P2 | Pending |
| PiP Mode | Medium | Low | P2 | Pending |
| Quality Selection | Medium | Medium | P2 | Pending |
| Chromecast | High | High | P2 | Pending |
| Parental Controls | Medium | Medium | P2 | ⚠️ Partial |
| Skip Intro | Medium | Medium | P2 | ✅ Done |
| Voice Search | Low | Low | P3 | Pending |
| Playback Speed | Low | Low | P3 | Pending |
| Sleep Timer | Low | Low | P3 | Pending |
| Data Saver Mode | Low | Low | P3 | Pending |
| Stream Error Recovery | Medium | Medium | P2 | ✅ Done |
| Playback Preferences UI | Low | Low | P3 | ✅ Done |

---

## 2026-02-08 Review Additions (Near-Term)

### 33. Complete PIN Verification Flow

**Description**: Enforce PIN checks before opening protected profiles.

**Why now**:
- Existing profile model already supports `has_pin`.
- Current flow allows bypass, so this closes a real security/parental-controls gap.

**Implementation**:
- Add PIN input dialog on profile select.
- Verify PIN with backend.
- Add retry limit and temporary lockout messaging.

**Complexity**: Medium

---

### 34. Continue Watching Management

**Description**: Let users hide entries, mark as watched, and reset progress.

**Benefits**:
- Cleaner dashboard rows.
- Better control over recommendations and playback history.

**Implementation**:
- Long-press actions on continue-watching cards.
- New endpoints or reuse progress endpoint with reset action.
- Sync state immediately and optimistically update UI.

**Complexity**: Medium

---

### 35. Offline Action Queue (Watchlist + Progress)

**Description**: Queue watchlist/progress changes when offline and replay when network returns.

**Benefits**:
- Fewer user-visible errors.
- Better resilience on unstable connections.

**Implementation**:
- Persist pending actions locally (Room).
- Background worker retries with exponential backoff.
- Conflict handling (latest-write-wins for progress).

**Complexity**: Medium-High
