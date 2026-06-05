package com.example.accessibilitroller;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private SettingsManager settings;

    private ImageView ivOverlayStatus;
    private ImageView ivAccessibilityStatus;
    private ImageView ivNotificationStatus;
    private View btnOverlayGrant;
    private View btnAccessibilityGrant;
    private View btnNotificationGrant;

    private CardView cardCircle;
    private CardView cardSemiCircle;

    private Spinner spinnerSingle;
    private Spinner spinnerDouble;
    private Spinner spinnerHold;
    private Spinner spinnerDoubleHold;

    private Spinner spinnerTtsVoice;
    private TextToSpeech mainTts;
    private String[] ttsVoiceKeys;

    private SwitchMaterial switchService;

    private ActivityResultLauncher<String> notificationPermLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = SettingsManager.getInstance(this);

        notificationPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> refreshPermissionCards());

        initPermissionSection();
        initButtonStyleSection();
        initGestureSection();
        initTtsSection();
        initServiceToggle();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionCards();
        refreshServiceToggle();
    }

    @Override
    protected void onDestroy() {
        if (mainTts != null) {
            mainTts.stop();
            mainTts.shutdown();
            mainTts = null;
        }
        super.onDestroy();
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    private void initPermissionSection() {
        ivOverlayStatus = findViewById(R.id.iv_overlay_status);
        ivAccessibilityStatus = findViewById(R.id.iv_accessibility_status);
        ivNotificationStatus = findViewById(R.id.iv_notification_status);

        btnOverlayGrant = findViewById(R.id.btn_overlay_grant);
        btnAccessibilityGrant = findViewById(R.id.btn_accessibility_grant);
        btnNotificationGrant = findViewById(R.id.btn_notification_grant);

        btnOverlayGrant.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });

        btnAccessibilityGrant.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        btnNotificationGrant.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        });

        refreshPermissionCards();
    }

    private void refreshPermissionCards() {
        updatePermCard(ivOverlayStatus, btnOverlayGrant,
                PermissionHelper.hasOverlayPermission(this));
        updatePermCard(ivAccessibilityStatus, btnAccessibilityGrant,
                PermissionHelper.isAccessibilityServiceEnabled(this));
        updatePermCard(ivNotificationStatus, btnNotificationGrant,
                PermissionHelper.hasNotificationPermission(this));
    }

    private void updatePermCard(ImageView icon, View grantBtn, boolean granted) {
        if (granted) {
            icon.setImageResource(android.R.drawable.ic_menu_myplaces);
            grantBtn.setVisibility(View.GONE);
        } else {
            icon.setImageResource(android.R.drawable.ic_dialog_alert);
            grantBtn.setVisibility(View.VISIBLE);
        }
    }

    // ── Button style ──────────────────────────────────────────────────────────

    private void initButtonStyleSection() {
        cardCircle = findViewById(R.id.card_circle);
        cardSemiCircle = findViewById(R.id.card_semicircle);

        cardCircle.setOnClickListener(v -> selectButtonStyle(SettingsManager.STYLE_CIRCLE));
        cardSemiCircle.setOnClickListener(v -> selectButtonStyle(SettingsManager.STYLE_SEMICIRCLE));

        highlightStyleCard(settings.getButtonStyle());
    }

    private void selectButtonStyle(String style) {
        settings.setButtonStyle(style);
        highlightStyleCard(style);
    }

    private void highlightStyleCard(String style) {
        float selected = getResources().getDimension(R.dimen.card_selected_elevation);
        float normal = getResources().getDimension(R.dimen.card_normal_elevation);
        if (SettingsManager.STYLE_CIRCLE.equals(style)) {
            cardCircle.setCardElevation(selected);
            cardSemiCircle.setCardElevation(normal);
        } else {
            cardCircle.setCardElevation(normal);
            cardSemiCircle.setCardElevation(selected);
        }
    }

    // ── Gesture actions ───────────────────────────────────────────────────────

    private void initGestureSection() {
        spinnerSingle = findViewById(R.id.spinner_single);
        spinnerDouble = findViewById(R.id.spinner_double);
        spinnerHold = findViewById(R.id.spinner_hold);
        spinnerDoubleHold = findViewById(R.id.spinner_double_hold);

        String[] standardKeys = {
            SettingsManager.ACTION_SPEAK_ON_TOUCH,
            SettingsManager.ACTION_NONE
        };
        String[] standardLabels = {
            getString(R.string.action_speak_on_touch),
            getString(R.string.action_none)
        };
        String[] allKeys = {
            SettingsManager.ACTION_SPEAK_ON_TOUCH,
            SettingsManager.ACTION_ENABLE_DRAG,
            SettingsManager.ACTION_NONE
        };
        String[] allLabels = {
            getString(R.string.action_speak_on_touch),
            getString(R.string.action_enable_drag),
            getString(R.string.action_none)
        };

        setupSpinner(spinnerSingle,     standardLabels, standardKeys, SettingsManager.KEY_GESTURE_SINGLE);
        setupSpinner(spinnerDouble,     standardLabels, standardKeys, SettingsManager.KEY_GESTURE_DOUBLE);
        setupSpinner(spinnerHold,       standardLabels, standardKeys, SettingsManager.KEY_GESTURE_HOLD);
        setupSpinner(spinnerDoubleHold, allLabels,      allKeys,      SettingsManager.KEY_GESTURE_DOUBLE_HOLD);
    }

    private void setupSpinner(Spinner spinner, String[] labels, String[] keys, String gestureKey) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        String current = settings.getGestureAction(gestureKey);
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(current)) {
                spinner.setSelection(i);
                break;
            }
        }

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean initializing = true;
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (initializing) { initializing = false; return; }
                settings.setGestureAction(gestureKey, keys[position]);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // ── TTS voice ─────────────────────────────────────────────────────────────

    private void initTtsSection() {
        spinnerTtsVoice = findViewById(R.id.spinner_tts_voice);

        // Show placeholder until TTS initialises
        ArrayAdapter<String> placeholder = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{getString(R.string.tts_voice_default)});
        placeholder.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTtsVoice.setAdapter(placeholder);

        mainTts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                populateTtsVoices();
            }
        });
    }

    private void populateTtsVoices() {
        Set<Voice> voices = mainTts.getVoices();
        if (voices == null || voices.isEmpty()) return;

        String lang = Locale.getDefault().getLanguage();
        List<Voice> filtered = new ArrayList<>();
        for (Voice v : voices) {
            if (!v.isNetworkConnectionRequired()
                    && v.getLocale().getLanguage().equals(lang)) {
                filtered.add(v);
            }
        }
        if (filtered.isEmpty()) {
            for (Voice v : voices) {
                if (!v.isNetworkConnectionRequired()) filtered.add(v);
            }
        }
        filtered.sort((a, b) -> a.getName().compareTo(b.getName()));

        ttsVoiceKeys = new String[filtered.size() + 1];
        String[] labels = new String[filtered.size() + 1];
        ttsVoiceKeys[0] = "";
        labels[0] = getString(R.string.tts_voice_default);
        for (int i = 0; i < filtered.size(); i++) {
            ttsVoiceKeys[i + 1] = filtered.get(i).getName();
            labels[i + 1] = filtered.get(i).getName();
        }

        String savedKey = settings.getTtsVoice();
        int savedPos = 0;
        for (int i = 0; i < ttsVoiceKeys.length; i++) {
            if (ttsVoiceKeys[i].equals(savedKey)) { savedPos = i; break; }
        }
        final int selectionPos = savedPos;

        runOnUiThread(() -> {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerTtsVoice.setAdapter(adapter);
            spinnerTtsVoice.setSelection(selectionPos);
            spinnerTtsVoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                boolean initializing = true;
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (initializing) { initializing = false; return; }
                    settings.setTtsVoice(ttsVoiceKeys[position]);
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        });
    }

    // ── Service toggle ────────────────────────────────────────────────────────

    private void initServiceToggle() {
        if (switchService == null) {
            switchService = findViewById(R.id.switch_service);
        }
        switchService.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                if (!PermissionHelper.allPermissionsGranted(this)) {
                    btn.setChecked(false);
                    StringBuilder missing = new StringBuilder();
                    if (!PermissionHelper.hasOverlayPermission(this))
                        missing.append(getString(R.string.permission_overlay_title)).append(" ");
                    if (!PermissionHelper.isAccessibilityServiceEnabled(this))
                        missing.append(getString(R.string.permission_accessibility_title)).append(" ");
                    if (!PermissionHelper.hasNotificationPermission(this))
                        missing.append(getString(R.string.permission_notifications_title));
                    Snackbar.make(btn,
                            getString(R.string.missing_permissions) + missing.toString().trim(),
                            Snackbar.LENGTH_LONG).show();
                } else {
                    Intent intent = new Intent(this, FloatingButtonService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent);
                    } else {
                        startService(intent);
                    }
                }
            } else {
                stopService(new Intent(this, FloatingButtonService.class));
            }
        });
    }

    private void refreshServiceToggle() {
        if (switchService == null) return;
        switchService.setOnCheckedChangeListener(null);
        switchService.setChecked(FloatingButtonService.isRunning());
        initServiceToggle();
    }
}
