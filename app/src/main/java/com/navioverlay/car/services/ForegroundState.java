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

    private static final String SELF_OVERLAY_PACKAGE = "com.navioverlay.car";

    private static volatile String currentPackage = "";
    private static volatile String currentClassName = "";
    private static volatile String currentWindowHint = "";
    private static volatile long updatedAt = 0;
    private static volatile String lastTriggerPackage = "";
    private static volatile long lastTriggerAt = 0;
    private static volatile String lastExternalPackage = "";
    private static volatile long lastExternalAt = 0;
    private static volatile boolean transientSystemWindow = false;
    private static volatile long transientUiUntilAt = 0;
    private static volatile boolean reverseCameraLatched = false;
    private static volatile long reverseCameraEnteredAt = 0;
    private static volatile long reverseCameraLastSignalAt = 0;
    private static volatile long reverseCameraReleaseCandidateAt = 0;
    private static volatile long volumeUiUntilAt = 0;
    private static volatile long volumeUiLastSignalAt = 0;

    // TS10S reverse does not behave like a normal stable foreground app.
    // We keep reverse latched until we see a stable non-system screen after the signal.
    private static final long REVERSE_RELEASE_STABLE_MS = 1100L;
    private static final long REVERSE_MIN_HOLD_AFTER_SIGNAL_MS = 900L;
    private static final long REVERSE_FAILSAFE_MAX_MS = 90_000L;
    private static final long VOLUME_UI_HOLD_MS = 1800L;

    public static void set(String pkg) { set(pkg, null); }

    public static void set(String pkg, Set<String> triggerPackages) {
        String p = pkg == null ? "" : pkg;
        currentPackage = p;
        updatedAt = System.currentTimeMillis();
        if (!p.isEmpty() && !p.equals(SELF_OVERLAY_PACKAGE)) {
            lastExternalPackage = p;
            lastExternalAt = updatedAt;
        }
        transientSystemWindow = isTransientPackage(p);
        if (isReverseCameraPackage(p)) markReverseCameraSignal();
        if (isVolumeUiPackage(p)) markVolumeUiSignal();
        if (triggerPackages != null && triggerPackages.contains(p)) {
            lastTriggerPackage = p;
            lastTriggerAt = updatedAt;
            transientSystemWindow = false;
        }
        evaluateReverseLatch(p, triggerPackages);
    }

    public static void setWindowSignal(String className, CharSequence windowHint) {
        currentClassName = className == null ? "" : className;
        String text = windowHint == null ? "" : windowHint.toString().trim();
        if (text.length() > 120) text = text.substring(0, 120);
        currentWindowHint = text;
        if (isReverseCameraSignal(currentClassName) || isReverseCameraSignal(currentWindowHint)) {
            markReverseCameraSignal();
        }
        if (isVolumeUiSignal(currentClassName, currentWindowHint, currentPackage)) {
            markVolumeUiSignal();
        }
    }

    public static String get() { return currentPackage; }
    public static String currentClass() { return currentClassName; }
    public static String currentWindowHint() { return currentWindowHint; }
    public static long ageMs() { return System.currentTimeMillis() - updatedAt; }
    public static String lastTrigger() { return lastTriggerPackage; }
    public static long lastTriggerAgeMs() { return System.currentTimeMillis() - lastTriggerAt; }
    public static String lastExternalPackage() { return lastExternalPackage; }
    public static long lastExternalAgeMs() {
        if (lastExternalAt <= 0L) return Long.MAX_VALUE;
        return Math.max(0L, System.currentTimeMillis() - lastExternalAt);
    }
    public static boolean isTransientSystemWindow() { return transientSystemWindow && ageMs() < 3500; }
    public static void markTransientUi(long durationMs) {
        transientUiUntilAt = System.currentTimeMillis() + Math.max(500L, durationMs);
    }
    public static boolean hasRecentTransientUi() {
        return transientUiUntilAt > System.currentTimeMillis();
    }
    public static void markReverseCameraSignal() {
        long now = System.currentTimeMillis();
        reverseCameraLatched = true;
        if (reverseCameraEnteredAt <= 0L) reverseCameraEnteredAt = now;
        reverseCameraLastSignalAt = now;
        reverseCameraReleaseCandidateAt = 0L;
    }
    public static boolean isReverseCameraActive() {
        if (!reverseCameraLatched) return false;
        long now = System.currentTimeMillis();
        if (reverseCameraEnteredAt > 0L && now - reverseCameraEnteredAt > REVERSE_FAILSAFE_MAX_MS) {
            resetReverseCameraState();
            return false;
        }
        return true;
    }
    public static long reverseCameraAgeMs() {
        long enteredAt = reverseCameraEnteredAt;
        if (enteredAt <= 0L || !reverseCameraLatched) return -1L;
        return Math.max(0L, System.currentTimeMillis() - enteredAt);
    }

    public static void markVolumeUiSignal() {
        long now = System.currentTimeMillis();
        volumeUiLastSignalAt = now;
        volumeUiUntilAt = now + VOLUME_UI_HOLD_MS;
    }

    public static boolean isVolumeUiActive() {
        long untilAt = volumeUiUntilAt;
        return untilAt > System.currentTimeMillis();
    }

    public static long volumeUiAgeMs() {
        long lastAt = volumeUiLastSignalAt;
        if (lastAt <= 0L || !isVolumeUiActive()) return -1L;
        return Math.max(0L, System.currentTimeMillis() - lastAt);
    }

    public static boolean isReverseCameraPackage(String p) {
        if (p == null || p.length() == 0) return false;
        String v = p.toLowerCase();
        return v.equals("com.tw.reverse")
                || v.equals("com.reversecar")
                || v.contains("reversecar")
                || v.contains("backcar")
                || v.contains("rearcamera")
                || v.contains("rear.camera")
                || v.contains("rearview")
                || v.contains("rear.view")
                || v.contains(".reverse")
                || v.endsWith(".reverse")
                || v.contains(".driving")
                || v.endsWith(".driving")
                || v.contains("driving2")
                || v.contains("backsight")
                || v.contains("back_sight");
    }

    public static boolean hasReverseCameraSignal() {
        return isReverseCameraActive()
                || isReverseCameraSignal(currentClassName)
                || isReverseCameraSignal(currentWindowHint);
    }

    public static boolean isVolumeUiPackage(String p) {
        if (p == null || p.length() == 0) return false;
        String v = p.toLowerCase();
        return v.equals("com.tw.service");
    }

    private static boolean isReverseCameraSignal(String value) {
        if (value == null || value.length() == 0) return false;
        String v = value.toLowerCase();
        return v.contains("reversecar")
                || v.contains("backcar")
                || v.contains("back car")
                || v.contains("backsight")
                || v.contains("back_sight")
                || v.contains("rearview")
                || v.contains("rear view")
                || v.contains("rearcamera")
                || v.contains("rear camera")
                || v.contains("reverse camera")
                || v.contains("360car")
                || v.contains("360 car")
                || v.contains("frontrearcam")
                || v.contains("front rear cam")
                || v.contains("reverse stop")
                || v.contains("driving2");
    }

    private static boolean isVolumeUiSignal(String className, String windowHint, String pkg) {
        if (!isVolumeUiPackage(pkg)) return false;
        String cls = className == null ? "" : className.toLowerCase();
        String hint = windowHint == null ? "" : windowHint.toLowerCase();
        return cls.contains("seekbar")
                || cls.contains("textview")
                || cls.contains("linearlayout")
                || hint.matches(".*\\d+.*");
    }

    private static void evaluateReverseLatch(String pkg, Set<String> triggerPackages) {
        if (!reverseCameraLatched) return;
        long now = System.currentTimeMillis();
        if (isReverseCameraPackage(pkg)) {
            reverseCameraReleaseCandidateAt = 0L;
            return;
        }
        if (now - reverseCameraLastSignalAt < REVERSE_MIN_HOLD_AFTER_SIGNAL_MS) {
            reverseCameraReleaseCandidateAt = 0L;
            return;
        }
        if (isStableExitPackage(pkg, triggerPackages)) {
            if (reverseCameraReleaseCandidateAt <= 0L) {
                reverseCameraReleaseCandidateAt = now;
                return;
            }
            if (now - reverseCameraReleaseCandidateAt >= REVERSE_RELEASE_STABLE_MS) {
                resetReverseCameraState();
            }
        } else {
            reverseCameraReleaseCandidateAt = 0L;
        }
    }

    private static boolean isStableExitPackage(String pkg, Set<String> triggerPackages) {
        if (pkg == null || pkg.isEmpty()) return false;
        String p = pkg.toLowerCase();
        if (p.equals(SELF_OVERLAY_PACKAGE)) return false;
        if (isReverseCameraPackage(p) || isVolumeUiPackage(p)) return false;
        if (p.equals("com.android.systemui") || p.equals("android") || p.contains("systemui")) return false;
        if (triggerPackages != null && triggerPackages.contains(pkg)) return true;
        return !isTransientPackage(p);
    }

    private static void resetReverseCameraState() {
        reverseCameraLatched = false;
        reverseCameraEnteredAt = 0L;
        reverseCameraLastSignalAt = 0L;
        reverseCameraReleaseCandidateAt = 0L;
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
                || isVolumeUiPackage(p)
                || isReverseCameraPackage(p)
                || p.contains("reverse")
                || p.contains("camera");
    }
}
