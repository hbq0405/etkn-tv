package xyz.etkn.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;

/** 仅在用户启用后，将 FengOS 原厂桌面的窗口切换交给应用正常入口。 */
public final class LauncherTakeoverService extends AccessibilityService {
    private long lastLaunch = -1000;

    static boolean isLauncherWindow(int type, CharSequence packageName, CharSequence className) {
        return type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            && "com.tvfengmi.launcher".contentEquals(packageName == null ? "" : packageName)
            && "com.gala.video.app.epg.HomeActivity".contentEquals(className == null ? "" : className);
    }

    static boolean isEnabled(Context context) {
        if (Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false;
        String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName service = new ComponentName(context, LauncherTakeoverService.class);
        for (String name : enabled.split(":")) if (service.equals(ComponentName.unflattenFromString(name))) return true;
        return false;
    }

    static void openApp(Context context) {
        context.startActivity(new Intent(context, SetupActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || !isLauncherWindow(event.getEventType(), event.getPackageName(), event.getClassName())) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastLaunch < 800) return;
        lastLaunch = now;
        openApp(this);
    }

    @Override public void onInterrupt() {}
}
