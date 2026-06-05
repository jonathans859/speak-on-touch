package com.example.accessibilitroller;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.util.Locale;
import java.util.Set;

public class AccessibilitrollerService extends AccessibilityService {

    private static final String TAG = "AccessibilitrollerSvc";
    public static final String ACTION_TOGGLE_SPEAK =
            "com.example.accessibilitroller.TOGGLE_SPEAK";

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean speakOnTouchEnabled = false;

    private final BroadcastReceiver toggleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            speakOnTouchEnabled = !speakOnTouchEnabled;
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                applyVoiceOrLocale();
            } else {
                Log.e(TAG, "TTS init failed with status: " + status);
                tts = null;
            }
        });

        LocalBroadcastManager.getInstance(this).registerReceiver(
                toggleReceiver, new IntentFilter(ACTION_TOGGLE_SPEAK));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!speakOnTouchEnabled) return;
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER) {
            speakNode(event.getSource());
        }
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(toggleReceiver);
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ttsReady = false;
        super.onDestroy();
    }

    // ── TTS voice setup ───────────────────────────────────────────────────────

    private void applyVoiceOrLocale() {
        String savedName = SettingsManager.getInstance(this).getTtsVoice();
        if (!savedName.isEmpty()) {
            Set<android.speech.tts.Voice> voices = tts.getVoices();
            if (voices != null) {
                for (android.speech.tts.Voice v : voices) {
                    if (v.getName().equals(savedName)) {
                        tts.setVoice(v);
                        ttsReady = true;
                        return;
                    }
                }
            }
        }
        int result = tts.setLanguage(Locale.getDefault());
        if (result == TextToSpeech.LANG_MISSING_DATA
                || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.ENGLISH);
        }
        ttsReady = true;
    }

    // ── speakNode ─────────────────────────────────────────────────────────────

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
        if (tts != null && ttsReady && text.length() > 0) {
            tts.speak(text.toString(), TextToSpeech.QUEUE_FLUSH, null, "accessibilitroller_utt");
        }
        node.recycle();
    }

    // ── Coordinate-based fallback ─────────────────────────────────────────────
    // Used for custom views that do not fire TYPE_VIEW_HOVER_ENTER.

    public void speakNodeAtCoordinates(int x, int y) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        AccessibilityNodeInfo target = findDeepestNodeAt(root, x, y);
        if (target != null) {
            speakNode(target);
        } else {
            root.recycle();
        }
    }

    private AccessibilityNodeInfo findDeepestNodeAt(AccessibilityNodeInfo node, int x, int y) {
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        if (!rect.contains(x, y)) {
            node.recycle();
            return null;
        }
        // Prefer the deepest child that contains the point
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
