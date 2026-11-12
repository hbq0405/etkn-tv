package xyz.etkn.tv;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

public final class StartupReceiver extends BroadcastReceiver {
    static final String FAST_BOOT = "fengos.intent.action.ACTION_FAST_BOOT";
    static boolean enabled(Context context) {
        return context.getPackageManager().getComponentEnabledSetting(new ComponentName(context, StartupReceiver.class))
            == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getPackageManager().setComponentEnabledSetting(new ComponentName(context, StartupReceiver.class),
            enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP);
    }

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if ((!Intent.ACTION_BOOT_COMPLETED.equals(action) && !FAST_BOOT.equals(action)) || !enabled(context)) return;
        SharedPreferences state = context.getSharedPreferences("startup", Context.MODE_PRIVATE);
        int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        int previousBoot = state.getInt("last_boot", -1);
        long now = SystemClock.elapsedRealtime(), previous = state.getLong("last_launch_elapsed", -60000);
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) && boot >= 0 && previousBoot == boot) return;
        if (previousBoot == boot && now >= previous && now - previous < 30000) return;
        context.startActivity(new Intent(context, SetupActivity.class)
            .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        state.edit().putInt("last_boot", boot).putLong("last_launch_elapsed", now).apply();
        Log.i("EtknStartup", "Auto-start from " + action);
    }
}
