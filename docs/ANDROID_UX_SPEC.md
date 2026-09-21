# Android UX Spec — Wake Capture

Product-specific UX contract. Changes to this app's behavior should not violate these rules.

---

## Core interaction model

Wake Capture is designed for a user who is half-awake. Every interaction must be:
- **Minimal** — fewest possible taps to capture a thought
- **Forgiving** — accidental taps don't cause data loss
- **Obvious** — state is immediately visible even with blurry vision

---

## Screens and behavior

### Home Screen

**Layout:** Single card with arm toggle, optional capture button, optional recording indicator.

**Arm switch:**
- Toggle between DISARMED and ARMED
- Disabled during STARTING, RECORDING, STOPPING
- When ARMED: show auto-disarm countdown (if configured)
- When RECORDING: show elapsed time and stop FAB

**Start Capture button:**
- Visible ONLY when state is ARMED
- Red, full-width, with mic icon and "Start Capture" text
- Triggers foreground service start

**Recording indicator:**
- Visible ONLY when state is RECORDING
- Shows mic icon, "Recording in progress", and elapsed time (MM:SS)
- Red-tinted card

**Stop FAB:**
- Visible ONLY when state is RECORDING
- Red, with stop icon
- Sends stop intent to service

**Error card:**
- Appears below main content when an error exists
- Uses errorContainer/onErrorContainer colors
- Disappears when error is cleared

**Top bar actions:**
- History (clock icon, right)
- Settings (gear icon, right)

### Onboarding

**Flow:** 3-page horizontal pager. Shown once on first launch.

1. Welcome — app icon, name, tagline. "Next" button.
2. Permissions — requests RECORD_AUDIO + POST_NOTIFICATIONS. "Grant Permissions" button, "Skip for now" text button.
3. Quick Settings — instructions for adding tile. "Get Started" completes onboarding.

**Page indicator:** dots at bottom.

**Navigation:** forward only via buttons/swipe. No back button. Completing page 3 navigates to Home and removes onboarding from the back stack.

### Permission Denied Screen

**Shown when:** RECORD_AUDIO is revoked/denied after onboarding.

**Layout:** Centered vertically. MicOff icon (error color), heading, explanation text, "Open Settings" primary button, "Disarm" outlined button.

**Behavior:**
- "Open Settings" opens app settings page
- On return to app, auto-checks permission via `repeatOnLifecycle(RESUMED)`
- If restored: navigates to Home
- "Disarm" disarms and navigates to Home

### Settings Screen

**Layout:** Scaffold with back arrow, scrollable settings list.

**Sections:**
1. **Recording** — "Auto-stop after silence" (dropdown, seconds), "Maximum recording length" (dropdown, minutes)
2. **Auto-disarm** — "Auto-disarm after" (dropdown: 8h / 12h / Until I disarm)

**Dropdowns:** ListItem with supporting text showing current value. Tap opens dropdown menu.

### Capture History

**Layout:** Scaffold with back arrow, LazyColumn of captures.

**Empty state:** "No captures yet" centered on screen.

**List item:** Title (or formatted date), duration + state label + source. Tap navigates to detail.

### Capture Detail

**Layout:** Scaffold with back arrow and delete icon in top bar. Card with Date, Duration, Status, Source, Format metadata. Play/stop button. "Transcription not yet available" placeholder text.

**Playback:** FilledTonalButton toggles between "Play Recording" (play icon) and "Stop Playback" (stop icon). Disabled when recording file is missing. Requests audio focus before playback; stops on focus loss. Button has live region semantics for TalkBack announcements on state change.

**Delete:** Tap delete icon → confirmation dialog → delete + navigate back.

---

## State display rules

| CaptureState | Arm card text | Card color | Switch | Capture button | FAB |
|---|---|---|---|---|---|
| DISARMED | "Disarmed" | Grey 5% | Off, enabled | Hidden | Hidden |
| ARMED | "Armed" | Green 10% | On, enabled | Visible | Hidden |
| STARTING | "Starting..." | Green 10% | On, disabled | Hidden | Hidden |
| RECORDING | "Recording" | Red 10% | On, disabled | Hidden | Stop (red) |
| STOPPING | "Stopping..." | Grey 5% | Disabled | Hidden | Hidden |
| INTERRUPTED | "Interrupted" | Grey 5% | Off, enabled | Hidden | Hidden |
| PERMISSION_DENIED | "Permission required" | Grey 5% | Off, enabled | Hidden | Hidden |
| FAILED | "Failed" | Grey 5% | Off, enabled | Hidden | Hidden |

---

## Notification

**Channel:** `wake_capture_recording`, name "Recording", importance LOW.

**Content:**
- Title: "Recording"
- Text: elapsed time (M:SS)
- Small icon: mic
- Ongoing: yes
- Silent: yes
- Tap: opens app
- Action: "Stop" with stop icon

---

## Quick Settings tile

| State | Tile state | Label | Tap action |
|---|---|---|---|
| ARMED | INACTIVE | "Capture" | Start recording |
| RECORDING | ACTIVE | "Recording" | Stop recording |
| DISARMED | INACTIVE | "Capture" | Launch app with "Arm first" message |
| PERMISSION_DENIED | INACTIVE | "Capture" | Launch app with permission message |

---

## Product invariants (do not change)

1. Capture is always explicit — never automatic
2. Arming never accesses the microphone
3. Raw audio is saved locally before success is reported
4. Network is never required for capture
5. No always-on microphone, no wake word
6. Foreground service notification is always visible during recording
7. After successful capture, state returns to ARMED (not DISARMED)
8. Auto-disarm timer survives app termination
9. Recording uses microphone foreground service type
10. Audio files are stored in private app storage only
