package xyz.etkn.tv;

import android.view.accessibility.AccessibilityEvent;
import org.junit.Test;
import static org.junit.Assert.*;

public class LauncherTakeoverTest {
    @Test public void onlyOriginalLauncherHomeWindowTriggersTakeover() {
        int window = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        String launcher = "com.tvfengmi.launcher", home = "com.gala.video.app.epg.HomeActivity";
        assertTrue(LauncherTakeoverService.isLauncherWindow(window, launcher, home));
        assertFalse(LauncherTakeoverService.isLauncherWindow(AccessibilityEvent.TYPE_VIEW_FOCUSED, launcher, home));
        assertFalse(LauncherTakeoverService.isLauncherWindow(window, launcher, "com.gala.video.app.settings.SettingsActivity"));
        assertFalse(LauncherTakeoverService.isLauncherWindow(window, "xyz.etkn.tv", home));
        assertFalse(LauncherTakeoverService.isLauncherWindow(window, null, null));
    }
}
