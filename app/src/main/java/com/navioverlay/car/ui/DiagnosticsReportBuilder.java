package com.navioverlay.car.ui;

import com.navioverlay.car.core.OverlayFonts;
import com.navioverlay.car.core.Prefs;
import com.navioverlay.car.engine.AppStateDetector;
import com.navioverlay.car.engine.TrackSnapshot;
import java.util.ArrayList;

final class DiagnosticsReportBuilder {
    private DiagnosticsReportBuilder() {}

    static String build(
            Prefs prefs,
            boolean overlay,
            boolean notificationListener,
            boolean notificationFresh,
            boolean notificationUsable,
            int notificationCount,
            long notificationAgeSec,
            long notificationStaleThresholdSec,
            long notificationFallbackThresholdSec,
            boolean postNotifications,
            boolean canExactAlarm,
            boolean accessibility,
            boolean accessibilityServiceConnected,
            long accessibilityConnectedAgeSec,
            long accessibilityLastEventAgeSec,
            long accessibilityLastRootReadAgeSec,
            boolean usageAccess,
            long foregroundAgeSec,
            String lastForegroundTrigger,
            long lastForegroundTriggerAgeSec,
            boolean transientSystemWindow,
            boolean recentTransientUi,
            long reverseCameraSignalAgeSec,
            long volumeUiSignalAgeSec,
            String accessibilityPackage,
            String accessibilityClass,
            String accessibilityWindowHint,
            String usagePackage,
            AppStateDetector.Result appState,
            boolean navigatorRunning,
            String appStateError,
            TrackSnapshot track,
            boolean musicRunning,
            String musicStateError,
            String seekDebugReason,
            boolean seekSessionHasRange,
            long seekSessionDurationSec,
            long seekSessionPositionSec,
            boolean albumArtWaitingForReal,
            int albumArtRetryCount
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Navi Overlay diagnostics\n");
        appendUserSummary(
                sb,
                prefs,
                overlay,
                notificationListener,
                notificationFresh,
                postNotifications,
                canExactAlarm,
                accessibility,
                accessibilityServiceConnected,
                usageAccess,
                appState,
                navigatorRunning,
                track,
                musicRunning
        );
        sb.append("enabled=").append(prefs.enabled()).append("\n");
        sb.append("overlay=").append(overlay).append("\n");
        sb.append("notificationListener=").append(notificationListener).append("\n");
        sb.append("notificationFresh=").append(notificationFresh).append("\n");
        sb.append("notificationUsable=").append(notificationUsable).append("\n");
        sb.append("notificationCount=").append(notificationCount).append("\n");
        sb.append("notificationAgeSec=").append(notificationAgeSec).append("\n");
        sb.append("notificationStaleThresholdSec=").append(notificationStaleThresholdSec).append("\n");
        sb.append("notificationFallbackThresholdSec=").append(notificationFallbackThresholdSec).append("\n");
        sb.append("postNotifications=").append(postNotifications).append("\n");
        sb.append("canExactAlarm=").append(canExactAlarm).append("\n");
        sb.append("accessibility=").append(accessibility).append("\n");
        sb.append("accessibilityServiceConnected=").append(accessibilityServiceConnected).append("\n");
        sb.append("accessibilityConnectedAgeSec=").append(accessibilityConnectedAgeSec).append("\n");
        sb.append("accessibilityLastEventAgeSec=").append(accessibilityLastEventAgeSec).append("\n");
        sb.append("accessibilityLastRootReadAgeSec=").append(accessibilityLastRootReadAgeSec).append("\n");
        sb.append("usageAccess=").append(usageAccess).append("\n");
        sb.append("foregroundAgeSec=").append(foregroundAgeSec).append("\n");
        sb.append("lastForegroundTrigger=").append(lastForegroundTrigger).append("\n");
        sb.append("lastForegroundTriggerAgeSec=").append(lastForegroundTriggerAgeSec).append("\n");
        sb.append("transientSystemWindow=").append(transientSystemWindow).append("\n");
        sb.append("recentTransientUi=").append(recentTransientUi).append("\n");
        sb.append("reverseCameraSignalAgeSec=").append(reverseCameraSignalAgeSec).append("\n");
        sb.append("volumeUiSignalAgeSec=").append(volumeUiSignalAgeSec).append("\n");
        sb.append("persistedLastTrigger=").append(prefs.lastTriggerPackage()).append("\n");
        sb.append("persistedLastTriggerAgeSec=").append(AppRuntimeStatusHelper.ageSecondsFromTimestamp(prefs.lastTriggerAt())).append("\n");
        sb.append("accessibilityPackage=").append(accessibilityPackage).append("\n");
        sb.append("accessibilityClass=").append(accessibilityClass).append("\n");
        sb.append("accessibilityWindowHint=").append(accessibilityWindowHint).append("\n");
        sb.append("usagePackage=").append(usagePackage).append("\n");
        if (appState != null) {
            sb.append("navigatorVisible=").append(appState.navigatorVisible).append(" (признак видимости навигатора на экране)\n");
            sb.append("navigatorRunning=").append(navigatorRunning).append(" (признак, что пакет навигатора запущен или был последним внешним экраном)\n");
            sb.append("appStateTransientOverlay=").append(appState.transientOverlay).append("\n");
            sb.append("reverseCameraActive=").append(appState.reverseCameraActive).append(" (признак активного заднего хода)\n");
            sb.append("volumeUiActive=").append(appState.volumeUiActive).append(" (признак активного окна громкости)\n");
            sb.append("foregroundPackage=").append(appState.foregroundPackage).append("\n");
            sb.append("navReason=").append(appState.reason).append("\n");
            sb.append("engineDecision=").append(com.navioverlay.car.engine.StateEngine.lastDecisionReason()).append("\n");
        } else if (appStateError != null && !appStateError.isEmpty()) {
            sb.append("appStateError=").append(appStateError).append("\n");
        }
        if (track != null) {
            sb.append("musicPlaying=").append(track.playing).append(" (признак, что музыка реально играет)\n");
            sb.append("musicAppRunning=").append(musicRunning).append(" (признак, что пакет выбранного плеера запущен или недавно был открыт)\n");
            sb.append("trackSourceType=").append(track.sourceType).append("\n");
            sb.append("trackAgeSec=").append(AppRuntimeStatusHelper.ageSecondsFromTimestamp(track.readAt)).append("\n");
            sb.append("trackHasText=").append(track.hasText()).append("\n");
            sb.append("trackHasAlbumArt=").append(track.hasAlbumArt()).append("\n");
            sb.append("trackHasSeekRange=").append(track.hasSeekRange()).append("\n");
            sb.append("seekDebugReason=").append(seekDebugReason == null ? "" : seekDebugReason).append("\n");
            sb.append("seekSessionHasRange=").append(seekSessionHasRange).append("\n");
            sb.append("seekSessionDurationSec=").append(seekSessionDurationSec).append("\n");
            sb.append("seekSessionPositionSec=").append(seekSessionPositionSec).append("\n");
            sb.append("albumArtWaitingForReal=").append(albumArtWaitingForReal).append("\n");
            sb.append("albumArtRetryCount=").append(albumArtRetryCount).append("\n");
            sb.append("trackPackage=").append(track.sourcePackage).append("\n");
            sb.append("artist=").append(track.artist).append("\n");
            sb.append("title=").append(track.title).append("\n");
            sb.append("featureControls=").append(prefs.featureControls()).append(" (кнопки управления музыкой)\n");
            sb.append("featureSwipeTracks=").append(prefs.featureSwipeTracks()).append(" (свайп по окну переключает треки)\n");
            sb.append("featureSnap=").append(prefs.featureSnap()).append(" (магнит окна)\n");
            sb.append("featureVolumeDim=").append(prefs.featureVolumeDim()).append(" (приглушение окна при громкости)\n");
            sb.append("featureFloating=").append(prefs.featureFloating()).append(" (показывать без навигатора)\n");
            sb.append("featureSoftRecoverySystemWindows=").append(prefs.featureSoftRecoverySystemWindows()).append(" (мягкое восстановление после системных окон)\n");
            sb.append("featureSeekBar=").append(prefs.featureSeekBar()).append(" (полоса перемотки)\n");
            sb.append("textAlign=").append(prefs.textAlign()).append(" (0 = слева, 1 = по центру)\n");
            sb.append("conflictApps=").append(new ArrayList<>(prefs.conflictApps())).append("\n");
            sb.append("controlsSize=").append(prefs.controlsSize()).append(" (0 = маленькие, 1 = средние, 2 = большие)\n");
            sb.append("controlsShape=").append(prefs.controlsShape()).append(" (0 = круглые, 1 = прямоугольные, 2 = квадратные, 3 = без рамок)\n");
            sb.append("controlsSpacing=").append(prefs.controlsSpacing()).append(" (0 = компактно, 1 = обычно, 2 = широко)\n");
            sb.append("pauseBehavior=").append(prefs.pauseBehavior()).append(" (0 = оставлять окно, 1 = скрывать, 2 = ненадолго оставить и скрыть)\n");
            sb.append("designPreset=").append(prefs.designPreset()).append(" (выбранный пресет оформления)\n");
            sb.append("textFont=").append(OverlayFonts.nameAt(prefs.textFont())).append("\n");
            sb.append("borderColor=#").append(Integer.toHexString(prefs.borderColor())).append("\n");
            sb.append("borderWidth=").append(prefs.borderWidth()).append("\n");
            sb.append("controlsBorderColor=#").append(Integer.toHexString(prefs.controlsBorderColor())).append("\n");
        } else if (musicStateError != null && !musicStateError.isEmpty()) {
            sb.append("musicStateError=").append(musicStateError).append("\n");
        }
        return sb.toString();
    }

    private static void appendUserSummary(
            StringBuilder sb,
            Prefs prefs,
            boolean overlay,
            boolean notificationListener,
            boolean notificationFresh,
            boolean postNotifications,
            boolean canExactAlarm,
            boolean accessibility,
            boolean accessibilityServiceConnected,
            boolean usageAccess,
            AppStateDetector.Result appState,
            boolean navigatorRunning,
            TrackSnapshot track,
            boolean musicRunning
    ) {
        boolean en = prefs.englishUi();
        String engineDecision = com.navioverlay.car.engine.StateEngine.lastDecisionReason();
        String summary;
        String hint;

        if (!prefs.enabled()) {
            summary = en ? "Overlay is disabled in app settings." : "Окно выключено в настройках приложения.";
            hint = en ? "Enable the main switch on the Home screen." : "Включи главный переключатель на главном экране.";
        } else if (!overlay) {
            summary = en ? "Draw-over-apps permission is missing." : "Нет разрешения на показ поверх других приложений.";
            hint = en ? "Open Permissions and allow Draw over apps." : "Открой раздел разрешений и выдай доступ поверх окон.";
        } else if (!notificationListener) {
            summary = en ? "Notification access is disabled." : "Отключён доступ к уведомлениям.";
            hint = en ? "Turn on notification access for Navi Overlay." : "Включи доступ к уведомлениям для Navi Overlay.";
        } else if (!accessibility && !usageAccess) {
            summary = en ? "No active-app detection method is enabled." : "Нет рабочего способа определить активное приложение.";
            hint = en ? "Enable Accessibility or Usage access." : "Включи специальные возможности или доступ к истории использования.";
        } else if (appState == null) {
            summary = en ? "Navigator status could not be read." : "Не удалось определить состояние навигатора.";
            hint = en ? "Reopen the navigator and copy diagnostics again." : "Открой навигатор заново и ещё раз скопируй диагностику.";
        } else if (appState.reverseCameraActive) {
            summary = en ? "Reverse camera mode is active." : "Сейчас активен режим заднего хода / камера заднего вида.";
            hint = en ? "The overlay is intentionally hidden until the reverse-camera screen disappears." : "Окно специально скрыто, пока не исчезнет экран заднего хода.";
        } else if (!prefs.featureFloating() && !appState.navigatorVisible) {
            summary = en ? "Navigator is not on screen now." : "Навигатор сейчас не находится на экране.";
            hint = en ? "Open the navigator in foreground or enable Always show window." : "Выведи навигатор на экран или включи постоянный показ окна.";
        } else if (!navigatorRunning && !prefs.featureFloating()) {
            summary = en ? "Selected navigator package is not running." : "Выбранный пакет навигатора сейчас не запущен.";
            hint = en ? "Check the Navigation apps list and start the needed navigator." : "Проверь список приложений навигации и запусти нужный навигатор.";
        } else if (track == null || !track.hasText()) {
            if (!notificationFresh) {
                summary = en ? "Track notifications are stale or missing." : "Уведомления с данными трека устарели или не приходят.";
                hint = en ? "Open the player once and make sure its notifications are visible." : "Открой плеер и проверь, что его уведомление реально видно в системе.";
            } else {
                summary = en ? "Track metadata is not available yet." : "Данные о текущем треке пока недоступны.";
                hint = en ? "Start music playback and make sure the player is selected in Audio apps." : "Запусти воспроизведение и проверь, что плеер выбран в аудио приложениях.";
            }
        } else if (!track.sourcePackage.isEmpty() && !prefs.players().isEmpty() && !prefs.isPlayer(track.sourcePackage)) {
            summary = en ? "Track data came from an app that is not selected as an audio player." : "Данные трека пришли от приложения, которое не выбрано как аудио плеер.";
            hint = en ? "Check Audio apps and remove unrelated apps from track detection." : "Проверь список аудио приложений и убери лишние приложения из определения треков.";
        } else if (!musicRunning) {
            summary = en ? "Player package is not running now." : "Пакет аудио приложения сейчас не запущен.";
            hint = en ? "Check the Audio apps list and reopen the player." : "Проверь список аудио приложений и открой плеер заново.";
        } else if ("hide_music_inactive".equals(engineDecision)) {
            summary = en ? "Playback is paused and current pause behavior hides the window." : "Музыка стоит на паузе, а текущий режим паузы скрывает окно.";
            hint = en ? "Change Window settings -> Pause behavior if needed." : "При необходимости измени поведение окна при паузе в настройках окна.";
        } else if (engineDecision.startsWith("hold_")) {
            summary = en ? "The window is waiting for a system or external overlay to disappear." : "Окно ждёт, пока исчезнет системное или внешнее поверхностное окно.";
            hint = en ? "Wait a moment after volume/radar overlays disappear." : "Подожди немного после исчезновения громкости, антирадара или другого окна поверх экрана.";
        } else if ("suppress_same_track".equals(engineDecision)) {
            summary = en ? "The same track is already shown and repeated popup is suppressed." : "Этот же трек уже показан, повторный всплывающий показ временно подавлен.";
            hint = en ? "This is normal behavior for repeated track checks." : "Это нормальное поведение для повторных проверок одного и того же трека.";
        } else {
            summary = en ? "Base checks look normal." : "Базовые проверки выглядят нормально.";
            hint = en ? "If the window still does not appear, copy this report and compare engineDecision with current screen state." : "Если окно всё равно не появляется, скопируй этот отчёт и сравни engineDecision с тем, что реально происходит на экране.";
        }

        sb.append("summary=").append(summary).append("\n");
        sb.append("nextStep=").append(hint).append("\n");
        if (!postNotifications) {
            sb.append("note=").append(en
                    ? "App notifications are disabled. This is optional on some Android versions but can reduce metadata reliability."
                    : "Уведомления приложения выключены. На части Android это не критично, но может ухудшать получение данных о треке.")
                    .append("\n");
        }
        if (!canExactAlarm) {
            sb.append("note2=").append(en
                    ? "Exact alarms are unavailable. This does not always break the overlay, but some timing may be less precise."
                    : "Точные таймеры недоступны. Это не всегда ломает окно, но часть таймингов может быть менее точной.")
                    .append("\n");
        }
    }
}
