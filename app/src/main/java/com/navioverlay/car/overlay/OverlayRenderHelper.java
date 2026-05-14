package com.navioverlay.car.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.navioverlay.car.core.OverlayShapeDrawable;
import com.navioverlay.car.core.Prefs;
import com.navioverlay.car.core.Ui;

final class OverlayRenderHelper {
    private OverlayRenderHelper() {}

    static int effectiveWindowAlphaPercent(Prefs prefs, long volumeDimUntilAt, long now) {
        int basePercent = Math.max(15, Math.min(100, prefs.windowAlpha()));
        if (volumeDimUntilAt > now) {
            return Math.max(12, Math.round(basePercent * 0.35f));
        }
        return basePercent;
    }

    static void applyPanelBackground(Context c, LinearLayout panelView, int bgColor, int strokeWidth, int strokeColor, int corner, int alphaPercent, int design) {
        if (panelView == null) return;
        Drawable drawable;
        if (design == 2) {
            drawable = new OverlayShapeDrawable(bgColor, strokeColor, Ui.dp(c, corner), Ui.dp(c, strokeWidth), OverlayShapeDrawable.STYLE_WAVE);
        } else if (design == 3) {
            drawable = new OverlayShapeDrawable(bgColor, strokeColor, Ui.dp(c, corner), Ui.dp(c, strokeWidth), OverlayShapeDrawable.STYLE_TECH);
        } else if (design == 8) {
            drawable = new OverlayShapeDrawable(bgColor, strokeColor, Ui.dp(c, corner), Ui.dp(c, strokeWidth), OverlayShapeDrawable.STYLE_SPIKES);
        } else if (design == 9) {
            drawable = new OverlayShapeDrawable(bgColor, strokeColor, Ui.dp(c, corner), Ui.dp(c, strokeWidth), OverlayShapeDrawable.STYLE_OVAL);
        } else if (design == 7) {
            drawable = new OverlayShapeDrawable(bgColor, strokeColor, Ui.dp(c, corner), Ui.dp(c, strokeWidth), OverlayShapeDrawable.STYLE_PREMIUM);
        } else {
            drawable = Ui.stroke(bgColor, strokeWidth, strokeColor, corner, c);
        }
        drawable.mutate().setAlpha(windowPercentToDrawableAlpha(alphaPercent));
        panelView.setBackground(drawable);
    }

    static void applyPanelBackgroundAlpha(LinearLayout panelView, int alphaPercent) {
        if (panelView == null) return;
        Drawable bg = panelView.getBackground();
        if (bg == null) return;
        bg.mutate().setAlpha(windowPercentToDrawableAlpha(alphaPercent));
        panelView.invalidate();
    }

    static void applyContentDimAlpha(TextView lineView, ImageView albumArtView, LinearLayout controlsRow, LinearLayout seekContainer, float alpha) {
        if (lineView != null) lineView.setAlpha(alpha);
        if (albumArtView != null) albumArtView.setAlpha(albumArtView.getVisibility() == ImageView.VISIBLE ? alpha : 0f);
        if (controlsRow != null) controlsRow.setAlpha(alpha);
        if (seekContainer != null) seekContainer.setAlpha(alpha);
    }

    static int windowPercentToDrawableAlpha(int alphaPercent) {
        int clamped = Math.max(0, Math.min(100, alphaPercent));
        return Math.round(255f * (clamped / 100f));
    }

    static boolean sameBitmap(Bitmap a, Bitmap b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight();
    }
}
