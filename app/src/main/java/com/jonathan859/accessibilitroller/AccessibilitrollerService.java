package com.jonathan859.accessibilitroller;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AccessibilitrollerService extends AccessibilityService {

    private static final String TAG = "AccessibilitrollerSvc";
    private static final int SNAP_RADIUS_DP = 28;

    private static AccessibilitrollerService instance;

    static boolean isAvailable() { return instance != null; }

    static void addOverlayView(View v, WindowManager.LayoutParams lp) {
        if (instance == null) return;
        lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        instance.windowManager.addView(v, lp);
    }

    // FloatingButtonService sends this with EXTRA_X / EXTRA_Y to request a speak
    public static final String ACTION_SPEAK_AT = "com.jonathan859.accessibilitroller.SPEAK_AT";
    public static final String EXTRA_X = "x";
    public static final String EXTRA_Y = "y";

    private WindowManager windowManager;
    private View highlightView;
    private final Handler highlightHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideHighlight = this::removeHighlight;

    private final SharedPreferences.OnSharedPreferenceChangeListener engineListener =
            (prefs, key) -> {
                if (SettingsManager.KEY_TTS_ENGINE.equals(key)) reinitTts();
            };

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String lastSpokenText = null;
    private long lastSpeakBroadcastTime = 0;
    private static final long SESSION_RESET_MS = 3000;

    private final BroadcastReceiver speakReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int x = intent.getIntExtra(EXTRA_X, -1);
            int y = intent.getIntExtra(EXTRA_Y, -1);
            if (x < 0 || y < 0) return;
            long now = System.currentTimeMillis();
            if (now - lastSpeakBroadcastTime > SESSION_RESET_MS) {
                lastSpokenText = null; // new speak session — allow re-reading
            }
            lastSpeakBroadcastTime = now;
            speakNodeAtCoordinates(x, y);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        SettingsManager.getInstance(this).registerListener(engineListener);
        initTts();
        LocalBroadcastManager.getInstance(this).registerReceiver(
                speakReceiver, new IntentFilter(ACTION_SPEAK_AT));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        instance = null;
        SettingsManager.getInstance(this).unregisterListener(engineListener);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(speakReceiver);
        highlightHandler.removeCallbacksAndMessages(null);
        removeHighlight();
        shutdownTts();
        super.onDestroy();
    }

    // ── TTS engine setup ──────────────────────────────────────────────────────

    private void initTts() {
        String engine = SettingsManager.getInstance(this).getTtsEngine();
        TextToSpeech.OnInitListener listener = status -> {
            if (status == TextToSpeech.SUCCESS) {
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

    // ── Speak ─────────────────────────────────────────────────────────────────

    private void speakNodeAtCoordinates(int x, int y) {
        boolean readBg = SettingsManager.getInstance(this).getReadBackgrounds();
        AccessibilityNodeInfo target = null;

        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows != null) {
            // Phase 1: exact hit-test across all windows (front-to-back)
            for (AccessibilityWindowInfo w : windows) {
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
        if (target != null) speakNode(target);
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

    private boolean hasMeaningfulContent(AccessibilityNodeInfo node) {
        return (node.getContentDescription() != null && node.getContentDescription().length() > 0)
                || (node.getText() != null && node.getText().length() > 0)
                || node.isClickable()
                || node.isLongClickable();
    }

    private void speakNode(AccessibilityNodeInfo node) {
        if (node == null) return;
        CharSequence text = node.getContentDescription();
        if (text == null || text.length() == 0) text = node.getText();
        if (text == null || text.length() == 0) {
            String className = node.getClassName() != null
                    ? node.getClassName().toString() : "";
            int dot = className.lastIndexOf('.');
            text = dot >= 0 ? className.substring(dot + 1) : className;
        }
        StringBuilder sb = new StringBuilder(text);
        if (node.isCheckable()) {
            sb.append(node.isChecked() ? ", checked" : ", unchecked");
        }
        String textStr = sb.toString();

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        node.recycle();

        if (textStr.equals(lastSpokenText)) return;
        lastSpokenText = textStr;
        if (tts != null && ttsReady && !textStr.isEmpty()) {
            tts.speak(textStr, TextToSpeech.QUEUE_FLUSH, null, "accessibilitroller_utt");
        }
        SettingsManager settings = SettingsManager.getInstance(this);
        if (settings.getHighlightEnabled() && !bounds.isEmpty()) {
            showHighlight(bounds);
        }
    }

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
}
