package com.navioverlay.car.engine;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import com.navioverlay.car.core.Logx;
import com.navioverlay.car.core.Prefs;
import com.navioverlay.car.services.MusicNotificationListenerService;
import java.util.List;

/**
 * Читает состояние музыки по запросу. Не ждёт callbacks.
 * Источники: MediaSession (основной), активные notification (fallback), AudioManager (играет/не играет).
 */
public final class MusicStateDetector {
    private static final long LAST_GOOD_TTL_MS = 1500L;

    private final Context app;
    private final Prefs prefs;
    private TrackSnapshot lastGood = TrackSnapshot.empty();
    private long lastGoodAt = 0L;

    public MusicStateDetector(Context context) {
        app = context.getApplicationContext();
        prefs = new Prefs(app);
    }

    public TrackSnapshot read() {
        boolean audioActive = isAudioPlaybackActive();
        StatusBarNotification[] notifications = MusicNotificationListenerService.latestNotifications();
        TrackSnapshot fromSession = readFromMediaSessions(notifications);
        TrackSnapshot fromNotifications = readFromActiveNotifications(notifications, audioActive);
        if (fromSession.hasText() && fromSession.playing) return remember(fromSession);
        if (fromNotifications.hasText() && fromNotifications.playing) return remember(fromNotifications);
        if (fromNotifications.hasText()) return fromNotifications;
        if (fromSession.hasText()) return fromSession;

        if (audioActive && lastGood.hasText() && System.currentTimeMillis() - lastGoodAt < LAST_GOOD_TTL_MS) {
            return new TrackSnapshot(lastGood.sourcePackage, lastGood.title, lastGood.artist, true, TrackSnapshot.SOURCE_LAST_GOOD, lastGood.albumArt);
        }
        return TrackSnapshot.empty();
    }

    private TrackSnapshot remember(TrackSnapshot s) {
        if (s != null && s.hasText() && s.playing) {
            lastGood = s;
            lastGoodAt = System.currentTimeMillis();
        }
        return s == null ? TrackSnapshot.empty() : s;
    }

    private TrackSnapshot readFromMediaSessions(StatusBarNotification[] notifications) {
        try {
            MediaSessionManager msm = (MediaSessionManager) app.getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName listener = new ComponentName(app, MusicNotificationListenerService.class);
            List<MediaController> controllers = msm.getActiveSessions(listener);
            if (controllers == null) return TrackSnapshot.empty();

            TrackSnapshot fallback = TrackSnapshot.empty();
            for (MediaController c : controllers) {
                if (c == null) continue;
                String pkg = c.getPackageName();
                if (pkg == null || pkg.isEmpty()) continue;
                if (!prefs.players().isEmpty() && !prefs.isPlayer(pkg)) continue;

                boolean playing = isPlaying(c);
                MediaMetadata m = c.getMetadata();
                if (m == null) continue;
                String title = first(m.getString(MediaMetadata.METADATA_KEY_TITLE), m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
                String artist = first(m.getString(MediaMetadata.METADATA_KEY_ARTIST), m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST), m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE));
                Bitmap albumArt = normalizeBitmap(firstBitmap(
                        m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART),
                        m.getBitmap(MediaMetadata.METADATA_KEY_ART),
                        m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)));
                TrackSnapshot s = new TrackSnapshot(pkg, title, artist, playing, TrackSnapshot.SOURCE_MEDIA_SESSION, albumArt);
                if (s.hasText() && playing) return s;
                if (s.hasText() && hasActiveNotificationForPackage(notifications, pkg)) fallback = s;
            }
            return fallback;
        } catch (SecurityException se) {
            Logx.e("MediaSession permission/listener unavailable", se);
            return TrackSnapshot.empty();
        } catch (Throwable t) {
            Logx.e("readFromMediaSessions failed", t);
            return TrackSnapshot.empty();
        }
    }

    private TrackSnapshot readFromActiveNotifications(StatusBarNotification[] arr, boolean audioActive) {
        try {
            if (arr == null) return TrackSnapshot.empty();
            for (StatusBarNotification sbn : arr) {
                if (sbn == null || sbn.getNotification() == null) continue;
                String pkg = sbn.getPackageName();
                if (pkg == null || pkg.isEmpty()) continue;
                if (!prefs.players().isEmpty() && !prefs.isPlayer(pkg)) continue;
                Notification n = sbn.getNotification();
                Bundle e = n.extras;
                if (e == null) continue;
                String title = str(e.getCharSequence(Notification.EXTRA_TITLE));
                String text = str(e.getCharSequence(Notification.EXTRA_TEXT));
                String big = str(e.getCharSequence(Notification.EXTRA_BIG_TEXT));
                String sub = str(e.getCharSequence(Notification.EXTRA_SUB_TEXT));
                String artist = first(text, big, sub);
                Bitmap albumArt = normalizeBitmap(extractNotificationArt(n, e));
                TrackSnapshot s = new TrackSnapshot(pkg, title, artist, audioActive, TrackSnapshot.SOURCE_NOTIFICATION, albumArt);
                if (s.hasText()) return s;
            }
        } catch (Throwable t) {
            Logx.e("readFromActiveNotifications failed", t);
        }
        return TrackSnapshot.empty();
    }

    private boolean hasActiveNotificationForPackage(StatusBarNotification[] arr, String pkg) {
        if (arr == null || pkg == null || pkg.isEmpty()) return false;
        for (StatusBarNotification sbn : arr) {
            if (sbn == null) continue;
            if (pkg.equals(sbn.getPackageName()) && sbn.getNotification() != null) return true;
        }
        return false;
    }

    private static boolean isPlaying(MediaController c) {
        try {
            PlaybackState s = c.getPlaybackState();
            if (s == null) return false;
            int st = s.getState();
            return st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING
                    || st == PlaybackState.STATE_FAST_FORWARDING || st == PlaybackState.STATE_REWINDING
                    || st == PlaybackState.STATE_SKIPPING_TO_NEXT || st == PlaybackState.STATE_SKIPPING_TO_PREVIOUS;
        } catch (Throwable t) { return false; }
    }

    private boolean isAudioPlaybackActive() {
        try {
            AudioManager audioManager = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            return audioManager != null && audioManager.isMusicActive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String first(String... values) {
        if (values == null) return "";
        for (String v : values) {
            v = TrackSnapshot.clean(v);
            if (!v.isEmpty()) return v;
        }
        return "";
    }

    private Bitmap extractNotificationArt(Notification notification, Bundle extras) {
        Bitmap fromExtras = firstBitmap(
                bitmapOf(extras.get(Notification.EXTRA_LARGE_ICON_BIG)),
                bitmapOf(extras.get(Notification.EXTRA_LARGE_ICON)));
        if (fromExtras != null) return fromExtras;

        try {
            Icon icon = notification.getLargeIcon();
            return bitmapOf(icon);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Bitmap firstBitmap(Bitmap... values) {
        if (values == null) return null;
        for (Bitmap value : values) {
            if (value != null && value.getWidth() > 0 && value.getHeight() > 0) return value;
        }
        return null;
    }

    private Bitmap normalizeBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;
        try {
            int maxSide = Math.max(96, Math.round(app.getResources().getDisplayMetrics().density * 72f));
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (width <= 0 || height <= 0) return null;
            if (width <= maxSide && height <= maxSide) return bitmap;
            float scale = Math.min((float) maxSide / width, (float) maxSide / height);
            int targetW = Math.max(1, Math.round(width * scale));
            int targetH = Math.max(1, Math.round(height * scale));
            return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true);
        } catch (Throwable t) {
            Logx.e("normalizeBitmap failed", t);
            return null;
        }
    }

    private Bitmap bitmapOf(Object value) {
        if (value instanceof Bitmap) return (Bitmap) value;
        if (value instanceof Icon) return bitmapOf((Icon) value);
        if (value instanceof BitmapDrawable) return ((BitmapDrawable) value).getBitmap();
        if (value instanceof Drawable && value instanceof BitmapDrawable) return ((BitmapDrawable) value).getBitmap();
        return null;
    }

    private Bitmap bitmapOf(Icon icon) {
        if (icon == null) return null;
        try {
            Drawable drawable = icon.loadDrawable(app);
            if (drawable instanceof BitmapDrawable) return ((BitmapDrawable) drawable).getBitmap();
        } catch (Throwable ignored) {}
        return null;
    }

    private static String str(CharSequence c) { return c == null ? "" : TrackSnapshot.clean(c.toString()); }
}
