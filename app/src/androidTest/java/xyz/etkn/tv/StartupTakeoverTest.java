package xyz.etkn.tv;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public class StartupTakeoverTest {
    @Test public void takeoverIsOptInAndDoesNotReadWindowContents() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertFalse("测试包尚未启用接管", LauncherTakeoverService.isEnabled(context));
        new StartupReceiver().onReceive(new ContextWrapper(context) {
            @Override public void startActivity(Intent intent) { fail("未启用时开机不能启动应用"); }
        }, new Intent(Intent.ACTION_BOOT_COMPLETED));
        AccessibilityManager manager = context.getSystemService(AccessibilityManager.class);
        ComponentName component = new ComponentName(context, LauncherTakeoverService.class);
        AccessibilityServiceInfo service = manager.getInstalledAccessibilityServiceList().stream()
            .filter(info -> component.equals(new ComponentName(info.getResolveInfo().serviceInfo.packageName, info.getResolveInfo().serviceInfo.name)))
            .findFirst().orElseThrow(() -> new AssertionError("缺少接管服务声明"));
        assertEquals(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, service.eventTypes);
        assertArrayEquals(new String[]{"com.tvfengmi.launcher"}, service.packageNames);
        assertEquals(0, service.getCapabilities() & AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT);
    }
}
