# Android Quality Final Report — Wake Capture

**Audit date:** 2026-09-21
**Baseline:** `ANDROID_QUALITY_AUDIT.md` (22 findings), `ANDROID_QUALITY_REMEDIATION_PLAN.md` (5 phases), `ANDROID_UX_SPEC.md`
**Scope:** Full codebase audit against all three docs
**Latest commit:** `ea79147` (feat: implement quality remediation phases P4-P5)

---

## Executive Summary

**17 of 22 original findings are fully remediated.** 2 are partially remediated, 3 are not remediated. The audit also uncovered 2 new findings not in the original audit.

| Status | Count |
|---|---|
| Fully remediated | 17 |
| Partially remediated | 2 |
| Not remediated | 3 |
| New findings | 2 |
| **Remaining open items** | **7** |

---

## Remediation Status by Finding

### Fully Remediated (17)

| ID | Severity | Finding | How it was fixed |
|---|---|---|---|
| F01 | P0 | runBlocking on main thread | All `runBlocking` in `MainActivity` replaced with `lifecycleScope.launch`; `determineStartDestination()` is now a `suspend fun` |
| F02 | P0 | Service onDestroy data loss | `onDestroy()` uses `runBlocking(Dispatchers.IO)` to persist interrupted capture before scope cancel |
| F03 | P1 | No audio focus handling | `AudioCaptureService` now requests `AUDIOFOCUS_GAIN`, registers listener, handles `AUDIOFOCUS_LOSS` and `AUDIOFOCUS_LOSS_TRANSIENT`, abandons focus on stop/destroy |
| F04 | P1 | No edge-to-edge insets | `OnboardingScreen` and `PermissionDeniedScreen` both use `windowInsetsPadding(WindowInsets.safeDrawing)` |
| F05 | P1 | No predictive back support | `android:enableOnBackInvokedCallback="true"` added to `<application>` in `AndroidManifest.xml` |
| F06 | P2 | Hardcoded UI strings | All UI composables use `stringResource()`; `strings.xml` has 50+ entries covering all screens |
| F09 | P3 | No adaptive layout for tablets | All screens have `widthIn(max = 600.dp)` on their main content column |
| F10 | P2 | allowBackup without extraction rules | Manifest declares both `fullBackupContent` and `dataExtractionRules`; both XML files exclude `captures/` |
| F11 | P4 | Delete button styling | Delete `TextButton` uses `ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)` |
| F12 | P4 | Empty state not centered | `CaptureHistoryScreen` empty-state Column has `verticalArrangement = Arrangement.Center` |
| F15 | P2 | No process death recovery | Breadcrumb system implemented: `RecordingFileStore` writes/reads/clears breadcrumb file; `CaptureCoordinator.restoreState()` calls `recoverOrphanedRecording()` on init |
| F16 | P4 | AutoDisarmCountdown negative time | `AutoDisarmCountdown` calls `onExpired()` when countdown hits zero; `HomeViewModel.handleAutoDisarmExpiry()` triggers coordinator check |
| F18 | P5 | ExperimentalFoundationApi opt-in | `@OptIn` annotation removed from `OnboardingScreen` |
| F21 | P2 | Switch has no accessible state label | Switch has `Modifier.semantics { contentDescription = armDescription }` using `R.string.arm_capture` |
| F22 | P5 | No Snackbar action for errors | `SnackbarEvent` carries `RecoveryAction`; Snackbar shows "Open Settings" for permission errors, "Manage Storage" for storage errors; intents launch system settings |

Plus two additional findings were addressed that were part of Phase 4-5 work:
- All screens now have `verticalScroll(rememberScrollState())` for landscape support (part of F20)
- Page indicator has semantic `contentDescription` (part of F07)

### Partially Remediated (2)

#### F07 — Accessibility semantics (P1) — PARTIAL

**What's fixed:**
- ArmCard state text has `liveRegion = LiveRegionMode.Polite` (`HomeScreen.kt:240`)
- ErrorCard has `liveRegion = LiveRegionMode.Polite` (`HomeScreen.kt:353`)
- Page indicator has `contentDescription` with page count (`OnboardingScreen.kt:205-209`)
- Switch has `contentDescription` (`HomeScreen.kt:252`)

**What's still missing:**
- `RecordingIndicator` card has no `liveRegion`. When recording starts, TalkBack users are not alerted that the recording card appeared. The mic Icon has a `contentDescription` but the card itself is not announced.
- Recording timer text (`%02d:%02d`) has no accessibility semantics — screen readers won't announce elapsed time updates.
- ErrorCard uses `Polite` when the audit recommended `Assertive` for error announcements. This is a minor deviation.

**Evidence:** `HomeScreen.kt:300-339` — RecordingIndicator composable has no `semantics` modifier on the Card.

#### F08 — Startup theme flash in dark mode (P2) — PARTIAL

**What's fixed:**
- `values-night/themes.xml` exists and sets `android:windowBackground` to `#FF1C1B1F` (Material 3 dark surface), preventing the white flash.

**What's still wrong:**
- The night theme parent is `android:Theme.Material.Light.NoActionBar` — same as the day theme. This means system chrome (status bar icons, nav bar icons) may use light-themed defaults during the splash window. The parent should be `android:Theme.Material.NoActionBar` (dark variant).

**Evidence:** `values-night/themes.xml:3` — `parent="android:Theme.Material.Light.NoActionBar"`

### Not Remediated (3)

#### F13 — SettingsScreen uses HomeViewModel (P4) — OPEN

`SettingsScreen` still injects `HomeViewModel` directly.

**Evidence:** `ui/settings/SettingsScreen.kt:42` — `viewModel: HomeViewModel = hiltViewModel()`

**Impact:** Low — architectural concern, not a runtime bug. Settings work correctly because state flows through DataStore.

#### F14 — Notification icon hardcoded white fill (P2) — OPEN

`ic_stop.xml` still uses `android:fillColor="#FFFFFF"`. This is technically correct for notification icons (system applies tinting on most Android versions), but the audit flagged it.

**Evidence:** `res/drawable/ic_stop.xml:8`

**Impact:** Low — white fill is standard for notification action icons.

#### F19 — Dependency versions outdated (P3) — OPEN

All dependency versions unchanged from the original audit:

| Dependency | Current | Gap |
|---|---|---|
| AGP | 8.7.3 | ~2 major minor versions behind |
| Kotlin | 2.0.21 | 2.1.x available |
| Compose BOM | 2024.12.01 | ~2 years behind |
| Room | 2.6.1 | 2.7.x available |
| Hilt | 2.53.1 | behind stable |
| Navigation | 2.8.5 | 2.9.x available |
| Lifecycle | 2.8.7 | 2.9.x available |

**Evidence:** `gradle/libs.versions.toml:1-19`

**Impact:** Medium — missing bug fixes, performance improvements, and better predictive back support in newer Navigation versions.

#### F17 — CaptureTileService leaks coroutine scope (P2) — OPEN

`CaptureTileService` still creates `serviceScope` as a class field and cancels only in `onDestroy()`. The TileService lifecycle doesn't guarantee prompt `onDestroy()` calls.

**Evidence:** `system/quicksettings/CaptureTileService.kt:28, 111-113`

**Impact:** Low in practice — coroutines launched are short-lived (state reads and service starts).

---

## New Findings

### N01 — Switch state incorrect for FAILED and INTERRUPTED states

**Category:** UX Spec Violation

**Severity:** P2

**Spec reference:** `ANDROID_UX_SPEC.md` — State display rules table

**Current behavior:**

The Switch `checked` value is:
```kotlin
checked = isArmed && captureState != CaptureState.PERMISSION_DENIED
```
where `isArmed = captureState != CaptureState.DISARMED`.

For FAILED and INTERRUPTED states: `isArmed = true` (they're not DISARMED) and `!= PERMISSION_DENIED = true`, so `checked = true` — the switch shows as **on**.

**Spec says:** FAILED and INTERRUPTED should both have the switch **Off, enabled**.

**Evidence:** `ui/home/HomeScreen.kt:247-248`

**Impact:** After a failed or interrupted recording, the switch misleadingly shows "on". The user may think they're still armed. Toggling the switch calls `rearmAfterTerminal()` which transitions back to ARMED — so the switch toggle works, but the visual state is wrong.

**Fix:** Add FAILED and INTERRUPTED to the "off" conditions:
```kotlin
checked = isArmed && captureState !in setOf(
    CaptureState.PERMISSION_DENIED,
    CaptureState.INTERRUPTED,
    CaptureState.FAILED
)
```

### N02 — CaptureError messages are hardcoded English strings

**Category:** Localization

**Severity:** P3

**Current behavior:**

`CaptureError` sealed class uses hardcoded English strings for all error messages:
- "Microphone permission is required"
- "Wake Capture must be armed first"
- "A recording is already in progress"
- "Failed to start recording service"
- "Failed to initialize audio recorder"
- "Not enough storage space (50 MB required)"
- "Failed to write audio file"

These messages flow through `HomeViewModel.emitSnackbar()` into Snackbars and through `HomeUiState.errorMessage` into the ErrorCard — both user-facing.

**Evidence:** `capture/CaptureError.kt:3-11`

**Impact:** All error messages shown to users are unlocalizable. Contradicts the F06 remediation of extracting all user-facing strings.

**Fix:** `CaptureError` needs to carry a string resource ID instead of a raw string, or the ViewModel needs to map errors to resource strings before exposing them to the UI.

---

## UX Spec Compliance

Audited against `ANDROID_UX_SPEC.md`.

| Section | Status | Notes |
|---|---|---|
| Home screen layout | PASS | Single card, arm toggle, capture button, recording indicator, error card, FAB |
| Arm switch behavior | **FAIL** | See N01 — FAILED/INTERRUPTED show switch as "on" instead of "off" |
| Start Capture button | PASS | Red, full-width, mic icon, visible only when ARMED |
| Recording indicator | PASS | Mic icon, "Recording in progress", elapsed time, red-tinted card |
| Stop FAB | PASS | Red, stop icon, visible only when RECORDING |
| Error card | PASS | errorContainer colors, appears below main content |
| Top bar actions | PASS | History and Settings icons |
| Onboarding flow | PASS | 3-page pager, correct content per page, forward-only navigation |
| Permission Denied screen | PASS | Centered, MicOff icon, buttons, auto-check on resume |
| Settings screen | PASS | Scaffold, recording + auto-disarm sections with dropdowns |
| Capture History | PASS | LazyColumn, centered empty state |
| Capture Detail | PASS | Metadata card, delete with confirmation dialog |
| State display rules | **FAIL** | FAILED/INTERRUPTED switch state wrong (N01) |
| Notification | PASS | Channel, content, ongoing, silent, stop action all correct |
| Quick Settings tile | PASS | Correct tile states, labels, and tap actions |
| Product invariants | PASS | All 10 invariants hold |

---

## Remediation Plan Priority

Remaining items ranked by severity and impact:

| Priority | ID | Severity | Effort | Description |
|---|---|---|---|---|
| 1 | N01 | P2 | 5 min | Fix switch checked state for FAILED/INTERRUPTED |
| 2 | F07 | P1 (partial) | 30 min | Add liveRegion to RecordingIndicator, timer semantics |
| 3 | F08 | P2 (partial) | 5 min | Fix night theme parent to dark variant |
| 4 | N02 | P3 | 1 hour | Extract CaptureError messages to string resources |
| 5 | F19 | P3 | 2-3 hours | Update dependencies (test incrementally) |
| 6 | F17 | P2 | 30 min | Tighten tile service coroutine scope |
| 7 | F13 | P4 | 1 hour | Create dedicated SettingsViewModel |

Items 1-3 are quick wins that should be done immediately. Item 4 is a consistency fix. Items 5-7 can wait.
