package xyz.etkn.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.accessibility.AccessibilityManager;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public class StartupReceiverTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void bootIsOptInAndHomeHasNoTakeoverService() throws Exception {
        ComponentName receiver = new ComponentName(context, StartupReceiver.class);
        assertFalse(context.getPackageManager().getReceiverInfo(receiver, PackageManager.MATCH_DISABLED_COMPONENTS).enabled);
        assertFalse(StartupReceiver.enabled(context));
        try {
            StartupReceiver.setEnabled(context, true);
            assertTrue(StartupReceiver.enabled(context));
            assertTrue(context.getPackageManager().queryBroadcastReceivers(new Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(context.getPackageName()), 0)
                .stream().anyMatch(info -> StartupReceiver.class.getName().equals(info.activityInfo.name)));
        } finally { StartupReceiver.setEnabled(context, false); }
        assertFalse(StartupReceiver.enabled(context));
        assertTrue(context.getPackageManager().queryBroadcastReceivers(new Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(context.getPackageName()), 0).isEmpty());
        AccessibilityManager manager = context.getSystemService(AccessibilityManager.class);
        assertFalse(manager.getInstalledAccessibilityServiceList().stream()
            .anyMatch(info -> context.getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)));
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        assertFalse(context.getPackageManager().queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY).stream()
            .anyMatch(info -> context.getPackageName().equals(info.activityInfo.packageName)));
    }

    @Test public void onlyBootLaunchesNormalEntryWithoutClearingTask() {
        Intent[] launched = {null};
        Context capture = new ContextWrapper(context) {
            @Override public void startActivity(Intent intent) { assertNull(launched[0]); launched[0] = intent; }
        };
        StartupReceiver receiver = new StartupReceiver();
        receiver.onReceive(capture, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
        assertNull(launched[0]);
        receiver.onReceive(capture, new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertNotNull(launched[0]);
        assertEquals(new ComponentName(context, SetupActivity.class), launched[0].getComponent());
        assertEquals(Intent.ACTION_MAIN, launched[0].getAction());
        assertTrue(launched[0].hasCategory(Intent.CATEGORY_LAUNCHER));
        assertEquals(0, launched[0].getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }
}
