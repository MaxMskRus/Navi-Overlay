package com.navioverlay.car.services;

import java.util.Set;

/**
 * Потокобезопасное состояние видимого приложения.
 *
 * Системные окна, шторка, лаунчер, камера заднего вида и оболочки ГУ могут временно становиться
 * foreground-пакетом. В этот момент не надо считать, что навигатор окончательно закрыт: храним
 * последний реально видимый выбранный навигатор и помечаем такие окна как transient.
 */
public final class ForegroundState {
    private ForegroundState() {}

    private static volatile String currentPackage = "";
    private static volatile long updatedAt = 0;
    private static volatile String lastTriggerPackage = "";
    private static volatile long lastTriggerAt = 0;
    private static volatile boolean transientSystemWindow = false;
    private static volatile long transientUiUntilAt = 0;

    public static void set(String pkg) { set(pkg, null); }

    public static void set(String pkg, Set<String> triggerPackages) {
        String p = pkg == null ? "" : pkg;
        currentPackage = p;
        updatedAt = System.currentTimeMillis();
        transientSystemWindow = isTransientPackage(p);
        if (triggerPackages != null && triggerPackages.contains(p)) {
            lastTriggerPackage = p;
            lastTriggerAt = updatedAt;
            transientSystemWindow = false;
        }
    }

    public static String get() { return currentPackage; }
    public static long ageMs() { return System.currentTimeMillis() - updatedAt; }
    public static String lastTrigger() { return lastTriggerPackage; }
    public static long lastTriggerAgeMs() { return System.currentTimeMillis() - lastTriggerAt; }
    public static boolean isTransientSystemWindow() { return transientSystemWindow && ageMs() < 3500; }
    public static void markTransientUi(long durationMs) {
        transientUiUntilAt = System.currentTimeMillis() + Math.max(500L, durationMs);
    }
    public static boolean hasRecentTransientUi() {
        return transientUiUntilAt > System.currentTimeMillis();
    }

    public static boolean isTransientPackage(String p) {
        if (p == null || p.length() == 0) return false;
        return p.equals("android")
                || p.equals("com.android.systemui")
                || p.equals("com.google.android.permissioncontroller")
                || p.equals("com.android.permissioncontroller")
                || p.startsWith("com.samsung.android.app.aodservice")
                || p.startsWith("com.samsung.android.bixby")
                || p.startsWith("com.samsung.android.app.settings")
                || p.startsWith("com.miui.")
                || p.startsWith("com.huawei.systemmanager")
                || p.contains("systemui")
                || p.contains("reverse")
                || p.contains("camera");
    }
}
