package xyz.etkn.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.provider.Settings;
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
        android.accessibilityservice.AccessibilityServiceInfo service = manager.getInstalledAccessibilityServiceList().stream()
            .filter(info -> BootStartupService.class.getName().equals(info.getResolveInfo().serviceInfo.name)
                && context.getPackageName().equals(info.getResolveInfo().serviceInfo.packageName))
            .findFirst().orElseThrow(() -> new AssertionError("Missing boot binding service"));
        assertEquals(0, service.eventTypes);
        assertEquals(0, service.getCapabilities() & android.accessibilityservice.AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT);
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
        context.getSharedPreferences("startup", Context.MODE_PRIVATE).edit().clear().commit();
        try {
            receiver.onReceive(capture, new Intent(Intent.ACTION_BOOT_COMPLETED));
            assertNull(launched[0]);
            StartupReceiver.setEnabled(context, true);
            receiver.onReceive(capture, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
            assertNull(launched[0]);
            receiver.onReceive(capture, new Intent(Intent.ACTION_BOOT_COMPLETED));
            assertNotNull(launched[0]);
            assertEquals(new ComponentName(context, SetupActivity.class), launched[0].getComponent());
            assertEquals(Intent.ACTION_MAIN, launched[0].getAction());
            assertTrue(launched[0].hasCategory(Intent.CATEGORY_LAUNCHER));
            assertEquals(0, launched[0].getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TASK);
            receiver.onReceive(capture, new Intent(Intent.ACTION_BOOT_COMPLETED));
            receiver.onReceive(capture, new Intent(StartupReceiver.FAST_BOOT));
        } finally {
            StartupReceiver.setEnabled(context, false);
            context.getSharedPreferences("startup", Context.MODE_PRIVATE).edit().clear().commit();
        }
    }

    @Test public void newBootAndFastWakeCanLaunchAgain() {
        int[] launches = {0};
        Context capture = new ContextWrapper(context) {
            @Override public void startActivity(Intent intent) { launches[0]++; }
        };
        android.content.SharedPreferences state = context.getSharedPreferences("startup", Context.MODE_PRIVATE);
        int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        state.edit().clear().putInt("last_boot", boot - 1).putLong("last_launch_elapsed", SystemClock.elapsedRealtime()).commit();
        try {
            StartupReceiver.setEnabled(context, true);
            StartupReceiver receiver = new StartupReceiver();
            receiver.onReceive(capture, new Intent(Intent.ACTION_BOOT_COMPLETED));
            assertEquals(1, launches[0]);
            state.edit().putLong("last_launch_elapsed", -60000).commit();
            receiver.onReceive(capture, new Intent(StartupReceiver.FAST_BOOT));
            receiver.onReceive(capture, new Intent(StartupReceiver.FAST_BOOT));
            receiver.onReceive(capture, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
            assertEquals(2, launches[0]);
        } finally {
            StartupReceiver.setEnabled(context, false);
            state.edit().clear().commit();
        }
    }
}
