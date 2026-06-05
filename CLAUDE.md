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
- **`AccessibilitrollerService`** — `AccessibilityService`. Owns TTS. Receives `ACTION_SPEAK_AT` broadcasts with `x`/`y` coordinates and performs coordinate-based hit-testing to find and speak the deepest UI node.

### IPC
`LocalBroadcastManager` sends `ACTION_SPEAK_AT` (+ `EXTRA_X`, `EXTRA_Y`) from `FloatingButtonService` → `AccessibilitrollerService`.

### Settings
`SettingsManager` — singleton `SharedPreferences` wrapper. Keys:
- `button_style` — `"circle"` | `"semicircle"`
- `gesture_single` / `gesture_double` / `gesture_hold` / `gesture_double_hold`
- `button_pos_x` / `button_pos_y` — float 0–1 relative to screen
- `tts_voice` — voice name string (empty = system default)
- `button_color` — int ARGB (default `0xFF6200EE`)
- `speak_auto_disable` — boolean (default false)

Action constants: `speak_on_touch`, `enable_drag`, `none`.

---

## Critical implementation details

### Overlay window type
Use **`TYPE_APPLICATION_OVERLAY`** everywhere (both button and speak overlay).  
`TYPE_ACCESSIBILITY_OVERLAY` is restricted to actual `AccessibilityService` processes — using it in `FloatingButtonService` throws `BadTokenException` and crashes.

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
Speak overlay is added first (`windowManager.addView(speakOverlay, …)`), then button is removed and re-added so it sits on top. Both happen before the next Choreographer frame — no visible flicker.

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
| `app/src/main/java/…/MainActivity.java` | Settings UI: permissions, style cards, colour swatches, gesture spinners, TTS voice spinner, auto-disable switch, service toggle |
| `app/src/main/java/…/FloatingButtonService.java` | Overlay button, gesture state machine, speak mode, drag mode |
| `app/src/main/java/…/AccessibilitrollerService.java` | AccessibilityService, TTS ownership, coordinate hit-testing |
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

## Current status (as of commit `ef987c1`)

All known issues resolved. The last batch of changes included:

1. **Button size fixed** — explicit `56dp` px in `WindowManager.LayoutParams`
2. **Colour picker** — 6 swatches in `MainActivity`, persisted as `KEY_BUTTON_COLOR`
3. **No-repeat TTS** — session-based `lastSpokenText` tracking with 3 s reset
4. **Auto-disable on finger lift** — `switch_speak_auto_disable` + `getSpeakAutoDisable()` in `onSpeakTouch()`
5. **TalkBack focusable button** — delegate, content description, click listener

**Awaiting user test** of the above. No pending tasks.

---

## Known gotchas / lessons learned

- `WRAP_CONTENT` in `WindowManager.LayoutParams` on a view with no intrinsic size = full-screen button.
- `TYPE_ACCESSIBILITY_OVERLAY` outside an `AccessibilityService` process = crash (`BadTokenException`).
- `flagRequestTouchExplorationMode` = entire system switches to TalkBack gesture mode.
- `AccessibilityManager.getEnabledAccessibilityServiceList()` unreliable — always use `Settings.Secure`.
- Hardcoding `colorBackground`/`colorSurface` in `themes.xml` breaks dark mode.
- Fresh CI keystore on every run = signature mismatch on update install — must cache the keystore.
- No `gradle-wrapper.jar` in repo — use `gradle/actions/setup-gradle` with explicit version.
