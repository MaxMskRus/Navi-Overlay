package com.navioverlay.car.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.navioverlay.car.core.Prefs;
import com.navioverlay.car.services.MonitorService;

/** Внутренний ресивер мягкого восстановления сервиса. exported=false в manifest. */
public class RestartReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!new Prefs(context).enabled()) return;
        final Context app = context.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(() -> MonitorService.start(app), 1200);
    }
}
