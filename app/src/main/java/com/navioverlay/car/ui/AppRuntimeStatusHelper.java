package com.navioverlay.car.ui;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.content.Context;
import android.os.Build;
import com.navioverlay.car.core.Prefs;
import com.navioverlay.car.engine.AppStateDetector;
import com.navioverlay.car.engine.TrackSnapshot;
import com.navioverlay.car.services.ForegroundState;
import java.util.List;

final class AppRuntimeStatusHelper {
    private AppRuntimeStatusHelper() {}

    private static final long STATUS_TRACK_FRESH_MS = 15_000L;
    private static final long STATUS_LAST_EXTERNAL_FRESH_MS = 45_000L;
    static long ageSecondsFromTimestamp(long ts) {
        if (ts <= 0L) return -1L;
        long delta = System.currentTimeMillis() - ts;
        return Math.max(0L, delta / 1000L);
    }

    static long ageMsToSeconds(long ageMs) {
        if (ageMs == Long.MAX_VALUE || ageMs < 0L) return -1L;
        return Math.max(0L, ageMs / 1000L);
    }

    static boolean canScheduleExactAlarmsCompat(Context context) {
        if (Build.VERSION.SDK_INT < 31) return true;
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean isPackageProcessRunning(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> processes = am == null ? null : am.getRunningAppProcesses();
            if (processes == null) return false;
            for (ActivityManager.RunningAppProcessInfo proc : processes) {
                if (proc == null || proc.pkgList == null) continue;
                for (String procPkg : proc.pkgList) {
                    if (pkg.equals(procPkg)) return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    static boolean isAnyPackageRunning(Context context, Iterable<String> packages) {
        if (packages == null) return false;
        for (String pkg : packages) {
            if (isPackageProcessRunning(context, pkg)) return true;
        }
        return false;
    }

    static boolean isNavigatorVisibleForStatus(AppStateDetector.Result appState) {
        return appState != null && appState.navigatorVisible;
    }

    static boolean isNavigatorRunningForStatus(Context context, Prefs prefs, AppStateDetector.Result appState) {
        if (appState != null && appState.navigatorVisible) return true;
        if (appState != null && prefs.isTrigger(appState.foregroundPackage)) return true;
        String lastExternal = ForegroundState.lastExternalPackage();
        if (prefs.isTrigger(lastExternal) && ForegroundState.lastExternalAgeMs() <= STATUS_LAST_EXTERNAL_FRESH_MS) return true;
        return isAnyPackageRunning(context, prefs.triggers());
    }

    static String lastSeenNavigatorPackage(Prefs prefs, AppStateDetector.Result appState) {
        if (prefs == null) return "";
        if (appState != null && prefs.isTrigger(appState.foregroundPackage)) return appState.foregroundPackage;
        String lastExternal = ForegroundState.lastExternalPackage();
        if (prefs.isTrigger(lastExternal) && ForegroundState.lastExternalAgeMs() <= STATUS_LAST_EXTERNAL_FRESH_MS) return lastExternal;
        String persisted = prefs.lastTriggerPackage();
        return prefs.isTrigger(persisted) ? persisted : "";
    }

    static long lastSeenNavigatorAgeMs(Prefs prefs, AppStateDetector.Result appState) {
        if (prefs == null) return Long.MAX_VALUE;
        if (appState != null && prefs.isTrigger(appState.foregroundPackage) && appState.navigatorVisible) return 0L;
        String lastExternal = ForegroundState.lastExternalPackage();
        if (prefs.isTrigger(lastExternal) && ForegroundState.lastExternalAgeMs() <= STATUS_LAST_EXTERNAL_FRESH_MS) {
            return ForegroundState.lastExternalAgeMs();
        }
        long ts = prefs.lastTriggerAt();
        if (prefs.isTrigger(prefs.lastTriggerPackage()) && ts > 0L) return Math.max(0L, System.currentTimeMillis() - ts);
        return Long.MAX_VALUE;
    }

    static boolean isMusicPlayingForStatus(Prefs prefs, TrackSnapshot track) {
        return track != null && track.playing && isTrackFromConfiguredPlayer(prefs, track);
    }

    static boolean isMusicRunningForStatus(Context context, Prefs prefs, TrackSnapshot track, String accessibilityPackage, String usagePackage) {
        if (isMusicPlayingForStatus(prefs, track)) return true;
        if (isConfiguredPlayerPackage(prefs, accessibilityPackage) || isConfiguredPlayerPackage(prefs, usagePackage)) return true;
        String lastExternal = ForegroundState.lastExternalPackage();
        if (isConfiguredPlayerPackage(prefs, lastExternal) && ForegroundState.lastExternalAgeMs() <= STATUS_LAST_EXTERNAL_FRESH_MS) return true;
        if (track != null && isFreshTrack(track) && isTrackFromConfiguredPlayer(prefs, track)
                && isPackageProcessRunning(context, track.sourcePackage)) return true;
        return isAnyPackageRunning(context, prefs.players());
    }

    static String lastSeenPlayerPackage(Prefs prefs, TrackSnapshot track, String accessibilityPackage, String usagePackage) {
        if (isMusicPlayingForStatus(prefs, track)) return track.sourcePackage;
        if (track != null && isFreshTrack(track) && isTrackFromConfiguredPlayer(prefs, track)) return track.sourcePackage;
        if (isConfiguredPlayerPackage(prefs, accessibilityPackage)) return accessibilityPackage;
        if (isConfiguredPlayerPackage(prefs, usagePackage)) return usagePackage;
        String lastExternal = ForegroundState.lastExternalPackage();
        if (isConfiguredPlayerPackage(prefs, lastExternal) && ForegroundState.lastExternalAgeMs() <= STATUS_LAST_EXTERNAL_FRESH_MS) return lastExternal;
        return "";
    }

    static long lastSeenPlayerAgeMs(Prefs prefs, TrackSnapshot track, String accessibilityPackage, String usagePackage) {
        if (isMusicPlayingForStatus(prefs, track)) return 0L;
        if (track != null && isFreshTrack(track) && isTrackFromConfiguredPlayer(prefs, track)) {
            return Math.max(0L, System.currentTimeMillis() - track.readAt);
        }
        if (isConfiguredPlayerPackage(prefs, accessibilityPackage) || isConfiguredPlayerPackage(prefs, usagePackage)) return 0L;
        String lastExternal = ForegroundState.lastExternalPackage();
        if (isConfiguredPlayerPackage(prefs, lastExternal) && ForegroundState.lastExternalAgeMs() <= STATUS_LAST_EXTERNAL_FRESH_MS) {
            return ForegroundState.lastExternalAgeMs();
        }
        return Long.MAX_VALUE;
    }

    private static boolean isTrackFromConfiguredPlayer(Prefs prefs, TrackSnapshot track) {
        if (prefs == null || track == null) return false;
        String pkg = track.sourcePackage;
        return isConfiguredPlayerPackage(prefs, pkg);
    }

    private static boolean isConfiguredPlayerPackage(Prefs prefs, String pkg) {
        return prefs != null && pkg != null && !pkg.isEmpty() && prefs.isPlayer(pkg);
    }

    private static boolean isFreshTrack(TrackSnapshot track) {
        if (track == null || !track.hasText()) return false;
        long age = System.currentTimeMillis() - track.readAt;
        return age >= 0L && age <= STATUS_TRACK_FRESH_MS;
    }
}
