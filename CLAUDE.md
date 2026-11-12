# Speak on Touch — Project Reference

## What this is
A pure-Java Android app (min SDK 26, target SDK 35), formerly "Accessibilitroller". While its AccessibilityService is enabled, a full-screen touch layer reads aloud whatever the user touches or drags over, using AccessibilityService + TextToSpeech. There is **no in-app floating button** — speak mode is switched on and off purely by enabling/disabling the service (accessibility shortcut, accessibility button, volume-key shortcut, or Settings).

---

## Build configuration

| Field | Value |
|---|---|
| Namespace / Application ID | `com.jonathan859.speakontouch` (was `com.jonathan859.accessibilitroller` — installs as a separate app) |
| Display name | `Speak on Touch` |
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
Keystore is committed at `keystore/debug.keystore` (`.gitignore` has a `!keystore/debug.keystore` exception).  
`build.gradle` references it via `rootProject.file("keystore/debug.keystore")` — works identically for local builds and CI.  
Credentials: storePassword `android`, keyAlias `androiddebugkey`, keyPassword `android`.  
No release keystore exists — debug only.

---

## Architecture

### Single service
- **`SpeakOnTouchService`** — `AccessibilityService`. Owns TTS, the touch layer, hit-testing and the highlight overlay. No IPC, no foreground service, no notification.

Lifecycle:
- `onServiceConnected()` → init TTS, add touch layer (`TYPE_ACCESSIBILITY_OVERLAY`, full screen, `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN`), vibrate `(20, 80)`.
- `onDestroy()` → remove touch layer + highlight, vibrate `(15, 60)`, shut down TTS.
- The service does **not** set `flagRequestAccessibilityButton`, so on Android 12+ the accessibility shortcut simply toggles the service on/off — that *is* the on/off switch for speak mode.

### Settings
`SettingsManager` — singleton `SharedPreferences` wrapper. File name `speakontouch_prefs`. Keys:
- `tts_engine` — TTS engine package name (empty = system default)
- `speak_auto_disable` — boolean (default false)
- `highlight_enabled` — boolean (default false)
- `read_backgrounds` — boolean (default false)
- `focus_whole_item` — boolean (default false)

### MainActivity
Service status card (on/off + "Open settings" → `ACTION_ACCESSIBILITY_SETTINGS`), how-to text, TTS engine spinner, four switches.

---

## Critical implementation details

### Touch layer ignores itself in hit-testing
`getWindows()` returns our own `TYPE_ACCESSIBILITY_OVERLAY` windows (touch layer, highlight). Phase 1 skips `AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY` windows — otherwise "read backgrounds" mode would hit the touch layer's root view. Both overlay views also set `IMPORTANT_FOR_ACCESSIBILITY_NO`.

### Multi-window hit-testing (status bar access)
`getRootInActiveWindow()` only returns the foreground app — it cannot see the status bar or nav bar.

Use `getWindows()` instead, which returns all `AccessibilityWindowInfo` objects including `TYPE_SYSTEM` windows. Iterate front-to-back for the exact hit-test.

**Two-phase search in `speakNodeAtCoordinates()`:**
1. Exact hit-test across all windows (front-to-back order)
2. If miss: nearest-node snap (`findNearestMeaningfulNode`) restricted to `TYPE_SYSTEM` windows with a 28dp radius — uses Euclidean distance from touch point to node bounds edge (`distToRect()`).

### Focus whole item (TalkBack-style grouping)
When `focus_whole_item` is on, after hit-testing:
- `promoteToFocusableItem()` walks `getParent()` up from the touched node to the nearest **focusable item**: `isClickable() || isLongClickable() || isScreenReaderFocusable()` (API 28+). Stops at a scrollable ancestor and never picks the window root (no parent) — so a list or entire screen is never one item. Falls back to the touched node.
- `collectGroupText()` builds the utterance: the item's content description if it has one (replaces subtree), otherwise text of descendants in order, joined with ", ". Skips invisible descendants and descendants that are themselves focusable items (e.g. an avatar button inside a chat row). Non-clickable checkables inside the row contribute "checked"/"unchecked".
- Highlight uses the item's bounds, so the border surrounds the whole row.
- `isCheckable()` deliberately does **not** make a node an item: a non-clickable switch inside a clickable Settings row belongs to the row.

### Speak debounce / no-repeat TTS
No throttle on `ACTION_MOVE`. `speakNode()` deduplicates by `lastSpokenText`; resets after a >3 s gap between touches (`SESSION_RESET_MS = 3000`).

### Auto-disable on finger lift
On `ACTION_UP`/`ACTION_CANCEL` with `speak_auto_disable` set: remove touch layer immediately, vibrate, then poll `tts.isSpeaking()` (first check after 150 ms) and call `disableSelf()` once speech finishes — TTS is shut down in `onDestroy()`, so disabling immediately would cut off the last utterance. `disabling` flag prevents repeats.

### Accessibility service detection
Use `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` + `TextUtils.SimpleStringSplitter(':')` + `ComponentName.unflattenFromString()`.  
**Do not** use `AccessibilityManager.getEnabledAccessibilityServiceList()` — unreliable on many ROMs.

### Touch exploration mode — DO NOT USE
`flagRequestTouchExplorationMode` / `canRequestTouchExplorationMode` must **not** appear in `accessibility_service_config.xml`.  
This flag puts the entire system into TalkBack explore-by-touch mode, replacing all normal gestures system-wide.

### Accessibility service config
```xml
android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"
```
No hover events. No touch exploration. No `flagRequestAccessibilityButton` (see Lifecycle).

### Dark mode
`themes.xml` (`Theme.SpeakOnTouch`) must **not** override `colorBackground`, `colorSurface`, or `android:colorBackground`.  
Use `Theme.Material3.DayNight.NoActionBar` and let it handle surface colours.

### Checkbox / checkable state reading
Appends ", checked" / ", unchecked" for checkable nodes (both modes).

### Highlight overlay
Optional yellow border overlay drawn via `GradientDrawable` in `showHighlight()`. `TYPE_ACCESSIBILITY_OVERLAY`, `FLAG_NOT_TOUCHABLE`. Auto-hides after 1500ms.

### TTS engine picker
`populateTtsEngines()` uses `PackageManager.queryIntentServices("android.intent.action.TTS_SERVICE")` — more comprehensive than `TextToSpeech.getEngines()` on many devices.  
Requires `<queries>` block in `AndroidManifest.xml` for Android 11+ package visibility.  
Engine change triggers `reinitTts()` via `SharedPreferences.OnSharedPreferenceChangeListener`.

### CI
`.github/workflows/build.yml` uses:
- `gradle/actions/setup-gradle@v4` with `gradle-version: '8.7'`
- `gradle assembleDebug` (not `./gradlew` — no wrapper JAR committed)
- Artifact `speak-on-touch-debug`: `app/build/outputs/apk/debug/app-debug.apk`

---

## File map

Java sources live under `app/src/main/java/com/jonathan859/speakontouch/`.

| File | Purpose |
|---|---|
| `…/MainActivity.java` | Settings UI: service status, TTS engine spinner, focus-whole-item / auto-disable / highlight / read-backgrounds switches |
| `…/SpeakOnTouchService.java` | AccessibilityService: touch layer, TTS, multi-window hit-testing, nearest-node snap, whole-item grouping, highlight overlay |
| `…/SettingsManager.java` | SharedPreferences singleton |
| `…/PermissionHelper.java` | Accessibility-service-enabled check |
| `app/src/main/res/xml/accessibility_service_config.xml` | Service flags (no touch exploration) |
| `app/src/main/res/values/themes.xml` | Brand colours only — no surface overrides |
| `app/src/main/res/layout/activity_main.xml` | Main screen layout |
| `app/build.gradle` | AGP config + explicit debug signing |
| `.github/workflows/build.yml` | CI build |

---

## History
- Up to `850d392`: app drew its own floating button (`FloatingButtonService`, circle/semicircle styles, colours, gesture state machine, drag mode, static `TYPE_ACCESSIBILITY_OVERLAY` proxy). All removed when the app became Speak on Touch; see git history if any of it is needed again.
- Renamed to Speak on Touch: application ID `com.jonathan859.accessibilitroller` → `com.jonathan859.speakontouch`, service class `AccessibilitrollerService` → `SpeakOnTouchService`. New install; the old app must be uninstalled separately and settings start from defaults.

---

## Known gotchas / lessons learned

- `TYPE_ACCESSIBILITY_OVERLAY` can only be added from inside the `AccessibilityService` (else `BadTokenException`).
- `TYPE_APPLICATION_OVERLAY` sits below the notification shade.
- `WRAP_CONTENT` in `WindowManager.LayoutParams` on a view with no intrinsic size = full-screen window.
- `TYPE_WINDOW_STATE_CHANGED` fires on virtually every UI interaction — never use it to re-raise a window.
- `getRootInActiveWindow()` cannot see the status bar — use `getWindows()` and check `AccessibilityWindowInfo.TYPE_SYSTEM`.
- `getWindows()` includes the service's own overlay windows — skip `TYPE_ACCESSIBILITY_OVERLAY`.
- `flagRequestTouchExplorationMode` = entire system switches to TalkBack gesture mode.
- `AccessibilityManager.getEnabledAccessibilityServiceList()` unreliable — always use `Settings.Secure`.
- `TextToSpeech.getEngines()` misses some installed engines — use `PackageManager.queryIntentServices`.
- Hardcoding `colorBackground`/`colorSurface` in `themes.xml` breaks dark mode.
- GitHub Actions cache evicts after 7 days — keystore is committed instead.
- No `gradle-wrapper.jar` in repo — use `gradle/actions/setup-gradle` with explicit version.
- `<queries>` block required in `AndroidManifest.xml` for `PackageManager.queryIntentServices` on Android 11+.
