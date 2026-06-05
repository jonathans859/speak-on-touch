package com.example.accessibilitroller;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import androidx.core.content.ContextCompat;

public class PermissionHelper {

    public static boolean hasOverlayPermission(Context context) {
        return Settings.canDrawOverlays(context);
    }

    public static boolean isAccessibilityServiceEnabled(Context context) {
        String flat = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (flat == null || flat.isEmpty()) return false;
        ComponentName target = new ComponentName(
                context.getPackageName(), AccessibilitrollerService.class.getName());
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(flat);
        while (splitter.hasNext()) {
            ComponentName cn = ComponentName.unflattenFromString(splitter.next());
            if (cn != null && cn.equals(target)) return true;
        }
        return false;
    }

    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean allPermissionsGranted(Context context) {
        return hasOverlayPermission(context)
                && isAccessibilityServiceEnabled(context)
                && hasNotificationPermission(context);
    }
}
