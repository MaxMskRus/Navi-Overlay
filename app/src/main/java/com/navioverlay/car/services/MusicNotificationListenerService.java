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
    private static volatile long latestUpdatedAt = 0L;
    private static final long STALE_MS = 30_000L;
    private static final long FALLBACK_STALE_MS = 90_000L;

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
            latestUpdatedAt = System.currentTimeMillis();
        } catch (SecurityException se) {
            Logx.e("NotificationListener access unavailable", se);
        } catch (RuntimeException re) {
            Logx.e("getActiveNotifications failed", re);
        }
    }

    @Override public void onListenerDisconnected() {
        super.onListenerDisconnected();
        latest = new StatusBarNotification[0];
        latestUpdatedAt = 0L;
    }

    public static StatusBarNotification[] latestNotifications(){ return latest; }
    public static int latestNotificationsCount() {
        StatusBarNotification[] snapshot = latest;
        return snapshot == null ? 0 : snapshot.length;
    }
    public static long latestNotificationsAgeMs() {
        long ts = latestUpdatedAt;
        if (ts <= 0L) return Long.MAX_VALUE;
        return Math.max(0L, System.currentTimeMillis() - ts);
    }
    public static long staleThresholdMs() { return STALE_MS; }
    public static long fallbackStaleThresholdMs() { return FALLBACK_STALE_MS; }
    public static boolean hasFreshNotifications() {
        return latestUpdatedAt > 0L && latestNotificationsAgeMs() <= STALE_MS;
    }
    public static boolean hasUsableNotifications() {
        return latestUpdatedAt > 0L
                && latestNotificationsCount() > 0
                && latestNotificationsAgeMs() <= FALLBACK_STALE_MS;
    }
}
