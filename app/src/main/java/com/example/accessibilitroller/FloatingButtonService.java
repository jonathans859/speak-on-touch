package com.example.accessibilitroller;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.ContextThemeWrapper;
import androidx.core.app.NotificationCompat;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class FloatingButtonService extends Service {

    private static final String CHANNEL_ID = "accessibilitroller_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final long SPEAK_DEBOUNCE_MS = 350;
    private static final long SINGLE_PRESS_TIMEOUT_MS = 200;

    private static boolean running = false;

    private WindowManager windowManager;
    private View buttonView;
    private WindowManager.LayoutParams params;
    private SettingsManager settings;

    private final Handler gestureHandler = new Handler(Looper.getMainLooper());
    private Runnable longPressRunnable;
    private Runnable resolutionRunnable;

    private static final int GESTURE_IDLE           = 0;
    private static final int GESTURE_FIRST_DOWN     = 1;
    private static final int GESTURE_FIRST_WAITING  = 2;
    private static final int GESTURE_SECOND_DOWN    = 3;

    private int gestureState = GESTURE_IDLE;
    private boolean longPressFired = false;

    private boolean dragMode = false;
    private float dragStartX, dragStartY;
    private int dragStartParamX, dragStartParamY;

    private boolean speakModeActive = false;
    private View speakOverlay;
    private long lastSpeakTime = 0;

    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (prefs, key) -> {
                if (SettingsManager.KEY_BUTTON_COLOR.equals(key)) {
                    applyButtonColor(settings.getButtonColor());
                } else if (SettingsManager.KEY_BUTTON_STYLE.equals(key)) {
                    updateButtonStyle();
                }
            };

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        settings = SettingsManager.getInstance(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        settings.registerListener(prefListener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        setupOverlay();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        settings.unregisterListener(prefListener);
        cancelLongPress();
        cancelResolution();
        removeSpeakOverlay();
        if (buttonView != null && buttonView.isAttachedToWindow()) {
            windowManager.removeView(buttonView);
            buttonView = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ── Notification ─────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, mainIntent,
                PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    // ── Overlay setup ─────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private void setupOverlay() {
        Display display = windowManager.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        int buttonPx = dpToPx(56);
        params = new WindowManager.LayoutParams(
                buttonPx,
                buttonPx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = (int) (settings.getButtonPosX() * size.x);
        params.y = (int) (settings.getButtonPosY() * size.y);

        int color = settings.getButtonColor();
        Context themed = new ContextThemeWrapper(this, R.style.Theme_Accessibilitroller);
        String style = settings.getButtonStyle();

        if (SettingsManager.STYLE_SEMICIRCLE.equals(style)) {
            SemiCircleButtonView semi = new SemiCircleButtonView(themed);
            semi.setColor(color);
            semi.setSnapLeft(params.x < size.x / 2);
            buttonView = semi;
            // Semicircle is narrower — set width to 28dp
            params.width = dpToPx(28);
        } else {
            View btn = new View(themed);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            btn.setBackground(bg);
            btn.setElevation(dpToPx(8));
            buttonView = btn;
        }

        // TalkBack accessibility
        buttonView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        buttonView.setClickable(true);
        ViewCompat.setAccessibilityDelegate(buttonView, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host,
                    AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Button.class.getName());
                info.setClickable(true);
            }
        });
        updateButtonContentDescription();

        // Click listener for TalkBack activation (double-tap)
        buttonView.setOnClickListener(v -> {
            if (speakModeActive) deactivateSpeakMode();
            else executeGestureAction(SettingsManager.KEY_GESTURE_SINGLE);
        });
        buttonView.setOnTouchListener(this::onButtonTouch);
        windowManager.addView(buttonView, params);
    }

    private void updateButtonContentDescription() {
        if (buttonView == null) return;
        buttonView.setContentDescription(speakModeActive
                ? getString(R.string.button_desc_active)
                : getString(R.string.button_desc_inactive));
    }

    private void updateButtonStyle() {
        if (speakModeActive) deactivateSpeakMode();
        if (buttonView != null && buttonView.isAttachedToWindow()) {
            windowManager.removeView(buttonView);
            buttonView = null;
        }
        setupOverlay();
    }

    private void applyButtonColor(int color) {
        if (buttonView == null) return;
        if (buttonView instanceof SemiCircleButtonView) {
            ((SemiCircleButtonView) buttonView).setColor(color);
        } else {
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            buttonView.setBackground(bg);
        }
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ── Touch dispatcher ──────────────────────────────────────────────────────

    private boolean onButtonTouch(View v, MotionEvent event) {
        if (dragMode) return handleDrag(event);
        return handleGesture(event);
    }

    // ── Speak mode ────────────────────────────────────────────────────────────

    private void activateSpeakMode() {
        speakModeActive = true;
        vibrate(20, 80);
        if (buttonView != null) buttonView.setAlpha(0.6f);
        updateButtonContentDescription();

        WindowManager.LayoutParams overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSPARENT);
        speakOverlay = new View(this);
        speakOverlay.setOnTouchListener(this::onSpeakTouch);
        windowManager.addView(speakOverlay, overlayParams);

        // Re-add button on top so its gestures still work
        if (buttonView != null && buttonView.isAttachedToWindow()) {
            windowManager.removeView(buttonView);
            windowManager.addView(buttonView, params);
        }
    }

    private void deactivateSpeakMode() {
        speakModeActive = false;
        if (buttonView != null) buttonView.setAlpha(1.0f);
        updateButtonContentDescription();
        removeSpeakOverlay();
        vibrate(15, 60);
    }

    private void removeSpeakOverlay() {
        if (speakOverlay != null && speakOverlay.isAttachedToWindow()) {
            windowManager.removeView(speakOverlay);
        }
        speakOverlay = null;
    }

    private boolean onSpeakTouch(View v, MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            long now = System.currentTimeMillis();
            if (action == MotionEvent.ACTION_DOWN || now - lastSpeakTime >= SPEAK_DEBOUNCE_MS) {
                lastSpeakTime = now;
                broadcastSpeak((int) event.getRawX(), (int) event.getRawY());
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (settings.getSpeakAutoDisable()) {
                deactivateSpeakMode();
            }
        }
        return true;
    }

    private void broadcastSpeak(int x, int y) {
        Intent broadcast = new Intent(AccessibilitrollerService.ACTION_SPEAK_AT);
        broadcast.putExtra(AccessibilitrollerService.EXTRA_X, x);
        broadcast.putExtra(AccessibilitrollerService.EXTRA_Y, y);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    private void vibrate(long ms, int amplitude) {
        Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vib != null && vib.hasVibrator()) {
            vib.vibrate(VibrationEffect.createOneShot(ms, amplitude));
        }
    }

    // ── Drag mode ─────────────────────────────────────────────────────────────

    private void enterDragMode() {
        dragMode = true;
        animateScale(1.0f, 1.15f);
    }

    private void exitDragMode() {
        dragMode = false;
        animateScale(1.15f, 1.0f);
    }

    private boolean handleDrag(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                dragStartX = event.getRawX();
                dragStartY = event.getRawY();
                dragStartParamX = params.x;
                dragStartParamY = params.y;
                return true;
            case MotionEvent.ACTION_MOVE:
                params.x = (int) (event.getRawX() - buttonView.getWidth() / 2f);
                params.y = (int) (event.getRawY() - buttonView.getHeight() / 2f);
                windowManager.updateViewLayout(buttonView, params);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                snapToEdge();
                savePosition();
                exitDragMode();
                return true;
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private void snapToEdge() {
        Display display = windowManager.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        boolean goRight = (params.x + buttonView.getWidth() / 2) >= size.x / 2;
        params.x = goRight ? size.x - buttonView.getWidth() : 0;
        windowManager.updateViewLayout(buttonView, params);
        if (buttonView instanceof SemiCircleButtonView) {
            ((SemiCircleButtonView) buttonView).setSnapLeft(!goRight);
        }
    }

    @SuppressWarnings("deprecation")
    private void savePosition() {
        Display display = windowManager.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        if (size.x > 0 && size.y > 0) {
            settings.setButtonPos((float) params.x / size.x, (float) params.y / size.y);
        }
    }

    private void animateScale(float from, float to) {
        ValueAnimator anim = ValueAnimator.ofFloat(from, to);
        anim.setDuration(150);
        anim.addUpdateListener(a -> {
            float val = (float) a.getAnimatedValue();
            buttonView.setScaleX(val);
            buttonView.setScaleY(val);
        });
        anim.start();
    }

    // ── Gesture state machine ─────────────────────────────────────────────────

    private boolean handleGesture(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:  onGestureDown(); return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: onGestureUp(); return true;
        }
        return false;
    }

    private void onGestureDown() {
        longPressFired = false;
        if (gestureState == GESTURE_IDLE) {
            gestureState = GESTURE_FIRST_DOWN;
            longPressRunnable = () -> {
                longPressFired = true;
                gestureState = GESTURE_IDLE;
                cancelResolution();
                executeGestureAction(SettingsManager.KEY_GESTURE_HOLD);
            };
            gestureHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
        } else if (gestureState == GESTURE_FIRST_WAITING) {
            cancelResolution();
            gestureState = GESTURE_SECOND_DOWN;
            longPressRunnable = () -> {
                longPressFired = true;
                gestureState = GESTURE_IDLE;
                executeGestureAction(SettingsManager.KEY_GESTURE_DOUBLE_HOLD);
            };
            gestureHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
        }
    }

    private boolean needsDoubleTapWindow() {
        return !SettingsManager.ACTION_NONE.equals(
                settings.getGestureAction(SettingsManager.KEY_GESTURE_DOUBLE))
                || !SettingsManager.ACTION_NONE.equals(
                settings.getGestureAction(SettingsManager.KEY_GESTURE_DOUBLE_HOLD));
    }

    private void onGestureUp() {
        if (gestureState == GESTURE_FIRST_DOWN) {
            cancelLongPress();
            if (!longPressFired) {
                if (needsDoubleTapWindow()) {
                    gestureState = GESTURE_FIRST_WAITING;
                    resolutionRunnable = () -> {
                        gestureState = GESTURE_IDLE;
                        executeGestureAction(SettingsManager.KEY_GESTURE_SINGLE);
                    };
                    gestureHandler.postDelayed(resolutionRunnable, SINGLE_PRESS_TIMEOUT_MS);
                } else {
                    gestureState = GESTURE_IDLE;
                    executeGestureAction(SettingsManager.KEY_GESTURE_SINGLE);
                }
            } else {
                gestureState = GESTURE_IDLE;
            }
        } else if (gestureState == GESTURE_SECOND_DOWN) {
            cancelLongPress();
            if (!longPressFired) {
                gestureState = GESTURE_IDLE;
                executeGestureAction(SettingsManager.KEY_GESTURE_DOUBLE);
            } else {
                gestureState = GESTURE_IDLE;
            }
        }
    }

    private void cancelLongPress() {
        if (longPressRunnable != null) {
            gestureHandler.removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
    }

    private void cancelResolution() {
        if (resolutionRunnable != null) {
            gestureHandler.removeCallbacks(resolutionRunnable);
            resolutionRunnable = null;
        }
    }

    // ── Action dispatch ───────────────────────────────────────────────────────

    private void executeGestureAction(String gestureKey) {
        if (speakModeActive) {
            deactivateSpeakMode();
            return;
        }
        String action = settings.getGestureAction(gestureKey);
        switch (action) {
            case SettingsManager.ACTION_SPEAK_ON_TOUCH:
                activateSpeakMode();
                break;
            case SettingsManager.ACTION_ENABLE_DRAG:
                enterDragMode();
                break;
            default:
                break;
        }
    }
}
