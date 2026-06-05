package com.jonathan859.accessibilitroller;

import android.Manifest;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private SettingsManager settings;

    private ImageView ivOverlayStatus;
    private ImageView ivAccessibilityStatus;
    private ImageView ivNotificationStatus;
    private View btnOverlayGrant;
    private View btnAccessibilityGrant;
    private View btnNotificationGrant;

    private MaterialCardView cardCircle;
    private MaterialCardView cardSemiCircle;

    private Spinner spinnerSingle;
    private Spinner spinnerDouble;
    private Spinner spinnerHold;
    private Spinner spinnerDoubleHold;

    private Spinner spinnerTtsVoice;
    private SwitchMaterial switchSpeakAutoDisable;
    private SwitchMaterial switchHighlight;
    private SwitchMaterial switchReadBackgrounds;
    private String[] ttsVoiceKeys;

    private static final int[] BUTTON_COLORS = {
        0xFF6200EE, 0xFF1976D2, 0xFF388E3C,
        0xFFF57C00, 0xFFD32F2F, 0xFF00796B
    };
    private static final int[] COLOR_NAME_RES = {
        R.string.color_purple, R.string.color_blue, R.string.color_green,
        R.string.color_orange, R.string.color_red,  R.string.color_teal
    };
    private View[] colorSwatches;

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
        initColorSection();
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
        applyCardSelection(cardCircle,     SettingsManager.STYLE_CIRCLE.equals(style),
                getString(R.string.style_circle));
        applyCardSelection(cardSemiCircle, SettingsManager.STYLE_SEMICIRCLE.equals(style),
                getString(R.string.style_semicircle));
    }

    private void applyCardSelection(MaterialCardView card, boolean selected, String label) {
        float selElev = getResources().getDimension(R.dimen.card_selected_elevation);
        float normElev = getResources().getDimension(R.dimen.card_normal_elevation);
        card.setCardElevation(selected ? selElev : normElev);
        card.setStrokeWidth(selected ? dpToPx(2) : 0);
        card.setStrokeColor(selected
                ? ContextCompat.getColor(this, R.color.colorPrimary)
                : android.graphics.Color.TRANSPARENT);
        card.setContentDescription(selected
                ? label + ", " + getString(R.string.selected)
                : label);
    }

    // ── Button colour ─────────────────────────────────────────────────────────

    private void initColorSection() {
        LinearLayout row = findViewById(R.id.color_swatch_row);
        row.removeAllViews();
        int sizePx  = dpToPx(36);
        int marginPx = dpToPx(10);
        colorSwatches = new View[BUTTON_COLORS.length];
        for (int i = 0; i < BUTTON_COLORS.length; i++) {
            View swatch = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
            lp.setMargins(0, 0, marginPx, 0);
            swatch.setLayoutParams(lp);
            swatch.setContentDescription(getString(COLOR_NAME_RES[i]));
            swatch.setFocusable(true);
            colorSwatches[i] = swatch;
            final int color = BUTTON_COLORS[i];
            swatch.setOnClickListener(v -> selectColor(color));
            row.addView(swatch);
        }
        refreshColorSwatches();
    }

    private void selectColor(int color) {
        settings.setButtonColor(color);
        refreshColorSwatches();
    }

    private void refreshColorSwatches() {
        int current = settings.getButtonColor();
        for (int i = 0; i < BUTTON_COLORS.length; i++) {
            int c = BUTTON_COLORS[i];
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(c);
            if (c == current) bg.setStroke(dpToPx(3), Color.WHITE);
            colorSwatches[i].setBackground(bg);
        }
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
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
        setupSpinner(spinnerHold,       allLabels,      allKeys,      SettingsManager.KEY_GESTURE_HOLD);
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

    // ── TTS / speak settings ──────────────────────────────────────────────────

    private void initTtsSection() {
        spinnerTtsVoice = findViewById(R.id.spinner_tts_voice);

        switchSpeakAutoDisable = findViewById(R.id.switch_speak_auto_disable);
        switchSpeakAutoDisable.setChecked(settings.getSpeakAutoDisable());
        switchSpeakAutoDisable.setOnCheckedChangeListener((btn, checked) ->
                settings.setSpeakAutoDisable(checked));

        switchHighlight = findViewById(R.id.switch_highlight_enabled);
        switchHighlight.setChecked(settings.getHighlightEnabled());
        switchHighlight.setOnCheckedChangeListener((btn, checked) ->
                settings.setHighlightEnabled(checked));

        switchReadBackgrounds = findViewById(R.id.switch_read_backgrounds);
        switchReadBackgrounds.setChecked(settings.getReadBackgrounds());
        switchReadBackgrounds.setOnCheckedChangeListener((btn, checked) ->
                settings.setReadBackgrounds(checked));

        populateTtsEngines();
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
