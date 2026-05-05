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

    public AppStateDetector(Context context) {
        app = context.getApplicationContext();
        prefs = new Prefs(app);
    }

    public Result read() {
        if (!prefs.showOnlyWithTrigger()) {
            return new Result(true, "disabled-filter", false, "Фильтр навигатора выключен");
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
            return new Result(true, accessibilityPkg, false, "Навигатор на экране");
        }

        if (prefs.isTrigger(usagePkg)) {
            prefs.setLastTrigger(usagePkg);
            lastNavigatorVisibleAt = now;
            return new Result(true, usagePkg, false,
                    accEnabled ? "Навигатор определён через UsageStats" : "Навигатор определён через UsageStats, Accessibility выключен");
        }

        String pkg = !accessibilityPkg.isEmpty() ? accessibilityPkg : usagePkg;
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
        boolean canUseLastTriggerFallback = transientWindow
                || (TextUtils.isEmpty(accessibilityPkg) && TextUtils.isEmpty(usagePkg));

        if (transientWindow && now - lastNavigatorVisibleAt < graceMs) {
            return new Result(true, pkg, true, "Временное системное окно поверх навигатора");
        }

        if (accessibilityOtherApp) {
            return new Result(false, accessibilityPkg, false, "Открыто другое приложение");
        }

        if (usageOtherApp) {
            return new Result(false, usagePkg, false,
                    accEnabled ? "Навигатор свернут, на экране другое приложение" : "UsageStats показывает другое приложение");
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
                    accEnabled ? "Accessibility временно без данных, используем последний навигатор" : "Accessibility выключен, используем последний навигатор");
        }

        if (!accEnabled) {
            return new Result(false, usagePkg, false, "Спец. возможности выключены — навигатор определяется нестабильно");
        }

        return new Result(false, pkg, false, "Навигатор не на экране");
    }

    public static final class Result {
        public final boolean navigatorVisible;
        public final String foregroundPackage;
        public final boolean transientOverlay;
        public final String reason;
        public Result(boolean navigatorVisible, String foregroundPackage, boolean transientOverlay, String reason) {
            this.navigatorVisible = navigatorVisible;
            this.foregroundPackage = foregroundPackage == null ? "" : foregroundPackage;
            this.transientOverlay = transientOverlay;
            this.reason = reason == null ? "" : reason;
        }
    }
}
