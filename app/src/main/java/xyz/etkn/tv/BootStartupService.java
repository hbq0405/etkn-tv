package xyz.etkn.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.content.ContextCompat;

/** FengOS does not start third-party processes for broadcasts. System binding keeps the boot receiver reachable. */
public final class BootStartupService extends AccessibilityService {
    private StartupReceiver receiver;

    @Override protected void onServiceConnected() {
        if (receiver != null) return;
        receiver = new StartupReceiver();
        IntentFilter filter = new IntentFilter(Intent.ACTION_BOOT_COMPLETED);
        filter.addAction(StartupReceiver.FAST_BOOT);
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}

    @Override public void onDestroy() {
        if (receiver != null) unregisterReceiver(receiver);
        super.onDestroy();
    }
}
