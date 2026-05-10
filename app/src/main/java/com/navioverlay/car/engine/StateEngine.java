package com.navioverlay.car.engine;

import android.content.Context;
import com.navioverlay.car.core.Logx;
import com.navioverlay.car.core.Prefs;
import com.navioverlay.car.overlay.TrackOverlayManager;

/** Единственное место, где принимается решение показывать overlay или нет. */
public final class StateEngine {
    private static final long SAME_TRACK_SUPPRESS_MS = 6000L;

    private final Context app;
    private final Prefs prefs;
    private String lastShownKey = "";
    private String lastShownPackage = "";
    private String lastShownTitle = "";
    private String lastShownArtist = "";
    private long lastShownAt = 0L;
    private boolean wasNavigatorVisible = false;
    private boolean wasPlayerActive = false;
    private boolean wasTrackPlaying = false;
    private long pausedVisibleUntilAt = 0L;
    private long lastDebugAt = 0L;
    private static final long PAUSED_HOLD_MS = 2500L;

    public StateEngine(Context context) {
        app = context.getApplicationContext();
        prefs = new Prefs(app);
    }

    public int tick(AppStateDetector.Result appState, TrackSnapshot track) {
        if (!prefs.enabled()) {
            TrackOverlayManager.hideNow();
            return 2500;
        }
        if (appState == null) return 2500;

        boolean floating = prefs.featureFloating();
        boolean nav = floating || appState.navigatorVisible;
        boolean hasTrack = track != null && track.hasText();
        long now = System.currentTimeMillis();
        boolean canResumePaused = hasTrack && (TrackSnapshot.SOURCE_MEDIA_SESSION.equals(track.sourceType)
                || TrackSnapshot.SOURCE_NOTIFICATION.equals(track.sourceType));
        boolean playingNow = hasTrack && track.playing;
        if (canResumePaused && wasTrackPlaying && !playingNow && prefs.pauseBehavior() == Prefs.PAUSE_BEHAVIOR_SHORT) {
            pausedVisibleUntilAt = now + PAUSED_HOLD_MS;
        } else if (playingNow) {
            pausedVisibleUntilAt = 0L;
        }
        boolean activePlayer;
        switch (prefs.pauseBehavior()) {
            case Prefs.PAUSE_BEHAVIOR_HIDE:
                activePlayer = playingNow;
                break;
            case Prefs.PAUSE_BEHAVIOR_SHORT:
                activePlayer = hasTrack && (playingNow
                        || TrackSnapshot.SOURCE_LAST_GOOD.equals(track.sourceType)
                        || (canResumePaused && now < pausedVisibleUntilAt));
                break;
            case Prefs.PAUSE_BEHAVIOR_KEEP:
            default:
                activePlayer = hasTrack && (playingNow
                        || TrackSnapshot.SOURCE_MEDIA_SESSION.equals(track.sourceType)
                        || TrackSnapshot.SOURCE_NOTIFICATION.equals(track.sourceType)
                        || TrackSnapshot.SOURCE_LAST_GOOD.equals(track.sourceType));
                break;
        }
        boolean softRecoveryMode = prefs.featureSoftRecoverySystemWindows();
        boolean conflictOverlay = prefs.isConflictApp(appState.foregroundPackage);
        boolean recentTransientUi = softRecoveryMode && com.navioverlay.car.services.ForegroundState.hasRecentTransientUi();

        if (!nav) {
            if (recentTransientUi) {
                wasNavigatorVisible = true;
                TrackOverlayManager.noteTransientInterruption(app, 2400L);
                debug(now, "hold: recent transient while nav=false fg=" + appState.foregroundPackage);
                return 700;
            }
            wasNavigatorVisible = false;
            wasPlayerActive = false;
            wasTrackPlaying = false;
            pausedVisibleUntilAt = 0L;
            TrackOverlayManager.hideNow();
            debug(now, "sleep: nav=false fg=" + appState.foregroundPackage);
            return 3000;
        }

        if (softRecoveryMode && appState.transientOverlay) {
            wasNavigatorVisible = true;
            TrackOverlayManager.noteTransientInterruption(app, 2400L);
            debug(now, "transient overlay soft-hold fg=" + appState.foregroundPackage);
            return 900;
        }

        if (!activePlayer) {
            if (recentTransientUi) {
                wasNavigatorVisible = true;
                TrackOverlayManager.noteTransientInterruption(app, 2400L);
                debug(now, "hold: recent transient while music=false fg=" + appState.foregroundPackage);
                return 700;
            }
            wasNavigatorVisible = true;
            wasPlayerActive = false;
            wasTrackPlaying = false;
            TrackOverlayManager.hideNow();
            debug(now, "nav=true music=false fg=" + appState.foregroundPackage);
            return 1500;
        }

        if (appState.transientOverlay) {
            wasNavigatorVisible = true;
            if (softRecoveryMode || conflictOverlay) {
                TrackOverlayManager.noteTransientInterruption(app, 2400L);
            }
            debug(now, "transient overlay, keep engine warm fg=" + appState.foregroundPackage);
            return 900;
        }

        if (TrackSnapshot.SOURCE_LAST_GOOD.equals(track.sourceType)) {
            wasNavigatorVisible = true;
            debug(now, "waiting fresh metadata, keep polling fg=" + appState.foregroundPackage);
            return 450;
        }

        boolean enteredNavigator = !wasNavigatorVisible;
        boolean enteredPlayerActive = !wasPlayerActive;
        boolean sameTrack = sameLogicalTrack(track, lastShownPackage, lastShownTitle, lastShownArtist);
        boolean changed = !sameTrack;
        boolean sameTrackSuppressed = sameTrack && (now - lastShownAt) < SAME_TRACK_SUPPRESS_MS;
        boolean coolDownPassed = now - lastShownAt > Math.max(1000, Math.min(4000, prefs.displayMs()));
        boolean restoreAfterNav = enteredNavigator && prefs.featureHideWithNavigation();
        boolean restoreWhilePlaying = enteredNavigator && prefs.displayWhilePlaying() && hasTrack;
        if ((restoreWhilePlaying || enteredPlayerActive || !sameTrackSuppressed) && (changed || restoreAfterNav || restoreWhilePlaying || enteredPlayerActive || (enteredNavigator && coolDownPassed))) {
            Logx.d("ENGINE show overlay fg=" + appState.foregroundPackage + " reason=" + (floating ? "floating" : appState.reason));
            if (enteredNavigator) {
                TrackOverlayManager.markRestoreRequired();
            }
            TrackOverlayManager.showTrack(app, track);
            if (changed) prefs.pushRecentTrack(track.artist, track.title);
            lastShownKey = track.key();
            lastShownPackage = safe(track.sourcePackage);
            lastShownTitle = safe(track.title);
            lastShownArtist = safe(track.artist);
            lastShownAt = now;
        }
        wasNavigatorVisible = true;
        wasPlayerActive = true;
        wasTrackPlaying = playingNow;
        return appState.transientOverlay ? 900 : 700;
    }

    private void debug(long now, String msg) {
        if (now - lastDebugAt > 5000) {
            lastDebugAt = now;
            Logx.d("ENGINE " + msg);
        }
    }

    private static boolean sameLogicalTrack(TrackSnapshot track, String pkg, String title, String artist) {
        if (track == null) return false;
        String currentPkg = safe(track.sourcePackage);
        String currentTitle = safe(track.title);
        String currentArtist = safe(track.artist);
        String shownPkg = safe(pkg);
        String shownTitle = safe(title);
        String shownArtist = safe(artist);
        if (currentPkg.isEmpty() || shownPkg.isEmpty()) return false;
        if (!currentPkg.equals(shownPkg)) return false;
        if (!currentTitle.isEmpty() && !shownTitle.isEmpty() && currentTitle.equals(shownTitle)) {
            return currentArtist.isEmpty() || shownArtist.isEmpty() || currentArtist.equals(shownArtist);
        }
        return false;
    }

    private static String safe(String value) {
        return value == null ? "" : TrackSnapshot.clean(value);
    }
}
