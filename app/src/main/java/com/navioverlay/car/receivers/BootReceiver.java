package com.navioverlay.car.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.navioverlay.car.core.Prefs;
import com.navioverlay.car.services.MonitorService;

public class BootReceiver extends BroadcastReceiver {
    private static final long[] START_DELAYS_MS = new long[]{900L, 8000L, 20000L};

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        boolean allowed = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || Intent.ACTION_USER_UNLOCKED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action);
        if (!allowed) return;
        if (!new Prefs(context).enabled()) return;
        final Context app = context.getApplicationContext();
        scheduleRecoveryStarts(app);
    }

    private void scheduleRecoveryStarts(Context app) {
        try {
            AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
            if (am == null) {
                MonitorService.start(app);
                return;
            }
            for (int i = 0; i < START_DELAYS_MS.length; i++) {
                long at = System.currentTimeMillis() + START_DELAYS_MS[i];
                PendingIntent pi = startPendingIntent(app, i);
                if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                else am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (Throwable ignored) {
            MonitorService.start(app);
        }
    }

    private PendingIntent startPendingIntent(Context app, int requestCode) {
        Intent serviceIntent = new Intent(app, MonitorService.class);
        serviceIntent.setAction(MonitorService.ACTION_START);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        if (Build.VERSION.SDK_INT >= 26) {
            return PendingIntent.getForegroundService(app, 5100 + requestCode, serviceIntent, flags);
        }
        return PendingIntent.getService(app, 5100 + requestCode, serviceIntent, flags);
    }
}
