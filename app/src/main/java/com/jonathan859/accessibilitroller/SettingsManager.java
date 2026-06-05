package com.jonathan859.accessibilitroller;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

public class SettingsManager {
    private static SettingsManager instance;
    private final SharedPreferences prefs;

    public static final String KEY_BUTTON_STYLE        = "button_style";
    public static final String KEY_GESTURE_SINGLE      = "gesture_single";
    public static final String KEY_GESTURE_DOUBLE      = "gesture_double";
    public static final String KEY_GESTURE_HOLD        = "gesture_hold";
    public static final String KEY_GESTURE_DOUBLE_HOLD = "gesture_double_hold";
    public static final String KEY_BUTTON_POS_X         = "button_pos_x";
    public static final String KEY_BUTTON_POS_Y         = "button_pos_y";
    public static final String KEY_TTS_ENGINE            = "tts_engine";
    public static final String KEY_BUTTON_COLOR         = "button_color";
    public static final String KEY_SPEAK_AUTO_DISABLE   = "speak_auto_disable";
    public static final String KEY_HIGHLIGHT_ENABLED     = "highlight_enabled";
    public static final String KEY_READ_BACKGROUNDS      = "read_backgrounds";

    public static final int DEFAULT_BUTTON_COLOR = 0xFF6200EE;

    public static final String ACTION_SPEAK_ON_TOUCH = "speak_on_touch";
    public static final String ACTION_ENABLE_DRAG    = "enable_drag";
    public static final String ACTION_NONE           = "none";

    public static final String STYLE_CIRCLE     = "circle";
    public static final String STYLE_SEMICIRCLE = "semicircle";

    private SettingsManager(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences("accessibilitroller_prefs", Context.MODE_PRIVATE);
    }

    public static synchronized SettingsManager getInstance(Context context) {
        if (instance == null) {
            instance = new SettingsManager(context);
        }
        return instance;
    }

    public String getButtonStyle() {
        return prefs.getString(KEY_BUTTON_STYLE, STYLE_CIRCLE);
    }

    public void setButtonStyle(String style) {
        prefs.edit().putString(KEY_BUTTON_STYLE, style).apply();
    }

    public String getGestureAction(String gestureKey) {
        String defaultValue;
        switch (gestureKey) {
            case KEY_GESTURE_SINGLE:      defaultValue = ACTION_SPEAK_ON_TOUCH; break;
            case KEY_GESTURE_DOUBLE_HOLD: defaultValue = ACTION_ENABLE_DRAG; break;
            default:                      defaultValue = ACTION_NONE; break;
        }
        return prefs.getString(gestureKey, defaultValue);
    }

    public void setGestureAction(String gestureKey, String action) {
        prefs.edit().putString(gestureKey, action).apply();
    }

    public float getButtonPosX() {
        return prefs.getFloat(KEY_BUTTON_POS_X, 0.9f);
    }

    public float getButtonPosY() {
        return prefs.getFloat(KEY_BUTTON_POS_Y, 0.5f);
    }

    public void setButtonPos(float normX, float normY) {
        prefs.edit()
             .putFloat(KEY_BUTTON_POS_X, normX)
             .putFloat(KEY_BUTTON_POS_Y, normY)
             .apply();
    }

    public String getTtsEngine() {
        return prefs.getString(KEY_TTS_ENGINE, "");
    }

    public void setTtsEngine(String enginePackage) {
        prefs.edit().putString(KEY_TTS_ENGINE, enginePackage).apply();
    }

    public int getButtonColor() {
        return prefs.getInt(KEY_BUTTON_COLOR, DEFAULT_BUTTON_COLOR);
    }

    public void setButtonColor(int color) {
        prefs.edit().putInt(KEY_BUTTON_COLOR, color).apply();
    }

    public boolean getSpeakAutoDisable() {
        return prefs.getBoolean(KEY_SPEAK_AUTO_DISABLE, false);
    }

    public void setSpeakAutoDisable(boolean value) {
        prefs.edit().putBoolean(KEY_SPEAK_AUTO_DISABLE, value).apply();
    }

    public boolean getHighlightEnabled() {
        return prefs.getBoolean(KEY_HIGHLIGHT_ENABLED, false);
    }

    public void setHighlightEnabled(boolean value) {
        prefs.edit().putBoolean(KEY_HIGHLIGHT_ENABLED, value).apply();
    }

    public boolean getReadBackgrounds() {
        return prefs.getBoolean(KEY_READ_BACKGROUNDS, false);
    }

    public void setReadBackgrounds(boolean value) {
        prefs.edit().putBoolean(KEY_READ_BACKGROUNDS, value).apply();
    }

    public void registerListener(OnSharedPreferenceChangeListener listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterListener(OnSharedPreferenceChangeListener listener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
    }
}
