package com.example.accessibilitroller;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.ContextThemeWrapper;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class FloatingButtonService extends Service {

    private static final String CHANNEL_ID = "accessibilitroller_channel";
    private static final int NOTIFICATION_ID = 1001;

    private static boolean running = false;

    private WindowManager windowManager;
    private View buttonView;
    private WindowManager.LayoutParams params;
    private SettingsManager settings;

    private final Handler gestureHandler = new Handler(Looper.getMainLooper());
    private Runnable longPressRunnable;
    private Runnable resolutionRunnable;

    // Gesture state machine
    private static final int GESTURE_IDLE           = 0;
    private static final int GESTURE_FIRST_DOWN     = 1;
    private static final int GESTURE_FIRST_WAITING  = 2;
    private static final int GESTURE_SECOND_DOWN    = 3;

    private int gestureState = GESTURE_IDLE;
    private boolean longPressFired = false;

    private boolean dragMode = false;
    private float dragStartX, dragStartY;
    private int dragStartParamX, dragStartParamY;

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        settings = SettingsManager.getInstance(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
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
        cancelLongPress();
        cancelResolution();
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

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = (int) (settings.getButtonPosX() * size.x);
        params.y = (int) (settings.getButtonPosY() * size.y);

        Context themed = new ContextThemeWrapper(this, R.style.Theme_Accessibilitroller);
        String style = settings.getButtonStyle();
        if (SettingsManager.STYLE_SEMICIRCLE.equals(style)) {
            SemiCircleButtonView semi = new SemiCircleButtonView(themed);
            semi.setSnapLeft(params.x < size.x / 2);
            buttonView = semi;
        } else {
            buttonView = LayoutInflater.from(themed).inflate(R.layout.overlay_button, null);
        }

        buttonView.setOnTouchListener(this::onButtonTouch);
        windowManager.addView(buttonView, params);
    }

    // ── Touch dispatcher ──────────────────────────────────────────────────────

    private boolean onButtonTouch(View v, MotionEvent event) {
        if (dragMode) {
            return handleDrag(event);
        }
        return handleGesture(event);
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
        int mid = size.x / 2;
        boolean goRight = (params.x + buttonView.getWidth() / 2) >= mid;
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
            float v = (float) a.getAnimatedValue();
            buttonView.setScaleX(v);
            buttonView.setScaleY(v);
        });
        anim.start();
    }

    // ── Gesture state machine ─────────────────────────────────────────────────

    private boolean handleGesture(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                onGestureDown();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                onGestureUp();
                return true;
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
            // Second tap arrived while waiting for double-tap resolution
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

    private void onGestureUp() {
        if (gestureState == GESTURE_FIRST_DOWN) {
            cancelLongPress();
            if (!longPressFired) {
                gestureState = GESTURE_FIRST_WAITING;
                resolutionRunnable = () -> {
                    gestureState = GESTURE_IDLE;
                    executeGestureAction(SettingsManager.KEY_GESTURE_SINGLE);
                };
                gestureHandler.postDelayed(resolutionRunnable,
                        ViewConfiguration.getDoubleTapTimeout());
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
        String action = settings.getGestureAction(gestureKey);
        switch (action) {
            case SettingsManager.ACTION_SPEAK_ON_TOUCH:
                LocalBroadcastManager.getInstance(this).sendBroadcast(
                        new Intent(AccessibilitrollerService.ACTION_TOGGLE_SPEAK));
                break;
            case SettingsManager.ACTION_ENABLE_DRAG:
                enterDragMode();
                break;
            case SettingsManager.ACTION_NONE:
            default:
                break;
        }
    }
}
