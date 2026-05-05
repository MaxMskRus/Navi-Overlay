package com.navioverlay.car.services;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import com.navioverlay.car.core.Logx;

/**
 * Нужен не как основной триггер, а как разрешённый системный доступ к MediaSession
 * и как fallback-хранилище активных музыкальных уведомлений.
 */
public class MusicNotificationListenerService extends NotificationListenerService {
    private static volatile StatusBarNotification[] latest = new StatusBarNotification[0];

    @Override public void onListenerConnected(){
        super.onListenerConnected();
        Logx.d("NotificationListener connected");
        refreshLatest();
        MonitorService.poke(this);
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn){ refreshLatest(); MonitorService.poke(this); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn){ refreshLatest(); MonitorService.poke(this); }

    private void refreshLatest(){
        try {
            latest = getActiveNotifications();
        } catch (SecurityException se) {
            Logx.e("NotificationListener access unavailable", se);
        } catch (RuntimeException re) {
            Logx.e("getActiveNotifications failed", re);
        }
    }

    public static StatusBarNotification[] latestNotifications(){ return latest; }
}
