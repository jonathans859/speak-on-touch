package com.jonathan859.speakontouch;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

public class SettingsManager {
    private static SettingsManager instance;
    private final SharedPreferences prefs;

    public static final String KEY_TTS_ENGINE          = "tts_engine";
    public static final String KEY_SPEAK_AUTO_DISABLE  = "speak_auto_disable";
    public static final String KEY_HIGHLIGHT_ENABLED   = "highlight_enabled";
    public static final String KEY_READ_BACKGROUNDS    = "read_backgrounds";
    public static final String KEY_FOCUS_WHOLE_ITEM    = "focus_whole_item";

    private SettingsManager(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences("speakontouch_prefs", Context.MODE_PRIVATE);
    }

    public static synchronized SettingsManager getInstance(Context context) {
        if (instance == null) {
            instance = new SettingsManager(context);
        }
        return instance;
    }

    public String getTtsEngine() {
        return prefs.getString(KEY_TTS_ENGINE, "");
    }

    public void setTtsEngine(String enginePackage) {
        prefs.edit().putString(KEY_TTS_ENGINE, enginePackage).apply();
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

    public boolean getFocusWholeItem() {
        return prefs.getBoolean(KEY_FOCUS_WHOLE_ITEM, false);
    }

    public void setFocusWholeItem(boolean value) {
        prefs.edit().putBoolean(KEY_FOCUS_WHOLE_ITEM, value).apply();
    }

    public void registerListener(OnSharedPreferenceChangeListener listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterListener(OnSharedPreferenceChangeListener listener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
    }
}
