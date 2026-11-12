package com.jonathan859.speakontouch;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private SettingsManager settings;

    private ImageView ivAccessibilityStatus;
    private TextView tvAccessibilityStatus;

    private Spinner spinnerTtsVoice;
    private String[] ttsVoiceKeys;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = SettingsManager.getInstance(this);

        initServiceSection();
        initSpeakSection();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshServiceStatus();
    }

    // ── Accessibility service status ─────────────────────────────────────────

    private void initServiceSection() {
        ivAccessibilityStatus = findViewById(R.id.iv_accessibility_status);
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status);
        findViewById(R.id.btn_accessibility_settings).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        refreshServiceStatus();
    }

    private void refreshServiceStatus() {
        boolean on = PermissionHelper.isAccessibilityServiceEnabled(this);
        ivAccessibilityStatus.setImageResource(on
                ? android.R.drawable.ic_menu_myplaces
                : android.R.drawable.ic_dialog_alert);
        tvAccessibilityStatus.setText(on ? R.string.service_status_on : R.string.service_status_off);
    }

    // ── Speak settings ────────────────────────────────────────────────────────

    private void initSpeakSection() {
        spinnerTtsVoice = findViewById(R.id.spinner_tts_voice);

        bindSwitch(R.id.switch_focus_whole_item, settings.getFocusWholeItem(),
                settings::setFocusWholeItem);
        bindSwitch(R.id.switch_speak_auto_disable, settings.getSpeakAutoDisable(),
                settings::setSpeakAutoDisable);
        bindSwitch(R.id.switch_highlight_enabled, settings.getHighlightEnabled(),
                settings::setHighlightEnabled);
        bindSwitch(R.id.switch_read_backgrounds, settings.getReadBackgrounds(),
                settings::setReadBackgrounds);

        populateTtsEngines();
    }

    private interface BoolSetter { void set(boolean value); }

    private void bindSwitch(int id, boolean initial, BoolSetter setter) {
        SwitchMaterial sw = findViewById(id);
        sw.setChecked(initial);
        sw.setOnCheckedChangeListener((btn, checked) -> setter.set(checked));
    }

    private void populateTtsEngines() {
        PackageManager pm = getPackageManager();
        Intent ttsIntent = new Intent("android.intent.action.TTS_SERVICE");
        List<ResolveInfo> services = pm.queryIntentServices(ttsIntent, 0);
        services.sort((a, b) ->
                a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));

        ttsVoiceKeys = new String[services.size() + 1];
        String[] labels = new String[services.size() + 1];
        ttsVoiceKeys[0] = "";
        labels[0] = getString(R.string.tts_voice_default);
        for (int i = 0; i < services.size(); i++) {
            ttsVoiceKeys[i + 1] = services.get(i).serviceInfo.packageName;
            labels[i + 1] = services.get(i).loadLabel(pm).toString();
        }

        String savedKey = settings.getTtsEngine();
        int savedPos = 0;
        for (int i = 0; i < ttsVoiceKeys.length; i++) {
            if (ttsVoiceKeys[i].equals(savedKey)) { savedPos = i; break; }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTtsVoice.setAdapter(adapter);
        spinnerTtsVoice.setSelection(savedPos);
        spinnerTtsVoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean initializing = true;
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (initializing) { initializing = false; return; }
                settings.setTtsEngine(ttsVoiceKeys[position]);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
}
