package com.jonathan859.speakontouch;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

public class PermissionHelper {

    /**
     * Reads Settings.Secure directly — AccessibilityManager.getEnabledAccessibilityServiceList()
     * is unreliable on many ROMs.
     */
    public static boolean isAccessibilityServiceEnabled(Context context) {
        String flat = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (flat == null || flat.isEmpty()) return false;
        ComponentName target = new ComponentName(
                context.getPackageName(), SpeakOnTouchService.class.getName());
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(flat);
        while (splitter.hasNext()) {
            ComponentName cn = ComponentName.unflattenFromString(splitter.next());
            if (cn != null && cn.equals(target)) return true;
        }
        return false;
    }
}
