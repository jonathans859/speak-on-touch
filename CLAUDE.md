# Accessibilitroller — Project Reference

## What this is
A pure-Java Android app (min SDK 26, target SDK 35) providing a floating overlay button that reads UI elements aloud using Android's AccessibilityService + TextToSpeech APIs. Inspired by the iOS Zoom Controller.

---

## Build configuration

| Field | Value |
|---|---|
| Namespace | `com.example.accessibilitroller` |
| AGP | 8.6.0 |
| Gradle | 8.7 (no wrapper JAR — CI uses `gradle/actions/setup-gradle@v4`) |
| compileSdk / targetSdk | 35 |
| minSdk | 26 |
| Language | Pure Java (no Kotlin) |

### Key `gradle.properties`
```
android.useAndroidX=true
android.enableJetifier=true
org.gradle.jvmargs=-Xmx2048m
```

### Debug signing (`app/build.gradle`)
Explicit signing config that points to `~/.android/debug.keystore` is required so APK updates install without uninstalling. CI caches the keystore with `actions/cache@v4` key `debug-keystore-v1`.

---

## Architecture

### Services
- **`FloatingButtonService`** — foreground service (`foregroundServiceType="specialUse"`). Manages the overlay button, gesture state machine, speak mode, drag mode, and vibration.
- **`AccessibilitrollerService`** — `AccessibilityService`. Owns TTS. Receives `ACTION_SPEAK_AT` broadcasts with `x`/`y` coordinates and performs coordinate-based hit-testing across all windows to find and speak the target UI node.

### IPC
`LocalBroadcastManager` sends `ACTION_SPEAK_AT` (+ `EXTRA_X`, `EXTRA_Y`) from `FloatingButtonService` → `AccessibilitrollerService`.

### Settings
`SettingsManager` — singleton `SharedPreferences` wrapper. Keys:
- `button_style` — `"circle"` | `"semicircle"`
- `gesture_single` / `gesture_double` / `gesture_hold` / `gesture_double_hold`
- `button_pos_x` / `button_pos_y` — float 0–1 relative to screen
- `tts_engine` — TTS engine package name (empty = system default)
- `button_color` — int ARGB (default `0xFF6200EE`)
- `speak_auto_disable` — boolean (default false)
- `highlight_enabled` — boolean (default false)
- `read_backgrounds` — boolean (default false)

Action constants: `speak_on_touch`, `enable_drag`, `none`.

---

## Critical implementation details

### Overlay window type — TYPE_ACCESSIBILITY_OVERLAY via static proxy
The floating button must use **`TYPE_ACCESSIBILITY_OVERLAY`** to stay above the notification shade and quick-settings panel. `TYPE_APPLICATION_OVERLAY` windows sit below Android system UI.

**Problem:** `TYPE_ACCESSIBILITY_OVERLAY` can only be used from an `AccessibilityService` process. Calling `windowManager.addView(..., TYPE_ACCESSIBILITY_OVERLAY)` from `FloatingButtonService` throws `BadTokenException`.

**Solution:** Static proxy pattern in `AccessibilitrollerService`:
- `private static AccessibilitrollerService instance` — set in `onServiceConnected()`, cleared in `onDestroy()`
- `static boolean isAvailable()` — null check
- `static void addOverlayView(View v, WindowManager.LayoutParams lp)` — sets `lp.type = TYPE_ACCESSIBILITY_OVERLAY` and calls `instance.windowManager.addView()`

`FloatingButtonService` has three private helpers `wAddView()` / `wRemoveView()` / `wUpdateViewLayout()` that delegate to the static methods when the service is available, falling back to `TYPE_APPLICATION_OVERLAY` otherwise.

`WindowManagerGlobal` is a per-process singleton, so `removeView()` and `updateViewLayout()` can be called from any `WindowManager` instance regardless of which one did the `addView()`.

### Multi-window hit-testing (status bar access)
`getRootInActiveWindow()` only returns the foreground app — it cannot see the status bar or nav bar.

Use `getWindows()` instead, which returns all `AccessibilityWindowInfo` objects including `TYPE_SYSTEM` windows. Iterate front-to-back for the exact hit-test.

**Two-phase search in `speakNodeAtCoordinates()`:**
1. Exact hit-test across all windows (front-to-back order)
2. If miss: nearest-node snap (`findNearestMeaningfulNode`) restricted to `TYPE_SYSTEM` windows with a 28dp radius — uses Euclidean distance from touch point to node bounds edge (`distToRect()`). This makes tiny status bar icons reachable without pixel-perfect accuracy.

### Speak debounce
`SPEAK_DEBOUNCE_MS = 0` — no throttle on `ACTION_MOVE` events. Safe because `speakNode()` deduplicates by `lastSpokenText`; TTS only fires when a new element is entered. The accessibility tree traversal is fast (in-memory cache).

### Accessibility service detection
Use `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` + `TextUtils.SimpleStringSplitter(':')` + `ComponentName.unflattenFromString()`.  
**Do not** use `AccessibilityManager.getEnabledAccessibilityServiceList()` — unreliable on many ROMs.

### Button size
Always pass **explicit px dimensions** to `WindowManager.LayoutParams` (not `WRAP_CONTENT`).  
`WRAP_CONTENT` on a plain `View` with no intrinsic size expands to fill the screen.  
Circle button: `dpToPx(56)` × `dpToPx(56)`. Semicircle: `dpToPx(28)` × `dpToPx(56)`.

### Touch exploration mode — DO NOT USE
`flagRequestTouchExplorationMode` / `canRequestTouchExplorationMode` must **not** appear in `accessibility_service_config.xml`.  
This flag puts the entire system into TalkBack explore-by-touch mode, replacing all normal gestures system-wide.

### Accessibility service config
```xml
android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"
```
No hover events. No touch exploration.

### Dark mode
`themes.xml` must **not** override `colorBackground`, `colorSurface`, or `android:colorBackground`.  
Use `Theme.Material3.DayNight.NoActionBar` and let it handle surface colours.

### Speak mode z-order
Speak overlay is added first (`wAddView(speakOverlay, …)`), then button is removed and re-added so it sits on top. Both happen before the next Choreographer frame — no visible flicker.

### No-repeat TTS
`AccessibilitrollerService.speakNode()` tracks `lastSpokenText`. Skips the TTS call if the text is identical. Resets after a >3 s gap between broadcasts (`SESSION_RESET_MS = 3000`) so re-activating speak mode always starts fresh.

### Speak mode auto-disable
`onSpeakTouch()` checks `settings.getSpeakAutoDisable()` on `ACTION_UP` / `ACTION_CANCEL` and calls `deactivateSpeakMode()` if set.

### TalkBack support on the floating button
- `setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES)`
- `setClickable(true)`
- `AccessibilityDelegateCompat` sets class name to `android.widget.Button`
- `setContentDescription()` — dynamic: "Double-tap to start speak mode" / "Speak mode active. Double-tap to stop."
- `setOnClickListener` handles TalkBack double-tap

### Button colour
`GradientDrawable` (shape `OVAL`) set on circle button via `btn.setBackground(bg)`.  
`SemiCircleButtonView.setColor(int)` passes colour into the custom view.  
Persisted as `int` ARGB in `SettingsManager.KEY_BUTTON_COLOR`.  
Live updates via `SharedPreferences.OnSharedPreferenceChangeListener` in `FloatingButtonService` (watches `KEY_BUTTON_COLOR` and `KEY_BUTTON_STYLE`).

### TTS engine picker
`populateTtsEngines()` uses `PackageManager.queryIntentServices("android.intent.action.TTS_SERVICE")` — more comprehensive than `TextToSpeech.getEngines()` on many devices.  
Requires `<queries>` block in `AndroidManifest.xml` for Android 11+ package visibility.  
Engine change triggers `reinitTts()` via `SharedPreferences.OnSharedPreferenceChangeListener`.

### Style card selection (MainActivity)
`MaterialCardView` (not plain `CardView`) — required for `setStrokeWidth()` / `setStrokeColor()` selection indicator.  
`applyCardSelection()` sets stroke + content description ("Circle, selected" / "Semi-circle").

### Checkbox / checkable state reading
`speakNode()` appends `", checked"` or `", unchecked"` for nodes where `node.isCheckable()` is true.

### Highlight overlay
Optional yellow border overlay drawn via `GradientDrawable` in `AccessibilitrollerService.showHighlight()`.  
Uses `TYPE_ACCESSIBILITY_OVERLAY` (inside the service, so allowed).  
Auto-hides after 1500ms via `Handler.postDelayed`.

### Gesture state machine
`FloatingButtonService` states: `IDLE → FIRST_DOWN → FIRST_WAITING → SECOND_DOWN`  
`needsDoubleTapWindow()` — if neither double-press nor double-hold is configured, single press fires immediately on finger lift (no 200ms wait window). `SINGLE_PRESS_TIMEOUT_MS = 200`.

### Window-raise anti-pattern — DO NOT use `TYPE_WINDOW_STATE_CHANGED`
Raising the button to foreground by listening to `AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED` and removing/re-adding the button window causes:
- Constant button flashing (event fires on every focus change, keyboard event, etc.)
- Repeated "drawing over other apps" system notification on every re-add
The `TYPE_ACCESSIBILITY_OVERLAY` approach makes this unnecessary.

### CI
`.github/workflows/build.yml` uses:
- `gradle/actions/setup-gradle@v4` with `gradle-version: '8.7'`
- `actions/cache@v4` for keystore (key: `debug-keystore-v1`)
- `gradle assembleDebug` (not `./gradlew` — no wrapper JAR committed)
- Artifact upload: `app/build/outputs/apk/debug/app-debug.apk`

---

## File map

| File | Purpose |
|---|---|
| `app/src/main/java/…/MainActivity.java` | Settings UI: permissions, style cards, colour swatches, gesture spinners, TTS engine spinner, auto-disable/highlight/read-backgrounds switches, service toggle |
| `app/src/main/java/…/FloatingButtonService.java` | Overlay button, gesture state machine, speak mode, drag mode; `wAddView/wRemoveView/wUpdateViewLayout` helpers |
| `app/src/main/java/…/AccessibilitrollerService.java` | AccessibilityService, TTS ownership, multi-window hit-testing, nearest-node snap, highlight overlay; static proxy (`instance`, `addOverlayView`) |
| `app/src/main/java/…/SettingsManager.java` | SharedPreferences singleton |
| `app/src/main/java/…/PermissionHelper.java` | Overlay / accessibility / notification permission checks |
| `app/src/main/java/…/SemiCircleButtonView.java` | Custom View: half-circle via `canvas.drawArc()`, 28dp × 56dp |
| `app/src/main/res/xml/accessibility_service_config.xml` | Service flags (no touch exploration) |
| `app/src/main/res/values/themes.xml` | Brand colours only — no surface overrides |
| `app/src/main/res/layout/activity_main.xml` | Main screen layout |
| `app/build.gradle` | AGP config + explicit debug signing |
| `gradle.properties` | AndroidX flag, Jetifier, JVM args |
| `.github/workflows/build.yml` | CI build + keystore caching |

---

## Current status (as of commit `6c86c41`)

All known issues resolved. Features implemented and tested:

1. **Button stays above system UI** — `TYPE_ACCESSIBILITY_OVERLAY` via static proxy; survives notification shade pull-down
2. **Status bar reading** — `getWindows()` multi-window search; nearest-node snap for tiny icons (28dp radius, system windows only)
3. **Instant explore-by-touch** — `SPEAK_DEBOUNCE_MS = 0`; TTS dedup prevents noise
4. **Live settings** — colour and style update without service restart via `OnSharedPreferenceChangeListener`
5. **TTS engine picker** — `PackageManager.queryIntentServices` enumerates all engines; live reinit on change
6. **Highlight overlay** — optional yellow border around focused element (1.5 s auto-hide)
7. **Checkbox state** — appends ", checked" / ", unchecked" to node text
8. **Read backgrounds switch** — toggle between meaningful-content-only and full deepest-node traversal
9. **Style cards** — MaterialCardView stroke selection indicator; TalkBack-labelled
10. **Hold gesture → drag** — `enable_drag` available on hold and double-hold spinners
11. **Instant single press** — no double-tap window when double gestures set to "None"
12. **Softer vibration** — `VibrationEffect.createOneShot(20, 80)` / `(15, 60)`

No pending tasks.

---

## Known gotchas / lessons learned

- `WRAP_CONTENT` in `WindowManager.LayoutParams` on a view with no intrinsic size = full-screen button.
- `TYPE_ACCESSIBILITY_OVERLAY` outside an `AccessibilityService` process = crash (`BadTokenException`). Use the static proxy pattern.
- `TYPE_APPLICATION_OVERLAY` sits below the notification shade — button disappears when shade is pulled down.
- `WindowManagerGlobal` is a per-process singleton: `removeView()` / `updateViewLayout()` work from any `WindowManager` instance regardless of which one did `addView()`.
- `TYPE_WINDOW_STATE_CHANGED` fires on virtually every UI interaction (focus changes, keyboard events) — never use it to re-raise a window, it causes constant flashing and repeated system notifications.
- `getRootInActiveWindow()` cannot see the status bar — use `getWindows()` and check `AccessibilityWindowInfo.TYPE_SYSTEM` for system UI.
- `flagRequestTouchExplorationMode` = entire system switches to TalkBack gesture mode.
- `AccessibilityManager.getEnabledAccessibilityServiceList()` unreliable — always use `Settings.Secure`.
- `TextToSpeech.getEngines()` misses some installed engines — use `PackageManager.queryIntentServices("android.intent.action.TTS_SERVICE")` instead.
- `CardView` does not support stroke — must use `MaterialCardView` for selection indicators.
- Hardcoding `colorBackground`/`colorSurface` in `themes.xml` breaks dark mode.
- `GradientDrawable` is in `android.graphics.drawable`, not `android.graphics`.
- Fresh CI keystore on every run = signature mismatch on update install — must cache the keystore.
- No `gradle-wrapper.jar` in repo — use `gradle/actions/setup-gradle` with explicit version.
- `<queries>` block required in `AndroidManifest.xml` for `PackageManager.queryIntentServices` on Android 11+.
