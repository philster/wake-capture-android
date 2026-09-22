# Technical Specification — Wake Capture for Android

## 0. Purpose

Build an Android audio-only "Wake Capture" application optimized for a user who is half-awake during the night or immediately after waking.

Primary interaction:

1. User arms Wake Capture before sleep.
2. Device is locked.
3. User performs one deliberate system-level Capture action.
4. Audio recording begins immediately.
5. User speaks.
6. User stops capture with another deliberate action, or an inactivity timeout ends it.
7. Raw audio is durably saved locally.
8. Transcription/AI processing happens later and must never be required for capture success.

Do NOT implement video capture in this version.

The app must obey Android microphone permission, foreground-service, notification, privacy-indicator, lock-screen, and background-execution rules. Never bypass OS security boundaries.

---

## 1. Platform / implementation target

- Application ID: `com.wakecapture.app`
- Language: Kotlin 2.0+ (required for integrated Compose compiler plugin)
- UI: Jetpack Compose
- Architecture: native Android for MVP.
- Target SDK: current Play Store-required SDK at implementation time.
- Minimum SDK: 33 (Android 13) — provides `POST_NOTIFICATIONS` runtime permission, `requestAddTileService()`, foreground service type declarations, and a modern baseline.
- Primary APIs:
  - Android Service / Foreground Service
  - `TileService`
  - `StatusBarManager.requestAddTileService()` where available
  - `MediaRecorder` or `AudioRecord`
  - Notification APIs
  - App Shortcuts
  - optional App Actions / AppFunctions later
  - Kotlin Coroutines
  - Jetpack Compose
  - Room
  - WorkManager for post-processing, not live recording

Android's current rules are especially important:
- microphone foreground services use `foregroundServiceType="microphone"`;
- apps targeting Android 14+ need `FOREGROUND_SERVICE_MICROPHONE`;
- `RECORD_AUDIO` is a while-in-use permission;
- Android restricts starting microphone foreground services from the background;
- user/system-initiated exceptions must be used correctly.

---

## 2. Product invariants

### 2.1 Capture invariants

- Capture is always explicit.
- Wake Capture arming never accesses the microphone.
- No always-on microphone.
- No custom wake-word detector.
- No automatic recording based solely on time, motion, sleep state, screen state, or proximity.
- Recording starts only after an explicit user/system interaction that Android permits.
- Recording uses a microphone foreground service.
- Recording shows the required foreground-service notification.
- Raw audio is saved locally before success is reported.
- Network is not required.

### 2.2 Privacy invariants

- Request `android.permission.RECORD_AUDIO`.
- Declare `android.permission.FOREGROUND_SERVICE`.
- Declare `android.permission.FOREGROUND_SERVICE_MICROPHONE`.
- Declare `android.permission.POST_NOTIFICATIONS` (required on Android 13+).
- Declare the service with `android:foregroundServiceType="microphone"`.
- Do not hide the foreground-service notification.
- Do not circumvent Android microphone privacy controls.
- Do not use AccessibilityService for capture.
- Do not use Device Admin or other privileged mechanisms to evade lock-screen restrictions.
- Do not automatically upload audio in MVP.

---

## 3. Primary Android entry point

### 3.1 Quick Settings Tile

Implement a `TileService`.

Tile:
- label: `Capture`
- icon: microphone symbol
- state should reflect whether a recording is active, where practical.

The user must explicitly add the tile to Quick Settings.

On supported Android versions, use `StatusBarManager.requestAddTileService()` to request addition of the tile after onboarding.

The tile is the preferred Android system-level entry point because it is a direct user action and does not require a custom background listener.

### 3.2 In-app capture

When armed, the home screen shows a "Start Capture" button. This follows the same path as the Quick Settings tile: `requestStartCapture()` → `startForegroundService()`. The button is hidden when disarmed or already recording.

### 3.3 Arming UX

The app UI provides:

Wake Capture:
- Armed / Disarmed toggle
- Auto-disarm countdown displayed when armed (live-ticking; shows `Xh XXm` when over 1 hour, `MM:SS` when under 1 hour, or hidden when set to "Until I disarm")

Settings (configurable):
- Auto-stop after silence: configurable via selector (10, 15, 30, 45, 60, 90, 120 seconds; default 30)
- Maximum recording length: configurable via selector (1, 2, 5, 10, 15, 30 minutes; default 5)
- Auto-disarm after: 8 hours (default) / 12 hours / Until I disarm

Auto-disarm persistence:
- On arm, persist `armTimestamp` and `autoDisarmDuration` to DataStore.
- On disarm (manual or auto), clear both values.
- The source of truth for expiry is `armTimestamp + autoDisarmDuration > now`, not an in-memory timer. The in-memory timer is only a UI convenience for countdown display.
- On app launch or intent/tile invocation, evaluate expiry first. If expired, transition to `DISARMED` before processing any other action.
- Arming must survive app termination, background kill, and device restart. Android routinely kills background apps during sleep; tying arm state to process lifetime would break the core use case.

The armed state does NOT access the microphone. It is purely a readiness flag that gates whether system entry points (tile, widget, assistant) will attempt to start a capture.

### 3.4 Important Android limitation

Do NOT assume that every Quick Settings interaction is sufficient to bypass all foreground-service/microphone restrictions on every Android release.

The implementation must be tested against the current Android version.

The current Android documentation states that background-started foreground services have restrictions, and microphone foreground services are subject to while-in-use permission restrictions. Explicit user interactions such as widget interactions and other documented exceptions can affect eligibility.

Therefore the TileService must use the documented system interaction path and the recording service must validate that the start is permitted.

If the OS rejects the start:
- do not retry in a loop;
- never attempt a hidden workaround;
- from the tile: call `startActivityAndCollapse()` to launch the app showing the error state. This works from the lock screen (may require authentication, which is acceptable);
- from the app (foreground): show a Snackbar with actionable guidance;
- fallback: post a notification if the activity cannot be launched.

---

## 4. Secondary entry points

Implement later/alongside Quick Settings:

1. Home Screen widget.
2. Launcher shortcut.
3. Notification action.
4. Assistant/App Actions.
5. AppFunctions on supported Android versions when useful.

Do not make an OEM-specific hardware-button hack part of the core product.

Hardware-button behavior varies substantially between manufacturers and is generally not a portable third-party application capability.

---

## 5. Wake Capture state machine

```
                  arm()                    startCapture()
 ┌──────────┐  ──────────>  ┌──────────┐  ──────────────>  ┌──────────┐
 │ DISARMED │               │  ARMED   │                   │ STARTING │
 └──────────┘  <──────────  └──────────┘                   └────┬─────┘
                 disarm()       ^  ^  ^                         │
                                │  │  │    service started &    │
                                │  │  │    recorder ready       │
                                │  │  │                         v
                                │  │  │                    ┌──────────┐
                   stop ok ─────┘  │  │                    │RECORDING │
                   (persists as    │  │                    └──┬────┬──┘
                    SAVED)         │  │                       │    │
                                   │  │ user stop /           │    │  interruption /
                  ┌──────────┐     │  │ max duration /        │    │  audio focus loss /
                  │ STOPPING │ <───┼──┼── silence timeout ────┘    │  service killed
                  └──────────┘     │  │                            │
                                   │  │                            v
                                   │  │                  ┌─────────────┐
                                   │  └─── arm() ────────│ INTERRUPTED │
                                   │                     └─────────────┘
                                   │
                  ┌────────────────────┐
                  │ PERMISSION_DENIED  │ <── STARTING, when RECORD_AUDIO denied
                  └────┬──────────┬────┘
                       │          │
                       │          └── permission restored ──> ARMED
                       └── disarm() ──> DISARMED

                  ┌──────────┐
                  │  FAILED  │ <── any state, on unrecoverable error
                  └────┬─────┘
                       │
                       └───── arm() ────> ARMED
```

States:

- `DISARMED` — default; no capture possible.
- `ARMED` — ready for capture; no microphone access. Persisted to DataStore; survives process death.
- `STARTING` — foreground service being promoted, recorder initializing.
- `RECORDING` — actively capturing audio.
- `STOPPING` — finalizing file and persisting metadata.
- `SAVED` — persistence state only, written to `CaptureEntity.state`. After `STOPPING` completes, coordinator returns to `ARMED`.
- `INTERRUPTED` — partial audio preserved after system interruption (audio focus loss, service kill, phone call).
- `PERMISSION_DENIED` — `RECORD_AUDIO` permission revoked or denied. User must re-enable in Settings.
- `FAILED` — unrecoverable error (excluding permission denial, which uses `PERMISSION_DENIED`).

Transitions:

`DISARMED -> ARMED`
- explicit user action in the app UI.

`ARMED -> STARTING`
- explicit Capture system interaction (tile, widget, assistant).
- system entry points require armed state. If disarmed (or auto-disarm has expired), show an error instructing the user to arm first in the app.

`STARTING -> RECORDING`
- foreground service successfully started/promoted and recorder initialized.

`RECORDING -> STOPPING`
- user stop action, maximum-duration limit, or configured silence timeout.

`STOPPING -> ARMED`
- file closed, metadata committed, capture persisted with `SAVED` state.
- coordinator returns to `ARMED` so the user can immediately capture again without re-arming.
- the auto-disarm timer (if active) continues running; it is not reset by a capture cycle.

`RECORDING -> INTERRUPTED`
- audio focus loss, service termination, phone call, process/system interruption.
- partial audio is finalized and persisted with `INTERRUPTED` state.

`INTERRUPTED -> ARMED`
- re-arm after interrupted capture.

`STARTING -> PERMISSION_DENIED`
- `RECORD_AUDIO` permission is denied or revoked.
- shows dedicated UI with instructions to grant permission in Settings.
- on return to app, coordinator rechecks permission; if granted, auto-transitions to `ARMED`.

`PERMISSION_DENIED -> DISARMED`
- user cancels / disarms.

`PERMISSION_DENIED -> ARMED`
- permission restored while app is in foreground.

Any state -> `FAILED`
- unrecoverable error (excluding permission denial).

`FAILED -> ARMED`
- user re-arms after failure.

`SAVED -> ARMED`, `FAILED -> ARMED`, `INTERRUPTED -> ARMED`
- re-arm after terminal states.

Never:
`ARMED -> RECORDING` without an explicit user action.

---

## 6. AndroidManifest

Conceptual requirements:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<application ...>

    <service
        android:name=".capture.service.AudioCaptureService"
        android:exported="false"
        android:foregroundServiceType="microphone">
    </service>

    <service
        android:name=".system.quicksettings.CaptureTileService"
        android:exported="true"
        android:label="@string/capture"
        android:icon="@drawable/ic_mic"
        android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">

        <intent-filter>
            <action android:name="android.service.quicksettings.action.QS_TILE" />
        </intent-filter>
    </service>

</application>
```

Adjust exported/permission details to the current Android SDK requirements.

Do not copy this manifest blindly; validate against the current target SDK.

---

## 7. Onboarding and runtime permissions

### 7.1 Onboarding flow

First launch presents a 3-page horizontal pager:

1. **Welcome** — app icon, name, tagline. "Next" button.
2. **Permissions** — requests `RECORD_AUDIO` and `POST_NOTIFICATIONS` together. "Grant Permissions" button launches the system permission dialog. "Skip for now" advances without granting.
3. **Quick Settings Tile** — instructions for adding the tile to the QS panel. "Get Started" completes onboarding and navigates to the home screen.

Onboarding completion is persisted to DataStore (`onboardingCompleted`). The flow runs once and is not re-shown.

### 7.2 Permission requirements

Before the user can arm Wake Capture:

1. Explain microphone use.
2. Request `RECORD_AUDIO`.
3. Confirm permission is granted.
4. Only then enable the Wake Capture configuration.

The foreground-service microphone permission is a manifest permission, while `RECORD_AUDIO` is the runtime microphone permission.

On Android 13+ (API 33+), `POST_NOTIFICATIONS` is also a runtime permission. Request it during onboarding alongside `RECORD_AUDIO`, since the foreground-service notification is required for recording.

If microphone permission is denied:
- Wake Capture cannot be enabled.
- Explain how to grant it.
- Never attempt recording through another API.

### 7.3 Permission revoked after onboarding

Android allows users to revoke permissions at any time via Settings, which may kill the app process. On the next interaction (tile tap, widget tap, app launch):

1. `MainActivity.determineStartDestination()` checks onboarding and permission state separately: `!onboardingDone → ONBOARDING`, `!hasPermissions → PERMISSION_DENIED`, else `HOME`. This avoids re-showing onboarding when only the permission was revoked.
2. Coordinator checks `RECORD_AUDIO` permission state FIRST, before any other action.
3. If denied: transition to `PERMISSION_DENIED` state.
4. From tile: `startActivityAndCollapse()` to launch `PermissionDeniedScreen` with an "Open Settings" button (using `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` intent).
5. From app: show `PermissionDeniedScreen` inline via navigation route.
6. On return from Settings: lifecycle-aware recheck in `RESUMED` state. If granted, auto-navigate to Home.
7. User can cancel to disarm instead.

---

## 8. Foreground service

Create:

`AudioCaptureService : Service`

Responsibilities:
- start recording.
- remain foreground for entire recording.
- display an ongoing notification.
- own recorder lifecycle.
- finalize audio.
- communicate state to UI.

Startup sequence:

1. Validate current permission state.
2. Validate that service start was initiated through a permitted user/system context.
3. Call `startForegroundService()` where required.
4. In the service, call `ServiceCompat.startForeground()` with microphone foreground-service type.
5. Initialize audio recorder.
6. Begin capture.
7. Publish `RECORDING` state.

The current Android documentation requires appropriate foreground-service types and permissions for target SDK 34+ and restricts background starts. Do not attempt to start this service from arbitrary background code.

---

## 9. Foreground notification

Notification requirements:
- ongoing
- visible while recording
- title: `Recording`
- content: elapsed time or `Wake Capture`
- Stop action
- opens the app to capture details when appropriate

Use a dedicated notification channel:
- Channel ID: `wake_capture_recording`
- Channel name: `Recording`
- Importance: `IMPORTANCE_LOW` (no sound/vibration, but persistently visible)
- Description: `Shown while Wake Capture is recording audio`

Do not suppress or minimize the notification merely to make the product appear invisible.

The notification is part of the Android privacy/security contract.

---

## 10. Quick Settings Tile behavior

### When not recording

Tile:
- label: `Capture`
- inactive/default state
- tap => request/start capture.

### When recording

Tile:
- label/state indicates recording if the platform allows reliable update.
- tap => stop capture.

However, do not depend on the TileService remaining continuously bound. Android documents that TileService lifecycle callbacks can occur independently.

Therefore:
- persist recording state in shared storage.
- TileService queries persisted state.
- recording service is authoritative for actual capture state.

---

## 11. Capture coordinator

Create:

`CaptureCoordinator`

Responsibilities:
- validate permission.
- validate state.
- start/stop service.
- persist state.
- coordinate UI/tile/notification.
- prevent duplicate starts.
- recover from interrupted states.

Use a process-safe state representation because the TileService and main Activity are separate Android components.

Recommended state store:
- DataStore for app-level state (arm state, settings, auto-disarm timestamp).
- Room for capture records.

Process safety:
- The TileService must run in the **default process** (do not declare `android:process` on it). This avoids multi-process DataStore issues.
- If a separate process is required in the future, replace DataStore with a ContentProvider-backed store for cross-process state.

Avoid in-memory singleton state as the source of truth.

---

## 12. Recorder

Choose one implementation:

### Option A: MediaRecorder
Preferred for MVP if simple voice recording meets quality requirements.

### Option B: AudioRecord
Use when you need:
- precise audio buffers;
- custom VAD/silence detection;
- custom encoding pipeline;
- advanced audio processing.

Start with `MediaRecorder` unless testing shows a concrete requirement for `AudioRecord`.

Current configuration:
- AAC codec (`MediaRecorder.AudioEncoder.AAC`)
- M4A/MPEG4 container (`MediaRecorder.OutputFormat.MPEG_4`)
- 44100 Hz sample rate
- mono (1 channel) where supported
- 128 kbps bitrate

If the device does not support mono AAC via MediaRecorder, fall back to stereo. Do not fail silently — log the fallback.

The recording API should be isolated behind an `AudioRecorder` interface so the codec/implementation can be changed later.

File path:

`context.filesDir/captures/<UUID>.m4a`

Do not write recordings to public/shared storage in MVP.

Minimum storage check:
- Before starting a recording, verify at least 50 MB of free space is available in `context.filesDir`.
- At AAC 128 kbps, recordings use ~1 MB per minute. With a 30-minute max, the largest possible file is ~30 MB.
- If insufficient storage: do not start recording; transition to `FAILED` with a clear storage-full error.
- File storage management (directory creation, path resolution, existence checks, deletion, available-storage queries) is handled by `RecordingFileStore` in the `data/` package.

---

## 13. Local persistence

Room entity:

`CaptureEntity`
- id: UUID/String
- createdAt: Long
- durationMs: Long
- filePath: String
- mimeType: String
- codec: String
- sampleRate: Int?
- channels: Int?
- state: enum (`SAVED`, `INTERRUPTED`, `FAILED`) — persistence-only states; coordinator states (`ARMED`, `STARTING`, `RECORDING`, etc.) are NOT persisted here
- transcriptStatus: enum (`NONE`, `PENDING`, `DONE`, `FAILED`) — stub for future use; default `NONE`
- title: String?
- tags: String/normalized relation later
- createdFrom: enum (`TILE`, `WIDGET`, `APP`, `ASSISTANT`, etc.)

Audio file:
- private app storage.
- unique ID-based filename.

Successful save sequence:

1. stop recorder;
2. close file;
3. verify file exists;
4. verify file size/duration;
5. insert/update Room entity;
6. mark state `SAVED`.

If interrupted:
- preserve partial file where possible;
- mark `INTERRUPTED`;
- do not delete automatically.

---

## 13.1 Audio playback

The capture detail screen provides in-app playback of saved recordings.

Implementation:
- `CaptureDetailViewModel` manages a `MediaPlayer` instance.
- Before playback, request audio focus via `AudioManager` with `AudioAttributes.USAGE_MEDIA` / `AudioAttributes.CONTENT_TYPE_SPEECH`.
- On audio focus loss (transient or permanent), stop playback and release the player.
- On playback completion, release audio focus and the player.
- Expose `isPlaying`, `fileExists`, and `playbackError` as `StateFlow` for the Compose UI.
- The play/stop button uses `semantics { liveRegion = LiveRegionMode.Polite }` so screen readers announce state changes.
- If the audio file does not exist on disk, the play button is disabled.
- Playback errors show an `AlertDialog` with a dismiss action.

The player is released in `ViewModel.onCleared()` to prevent leaks.

Do not implement streaming, seeking, or background playback in MVP.

---

## 14. Lock-screen behavior

Do not assume a third-party app can freely launch microphone capture from every locked-screen context.

The design must rely on documented system entry points.

Quick Settings may be accessible from a locked device depending on device/security configuration.

The application must tolerate:
- tile unavailable from lock screen;
- authentication required;
- OEM-specific Quick Settings restrictions;
- policy restrictions;
- microphone access disabled at system level.

If authentication is required, accept it.

Do not use:
- AccessibilityService
- overlay tricks
- notification abuse
- hidden activities
- undocumented APIs

to bypass lock-screen security.

---

## 15. Assistant integration

Future phase:

Expose an action equivalent to:

`StartCapture`

Potential phrases:
- "Start a capture."
- "Record this."
- "Capture this thought."

Possible technologies:
- App Actions
- AppFunctions on supported Android versions
- Google/Gemini integrations as supported by the current platform

The assistant integration must still ultimately invoke the same `CaptureCoordinator`.

Do not create an always-listening custom assistant.

---

## 16. Widget

Implement a simple Home Screen widget later.

Widget:
- `Capture` button.
- optional state indicator.
- no sensitive transcript data.

The widget should invoke the same capture command path.

Because background-start restrictions apply, test whether the widget invocation is a documented exemption/context for starting the microphone foreground service on the target Android versions.

Do not assume widget support makes arbitrary background microphone starts legal.

---

## 17. Silence auto-stop

Not implemented in MVP. `CaptureCoordinator` declares `silenceTimeoutSeconds` (default 30) and `maxRecordingDurationMinutes` (default 5) as configurable values, but silence detection logic is deferred.

Maximum recording duration IS enforced in MVP — a simple timer stops recording after `maxRecordingDurationMinutes`.

When silence auto-stop is implemented (requires `AudioRecord`, not `MediaRecorder`):
- user explicitly starts recording.
- detect sustained low input level.
- begin countdown after configured silence period.
- stop after countdown.
- reset countdown if speech resumes.

Configurable values (matching iOS):
- Silence timeout: 10, 15, 30, 45, 60, 90, 120 seconds (default 30)
- Maximum recording length: 1, 2, 5, 10, 15, 30 minutes (default 5)

Never use silence detection to initiate recording.

---

## 18. Audio focus / interruptions

Handle during recording and playback:
- incoming phone calls
- other audio apps
- Bluetooth headset changes
- wired headset changes
- microphone becoming unavailable
- audio focus loss
- service termination
- battery restrictions
- process death

If recording is interrupted:
- finalize partial recording where possible.
- mark `INTERRUPTED`.
- preserve the file.

Do not falsely report a complete capture.

**Process death / sudden kill during recording.**

If the process is killed mid-recording (OEM battery management, system low memory, force stop), the M4A container may be invalid — AAC in MPEG4 requires metadata written at finalization (`MediaRecorder.stop()`). If stop never runs, the file on disk may contain audio frames but the container is unreadable.

Mitigation (implemented): **Recovery breadcrumb.** `RecordingFileStore` writes a breadcrumb file (containing the recording file path) at recording start and deletes it on successful stop. On next app launch, `CaptureCoordinator.checkForOrphanedRecordings()` detects orphaned breadcrumbs: if the referenced audio file exists, it persists the capture as `INTERRUPTED`; otherwise it cleans up the breadcrumb.

Future consideration: if recovery fidelity is critical, evaluate whether a streaming-friendly container (e.g., raw AAC with ADTS headers) would be more resilient to incomplete writes, at the cost of compatibility.

---

## 19. Android 15/16/17 forward-compatibility

The implementation must explicitly test current Android releases because foreground-service/background-audio rules are evolving.

In particular, current Android documentation states:
- Android 12+ restricts background foreground-service starts.
- Android 14+ applies while-in-use checks to microphone foreground services.
- Android 15+ introduces additional foreground-service restrictions.
- Android 17 documentation introduces additional background-audio hardening.

Therefore:
- keep all microphone access behind the foreground-service boundary;
- initiate recording from explicit user/system interactions;
- never depend on arbitrary background timers;
- never depend on BOOT_COMPLETED to begin microphone capture;
- run compatibility tests against the latest Android release and at least one previous release.

---

## 20. Manufacturer testing

At minimum test:

- Google Pixel
- Samsung Galaxy
- one additional major OEM

Test:
- locked screen
- unlocked screen
- Quick Settings tile
- tile added/removed
- screen off
- battery saver
- adaptive battery
- app force-stopped
- app process killed
- microphone permission revoked
- microphone privacy toggle disabled
- phone call
- Bluetooth headset
- reboot
- DND/Sleep mode
- notification permission state

OEM battery-management behavior must not be solved by requesting unnecessary unrestricted battery privileges.

Only request battery-optimization exceptions if a demonstrated, user-visible requirement justifies it and the current Play policy permits it.

---

## 21. Security model

Security boundary:

`User arms -> no sensor access`

`Explicit system/user action -> OS validates -> foreground microphone service -> recording`

Never:

`time/motion/sleep event -> microphone`

Never:
- hidden recording
- background microphone polling
- silent foreground-service notification
- lock-screen bypass
- Accessibility-based capture
- notification-click deception
- custom wake word

The app should be auditable as a straightforward voice recorder.

---

## 22. Application architecture

Recommended packages:

`capture/`
- CaptureCoordinator
- CaptureState
- CaptureRepository
- CaptureError

`capture/service/`
- AudioCaptureService
- AudioRecorder
- AudioSessionController

`system/quicksettings/`
- CaptureTileService

`system/widget/`
- CaptureWidget

`system/assistant/`
- AppActionsIntegration
- AppFunctionsIntegration

`data/`
- Room database (CaptureDatabase, CaptureDao)
- DataStore (arm state, settings, auto-disarm timestamp)
- RecordingFileStore (directory creation, path resolution, existence checks, deletion, available-storage queries)

`ui/`
- Onboarding
- PermissionDeniedScreen
- WakeModeSettings
- RecordingScreen
- CaptureHistory
- CaptureDetail (with audio playback via MediaPlayer)

`processing/` (stub only — not implemented in MVP)
- TranscriptionService (interface/protocol only)
- SummarizationService (interface/protocol only)

---

## 23. Dependency injection

Use Hilt (Dagger-Hilt) for dependency injection.

Rationale: `TileService`, `AudioCaptureService`, and `Activity` are separate Android components that share `CaptureCoordinator`, `CaptureRepository`, and DataStore instances. Hilt manages the component lifecycle boundaries and provides `@Singleton`-scoped shared instances without manual service locators.

Key bindings:
- `CaptureCoordinator` — `@Singleton`
- `CaptureRepository` (Room DAO access) — `@Singleton`
- `AudioRecorder` interface — bound to `MediaRecorderAudioRecorder` implementation
- DataStore — provided via `@Singleton` Hilt module
- Room database — provided via `@Singleton` Hilt module

Annotate:
- `Application` class with `@HiltAndroidApp`
- `Activity` with `@AndroidEntryPoint`
- `AudioCaptureService` with `@AndroidEntryPoint`
- `CaptureTileService` with `@AndroidEntryPoint`

---

## 24. Build configuration

Use Gradle with Kotlin DSL (`.kts`).

Single module for MVP. Multi-module is a future refactor if the codebase grows.

Key dependencies (use current stable versions at implementation time):
- Jetpack Compose BOM
- Room (runtime, compiler/KSP)
- Hilt (android, compiler/KSP)
- Hilt Navigation Compose
- DataStore Preferences
- Kotlin Coroutines (core, android)
- WorkManager (for future post-processing, not recording)
- Material 3

Build configuration:
- `compileSdk`: current Play Store-required SDK
- `minSdk`: 33 (Android 13) — provides `POST_NOTIFICATIONS` runtime permission, foreground service type declarations, and a modern baseline
- `targetSdk`: current Play Store-required SDK
- Enable Compose via Compose compiler plugin
- Use KSP (not KAPT) for Room and Hilt annotation processing

---

## 25. Concurrency

Use Kotlin Coroutines.

Rules:
- service owns actual recorder lifecycle.
- state updates are serialized.
- use a `Mutex` or actor-like coordinator to prevent duplicate starts/stops.
- UI observes state via `StateFlow`.
- persist state transitions.
- never depend on Activity lifecycle for an active recording.

---

## 26. MVP acceptance tests

### Permissions
- Fresh install -> request microphone.
- Denied -> clear failure.
- Granted -> capture works.

### Arming
- Arm -> no microphone access.
- Disarm -> no microphone access.

### Quick Settings
- Add Capture tile.
- Lock device.
- Attempt Capture.
- Measure whether recording starts.
- Record exact OS behavior.
- Stop.
- Verify saved file.

### Foreground service
- service promoted successfully.
- notification visible.
- microphone foreground-service type declared.
- no SecurityException.
- stop removes foreground service.

### Interruptions
- incoming call
- Bluetooth disconnect
- competing recorder
- microphone privacy toggle
- permission revoked
- storage full
- app process death
- device reboot

### Background restrictions
Test every supported Android release and document:
- whether tile invocation qualifies for service start;
- whether lock-screen state changes eligibility;
- whether OEM behavior differs.

---

## 27. Critical feasibility gate

Before implementing transcription/cloud/AI:

Prove on physical devices:

`arm before sleep -> lock -> open Quick Settings -> Capture -> foreground microphone service starts -> recording begins -> speak for 30 seconds -> stop -> file persists`

Then test the same flow on:
- Pixel/current Android
- Samsung/current Android
- one other major OEM

If a particular system surface cannot legally start the microphone service while locked on a device/version, do not build a workaround. Fall back to the next documented system surface.

---

## 27.1 Accessibility and localization

All user-visible strings are extracted to `res/values/strings.xml` for localization readiness.

Accessibility:
- All interactive controls have `contentDescription` semantics.
- State changes (recording status, playback toggle) use `LiveRegionMode.Polite` to announce updates to screen readers.
- The arm/disarm switch label reflects the current state for TalkBack users.
- Error dialogs and permission screens are navigable via accessibility services.

---

## 28. Non-goals

Do not implement:
- video
- continuous listening
- custom wake word
- automatic recording
- motion-triggered recording
- sleep-state-triggered recording
- lock-screen bypass
- AccessibilityService
- cloud sync
- social sharing
- complex audio editing

---

## 29. Current Android API references

- TileService: https://developer.android.com/reference/android/service/quicksettings/TileService
- Quick Settings tiles: https://developer.android.com/develop/ui/views/quicksettings-tiles
- Foreground services overview: https://developer.android.com/develop/background-work/services/fgs
- Launch foreground service: https://developer.android.com/develop/background-work/services/fgs/launch
- Background FGS restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Foreground service types: https://developer.android.com/develop/background-work/services/fgs/service-types
- AppFunctions: https://developer.android.com/ai/appfunctions

These references should be rechecked against the current Android SDK/target SDK before implementation because Android background-execution rules continue to evolve.
