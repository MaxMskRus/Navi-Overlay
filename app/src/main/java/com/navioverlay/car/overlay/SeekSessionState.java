package com.navioverlay.car.overlay;

import android.os.SystemClock;
import com.navioverlay.car.engine.TrackSnapshot;

/**
 * Отдельное живое состояние seek-сессии.
 *
 * Идея простая: overlay не должен ждать каждый раз идеальный snapshot от плеера.
 * Как только мы один раз получили валидные duration/position, дальше можем локально
 * экстраполировать позицию, пока не приедет более свежий playback state.
 */
final class SeekSessionState {
    private String sourceKey = "";
    private long durationMs = -1L;
    private long anchorPositionMs = -1L;
    private long anchorElapsedRealtimeMs = 0L;
    private float playbackSpeed = 0f;
    private boolean playing = false;

    void reset() {
        sourceKey = "";
        durationMs = -1L;
        anchorPositionMs = -1L;
        anchorElapsedRealtimeMs = 0L;
        playbackSpeed = 0f;
        playing = false;
    }

    boolean hasRange() {
        return durationMs > 1000L && anchorPositionMs >= 0L;
    }

    boolean matches(TrackSnapshot snapshot) {
        return snapshot != null && sourceKey.equals(buildKey(snapshot));
    }

    void updateFromSnapshot(TrackSnapshot snapshot) {
        if (snapshot == null || !snapshot.hasText()) return;
        String nextKey = buildKey(snapshot);
        if (!nextKey.equals(sourceKey)) {
            reset();
            sourceKey = nextKey;
        }
        playing = snapshot.playing;
        playbackSpeed = snapshot.playing ? 1f : 0f;
        if (snapshot.durationMs > 1000L) {
            durationMs = snapshot.durationMs;
        }
        if (snapshot.positionMs >= 0L) {
            anchorPositionMs = snapshot.positionMs;
            anchorElapsedRealtimeMs = SystemClock.elapsedRealtime();
        }
    }

    void markPlaying(boolean isPlaying) {
        if (playing == isPlaying) return;
        long now = SystemClock.elapsedRealtime();
        if (playing) {
            anchorPositionMs = estimatePositionAt(now);
            anchorElapsedRealtimeMs = now;
        }
        playing = isPlaying;
        playbackSpeed = isPlaying ? 1f : 0f;
    }

    void applyUserSeek(long targetPositionMs) {
        if (targetPositionMs < 0L) return;
        anchorPositionMs = clamp(targetPositionMs, 0L, durationMs > 0L ? durationMs : targetPositionMs);
        anchorElapsedRealtimeMs = SystemClock.elapsedRealtime();
    }

    long estimatePositionNow() {
        return estimatePositionAt(SystemClock.elapsedRealtime());
    }

    long durationMs() {
        return durationMs;
    }

    long estimatePositionAt(long elapsedRealtimeMs) {
        if (!hasRange()) return -1L;
        long position = anchorPositionMs;
        if (playing && playbackSpeed > 0f && anchorElapsedRealtimeMs > 0L) {
            long delta = Math.max(0L, elapsedRealtimeMs - anchorElapsedRealtimeMs);
            position += Math.round(delta * playbackSpeed);
        }
        return clamp(position, 0L, durationMs > 0L ? durationMs : position);
    }

    private static long clamp(long value, long min, long max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private static String buildKey(TrackSnapshot snapshot) {
        if (snapshot == null) return "";
        return TrackSnapshot.clean(snapshot.sourcePackage)
                + "|" + TrackSnapshot.clean(snapshot.artist)
                + "|" + TrackSnapshot.clean(snapshot.title);
    }
}
