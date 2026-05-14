package com.navioverlay.car.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import com.navioverlay.car.R;
import com.navioverlay.car.core.Prefs;
import com.navioverlay.car.core.Ui;
import com.navioverlay.car.engine.TrackSnapshot;

final class AlbumArtHelper {
    private static final int MAX_RETRY_COUNT = 12;

    private AlbumArtHelper() {}

    static boolean shouldScheduleRefresh(Prefs prefs, Bitmap rawAlbumArt, TrackSnapshot lastTrackSnapshot) {
        if (prefs == null || !prefs.featureAlbumArt()) return false;
        boolean missing = rawAlbumArt == null || rawAlbumArt.isRecycled();
        return missing && lastTrackSnapshot != null && lastTrackSnapshot.hasText();
    }

    static long nextRetryDelayMs(int retryCount) {
        if (retryCount >= MAX_RETRY_COUNT) return -1L;
        return retryCount == 0 ? 450L : 900L;
    }

    static int maxRetryCount() {
        return MAX_RETRY_COUNT;
    }

    static Bitmap getPlaceholderAlbumArt(Context c, int textSize, Bitmap cached) {
        int sizePx = Ui.dp(c, Math.max(72, Math.min(170, Math.round(textSize * 2.7f))));
        if (cached != null
                && !cached.isRecycled()
                && cached.getWidth() == sizePx
                && cached.getHeight() == sizePx) {
            return cached;
        }
        try {
            Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            canvas.drawColor(0xFF162235);
            Bitmap icon = BitmapFactory.decodeResource(c.getResources(), R.drawable.album_art_placeholder);
            if (icon != null) {
                int iconSize = Math.round(sizePx * 0.72f);
                Bitmap scaled = Bitmap.createScaledBitmap(icon, iconSize, iconSize, true);
                float left = (sizePx - iconSize) / 2f;
                float top = (sizePx - iconSize) / 2f;
                canvas.drawBitmap(scaled, left, top, null);
                if (scaled != icon) scaled.recycle();
            } else {
                Paint note = new Paint(Paint.ANTI_ALIAS_FLAG);
                note.setColor(0xFFEAF8FF);
                note.setTextAlign(Paint.Align.CENTER);
                note.setTypeface(Typeface.DEFAULT_BOLD);
                note.setTextSize(sizePx * 0.42f);
                Paint.FontMetrics fm = note.getFontMetrics();
                float y = sizePx * 0.56f - ((fm.ascent + fm.descent) / 2f);
                canvas.drawText("♪", sizePx * 0.5f, y, note);
            }
            return bmp;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
