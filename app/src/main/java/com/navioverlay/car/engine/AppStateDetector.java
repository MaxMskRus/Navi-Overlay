package com.navioverlay.car.engine;

import android.content.Context;
import android.text.TextUtils;
import com.navioverlay.car.core.Prefs;
import com.navioverlay.car.services.ForegroundDetector;
import com.navioverlay.car.services.ForegroundState;

/**
 * Определяет, действительно ли выбранный навигатор сейчас является основным экраном.
 * Главный источник — AccessibilityService. UsageStats используется только как запасной вариант.
 */
public final class AppStateDetector {
    private final Context app;
    private final Prefs prefs;
    private long lastNavigatorVisibleAt = 0L;
    private static final long SHORT_OTHER_APP_GRACE_MS = 4500L;

    public AppStateDetector(Context context) {
        app = context.getApplicationContext();
        prefs = new Prefs(app);
    }

    public Result read() {
        if (!prefs.showOnlyWithTrigger()) {
            return new Result(true, "disabled-filter", false, "Фильтр навигатора выключен", false, false);
        }

        long now = System.currentTimeMillis();
        long graceMs = Math.max(5000L, prefs.navGraceMs());
        boolean accEnabled = ForegroundDetector.isAccessibilityEnabled(app);

        String accessibilityPkg = TrackSnapshot.clean(ForegroundState.get());
        long accessibilityAge = ForegroundState.ageMs();
        boolean accessibilityFresh = !accessibilityPkg.isEmpty() && accessibilityAge < 5000L;

        String usagePkg = TrackSnapshot.clean(ForegroundDetector.currentByUsage(app));
        String self = app.getPackageName();

        if (prefs.isTrigger(accessibilityPkg)) {
            lastNavigatorVisibleAt = now;
            return new Result(true, accessibilityPkg, false, "Навигатор на экране", false, ForegroundState.isVolumeUiActive());
        }

        if (prefs.isTrigger(usagePkg)) {
            prefs.setLastTrigger(usagePkg);
            lastNavigatorVisibleAt = now;
            return new Result(true, usagePkg, false,
                    accEnabled ? "Навигатор определён через UsageStats" : "Навигатор определён через UsageStats, Accessibility выключен",
                    false, ForegroundState.isVolumeUiActive());
        }

        String pkg = !accessibilityPkg.isEmpty() ? accessibilityPkg : usagePkg;
        boolean reverseCameraForeground = ForegroundState.isReverseCameraActive()
                || ForegroundState.isReverseCameraPackage(accessibilityPkg)
                || ForegroundState.isReverseCameraPackage(usagePkg)
                || ForegroundState.hasReverseCameraSignal();
        boolean volumeUiForeground = ForegroundState.isVolumeUiActive()
                || ForegroundState.isVolumeUiPackage(accessibilityPkg)
                || ForegroundState.isVolumeUiPackage(usagePkg);
        boolean transientWindow = ForegroundState.isTransientSystemWindow()
                || ForegroundState.hasRecentTransientUi()
                || ForegroundState.isTransientPackage(accessibilityPkg)
                || ForegroundState.isTransientPackage(usagePkg);
        boolean selfOnScreen = (self.equals(accessibilityPkg) && accessibilityFresh)
                || self.equals(usagePkg);
        boolean accessibilityOtherApp = accEnabled
                && accessibilityFresh
                && !accessibilityPkg.isEmpty()
                && !ForegroundState.isTransientPackage(accessibilityPkg)
                && !prefs.isTrigger(accessibilityPkg)
                && !self.equals(accessibilityPkg);
        boolean usageOtherApp = !usagePkg.isEmpty()
                && !ForegroundState.isTransientPackage(usagePkg)
                && !prefs.isTrigger(usagePkg)
                && !self.equals(usagePkg);
        boolean accessibilityConflictApp = accessibilityOtherApp && prefs.isConflictApp(accessibilityPkg);
        boolean usageConflictApp = usageOtherApp && prefs.isConflictApp(usagePkg);
        boolean canUseLastTriggerFallback = transientWindow
                || (TextUtils.isEmpty(accessibilityPkg) && TextUtils.isEmpty(usagePkg));

        if (reverseCameraForeground) {
            prefs.setLastSystemWindow("reverse");
            return new Result(false, pkg, true, "Активен режим заднего хода / камера заднего вида", true, volumeUiForeground);
        }

        if (volumeUiForeground) {
            prefs.setLastSystemWindow("volume");
        }

        if (transientWindow && now - lastNavigatorVisibleAt < graceMs) {
            prefs.setLastSystemWindow("transient");
            return new Result(true, pkg, true, "Временное системное окно поверх навигатора", false, volumeUiForeground);
        }

        if ((accessibilityConflictApp || usageConflictApp) && now - lastNavigatorVisibleAt < graceMs) {
            String conflictPkg = accessibilityConflictApp ? accessibilityPkg : usagePkg;
            prefs.setLastSystemWindow("conflict");
            return new Result(true, conflictPkg, true, "Внешнее окно из списка конфликтующих приложений", false, volumeUiForeground);
        }

        if (prefs.featureSoftRecoverySystemWindows()
                && (accessibilityOtherApp || usageOtherApp)
                && now - lastNavigatorVisibleAt < Math.min(graceMs, SHORT_OTHER_APP_GRACE_MS)) {
            String transientPkg = !accessibilityPkg.isEmpty() ? accessibilityPkg : usagePkg;
            prefs.setLastSystemWindow("transient");
            return new Result(true, transientPkg, true, "Короткое внешнее окно поверх навигатора", false, volumeUiForeground);
        }

        if (accessibilityOtherApp) {
            return new Result(false, accessibilityPkg, false, "Открыто другое приложение", false, volumeUiForeground);
        }

        if (usageOtherApp) {
            return new Result(false, usagePkg, false,
                    accEnabled ? "Навигатор свернут, на экране другое приложение" : "UsageStats показывает другое приложение",
                    false, volumeUiForeground);
        }

        String lastTrigger = TrackSnapshot.clean(ForegroundState.lastTrigger());
        long lastTriggerAgeMs = ForegroundState.lastTriggerAgeMs();
        if (lastTrigger.isEmpty()) {
            lastTrigger = TrackSnapshot.clean(prefs.lastTriggerPackage());
            long persistedAt = prefs.lastTriggerAt();
            lastTriggerAgeMs = persistedAt > 0L ? Math.max(0L, now - persistedAt) : Long.MAX_VALUE;
        }
        if (canUseLastTriggerFallback && !lastTrigger.isEmpty() && lastTriggerAgeMs < graceMs && !selfOnScreen) {
            return new Result(true, lastTrigger, true,
                    accEnabled ? "Accessibility временно без данных, используем последний навигатор" : "Accessibility выключен, используем последний навигатор",
                    false, volumeUiForeground);
        }

        if (!accEnabled) {
            return new Result(false, usagePkg, false, "Спец. возможности выключены — навигатор определяется нестабильно", false, volumeUiForeground);
        }

        return new Result(false, pkg, false, "Навигатор не на экране", false, volumeUiForeground);
    }

    public static final class Result {
        public final boolean navigatorVisible;
        public final String foregroundPackage;
        public final boolean transientOverlay;
        public final String reason;
        public final boolean reverseCameraActive;
        public final boolean volumeUiActive;
        public Result(boolean navigatorVisible, String foregroundPackage, boolean transientOverlay, String reason) {
            this(navigatorVisible, foregroundPackage, transientOverlay, reason, false, false);
        }
        public Result(boolean navigatorVisible, String foregroundPackage, boolean transientOverlay, String reason, boolean reverseCameraActive) {
            this(navigatorVisible, foregroundPackage, transientOverlay, reason, reverseCameraActive, false);
        }
        public Result(boolean navigatorVisible, String foregroundPackage, boolean transientOverlay, String reason, boolean reverseCameraActive, boolean volumeUiActive) {
            this.navigatorVisible = navigatorVisible;
            this.foregroundPackage = foregroundPackage == null ? "" : foregroundPackage;
            this.transientOverlay = transientOverlay;
            this.reason = reason == null ? "" : reason;
            this.reverseCameraActive = reverseCameraActive;
            this.volumeUiActive = volumeUiActive;
        }
    }
}
