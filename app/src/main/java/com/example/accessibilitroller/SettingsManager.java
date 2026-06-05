package com.example.accessibilitroller;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {
    private static SettingsManager instance;
    private final SharedPreferences prefs;

    public static final String KEY_BUTTON_STYLE        = "button_style";
    public static final String KEY_GESTURE_SINGLE      = "gesture_single";
    public static final String KEY_GESTURE_DOUBLE      = "gesture_double";
    public static final String KEY_GESTURE_HOLD        = "gesture_hold";
    public static final String KEY_GESTURE_DOUBLE_HOLD = "gesture_double_hold";
    public static final String KEY_BUTTON_POS_X        = "button_pos_x";
    public static final String KEY_BUTTON_POS_Y        = "button_pos_y";
    public static final String KEY_TTS_VOICE           = "tts_voice";

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

    public String getTtsVoice() {
        return prefs.getString(KEY_TTS_VOICE, "");
    }

    public void setTtsVoice(String voiceName) {
        prefs.edit().putString(KEY_TTS_VOICE, voiceName).apply();
    }
}
