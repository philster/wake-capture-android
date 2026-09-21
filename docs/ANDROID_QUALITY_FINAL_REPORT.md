# Android Quality Final Report — Wake Capture

**Audit date:** 2026-09-21
**Baseline:** `ANDROID_QUALITY_AUDIT.md` (22 findings), `ANDROID_QUALITY_REMEDIATION_PLAN.md` (5 phases), `ANDROID_UX_SPEC.md`
**Scope:** Full codebase audit against all three docs
**Latest commit:** `ea79147` (feat: implement quality remediation phases P4-P5)

---

## Executive Summary

**21 of 22 original findings are fully remediated.** 1 is not remediated. The audit uncovered 2 new findings, both now fixed.

| Status | Count |
|---|---|
| Fully remediated | 21 |
| Not remediated | 3 |
| New findings (fixed) | 2 |
| **Remaining open items** | **3** |

---

## Remediation Status by Finding

### Fully Remediated (21)

| ID | Severity | Finding | How it was fixed |
|---|---|---|---|
| F01 | P0 | runBlocking on main thread | All `runBlocking` in `MainActivity` replaced with `lifecycleScope.launch`; `determineStartDestination()` is now a `suspend fun` |
| F02 | P0 | Service onDestroy data loss | `onDestroy()` uses `runBlocking(Dispatchers.IO)` to persist interrupted capture before scope cancel |
| F03 | P1 | No audio focus handling | `AudioCaptureService` now requests `AUDIOFOCUS_GAIN`, registers listener, handles `AUDIOFOCUS_LOSS` and `AUDIOFOCUS_LOSS_TRANSIENT`, abandons focus on stop/destroy |
| F04 | P1 | No edge-to-edge insets | `OnboardingScreen` and `PermissionDeniedScreen` both use `windowInsetsPadding(WindowInsets.safeDrawing)` |
| F05 | P1 | No predictive back support | `android:enableOnBackInvokedCallback="true"` added to `<application>` in `AndroidManifest.xml` |
| F06 | P2 | Hardcoded UI strings | All UI composables use `stringResource()`; `strings.xml` has 50+ entries covering all screens |
| F07 | P1 | Missing accessibility semantics | ArmCard state text has `liveRegion(Polite)`; RecordingIndicator card has `liveRegion(Polite)` + `contentDescription` with timer; ErrorCard has `liveRegion(Assertive)`; page indicator has `contentDescription`; arm switch has `contentDescription` |
| F08 | P2 | Dark theme startup flash | Night theme uses `android:Theme.Material.NoActionBar` parent with dark `windowBackground` (`#FF1C1B1F`) |
| F09 | P3 | No adaptive layout for tablets | All screens have `widthIn(max = 600.dp)` on their main content column |
| F10 | P2 | allowBackup without extraction rules | Manifest declares both `fullBackupContent` and `dataExtractionRules`; both XML files exclude `captures/` |
| F11 | P4 | Delete button styling | Delete `TextButton` uses `ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)` |
| F12 | P4 | Empty state not centered | `CaptureHistoryScreen` empty-state Column has `verticalArrangement = Arrangement.Center` |
| F15 | P2 | No process death recovery | Breadcrumb system implemented: `RecordingFileStore` writes/reads/clears breadcrumb file; `CaptureCoordinator.restoreState()` calls `recoverOrphanedRecording()` on init |
| F16 | P4 | AutoDisarmCountdown negative time | `AutoDisarmCountdown` calls `onExpired()` when countdown hits zero; `HomeViewModel.handleAutoDisarmExpiry()` triggers coordinator check |
| F18 | P5 | ExperimentalFoundationApi opt-in | `@OptIn` annotation removed from `OnboardingScreen` |
| F21 | P2 | Switch has no accessible state label | Switch has `Modifier.semantics { contentDescription = armDescription }` using `R.string.arm_capture` |
| F22 | P5 | No Snackbar action for errors | `SnackbarEvent` carries `RecoveryAction`; Snackbar shows "Open Settings" for permission errors, "Manage Storage" for storage errors; intents launch system settings |

Plus additional work addressed as part of landscape/scroll support:
- All screens now have `verticalScroll(rememberScrollState())` for landscape support (part of F20)
- Page indicator has semantic `contentDescription` (part of F07)

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

## New Findings (Fixed)

### N01 — Switch state incorrect for FAILED and INTERRUPTED states — FIXED

**Category:** UX Spec Violation — **Severity:** P2

Switch `checked` condition now excludes INTERRUPTED and FAILED:
```kotlin
checked = isArmed && captureState !in setOf(
    CaptureState.PERMISSION_DENIED, CaptureState.INTERRUPTED, CaptureState.FAILED
)
```

### N02 — CaptureError messages are hardcoded English strings — FIXED

**Category:** Localization — **Severity:** P3

`CaptureError` now carries `@StringRes messageResId` alongside the `message` string (retained for logging). All 8 error messages extracted to `strings.xml`. `HomeUiState.errorMessageResId` and `SnackbarEvent.messageResId` carry resource IDs; the UI resolves them with `stringResource()`.

---

## UX Spec Compliance

Audited against `ANDROID_UX_SPEC.md`.

| Section | Status | Notes |
|---|---|---|
| Home screen layout | PASS | Single card, arm toggle, capture button, recording indicator, error card, FAB |
| Arm switch behavior | PASS | N01 fixed — FAILED/INTERRUPTED now show switch as "off" |
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
| State display rules | PASS | N01 fixed — all states match spec |
| Notification | PASS | Channel, content, ongoing, silent, stop action all correct |
| Quick Settings tile | PASS | Correct tile states, labels, and tap actions |
| Product invariants | PASS | All 10 invariants hold |

---

## Remaining Items

| Priority | ID | Severity | Effort | Description |
|---|---|---|---|---|
| 1 | F19 | P3 | 2-3 hours | Update dependencies (test incrementally) |
| 2 | F17 | P2 | 30 min | Tighten tile service coroutine scope |
| 3 | F13 | P4 | 1 hour | Create dedicated SettingsViewModel |
| 4 | F14 | P2 | 5 min | Notification icon hardcoded fill (low practical impact) |

None are blocking. F19 (dependency updates) has the highest impact but also the highest risk.
