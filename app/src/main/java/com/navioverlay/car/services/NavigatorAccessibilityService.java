package com.navioverlay.car.services;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.navioverlay.car.core.Logx;
import com.navioverlay.car.core.Prefs;

/**
 * Определяет именно видимое приложение.
 *
 * На Samsung/ГУ после шторки, системных окон, камеры заднего вида или другого transient-window
 * событие возврата в навигатор иногда не приходит. Поэтому сервис дополнительно опрашивает
 * rootInActiveWindow, но делает это аккуратно: когда приложение выключено в настройках — почти спит,
 * а в лог пишет только реальные изменения пакета.
 */
public class NavigatorAccessibilityService extends AccessibilityService {
    private static final long POLL_ENABLED_MS = 900L;
    private static final long POLL_DISABLED_MS = 3000L;
    private static volatile boolean connected = false;
    private static volatile long connectedAt = 0L;
    private static volatile long lastEventAt = 0L;
    private static volatile long lastRootReadAt = 0L;

    private final Handler h = new Handler(Looper.getMainLooper());
    private String lastPackage = "";

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            long next = POLL_ENABLED_MS;
            try {
                Prefs prefs = new Prefs(NavigatorAccessibilityService.this);
                if (prefs.enabled()) {
                    readRootPackage(prefs);
                    next = POLL_ENABLED_MS;
                } else {
                    next = POLL_DISABLED_MS;
                }
            } catch (Throwable ignored) {
                next = POLL_DISABLED_MS;
            }
            h.postDelayed(this, next);
        }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
        connectedAt = System.currentTimeMillis();
        h.removeCallbacksAndMessages(null);
        h.post(poll);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            lastEventAt = System.currentTimeMillis();
            Prefs prefs = new Prefs(this);
            setPackage(event.getPackageName().toString(), prefs);
        }
    }

    private void readRootPackage(Prefs prefs) {
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root != null && root.getPackageName() != null) {
                lastRootReadAt = System.currentTimeMillis();
                setPackage(root.getPackageName().toString(), prefs);
            }
        } catch (Throwable ignored) {
        } finally {
            try { if (root != null) root.recycle(); } catch (Throwable ignored) {}
        }
    }

    private void setPackage(String pkg, Prefs prefs) {
        String safe = pkg == null ? "" : pkg;
        ForegroundState.set(safe, prefs.triggers());
        if (prefs.isTrigger(safe)) {
            prefs.setLastTrigger(safe);
        }
        if (!safe.equals(lastPackage)) {
            lastPackage = safe;
            Logx.d("foreground(accessibility)=" + safe);
        }
    }

    @Override public void onInterrupt() {}

    @Override public void onDestroy() {
        connected = false;
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    public static boolean isServiceConnected() { return connected; }
    public static long connectedAt() { return connectedAt; }
    public static long lastEventAt() { return lastEventAt; }
    public static long lastRootReadAt() { return lastRootReadAt; }
}
