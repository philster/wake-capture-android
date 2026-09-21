# Android Quality Remediation Plan — Wake Capture

Based on `ANDROID_QUALITY_AUDIT.md` findings.

---

## Implementation Order

Changes are grouped into phases. Each phase should be buildable and testable independently.

---

### Phase 1 — Critical (P0): Eliminate ANR risk and data loss

**Findings:** F01, F02

#### 1a. Fix runBlocking calls in MainActivity (F01)

**Files:** `ui/MainActivity.kt`

**Changes:**
1. Replace `runBlocking` in `onResume()` with `lifecycleScope.launch`.
2. Replace `runBlocking` in `determineStartDestination()` — restructure to use a splash/loading state while DataStore is read asynchronously, or use `runBlocking` on `Dispatchers.IO` as a short-term fix (acceptable since DataStore caches after first read).
3. Replace `runBlocking` in the `onDisarm` lambda with `lifecycleScope.launch`.

**Verification:** Cold start under StrictMode. Rapid lock/unlock cycles.

#### 1b. Fix service onDestroy data loss (F02)

**Files:** `capture/service/AudioCaptureService.kt`

**Changes:**
1. In `onDestroy()`, run the interrupted-capture persistence synchronously using `runBlocking(Dispatchers.IO)` to ensure the database write completes before the service is destroyed.
2. Move `serviceScope.cancel()` to after the blocking persistence.

**Verification:** `adb shell am force-stop com.wakecapture.app` during recording. Verify capture appears in history.

---

### Phase 2 — High (P1): Platform compliance and accessibility

**Findings:** F03, F04, F05, F07

#### 2a. Add audio focus handling (F03)

**Files:** `capture/service/AudioCaptureService.kt`

**Changes:**
1. Request `AUDIOFOCUS_GAIN` before starting recording.
2. Register `OnAudioFocusChangeListener`.
3. On `AUDIOFOCUS_LOSS`: finalize as interrupted.
4. On `AUDIOFOCUS_LOSS_TRANSIENT`: finalize as interrupted.
5. Abandon audio focus on stop/destroy.

**Verification:** Start recording → receive phone call. Start recording → play music from another app.

#### 2b. Fix edge-to-edge insets (F04)

**Files:** `ui/onboarding/OnboardingScreen.kt`, `ui/permission/PermissionDeniedScreen.kt`

**Changes:**
1. Add `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` to root layout, or wrap both screens in `Scaffold`.

**Verification:** Test on Android 15+ with gesture navigation and display cutouts.

#### 2c. Enable predictive back (F05)

**Files:** `AndroidManifest.xml`

**Changes:**
1. Add `android:enableOnBackInvokedCallback="true"` to `<application>`.
2. Test all back navigation paths.

**Verification:** Long-press back gesture on every screen. Verify preview animation and correct destination.

#### 2d. Add accessibility semantics (F07)

**Files:** `ui/home/HomeScreen.kt`, `ui/onboarding/OnboardingScreen.kt`

**Changes:**
1. Add `liveRegion` to state text in ArmCard.
2. Add `liveRegion` to ErrorCard.
3. Add semantics to onboarding page indicator.
4. Add accessible description to recording indicator.

**Verification:** Full TalkBack walkthrough of all screens and state transitions.

---

### Phase 3 — Medium (P2): UX, privacy, and data integrity

**Findings:** F06, F08, F10, F14, F15, F17, F21

#### 3a. Extract hardcoded strings (F06)

**Files:** All UI files, `values/strings.xml`

**Changes:** Extract all user-facing strings to resources. Mechanical change.

#### 3b. Fix dark theme flash (F08)

**Files:** `values/themes.xml`, new `values-night/themes.xml`

**Changes:** Create night theme variant or use DayNight parent theme.

#### 3c. Add data extraction rules (F10)

**Files:** `AndroidManifest.xml`, new `xml/data_extraction_rules.xml`

**Changes:** Exclude `captures/` from backup. Allow preferences backup.

#### 3d. Add process death recovery breadcrumb (F15)

**Files:** `capture/service/AudioCaptureService.kt`, `data/RecordingFileStore.kt`, `capture/CaptureCoordinator.kt`

**Changes:**
1. Write breadcrumb file at recording start.
2. Delete breadcrumb on successful stop.
3. Check for orphaned breadcrumbs on app launch.

#### 3e. Fix tile service scope (F17)

**Files:** `system/quicksettings/CaptureTileService.kt`

**Changes:** Use tighter coroutine scoping or accept that short-lived coroutines are acceptable for tile interactions.

#### 3f. Add switch accessibility (F21)

**Files:** `ui/home/HomeScreen.kt`

**Changes:** Add content description or merge semantics for the arm switch.

---

### Phase 4 — Medium (P3): Adaptive layout and dependencies

**Findings:** F09, F19, F20

#### 4a. Add max-width constraints (F09)

**Files:** All screen composables

**Changes:** Add `Modifier.widthIn(max = 600.dp)` to main content areas.

#### 4b. Ensure landscape scrollability (F20)

**Files:** `ui/onboarding/OnboardingScreen.kt`

**Changes:** Verify all screens scroll properly in landscape. Add `verticalScroll` if missing.

#### 4c. Update dependencies (F19)

**Files:** `gradle/libs.versions.toml`

**Changes:** Update incrementally with testing between each change.

---

### Phase 5 — Low (P4-P5): Polish

**Findings:** F11, F12, F13, F16, F18, F22

1. **F11** — Style delete button with error color
2. **F12** — Center empty state vertically
3. **F13** — Create SettingsViewModel (optional)
4. **F16** — Auto-trigger expiry check when countdown hits zero
5. **F18** — Remove ExperimentalFoundationApi opt-in
6. **F22** — Add Snackbar actions for errors

---

## Estimated Effort

| Phase | Findings | Effort |
|---|---|---|
| Phase 1 | F01, F02 | 1-2 hours |
| Phase 2 | F03, F04, F05, F07 | 3-4 hours |
| Phase 3 | F06, F08, F10, F14, F15, F17, F21 | 4-6 hours |
| Phase 4 | F09, F19, F20 | 2-3 hours |
| Phase 5 | F11, F12, F13, F16, F18, F22 | 2-3 hours |

---

## Blocked Items

- **Device testing** — All findings are statically verified. Device/emulator testing required for VERIFIED status.
- **F19 dependency updates** — Requires checking current latest stable versions and testing for breaking changes.
- **F15 process death recovery** — Requires physical device testing with OEM battery management.
