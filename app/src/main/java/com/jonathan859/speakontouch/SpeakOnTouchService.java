package com.jonathan859.speakontouch;

import android.accessibilityservice.AccessibilityButtonController;
import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Speak on Touch: the accessibility service runs quietly in the background.
 * Each press of the accessibility button / shortcut toggles speak mode, which
 * shows a full-screen touch layer and reads aloud whatever is under the finger.
 */
public class SpeakOnTouchService extends AccessibilityService {

    private static final String TAG = "SpeakOnTouchSvc";
    private static final int SNAP_RADIUS_DP = 28;
    private static final long SESSION_RESET_MS = 3000;

    private WindowManager windowManager;
    private View touchLayer;
    private View highlightView;
    private final Handler highlightHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideHighlight = this::removeHighlight;

    private final SharedPreferences.OnSharedPreferenceChangeListener engineListener =
            (prefs, key) -> {
                if (SettingsManager.KEY_TTS_ENGINE.equals(key)) reinitTts();
            };

    private final AccessibilityButtonController.AccessibilityButtonCallback buttonCallback =
            new AccessibilityButtonController.AccessibilityButtonCallback() {
                @Override
                public void onClicked(AccessibilityButtonController controller) {
                    toggleSpeakMode();
                }
            };

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String lastSpokenText = null;
    private long lastTouchTime = 0;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        SettingsManager.getInstance(this).registerListener(engineListener);
        initTts();
        // Requires flagRequestAccessibilityButton in accessibility_service_config.xml.
        // With it, the on-screen accessibility button / gesture / floating menu and
        // (once the service is on) the volume-key shortcut call onClicked instead of
        // toggling the whole service.
        getAccessibilityButtonController().registerAccessibilityButtonCallback(
                buttonCallback, highlightHandler);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {
        if (tts != null) tts.stop();
    }

    @Override
    public void onDestroy() {
        getAccessibilityButtonController().unregisterAccessibilityButtonCallback(buttonCallback);
        SettingsManager.getInstance(this).unregisterListener(engineListener);
        highlightHandler.removeCallbacksAndMessages(null);
        removeHighlight();
        removeTouchLayer();
        shutdownTts();
        super.onDestroy();
    }

    // ── Speak mode (touch layer) ──────────────────────────────────────────────

    private void toggleSpeakMode() {
        if (touchLayer != null) stopSpeakMode();
        else startSpeakMode();
    }

    private void startSpeakMode() {
        if (touchLayer != null) return;
        lastSpokenText = null;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSPARENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        touchLayer = new View(this);
        touchLayer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        touchLayer.setOnTouchListener(this::onTouchLayerTouch);
        windowManager.addView(touchLayer, lp);
        vibrate(20, 80);
    }

    private void stopSpeakMode() {
        if (touchLayer == null) return;
        removeTouchLayer();
        vibrate(15, 60);
    }

    private void removeTouchLayer() {
        if (touchLayer != null && touchLayer.isAttachedToWindow()) {
            windowManager.removeView(touchLayer);
        }
        touchLayer = null;
    }

    private boolean onTouchLayerTouch(View v, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            long now = System.currentTimeMillis();
            if (now - lastTouchTime > SESSION_RESET_MS) {
                lastSpokenText = null; // new touch session — allow re-reading
            }
            lastTouchTime = now;
            speakNodeAtCoordinates((int) event.getRawX(), (int) event.getRawY());
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // Removing only the touch layer; TTS keeps running, so the last item finishes speaking.
            if (SettingsManager.getInstance(this).getSpeakAutoDisable()) stopSpeakMode();
        }
        return true;
    }

    private void vibrate(long ms, int amplitude) {
        Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vib != null && vib.hasVibrator()) {
            vib.vibrate(VibrationEffect.createOneShot(ms, amplitude));
        }
    }

    // ── TTS engine setup ──────────────────────────────────────────────────────

    private void initTts() {
        String engine = SettingsManager.getInstance(this).getTtsEngine();
        TextToSpeech.OnInitListener listener = status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) {
                applyLocale();
            } else {
                Log.e(TAG, "TTS init failed: " + status);
                tts = null;
            }
        };
        tts = engine.isEmpty()
                ? new TextToSpeech(this, listener)
                : new TextToSpeech(this, listener, engine);
    }

    private void reinitTts() {
        shutdownTts();
        initTts();
    }

    private void shutdownTts() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ttsReady = false;
    }

    private void applyLocale() {
        int result = tts.setLanguage(Locale.getDefault());
        if (result == TextToSpeech.LANG_MISSING_DATA
                || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.ENGLISH);
        }
        ttsReady = true;
    }

    // ── Hit-testing ───────────────────────────────────────────────────────────

    private void speakNodeAtCoordinates(int x, int y) {
        SettingsManager settings = SettingsManager.getInstance(this);
        boolean readBg = settings.getReadBackgrounds();
        AccessibilityNodeInfo target = null;

        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows != null) {
            // Phase 1: exact hit-test across all windows (front-to-back)
            for (AccessibilityWindowInfo w : windows) {
                // Skip our own touch layer / highlight windows
                if (w.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue;
                AccessibilityNodeInfo root = w.getRoot();
                if (root == null) continue;
                target = readBg ? findDeepestNodeAt(root, x, y) : findMeaningfulNodeAt(root, x, y);
                if (target != null) break;
            }
            // Phase 2: snap to nearest node in system windows (status bar, nav bar)
            if (target == null) {
                int snapPx = dpToPx(SNAP_RADIUS_DP);
                for (AccessibilityWindowInfo w : windows) {
                    if (w.getType() != AccessibilityWindowInfo.TYPE_SYSTEM) continue;
                    AccessibilityNodeInfo root = w.getRoot();
                    if (root == null) continue;
                    target = findNearestMeaningfulNode(root, x, y, snapPx);
                    if (target != null) break;
                }
            }
        }
        // Fallback when getWindows() returns nothing
        if (target == null) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                target = readBg ? findDeepestNodeAt(root, x, y) : findMeaningfulNodeAt(root, x, y);
            }
        }
        if (target == null) return;

        if (settings.getFocusWholeItem()) {
            target = promoteToFocusableItem(target);
            speakNode(target, true);
        } else {
            speakNode(target, false);
        }
    }

    private AccessibilityNodeInfo findNearestMeaningfulNode(AccessibilityNodeInfo root,
            int x, int y, int radiusPx) {
        List<AccessibilityNodeInfo> candidates = new ArrayList<>();
        gatherMeaningfulNodes(root, candidates);

        AccessibilityNodeInfo best = null;
        float bestDist = Float.MAX_VALUE;
        Rect r = new Rect();
        for (AccessibilityNodeInfo node : candidates) {
            node.getBoundsInScreen(r);
            if (r.isEmpty()) continue;
            float dist = distToRect(x, y, r);
            if (dist <= radiusPx && dist < bestDist) {
                bestDist = dist;
                best = node;
            }
        }
        for (AccessibilityNodeInfo node : candidates) {
            if (node != best) node.recycle();
        }
        return best;
    }

    private void gatherMeaningfulNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out) {
        if (node == null) return;
        for (int i = 0; i < node.getChildCount(); i++) {
            gatherMeaningfulNodes(node.getChild(i), out);
        }
        if (hasMeaningfulContent(node)) {
            out.add(node);
        } else {
            node.recycle();
        }
    }

    private float distToRect(int x, int y, Rect r) {
        if (r.contains(x, y)) return 0f;
        float dx = Math.max(0f, (float) Math.max(r.left - x, x - r.right));
        float dy = Math.max(0f, (float) Math.max(r.top - y, y - r.bottom));
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private AccessibilityNodeInfo findMeaningfulNodeAt(AccessibilityNodeInfo node, int x, int y) {
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        if (!rect.contains(x, y)) { node.recycle(); return null; }
        for (int i = node.getChildCount() - 1; i >= 0; i--) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo result = findMeaningfulNodeAt(child, x, y);
            if (result != null) { node.recycle(); return result; }
        }
        if (hasMeaningfulContent(node)) return node;
        node.recycle();
        return null;
    }

    private AccessibilityNodeInfo findDeepestNodeAt(AccessibilityNodeInfo node, int x, int y) {
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        if (!rect.contains(x, y)) {
            node.recycle();
            return null;
        }
        for (int i = node.getChildCount() - 1; i >= 0; i--) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo result = findDeepestNodeAt(child, x, y);
            if (result != null) {
                node.recycle();
                return result;
            }
        }
        return node;
    }

    private boolean hasMeaningfulContent(AccessibilityNodeInfo node) {
        return !isEmpty(node.getContentDescription())
                || !isEmpty(node.getText())
                || node.isClickable()
                || node.isLongClickable();
    }

    // ── Whole-item focus (TalkBack-style grouping) ────────────────────────────

    /**
     * A node TalkBack would put its focus on as a single unit: something the user
     * can act on (tap / long-press), or that the app explicitly marked as a
     * screen-reader focus group. A non-clickable switch inside a clickable row is
     * not an item of its own — its state is read as part of the row.
     */
    private boolean isFocusableItem(AccessibilityNodeInfo node) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && node.isScreenReaderFocusable()) {
            return true;
        }
        return node.isClickable() || node.isLongClickable();
    }

    /**
     * Walks up from the touched node to the nearest ancestor that is a focusable item
     * (e.g. the whole WhatsApp chat row rather than the name label inside it).
     * Stops at scrolling containers and the window root, so a list or a whole screen
     * is never grabbed as one item. Returns the original node when no such item exists.
     */
    private AccessibilityNodeInfo promoteToFocusableItem(AccessibilityNodeInfo touched) {
        AccessibilityNodeInfo cur = touched;
        while (cur != null) {
            if (cur.isScrollable()) break;
            AccessibilityNodeInfo parent = cur.getParent();
            if (parent == null) break; // window root — never a whole-item target
            if (isFocusableItem(cur)) {
                parent.recycle();
                if (cur != touched) touched.recycle();
                return cur;
            }
            if (cur != touched) cur.recycle();
            cur = parent;
        }
        if (cur != null && cur != touched) cur.recycle();
        return touched;
    }

    /**
     * Builds what TalkBack would announce for a grouped item: its own label if it has
     * one, otherwise the labels of its descendants in order — skipping descendants that
     * are themselves separately focusable items (e.g. an avatar button inside a row).
     */
    private void collectGroupText(AccessibilityNodeInfo node, List<String> out, boolean isRoot) {
        if (!isRoot && !node.isVisibleToUser()) return;
        if (!isRoot && isFocusableItem(node)) return;

        CharSequence desc = node.getContentDescription();
        if (!isEmpty(desc)) {
            out.add(desc.toString());
            appendState(node, out);
            return; // a content description replaces the subtree, as in TalkBack
        }
        CharSequence text = node.getText();
        if (!isEmpty(text)) out.add(text.toString());
        appendState(node, out);

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            collectGroupText(child, out, false);
            child.recycle();
        }
    }

    private void appendState(AccessibilityNodeInfo node, List<String> out) {
        if (node.isCheckable()) out.add(node.isChecked() ? "checked" : "unchecked");
    }

    // ── Speak ─────────────────────────────────────────────────────────────────

    private void speakNode(AccessibilityNodeInfo node, boolean group) {
        if (node == null) return;
        String textStr;
        if (group) {
            List<String> parts = new ArrayList<>();
            collectGroupText(node, parts, true);
            textStr = parts.isEmpty() ? shortClassName(node) : String.join(", ", parts);
        } else {
            CharSequence text = node.getContentDescription();
            if (isEmpty(text)) text = node.getText();
            StringBuilder sb = new StringBuilder(isEmpty(text) ? shortClassName(node) : text);
            if (node.isCheckable()) {
                sb.append(node.isChecked() ? ", checked" : ", unchecked");
            }
            textStr = sb.toString();
        }

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        node.recycle();

        if (textStr.equals(lastSpokenText)) return;
        lastSpokenText = textStr;
        if (tts != null && ttsReady && !textStr.isEmpty()) {
            tts.speak(textStr, TextToSpeech.QUEUE_FLUSH, null, "speak_on_touch_utt");
        }
        if (SettingsManager.getInstance(this).getHighlightEnabled() && !bounds.isEmpty()) {
            showHighlight(bounds);
        }
    }

    private static String shortClassName(AccessibilityNodeInfo node) {
        String className = node.getClassName() != null ? node.getClassName().toString() : "";
        int dot = className.lastIndexOf('.');
        return dot >= 0 ? className.substring(dot + 1) : className;
    }

    private static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    // ── Highlight overlay ─────────────────────────────────────────────────────

    private void showHighlight(Rect bounds) {
        highlightHandler.removeCallbacks(hideHighlight);
        if (highlightView != null && highlightView.isAttachedToWindow()) {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) highlightView.getLayoutParams();
            lp.x = bounds.left;
            lp.y = bounds.top;
            lp.width = bounds.width();
            lp.height = bounds.height();
            windowManager.updateViewLayout(highlightView, lp);
        } else {
            GradientDrawable border = new GradientDrawable();
            border.setShape(GradientDrawable.RECTANGLE);
            border.setStroke(dpToPx(3), 0xFFFFEB3B);
            border.setColor(0x33FFEB3B);
            border.setCornerRadius(dpToPx(4));
            highlightView = new View(this);
            highlightView.setBackground(border);
            highlightView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    bounds.width(), bounds.height(),
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSPARENT);
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.x = bounds.left;
            lp.y = bounds.top;
            windowManager.addView(highlightView, lp);
        }
        highlightHandler.postDelayed(hideHighlight, 1500);
    }

    private void removeHighlight() {
        if (highlightView != null && highlightView.isAttachedToWindow()) {
            windowManager.removeView(highlightView);
        }
        highlightView = null;
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
