package com.navioverlay.car.core;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

public final class OverlayShapeDrawable extends Drawable {
    public static final int STYLE_DEFAULT = 0;
    public static final int STYLE_WAVE = 1;
    public static final int STYLE_TECH = 2;
    public static final int STYLE_PREMIUM = 3;
    public static final int STYLE_SPIKES = 4;
    public static final int STYLE_OVAL = 5;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final int style;
    private final float radiusPx;
    private final float strokePx;

    public OverlayShapeDrawable(int fillColor, int strokeColor, float radiusPx, float strokePx, int style) {
        this.style = style;
        this.radiusPx = Math.max(0f, radiusPx);
        this.strokePx = Math.max(0f, strokePx);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(fillColor);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(strokeColor);
        strokePaint.setStrokeWidth(this.strokePx);
    }

    @Override
    public void draw(Canvas canvas) {
        rect.set(getBounds());
        rect.inset(strokePx > 0f ? strokePx * 0.5f : 0f, strokePx > 0f ? strokePx * 0.5f : 0f);
        if (rect.width() <= 0f || rect.height() <= 0f) return;

        if (style == STYLE_DEFAULT) {
            canvas.drawRoundRect(rect, radiusPx, radiusPx, fillPaint);
            if (strokePx > 0f) canvas.drawRoundRect(rect, radiusPx, radiusPx, strokePaint);
            return;
        }
        if (style == STYLE_OVAL) {
            canvas.drawOval(rect, fillPaint);
            if (strokePx > 0f) canvas.drawOval(rect, strokePaint);
            return;
        }

        buildPath();
        canvas.drawPath(path, fillPaint);
        if (strokePx > 0f) canvas.drawPath(path, strokePaint);
    }

    private void buildPath() {
        path.reset();
        float l = rect.left;
        float t = rect.top;
        float r = rect.right;
        float b = rect.bottom;
        float w = rect.width();
        float h = rect.height();

        if (style == STYLE_WAVE) {
            float corner = Math.min(radiusPx, Math.min(w, h) * 0.16f);
            float waveY = Math.max(5f, Math.min(h * 0.11f, 10f));
            float waveX = Math.max(5f, Math.min(w * 0.03f, 10f));
            path.moveTo(l + corner, t);
            path.quadTo(l + w * 0.25f, t - waveY, l + w * 0.5f, t);
            path.quadTo(l + w * 0.75f, t - waveY, r - corner, t);
            path.quadTo(r, t, r, t + corner);
            path.quadTo(r + waveX, t + h * 0.25f, r, t + h * 0.5f);
            path.quadTo(r + waveX, t + h * 0.75f, r, b - corner);
            path.quadTo(r, b, r - corner, b);
            path.quadTo(l + w * 0.75f, b + waveY, l + w * 0.5f, b);
            path.quadTo(l + w * 0.25f, b + waveY, l + corner, b);
            path.quadTo(l, b, l, b - corner);
            path.quadTo(l - waveX, t + h * 0.75f, l, t + h * 0.5f);
            path.quadTo(l - waveX, t + h * 0.25f, l, t + corner);
            path.close();
            return;
        }

        if (style == STYLE_SPIKES) {
            float corner = Math.min(radiusPx, Math.min(w, h) * 0.12f);
            float spike = Math.max(8f, Math.min(h * 0.18f, 16f));
            float step = w / 6f;
            path.moveTo(l + corner, t);
            path.lineTo(l + step * 0.8f, t);
            path.lineTo(l + step * 1.15f, t - spike);
            path.lineTo(l + step * 1.5f, t);
            path.lineTo(l + step * 2.5f, t);
            path.lineTo(l + step * 2.85f, t - spike);
            path.lineTo(l + step * 3.2f, t);
            path.lineTo(r - corner, t);
            path.quadTo(r, t, r, t + corner);
            path.lineTo(r, b - corner);
            path.quadTo(r, b, r - corner, b);
            path.lineTo(l + step * 3.2f, b);
            path.lineTo(l + step * 2.85f, b + spike);
            path.lineTo(l + step * 2.5f, b);
            path.lineTo(l + step * 1.5f, b);
            path.lineTo(l + step * 1.15f, b + spike);
            path.lineTo(l + step * 0.8f, b);
            path.lineTo(l + corner, b);
            path.quadTo(l, b, l, b - corner);
            path.lineTo(l, t + corner);
            path.quadTo(l, t, l + corner, t);
            path.close();
            return;
        }

        if (style == STYLE_TECH) {
            float cut = Math.max(10f, Math.min(Math.min(w, h) * 0.16f, 22f));
            float notch = Math.max(10f, Math.min(h * 0.18f, 18f));
            path.moveTo(l + cut, t);
            path.lineTo(r - cut, t);
            path.lineTo(r, t + cut);
            path.lineTo(r, t + h * 0.35f);
            path.lineTo(r - notch, t + h * 0.5f);
            path.lineTo(r, t + h * 0.65f);
            path.lineTo(r, b - cut);
            path.lineTo(r - cut, b);
            path.lineTo(l + cut, b);
            path.lineTo(l, b - cut);
            path.lineTo(l, t + h * 0.65f);
            path.lineTo(l + notch, t + h * 0.5f);
            path.lineTo(l, t + h * 0.35f);
            path.lineTo(l, t + cut);
            path.close();
            return;
        }

        float bevel = Math.max(12f, Math.min(Math.min(w, h) * 0.18f, 28f));
        float inset = Math.max(10f, Math.min(w * 0.08f, 24f));
        path.moveTo(l + bevel, t);
        path.lineTo(r - bevel, t);
        path.lineTo(r, t + bevel);
        path.lineTo(r - inset, t + h * 0.5f);
        path.lineTo(r, b - bevel);
        path.lineTo(r - bevel, b);
        path.lineTo(l + bevel, b);
        path.lineTo(l, b - bevel);
        path.lineTo(l + inset, t + h * 0.5f);
        path.lineTo(l, t + bevel);
        path.close();
    }

    @Override
    public void setAlpha(int alpha) {
        fillPaint.setAlpha(alpha);
        strokePaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
        strokePaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
