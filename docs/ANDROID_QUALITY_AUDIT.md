# Android Quality Audit — Wake Capture

**Audit date:** 2026-09-20
**Auditor:** Android Quality Audit (automated)
**Application:** Wake Capture (`com.wakecapture.app`)
**Version:** 1.0.0

---

## Repository Summary

| Property | Value |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose (BOM 2024.12.01) |
| Design system | Material 3 with dynamic color |
| compileSdk | 36 |
| targetSdk | 36 |
| minSdk | 33 (Android 13) |
| AGP | 8.7.3 |
| Architecture | Single module, MVVM, Hilt DI |
| Navigation | Navigation Compose 2.8.5 |
| State management | StateFlow + collectAsStateWithLifecycle |
| Persistence | Room 2.6.1 + DataStore Preferences 1.1.1 |
| Services | AudioCaptureService (foreground, microphone), CaptureTileService (Quick Settings) |
| Permissions | RECORD_AUDIO, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE, POST_NOTIFICATIONS |
| Networking | None |
| Analytics/Crash reporting | None |
| Localization | English only, most strings hardcoded |
| Tests | Unit tests for CaptureCoordinator, CaptureRepository, CaptureState, RecordingFileStore |

### User Journeys

1. **Onboarding** — 3-page pager: welcome, permissions, Quick Settings instructions
2. **Arm/Disarm** — Toggle switch on home screen
3. **Start Capture** — Via in-app button (ARMED state) or Quick Settings tile
4. **Recording** — Foreground service with notification, elapsed time display
5. **Stop Capture** — Via FAB, notification action, or Quick Settings tile
6. **History** — List of past captures
7. **Detail** — View capture metadata, delete capture
8. **Settings** — Configure silence timeout, max recording duration, auto-disarm

---

## Findings

---

### [F01] runBlocking on main thread in MainActivity causes ANR risk

**Category:** Performance / Stability

**Severity:** Critical (P0)

**Guideline:** Android Vitals — ANR avoidance; Android Core App Quality CQ-1 (app does not crash, does not ANR)

**Current behavior:**

`MainActivity.onCreate()` calls `determineStartDestination()` which uses `runBlocking { preferencesManager.onboardingCompleted.first() }` to read from DataStore. DataStore reads perform disk I/O. This blocks the main thread during activity creation.

`MainActivity.onResume()` calls `runBlocking { coordinator.checkAndHandleExpiry(); ... }` which also performs DataStore I/O on the main thread.

The `onDisarm` callback passed to `WakeCaptureNavGraph` also uses `runBlocking { coordinator.disarm() }`.

**Evidence:**

- `app/src/main/java/com/wakecapture/app/ui/MainActivity.kt:64-69` — `onResume()` with `runBlocking`
- `app/src/main/java/com/wakecapture/app/ui/MainActivity.kt:78` — `determineStartDestination()` with `runBlocking`
- `app/src/main/java/com/wakecapture/app/ui/MainActivity.kt:55-57` — `onDisarm` callback with `runBlocking`

**Why it matters:**

DataStore I/O can take 50-500ms depending on device state. On cold start with encrypted storage or slow flash, this can exceed the 5-second ANR threshold. Every `onResume` (app switch, lock/unlock) re-triggers this, compounding the risk. This directly violates Android Core App Quality requirement CQ-1.

**Recommended change:**

1. For `determineStartDestination()`: read onboarding state synchronously from DataStore's cached value using `runBlocking` only as a fallback, or restructure to show a brief loading state while the destination is resolved asynchronously.
2. For `onResume()`: move the expiry/permission check to a coroutine launched in the activity's `lifecycleScope`.
3. For `onDisarm`: launch in a coroutine scope instead of blocking.

**Risk:** Low — these are straightforward coroutine scope changes.

**Verification:** Monitor for ANR-free cold starts. StrictMode disk-read-on-main-thread violations should be absent. Test lock/unlock cycles rapidly.

---

### [F02] Service onDestroy cancels scope before interrupted capture can persist

**Category:** Stability / Data loss

**Severity:** Critical (P0)

**Guideline:** Android Core App Quality — app does not lose user data

**Current behavior:**

In `AudioCaptureService.onDestroy()`, when a recording is active, the service launches `coordinator.onCaptureInterrupted(durationMs)` in `serviceScope`, then immediately calls `serviceScope.cancel()`. The coroutine is cancelled before the Room database insert completes, so the interrupted capture metadata is lost.

**Evidence:**

- `app/src/main/java/com/wakecapture/app/capture/service/AudioCaptureService.kt:189-198`

```kotlin
override fun onDestroy() {
    if (audioRecorder.isRecording) {
        val durationMs = System.currentTimeMillis() - recordingStartTime
        audioRecorder.stop()
        audioRecorder.release()
        serviceScope.launch { coordinator.onCaptureInterrupted(durationMs) }  // launched...
    }
    serviceScope.cancel()  // ...and immediately cancelled
    super.onDestroy()
}
```

**Why it matters:**

When the system kills the service (memory pressure, battery optimization, phone call), the audio file is written to disk but the database record is never created. The recording becomes an orphan file — invisible to the user. For a half-awake user, this is silent data loss of their capture.

**Recommended change:**

Use `runBlocking` for the critical persistence path in `onDestroy()`, or better, use `runBlocking(Dispatchers.IO)` to ensure the database write completes before the service is destroyed. Alternatively, write the interrupted state synchronously.

**Risk:** Low — `onDestroy` is allowed to do brief synchronous work before returning.

**Verification:** Kill the service mid-recording via `adb shell am stop-service`. Verify the capture appears in history with "interrupted" state.

---

### [F03] No audio focus handling

**Category:** UX / Platform compliance

**Severity:** High (P1)

**Guideline:** Android media guidelines — audio focus; SPEC section 18 (Audio focus / interruptions)

**Current behavior:**

`AudioCaptureService` never calls `AudioManager.requestAudioFocus()` or registers an `OnAudioFocusChangeListener`. If a phone call, navigation prompt, or other app requests audio focus, the recording continues silently capturing potentially garbled or muted audio. There is no interruption handling for audio focus loss.

**Evidence:**

- `app/src/main/java/com/wakecapture/app/capture/service/AudioCaptureService.kt` — no `AudioManager` usage anywhere in the file
- SPEC section 18 explicitly requires handling: incoming phone calls, other audio apps, Bluetooth headset changes, audio focus loss

**Why it matters:**

Without audio focus, the app can record over phone calls (privacy concern), fail silently when the microphone is taken by another app, and produce corrupt recordings that the user discovers only later.

**Recommended change:**

1. Request `AudioManager.AUDIOFOCUS_GAIN` before starting recording.
2. Register an `OnAudioFocusChangeListener`.
3. On `AUDIOFOCUS_LOSS`: finalize recording as interrupted.
4. On `AUDIOFOCUS_LOSS_TRANSIENT`: pause or finalize depending on product requirements.
5. On `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`: continue recording (voice capture doesn't duck).

**Risk:** Medium — requires testing with phone calls and competing audio apps.

**Verification:** Start recording, receive a phone call. Verify recording transitions to INTERRUPTED. Start recording, play music from another app that requests focus. Verify behavior.

---

### [F04] No edge-to-edge insets handling on OnboardingScreen and PermissionDeniedScreen

**Category:** UX / Visual quality

**Severity:** High (P1)

**Guideline:** Android Core App Quality — edge-to-edge display; Android 15+ requires edge-to-edge

**Current behavior:**

`MainActivity` calls `enableEdgeToEdge()`, which makes the app draw behind system bars. `HomeScreen`, `SettingsScreen`, `CaptureHistoryScreen`, and `CaptureDetailScreen` use `Scaffold` which handles window insets automatically. However, `OnboardingScreen` and `PermissionDeniedScreen` do NOT use `Scaffold` and have only hardcoded padding (`32.dp`). On devices with tall status bars, navigation gestures, or display cutouts, content may be drawn behind system bars.

**Evidence:**

- `app/src/main/java/com/wakecapture/app/ui/onboarding/OnboardingScreen.kt:63-136` — no inset handling
- `app/src/main/java/com/wakecapture/app/ui/permission/PermissionDeniedScreen.kt:54-107` — no inset handling
- `app/src/main/java/com/wakecapture/app/ui/MainActivity.kt:40` — `enableEdgeToEdge()`

**Why it matters:**

With `targetSdk 36`, edge-to-edge is enforced on Android 15+. Content drawn behind the status bar or gesture navigation bar is unreadable or untappable. The onboarding flow is the user's first experience with the app.

**Recommended change:**

Add `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` or wrap in `Scaffold` for both screens.

**Risk:** Low — additive change.

**Verification:** Test on Android 15+ device. Verify content is not behind system bars. Test with gesture navigation and 3-button navigation.

---

### [F05] No predictive back support

**Category:** Navigation / Platform compliance

**Severity:** High (P1)

**Guideline:** Android Adaptive App Quality — predictive back gesture support; required for targetSdk 35+

**Current behavior:**

The app targets SDK 36 but does not opt into predictive back animations. The manifest does not set `android:enableOnBackInvokedCallback="true"`. Navigation Compose provides some automatic predictive back support, but without the manifest flag, the system-level predictive back animation is disabled.

**Evidence:**

- `app/src/main/AndroidManifest.xml` — no `android:enableOnBackInvokedCallback` attribute
- No `OnBackPressedCallback` implementations in the codebase

**Why it matters:**

Starting with Android 15 (targetSdk 35), apps must support predictive back. Without it, the back gesture feels broken — there's no preview animation, and the behavior differs from other apps on the device.

**Recommended change:**

Add `android:enableOnBackInvokedCallback="true"` to the `<application>` tag in AndroidManifest.xml. Test that all back navigations work correctly with the predictive back animation.

**Risk:** Medium — requires testing all navigation paths to ensure no custom back handling breaks.

**Verification:** Enable gesture navigation. Long-press the back gesture to verify the predictive back preview animation appears. Navigate back from every screen and verify correct destination.

---

### [F06] Hardcoded UI strings prevent localization

**Category:** UX / Accessibility

**Severity:** Medium (P2)

**Guideline:** Android Core App Quality — i18n support; Android accessibility guidelines

**Current behavior:**

Nearly all user-facing strings are hardcoded in Kotlin source files rather than using string resources from `strings.xml`. Only `app_name` and `capture` are in string resources.

**Evidence:**

Hardcoded strings found in:
- `HomeScreen.kt` — "Wake Capture", "Disarmed", "Armed", "Starting...", "Recording", "Stopping...", "Interrupted", "Permission required", "Start Capture", "Auto-disarm in...", "Auto-disarm expired"
- `OnboardingScreen.kt` — "Wake Capture", "Capture your thoughts...", "Permissions", "Grant Permissions", "Skip for now", "Quick Settings Tile", "Get Started", "Next"
- `PermissionDeniedScreen.kt` — "Microphone Permission Required", "Open Settings", "Disarm"
- `SettingsScreen.kt` — "Settings", "Recording", "Auto-stop after silence", "Maximum recording length", "Auto-disarm", "Auto-disarm after"
- `CaptureHistoryScreen.kt` — "Capture History", "No captures yet"
- `CaptureDetailScreen.kt` — "Capture Details", "Delete capture?", "Date", "Duration", "Status", "Source", "Format", "Delete", "Cancel"
- `AudioCaptureService.kt` — "Recording", "Stop"
- `CaptureError.kt` — all error messages

**Why it matters:**

1. Localization is impossible without extracting strings.
2. Android Lint warns about hardcoded strings.
3. TalkBack may have language detection issues with hardcoded strings.

**Recommended change:**

Extract all user-facing strings to `strings.xml`. This is a mechanical change that doesn't affect behavior.

**Risk:** Very low — purely additive.

**Verification:** Build and verify no missing resource references. Run with device language changed.

---

### [F07] Accessibility: Missing semantics and live region announcements

**Category:** Accessibility

**Severity:** High (P1)

**Guideline:** Android accessibility guidelines; WCAG 2.1 Level AA

**Current behavior:**

Several accessibility issues:

1. **Recording state changes are not announced.** When recording starts/stops, the state text in ArmCard updates silently. TalkBack users receive no announcement.
2. **Error card has no live region.** When an error appears, TalkBack users must manually discover it.
3. **Page indicator dots have no semantics.** The onboarding page indicator provides no TalkBack information about current page or total pages.
4. **Recording timer has no accessibility.** The elapsed time counter in RecordingIndicator updates every second but has no semantics for screen readers.
5. **Countdown timer has no accessibility.** The auto-disarm countdown text updates but isn't announced.

**Evidence:**

- `HomeScreen.kt:151-301` — RecordingIndicator has no `liveRegion` modifier
- `HomeScreen.kt:304-316` — ErrorCard has no `liveRegion` or `semantics` modifier
- `OnboardingScreen.kt:182-203` — PageIndicator has no semantics
- `HomeScreen.kt:196-208` — State text in ArmCard has no state announcement

**Why it matters:**

Blind and low-vision users cannot know when recording starts, stops, or fails without manual screen exploration. For a sleepy-use-case app where visual attention is minimal, this is particularly important.

**Recommended change:**

1. Add `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` to the state text in ArmCard.
2. Add `Modifier.semantics { liveRegion = LiveRegionMode.Assertive }` to ErrorCard.
3. Add role and state descriptions to the page indicator.
4. Add content description to the recording indicator card.

**Risk:** Low — additive semantics changes.

**Verification:** Enable TalkBack. Arm the device, start recording, stop recording. Verify each state transition is announced. Navigate through onboarding with TalkBack enabled.

---

### [F08] Startup theme mismatch causes light flash in dark mode

**Category:** Visual quality

**Severity:** Medium (P2)

**Guideline:** Android design guidelines — dark theme support

**Current behavior:**

The XML theme `Theme.WakeCapture` in `values/themes.xml` extends `android:Theme.Material.Light.NoActionBar`. This theme is used during the brief period between activity creation and Compose rendering. In dark mode, the user sees a bright white flash before the Compose dark theme takes over.

**Evidence:**

- `app/src/main/res/values/themes.xml:3` — `parent="android:Theme.Material.Light.NoActionBar"`

**Why it matters:**

For a wake-up app used in dark environments, a flash of white light is particularly jarring. It also looks like a visual bug.

**Recommended change:**

1. Create `values-night/themes.xml` with a dark variant: `parent="android:Theme.Material.NoActionBar"`.
2. Or better, use `Theme.Material3.DayNight.NoActionBar` as the parent to automatically match system theme.
3. Set `android:windowBackground` to match the Material 3 surface color.

**Risk:** Low — XML theme change only affects the splash window.

**Verification:** Set device to dark mode. Cold-start the app. Verify no white flash before Compose renders.

---

### [F09] No adaptive layout for tablets and large screens

**Category:** Adaptive UI

**Severity:** Medium (P3)

**Guideline:** Android Adaptive App Quality guidelines; Large screen app quality

**Current behavior:**

All screens use `fillMaxWidth()` without any maximum width constraint. On tablets, foldables, and split-screen, the UI stretches across the entire width, creating extremely wide cards, buttons, and text lines that are difficult to read.

**Evidence:**

- `HomeScreen.kt:104-109` — Column with `fillMaxSize()` and `padding(16.dp)`
- `OnboardingScreen.kt:148-149` — Column with `fillMaxSize()` and `padding(32.dp)`
- `SettingsScreen.kt:55-60` — Column with `fillMaxSize()`
- No `WindowSizeClass` usage anywhere in the codebase
- No `widthIn(max = ...)` constraints anywhere

**Why it matters:**

Text lines over 80 characters wide are difficult to read. Buttons that span 800+ dp look unprofessional. This is a quality issue visible on any tablet, foldable, or desktop window mode.

**Recommended change:**

Add `Modifier.widthIn(max = 600.dp).align(Alignment.CenterHorizontally)` to the main content column on each screen. For a more complete solution, use `WindowSizeClass` to provide different layouts on larger screens.

**Risk:** Low — additive modifier.

**Verification:** Test on a tablet or emulator at 800dp+ width. Test in split-screen mode.

---

### [F10] `android:allowBackup="true"` without data extraction rules

**Category:** Privacy / Security

**Severity:** Medium (P2)

**Guideline:** Android backup/restore guidelines; targetSdk 31+ requires `dataExtractionRules`

**Current behavior:**

The manifest declares `android:allowBackup="true"` without specifying `android:dataExtractionRules` (for Android 12+) or `android:fullBackupContent` (for older). This means all app data — including audio recordings — is included in device backups and cloud backups.

**Evidence:**

- `app/src/main/AndroidManifest.xml:10` — `android:allowBackup="true"`

**Why it matters:**

Audio recordings are sensitive personal data. Uncontrolled backup could expose recordings to cloud backup services or device transfer, violating user privacy expectations. The SPEC says "Do not automatically upload audio in MVP."

**Recommended change:**

Add `android:dataExtractionRules="@xml/data_extraction_rules"` and create rules that exclude the `captures/` directory from backups while allowing preferences to be backed up.

**Risk:** Low — additive configuration.

**Verification:** Run `adb backup` and verify audio files are not included.

---

### [F11] Delete confirmation dialog uses non-destructive button styling

**Category:** UX

**Severity:** Low (P4)

**Guideline:** Material 3 dialog guidelines — destructive actions should use error color

**Current behavior:**

The delete confirmation dialog in `CaptureDetailScreen` uses a default `TextButton` for the "Delete" action. There is no visual distinction between the destructive action and the cancel action.

**Evidence:**

- `app/src/main/java/com/wakecapture/app/ui/detail/CaptureDetailScreen.kt:124-126`

**Why it matters:**

Users can accidentally tap "Delete" without realizing it's the destructive option, especially in a half-awake state.

**Recommended change:**

Style the "Delete" button with `colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)`.

**Risk:** None.

**Verification:** Open capture detail, tap delete icon, verify "Delete" button is visually distinct.

---

### [F12] Capture history empty state is not vertically centered

**Category:** Visual quality

**Severity:** Low (P4)

**Guideline:** Material 3 empty states guidance

**Current behavior:**

When the capture history is empty, the "No captures yet" text is aligned to the top of the content area, not centered vertically on screen. The Column uses `horizontalAlignment = Alignment.CenterHorizontally` but no `verticalArrangement = Arrangement.Center`.

**Evidence:**

- `app/src/main/java/com/wakecapture/app/ui/history/CaptureHistoryScreen.kt:54-62`

**Why it matters:**

Minor visual polish — the empty state looks unfinished.

**Recommended change:**

Add `verticalArrangement = Arrangement.Center` to the empty-state Column.

**Risk:** None.

**Verification:** Open history with no captures. Verify text is centered.

---

### [F13] SettingsScreen uses HomeViewModel — wrong ViewModel scope

**Category:** Architecture

**Severity:** Low (P4)

**Guideline:** Android architecture — ViewModel scoping

**Current behavior:**

`SettingsScreen` injects `HomeViewModel` via `hiltViewModel()`. Since Settings and Home are separate navigation destinations, they receive separate ViewModel instances. The Settings ViewModel has full access to `toggleArm()` and `requestStartCapture()`, which are not relevant to the settings screen.

**Evidence:**

- `app/src/main/java/com/wakecapture/app/ui/settings/SettingsScreen.kt:39` — `viewModel: HomeViewModel = hiltViewModel()`

**Why it matters:**

Code organization issue — the settings screen reads settings values and writes them back, which works because changes go through DataStore and are picked up by the Home ViewModel's flows. But the shared ViewModel type exposes unnecessary functionality and may cause confusion.

**Recommended change:**

Create a dedicated `SettingsViewModel` that only exposes setting read/write operations.

**Risk:** Low — architectural cleanup.

**Verification:** Settings changes still reflect on home screen after ViewModel separation.

---

### [F14] Notification action label "Stop" has no icon content description

**Category:** Accessibility

**Severity:** Medium (P2)

**Guideline:** Android accessibility — notification accessibility

**Current behavior:**

The foreground service notification has a "Stop" action using `R.drawable.ic_stop`. The `ic_stop.xml` vector drawable has no talkback-accessible description and the action relies on the text label "Stop" which is adequate but the icon has a hardcoded white fill that may not be visible on all notification shade backgrounds.

**Evidence:**

- `app/src/main/java/com/wakecapture/app/capture/service/AudioCaptureService.kt:184` — `.addAction(R.drawable.ic_stop, "Stop", stopPending)`
- `app/src/main/res/drawable/ic_stop.xml:8` — `android:fillColor="#FFFFFF"`

**Why it matters:**

On devices with light notification shades (some OEM skins), the white stop icon may be invisible. The text "Stop" is accessible but the icon should follow notification icon guidelines (single-color, tinted by the system).

**Recommended change:**

Use `@android:color/white` or remove the hardcoded fill color and let the system tint the icon. Or use `android:tint` on the notification builder. Actually, notification action icons should be monochrome — the system applies tinting. The `#FFFFFF` fill is correct for notification icons on most Android versions.

**Risk:** Low.

**Verification:** Check notification appearance on light and dark notification shades across OEMs.

---

### [F15] No process death recovery for orphaned recordings

**Category:** Data integrity

**Severity:** Medium (P2)

**Guideline:** SPEC section 18 — process death handling (listed as open item)

**Current behavior:**

If the process is killed mid-recording (OEM battery management, system low memory, force stop), the M4A file on disk may be incomplete (AAC in MPEG4 requires finalization metadata from `MediaRecorder.stop()`). There is no recovery mechanism to detect orphaned recording files on next app launch.

**Evidence:**

- SPEC section 18 explicitly identifies this as an open item
- No recovery breadcrumb logic exists in the codebase
- `RecordingFileStore` has no orphan detection

**Why it matters:**

The user's recording is silently lost. For the target use case (half-awake capture), this may be the only opportunity to capture a thought.

**Recommended change:**

Write a "recording in progress" breadcrumb file at recording start, delete on successful stop. On next app launch, check for orphaned breadcrumbs and either attempt file recovery or clean up with a user notification.

**Risk:** Medium — requires careful handling of partially-written files.

**Verification:** Kill app process during recording via `adb shell am force-stop`. Relaunch app. Verify orphan is detected and user is notified.

---

### [F16] AutoDisarmCountdown can show negative time

**Category:** UX

**Severity:** Low (P4)

**Guideline:** UX — state consistency

**Current behavior:**

The `AutoDisarmCountdown` composable shows "Auto-disarm expired" when `remainingMs <= 0`, but there's a window between when the timer expires and when `CaptureCoordinator.checkAndHandleExpiry()` runs where the UI shows "expired" but the app is still in ARMED state. The coordinator doesn't immediately react to the countdown expiring in the UI.

**Evidence:**

- `app/src/main/java/com/wakecapture/app/ui/home/HomeScreen.kt:228-260`
- `app/src/main/java/com/wakecapture/app/capture/CaptureCoordinator.kt:225-234`

**Why it matters:**

Minor UX inconsistency — the user sees "expired" but the arm switch is still on. The next interaction will trigger the expiry check and disarm.

**Recommended change:**

Call `checkAndHandleExpiry()` from the HomeViewModel on a periodic basis, or have the countdown composable trigger a callback when it hits zero.

**Risk:** Low.

**Verification:** Set auto-disarm to 8 hours, arm, wait for expiry. Verify the UI transitions to DISARMED without user interaction.

---

### [F17] CaptureTileService leaks coroutine scope

**Category:** Stability

**Severity:** Medium (P2)

**Guideline:** Android TileService lifecycle; memory leak prevention

**Current behavior:**

`CaptureTileService` creates a `CoroutineScope(SupervisorJob() + Dispatchers.Main)` as a field, but only cancels it in `onDestroy()`. Android `TileService` lifecycle is not guaranteed to call `onDestroy()` promptly — the service may be unbound and rebound multiple times without destruction. This means coroutines launched in `onClick` may outlive the tile service's bound state.

**Evidence:**

- `app/src/main/java/com/wakecapture/app/system/quicksettings/CaptureTileService.kt:27` — `serviceScope` field
- `app/src/main/java/com/wakecapture/app/system/quicksettings/CaptureTileService.kt:110-113` — cancel only in `onDestroy()`

**Why it matters:**

Not a critical issue since the coroutines are short-lived (state reads and service starts), but it's a lifecycle management concern that could cause issues if more complex async work is added later.

**Recommended change:**

Consider using `LifecycleService` patterns or scoping the coroutine work more tightly.

**Risk:** Low.

**Verification:** Add/remove Quick Settings tile repeatedly while monitoring for leaked coroutines.

---

### [F18] Deprecated API: ExperimentalFoundationApi opt-in for HorizontalPager

**Category:** SDK / Dependencies

**Severity:** Low (P5)

**Guideline:** API stability

**Current behavior:**

`OnboardingScreen` uses `@OptIn(ExperimentalFoundationApi::class)` for `HorizontalPager`. With Compose Foundation 1.5.0+, `HorizontalPager` graduated from experimental. The Compose BOM 2024.12.01 includes a version well past 1.5.0.

**Evidence:**

- `app/src/main/java/com/wakecapture/app/ui/onboarding/OnboardingScreen.kt:46` — `@OptIn(ExperimentalFoundationApi::class)`

**Why it matters:**

Unnecessary opt-in suppresses potential future warnings about actual experimental APIs.

**Recommended change:**

Remove the `@OptIn` annotation and the `ExperimentalFoundationApi` import.

**Risk:** None.

**Verification:** Build compiles without the opt-in.

---

### [F19] Dependency versions are outdated

**Category:** SDK / Dependencies

**Severity:** Medium (P3)

**Guideline:** Android Core App Quality — use current stable dependencies

**Current behavior:**

Several dependencies are significantly behind current stable releases (as of September 2026):

| Dependency | Current | Latest stable (approx) |
|---|---|---|
| AGP | 8.7.3 | 8.9.x+ |
| Kotlin | 2.0.21 | 2.1.x+ |
| Compose BOM | 2024.12.01 | 2026.x |
| Room | 2.6.1 | 2.7.x+ |
| Hilt | 2.53.1 | 2.54+ |
| Navigation | 2.8.5 | 2.9.x+ |
| Lifecycle | 2.8.7 | 2.9.x+ |

**Evidence:**

- `gradle/libs.versions.toml:1-19`

**Why it matters:**

Outdated dependencies miss bug fixes, performance improvements, and new API support. Particularly for Navigation Compose (predictive back improvements) and Lifecycle (Compose integration improvements).

**Recommended change:**

Update dependencies incrementally, testing after each update. Prioritize: Compose BOM (for predictive back), Navigation, and Lifecycle.

**Risk:** Medium — dependency updates can introduce breaking changes.

**Verification:** Full build and test pass after updates. Test all user flows.

---

### [F20] No landscape or multi-window adaptation

**Category:** Adaptive UI

**Severity:** Medium (P3)

**Guideline:** Android Adaptive App Quality — app renders correctly in all supported configurations

**Current behavior:**

The app doesn't declare `android:screenOrientation` (which is correct — it should support rotation). However, the layouts are designed exclusively for portrait phone form factor. In landscape, the vertically-centered onboarding content may not fit, and the home screen's vertically stacked cards waste horizontal space.

**Evidence:**

- `app/src/main/AndroidManifest.xml` — no orientation lock (correct)
- All screens use single-column vertical layouts with no landscape alternatives
- No `LocalConfiguration.current.orientation` checks

**Why it matters:**

Users may rotate their phone in bed. On tablets, landscape is the default orientation. In split-screen mode, the app gets a landscape-like window.

**Recommended change:**

At minimum, ensure all screens are scrollable in landscape (most already use `verticalScroll` or `LazyColumn`). For the onboarding screen, verify content fits with reduced height.

**Risk:** Low for scroll fixes; medium for layout changes.

**Verification:** Rotate phone to landscape on every screen. Test split-screen mode.

---

### [F21] Switch in ArmCard has no accessible state label

**Category:** Accessibility

**Severity:** Medium (P2)

**Guideline:** Android accessibility — custom controls must have state descriptions

**Current behavior:**

The Switch component in the ArmCard toggles arming state, but TalkBack announces it generically as "Switch, on/off" without context about what it controls. The surrounding "Wake Capture" text and state text are separate from the Switch's semantics.

**Evidence:**

- `app/src/main/java/com/wakecapture/app/ui/home/HomeScreen.kt:210-216`

**Why it matters:**

TalkBack users cannot easily understand what the switch controls without exploring the surrounding text elements first.

**Recommended change:**

Add `Modifier.semantics { contentDescription = "Arm Wake Capture" }` to the switch, or merge the row semantics so the entire card announces as a single interactive element.

**Risk:** Low.

**Verification:** Enable TalkBack, navigate to home screen, verify the switch announces its purpose.

---

### [F22] No Snackbar action for error states

**Category:** UX

**Severity:** Low (P4)

**Guideline:** Material 3 — Snackbars with actions; error recovery

**Current behavior:**

Error messages from `CaptureCoordinator` are shown via Snackbar but provide no actionable recovery. For example, "Not enough storage space (50 MB required)" gives no way to open storage settings. "Microphone permission is required" gives no shortcut to settings.

**Evidence:**

- `app/src/main/java/com/wakecapture/app/ui/home/HomeScreen.kt:70-75` — Snackbar shows message only
- `app/src/main/java/com/wakecapture/app/capture/CaptureError.kt` — error messages with no recovery path

**Why it matters:**

Users see an error but don't know how to fix it without exploring the app or system settings.

**Recommended change:**

Add Snackbar actions for recoverable errors (e.g., "Open Settings" for permission errors, "Manage Storage" for storage errors).

**Risk:** Low.

**Verification:** Trigger each error condition. Verify Snackbar shows with appropriate action.

---

---

## Severity Summary

| Severity | Count | IDs |
|---|---|---|
| Critical (P0) | 2 | F01, F02 |
| High (P1) | 4 | F03, F04, F05, F07 |
| Medium (P2) | 7 | F06, F08, F10, F14, F15, F17, F21 |
| Medium (P3) | 3 | F09, F19, F20 |
| Low (P4) | 4 | F11, F12, F13, F16 |
| Low (P5) | 2 | F18, F22 |

**Total findings: 22**

---

## Sections marked N/A

- **L. Notifications** — Notifications are used correctly for the foreground service. Channel configuration is appropriate. No promotional notifications.
- **M. Audio/media** — App uses microphone recording, not media playback. Audio focus finding covered in F03.
- **N. Sharing** — App does not share content externally.
- **O. Background work** — Foreground service usage is appropriate for the recording use case. WorkManager is declared as a dependency but not used yet (future post-processing per spec). No inappropriate background work.

---

## Verification Status Legend

- **VERIFIED** — Confirmed by code inspection and/or testing
- **STATICALLY VERIFIED** — Confirmed by code inspection only (no device test)
- **NOT TESTED** — Requires device/emulator testing
- **NOT APPLICABLE** — Section does not apply to this app
- **BLOCKED** — Cannot verify without additional setup

All findings in this audit are **STATICALLY VERIFIED** unless otherwise noted. Device testing is required before marking any finding as fully VERIFIED.
